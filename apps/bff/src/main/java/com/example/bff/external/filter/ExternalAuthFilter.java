package com.example.bff.external.filter;

import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.ExternalIntegrationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Authenticates external partner requests via mTLS ALB headers.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@ConditionalOnProperty(name = "app.external-integration.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ExternalAuthFilter implements WebFilter {

    // Header names for mapping to proxy auth system
    private static final String PROXY_AUTH_TYPE_HEADER = "X-Auth-Type";
    private static final String PROXY_OPERATOR_ID_HEADER = "X-Operator-Id";
    private static final String PROXY_PARTNER_ID_HEADER = "X-Partner-Id";

    // Validation pattern for identifiers (alphanumeric, dash, underscore)
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,128}$");

    // Max length for header values
    private static final int MAX_HEADER_LENGTH = 256;

    private final ExternalIntegrationProperties properties;

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Skip public paths
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Check if this is an external integration request (via mTLS ALB)
        String clientId = sanitizeHeaderValue(request.getHeaders().getFirst(properties.headers().clientId()));

        if (clientId == null || clientId.isBlank()) {
            // Not an external integration request - pass through
            return chain.filter(exchange);
        }

        // Validate client ID format
        if (!isValidIdentifier(clientId)) {
            log.warn("Invalid client ID format in external request");
            return forbiddenResponse(exchange, "INVALID_CLIENT_ID", "Invalid client identifier format");
        }

        log.debug("Processing external integration request from client: {}", StringSanitizer.forLog(clientId));

        // Validate required headers
        String persona = sanitizeHeaderValue(request.getHeaders().getFirst(properties.headers().persona()));
        String userId = sanitizeHeaderValue(request.getHeaders().getFirst(properties.headers().userId()));
        String idpType = sanitizeHeaderValue(request.getHeaders().getFirst(properties.headers().idpType()));

        // Persona is required
        if (persona == null || persona.isBlank()) {
            log.warn("External request missing persona header from client: {}", StringSanitizer.forLog(clientId));
            return unauthorizedResponse(exchange, "MISSING_PERSONA", "X-Persona header is required");
        }

        // User ID is required
        if (userId == null || userId.isBlank()) {
            log.warn("External request missing user-id header from client: {}", StringSanitizer.forLog(clientId));
            return unauthorizedResponse(exchange, "MISSING_USER_ID", "X-User-Id header is required");
        }

        // Validate identifiers format
        if (!isValidIdentifier(persona)) {
            log.warn("Invalid persona format from client: {}", StringSanitizer.forLog(clientId));
            return forbiddenResponse(exchange, "INVALID_PERSONA", "Invalid persona format");
        }

        if (!isValidIdentifier(userId)) {
            log.warn("Invalid user ID format from client: {}", StringSanitizer.forLog(clientId));
            return forbiddenResponse(exchange, "INVALID_USER_ID", "Invalid user ID format");
        }

        // Validate persona is allowed
        var allowedPersonas = properties.allowedPersonas();
        if (allowedPersonas == null || !allowedPersonas.contains(persona)) {
            log.warn("External request with disallowed persona from client: {}", StringSanitizer.forLog(clientId));
            return forbiddenResponse(exchange, "INVALID_PERSONA", "Persona not allowed");
        }

        // Validate IDP type if configured
        var trustedIdpTypes = properties.trustedIdpTypes();
        if (trustedIdpTypes != null && !trustedIdpTypes.isEmpty()) {
            if (idpType == null || !trustedIdpTypes.contains(idpType)) {
                log.warn("External request with untrusted IDP type from client: {}", StringSanitizer.forLog(clientId));
                return forbiddenResponse(exchange, "UNTRUSTED_IDP", "IDP type not trusted");
            }
        }

        // Map external headers to proxy auth headers for downstream processing
        ServerHttpRequest mutatedRequest = createMutatedRequest(request, userId, clientId);

        log.info("External auth validated - client: {}, persona: {}",
                StringSanitizer.forLog(clientId), StringSanitizer.forLog(persona));

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @NonNull
    private ServerHttpRequest createMutatedRequest(@NonNull ServerHttpRequest request, @NonNull String userId, @NonNull String clientId) {
        return new ServerHttpRequestDecorator(request) {
            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(super.getHeaders());

                // Set auth type for downstream filters
                headers.set(PROXY_AUTH_TYPE_HEADER, "proxy");

                // Map external user-id to operator-id (used by buildProxySubject)
                headers.set(PROXY_OPERATOR_ID_HEADER, userId);

                // Map client-id to partner-id
                headers.set(PROXY_PARTNER_ID_HEADER, clientId);

                return headers;
            }
        };
    }

    private boolean isPublicPath(@NonNull String path) {
        return "/".equals(path) ||
               path.startsWith("/api/auth/") ||
               path.startsWith("/actuator/") ||
               "/health".equals(path);
    }

    private boolean isValidIdentifier(@Nullable String value) {
        return value != null && SAFE_ID_PATTERN.matcher(value).matches();
    }

    @Nullable
    private String sanitizeHeaderValue(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_HEADER_LENGTH) {
            return trimmed.substring(0, MAX_HEADER_LENGTH);
        }
        return trimmed;
    }

    @NonNull
    private Mono<Void> unauthorizedResponse(@NonNull ServerWebExchange exchange, @NonNull String code, @NonNull String message) {

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"error\":\"unauthorized\",\"code\":\"%s\",\"message\":\"%s\"}",
                escapeJson(code), escapeJson(message));

        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @NonNull
    private Mono<Void> forbiddenResponse(@NonNull ServerWebExchange exchange, @NonNull String code, @NonNull String message) {

        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"error\":\"access_denied\",\"code\":\"%s\",\"message\":\"%s\"}",
                escapeJson(code), escapeJson(message));

        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @NonNull
    private String escapeJson(@NonNull String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
