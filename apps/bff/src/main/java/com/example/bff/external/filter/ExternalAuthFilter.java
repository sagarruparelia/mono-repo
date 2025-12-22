package com.example.bff.external.filter;

import com.example.bff.common.filter.FilterResponseUtils;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.ExternalIntegrationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

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
        String persona = sanitizeHeaderValue(request.getHeaders().getFirst(properties.headers().loggedInMemberPersona()));
        String loggedInMemberIdValue = sanitizeHeaderValue(request.getHeaders().getFirst(properties.headers().loggedInMemberIdValue()));
        String loggedInMemberIdType = sanitizeHeaderValue(request.getHeaders().getFirst(properties.headers().loggedInMemberIdType()));

        // Persona is required
        if (persona == null || persona.isBlank()) {
            log.warn("External request missing persona header from client: {}", StringSanitizer.forLog(clientId));
            return unauthorizedResponse(exchange, "MISSING_PERSONA", "X-Persona header is required");
        }

        // Logged-in member ID value is required
        if (loggedInMemberIdValue == null || loggedInMemberIdValue.isBlank()) {
            log.warn("External request missing logged-in-member-id-value header from client: {}", StringSanitizer.forLog(clientId));
            return unauthorizedResponse(exchange, "MISSING_LOGGED_IN_MEMBER_ID", "X-Logged-In-Member-Id-Value header is required");
        }

        // Validate identifiers format
        if (!isValidIdentifier(persona)) {
            log.warn("Invalid persona format from client: {}", StringSanitizer.forLog(clientId));
            return forbiddenResponse(exchange, "INVALID_PERSONA", "Invalid persona format");
        }

        if (!isValidIdentifier(loggedInMemberIdValue)) {
            log.warn("Invalid logged-in member ID format from client: {}", StringSanitizer.forLog(clientId));
            return forbiddenResponse(exchange, "INVALID_LOGGED_IN_MEMBER_ID", "Invalid logged-in member ID format");
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
            if (loggedInMemberIdType == null || !trustedIdpTypes.contains(loggedInMemberIdType)) {
                log.warn("External request with untrusted IDP type from client: {}", StringSanitizer.forLog(clientId));
                return forbiddenResponse(exchange, "UNTRUSTED_IDP", "IDP type not trusted");
            }
        }

        // Map external headers to proxy auth headers for downstream processing
        ServerHttpRequest mutatedRequest = createMutatedRequest(request, loggedInMemberIdValue, clientId);

        log.info("External auth validated - client: {}, persona: {}",
                StringSanitizer.forLog(clientId), StringSanitizer.forLog(persona));

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @NonNull
    private ServerHttpRequest createMutatedRequest(@NonNull ServerHttpRequest request, @NonNull String loggedInMemberIdValue, @NonNull String clientId) {
        return new ServerHttpRequestDecorator(request) {
            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(super.getHeaders());

                // Set auth type for downstream filters
                headers.set(PROXY_AUTH_TYPE_HEADER, "proxy");

                // Map external logged-in-member-id-value to operator-id (used by buildProxySubject)
                headers.set(PROXY_OPERATOR_ID_HEADER, loggedInMemberIdValue);

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
        return FilterResponseUtils.unauthorized(exchange, code, message, null);
    }

    @NonNull
    private Mono<Void> forbiddenResponse(@NonNull ServerWebExchange exchange, @NonNull String code, @NonNull String message) {
        return FilterResponseUtils.forbidden(exchange, code, message, null);
    }
}
