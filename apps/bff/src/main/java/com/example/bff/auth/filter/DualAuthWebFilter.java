package com.example.bff.auth.filter;

import com.example.bff.auth.model.AuthContext;
import com.example.bff.auth.util.AuthContextResolver;
import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.authz.model.PermissionSet;
import com.example.bff.common.dto.ErrorResponse;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.ExternalIntegrationProperties;
import com.example.bff.config.properties.IdpProperties;
import com.example.bff.config.properties.SecurityPathsProperties;
import com.example.bff.session.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Unified authentication filter supporting HSID (session cookie) and PROXY (header-based via mTLS) auth.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class DualAuthWebFilter implements WebFilter {

    private static final String SESSION_COOKIE_NAME = "BFF_SESSION";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String AUTH_TYPE_HEADER = "X-Auth-Type";

    private final SessionService sessionService;
    private final SecurityPathsProperties securityPaths;
    private final IdpProperties idpProperties;
    private final ExternalIntegrationProperties externalProperties;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        PathCategory category = determinePathCategory(path);

        log.debug("DualAuthWebFilter: path={}, category={}", path, category);

        // Public paths - no auth required
        if (category == PathCategory.PUBLIC) {
            return chain.filter(exchange);
        }

        String correlationId = getCorrelationId(exchange);

        // Try to resolve auth context
        return resolveAuthContext(exchange)
                .flatMap(authContext -> {
                    // Validate auth type matches path requirements
                    if (!isAuthTypeAllowedForPath(authContext, category)) {
                        return authTypeMismatchResponse(exchange, authContext, category, correlationId);
                    }

                    // Store AuthContext in exchange for downstream use
                    AuthContextResolver.store(exchange, authContext);

                    log.info("Auth resolved: type={}, userId={}, persona={}, path={}",
                            authContext.authType(),
                            StringSanitizer.forLog(authContext.userId()),
                            StringSanitizer.forLog(authContext.persona()),
                            path);

                    return chain.filter(exchange);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // No auth context resolved
                    if (category == PathCategory.DUAL_AUTH || category == PathCategory.SESSION_ONLY
                            || category == PathCategory.PROXY_ONLY) {
                        return unauthorizedResponse(exchange, correlationId,
                                ErrorResponse.Codes.UNAUTHORIZED,
                                "Authentication required");
                    }
                    return chain.filter(exchange);
                }));
    }

    @NonNull
    private Mono<AuthContext> resolveAuthContext(@NonNull ServerWebExchange exchange) {
        // First try HSID (session cookie)
        return resolveHsidContext(exchange)
                .switchIfEmpty(Mono.defer(() -> resolveProxyContext(exchange)));
    }

    @NonNull
    private Mono<AuthContext> resolveHsidContext(@NonNull ServerWebExchange exchange) {
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);

        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            return Mono.empty();
        }

        String sessionId = sessionCookie.getValue();
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            log.debug("Invalid session ID format");
            return Mono.empty();
        }

        return sessionService.getSession(sessionId)
                .flatMap(session -> sessionService.getPermissions(sessionId)
                        .defaultIfEmpty(PermissionSet.empty(session.userId(), session.persona()))
                        .map(permissions -> {
                            // Build SubjectAttributes for ABAC
                            SubjectAttributes subject = SubjectAttributes.forHsid(
                                    session.userId(),
                                    session.persona(),
                                    permissions
                            );

                            // For HSID, effectiveMemberId is userId (or selectedChild if parent)
                            // The actual selectedChild is managed at controller level via request body
                            String effectiveMemberId = session.userId();

                            return AuthContext.forHsid(
                                    session.userId(),
                                    effectiveMemberId,
                                    session.persona(),
                                    sessionId,
                                    subject
                            );
                        }));
    }

    @NonNull
    private Mono<AuthContext> resolveProxyContext(@NonNull ServerWebExchange exchange) {
        // Check if this is a proxy request (has X-Auth-Type: proxy or X-Client-Id)
        String authType = exchange.getRequest().getHeaders().getFirst(AUTH_TYPE_HEADER);
        String clientId = exchange.getRequest().getHeaders().getFirst(
                externalProperties.headers().clientId());

        if (!"proxy".equalsIgnoreCase(authType) && (clientId == null || clientId.isBlank())) {
            return Mono.empty();
        }

        String correlationId = getCorrelationId(exchange);

        // Extract required headers
        String persona = StringSanitizer.headerValue(exchange.getRequest().getHeaders()
                .getFirst(externalProperties.headers().persona()));
        String userId = StringSanitizer.headerValue(exchange.getRequest().getHeaders()
                .getFirst(externalProperties.headers().userId()));
        String idpType = StringSanitizer.headerValue(exchange.getRequest().getHeaders()
                .getFirst(externalProperties.headers().idpType()));
        String partnerId = StringSanitizer.headerValue(exchange.getRequest().getHeaders()
                .getFirst(externalProperties.headers().partnerId()));

        // Validate required headers
        if (persona == null || persona.isBlank()) {
            log.warn("Proxy auth missing persona header");
            return Mono.error(new ProxyAuthException(
                    ErrorResponse.Codes.UNAUTHORIZED,
                    "X-Persona header is required"));
        }

        if (userId == null || userId.isBlank()) {
            log.warn("Proxy auth missing user-id header");
            return Mono.error(new ProxyAuthException(
                    ErrorResponse.Codes.UNAUTHORIZED,
                    "X-User-Id header is required"));
        }

        // Validate IDP-persona mapping
        if (idpType != null && !idpType.isBlank()) {
            if (!idpProperties.isValidIdpType(idpType)) {
                log.warn("Invalid IDP type: {}", StringSanitizer.forLog(idpType));
                return Mono.error(new ProxyAuthException(
                        ErrorResponse.Codes.INVALID_IDP_TYPE,
                        "Unrecognized IDP type: " + idpType));
            }

            if (!idpProperties.isPersonaAllowed(idpType, persona)) {
                Set<String> allowed = idpProperties.getAllowedPersonas(idpType);
                log.warn("IDP-persona mismatch: idp={}, persona={}, allowed={}",
                        StringSanitizer.forLog(idpType), StringSanitizer.forLog(persona), allowed);
                return Mono.error(new ProxyAuthException(
                        ErrorResponse.Codes.IDP_PERSONA_MISMATCH,
                        "Persona '" + persona + "' is not allowed for IDP '" + idpType + "'"));
            }
        }

        // Build SubjectAttributes for ABAC
        // Note: memberId will be set from request body at controller level
        SubjectAttributes subject = SubjectAttributes.forProxy(
                userId,
                persona,
                partnerId,
                null,  // memberId - set from request body
                userId,
                null   // operatorName - could be added if needed
        );

        AuthContext authContext = AuthContext.forProxy(
                userId,
                null,  // effectiveMemberId - set from request body at controller
                persona,
                partnerId,
                userId,
                null,  // operatorName
                idpType,
                subject
        );

        return Mono.just(authContext);
    }

    @NonNull
    private PathCategory determinePathCategory(@NonNull String path) {
        // Check public paths
        for (String pattern : securityPaths.getPublicPatterns()) {
            if (pathMatcher.match(pattern, path)) {
                return PathCategory.PUBLIC;
            }
        }

        // Check dual-auth paths
        for (String pattern : securityPaths.getDualAuthPatterns()) {
            if (pathMatcher.match(pattern, path)) {
                return PathCategory.DUAL_AUTH;
            }
        }

        // Check session-only paths
        for (String pattern : securityPaths.getSessionAuthPatterns()) {
            if (pathMatcher.match(pattern, path)) {
                return PathCategory.SESSION_ONLY;
            }
        }

        // Check proxy-only paths
        for (String pattern : securityPaths.getProxyAuthPatterns()) {
            if (pathMatcher.match(pattern, path)) {
                return PathCategory.PROXY_ONLY;
            }
        }

        // Default: require some form of auth
        return PathCategory.DUAL_AUTH;
    }

    private boolean isAuthTypeAllowedForPath(@NonNull AuthContext auth, @NonNull PathCategory category) {
        return switch (category) {
            case PUBLIC -> true;
            case SESSION_ONLY -> auth.isHsid();
            case PROXY_ONLY -> auth.isProxy();
            case DUAL_AUTH -> true;  // Both allowed
        };
    }

    @NonNull
    private Mono<Void> authTypeMismatchResponse(
            @NonNull ServerWebExchange exchange,
            @NonNull AuthContext auth,
            @NonNull PathCategory category,
            @NonNull String correlationId) {

        String code = category == PathCategory.SESSION_ONLY
                ? ErrorResponse.Codes.SESSION_REQUIRED
                : ErrorResponse.Codes.PROXY_REQUIRED;
        String message = category == PathCategory.SESSION_ONLY
                ? "This endpoint requires session authentication"
                : "This endpoint requires proxy authentication";

        log.warn("Auth type mismatch: authType={}, required={}, path={}",
                auth.authType(), category, exchange.getRequest().getPath().value());

        return forbiddenResponse(exchange, correlationId, code, message);
    }

    @NonNull
    private Mono<Void> unauthorizedResponse(
            @NonNull ServerWebExchange exchange,
            @NonNull String correlationId,
            @NonNull String code,
            @NonNull String message) {

        return errorResponse(exchange, HttpStatus.UNAUTHORIZED, correlationId,
                ErrorResponse.Categories.AUTHENTICATION_REQUIRED, code, message, null);
    }

    @NonNull
    private Mono<Void> forbiddenResponse(
            @NonNull ServerWebExchange exchange,
            @NonNull String correlationId,
            @NonNull String code,
            @NonNull String message) {

        return errorResponse(exchange, HttpStatus.FORBIDDEN, correlationId,
                ErrorResponse.Categories.ACCESS_DENIED, code, message, null);
    }

    @NonNull
    private Mono<Void> errorResponse(
            @NonNull ServerWebExchange exchange,
            @NonNull HttpStatus status,
            @NonNull String correlationId,
            @NonNull String error,
            @NonNull String code,
            @NonNull String message,
            @Nullable Map<String, Object> details) {

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse errorResponse = new ErrorResponse(
                error,
                code,
                message,
                correlationId,
                Instant.now(),
                exchange.getRequest().getPath().value(),
                details
        );

        try {
            String body = objectMapper.writeValueAsString(errorResponse);
            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse()
                            .bufferFactory()
                            .wrap(body.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) {
            // Log the serialization failure for debugging
            log.warn("Failed to serialize error response: {}", e.getMessage());
            // Fallback to simple JSON
            String fallback = String.format(
                    "{\"error\":\"%s\",\"code\":\"%s\",\"message\":\"%s\"}",
                    error, code, StringSanitizer.escapeJson(message));
            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse()
                            .bufferFactory()
                            .wrap(fallback.getBytes(StandardCharsets.UTF_8))));
        }
    }

    @NonNull
    private String getCorrelationId(@NonNull ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = exchange.getLogPrefix();
        }
        return correlationId;
    }

    private enum PathCategory {
        PUBLIC,
        SESSION_ONLY,
        PROXY_ONLY,
        DUAL_AUTH
    }

    private static class ProxyAuthException extends RuntimeException {
        private final String code;

        ProxyAuthException(String code, String message) {
            super(message);
            this.code = code;
        }

        String getCode() {
            return code;
        }
    }
}
