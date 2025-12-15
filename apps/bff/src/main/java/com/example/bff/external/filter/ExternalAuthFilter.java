package com.example.bff.external.filter;

import com.example.bff.config.properties.ExternalIntegrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Filter for external partner authentication via mTLS ALB.
 *
 * <p>External partners (e.g., partner portals embedding MFE components) authenticate
 * via mTLS with the ALB and pass user context through headers. This filter:
 * <ol>
 *   <li>Detects requests from mTLS ALB via X-Client-Id header</li>
 *   <li>Validates required headers (persona, user-id, idp-type)</li>
 *   <li>Maps headers for downstream authorization filters</li>
 * </ol>
 *
 * <p>Architecture:
 * <pre>
 * Partner Site -> Partner Backend -> mTLS ALB -> BFF
 *                      |
 *                   Adds headers:
 *                   - X-Client-Id (partner identifier)
 *                   - X-User-Id (authenticated user)
 *                   - X-Persona (agent, case_worker, etc.)
 *                   - X-IDP-Type (partner IDP identifier)
 * </pre>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@ConditionalOnProperty(name = "app.external-integration.enabled", havingValue = "true")
public class ExternalAuthFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ExternalAuthFilter.class);

    // Header names for mapping to proxy auth system
    private static final String PROXY_AUTH_TYPE_HEADER = "X-Auth-Type";
    private static final String PROXY_OPERATOR_ID_HEADER = "X-Operator-Id";
    private static final String PROXY_PARTNER_ID_HEADER = "X-Partner-Id";

    private final ExternalIntegrationProperties properties;

    public ExternalAuthFilter(ExternalIntegrationProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Skip public paths
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Check if this is an external integration request (via mTLS ALB)
        String clientId = request.getHeaders().getFirst(properties.headers().clientId());

        if (clientId == null || clientId.isBlank()) {
            // Not an external integration request - pass through
            return chain.filter(exchange);
        }

        log.debug("Processing external integration request from client: {}", clientId);

        // Validate required headers
        String persona = request.getHeaders().getFirst(properties.headers().persona());
        String userId = request.getHeaders().getFirst(properties.headers().userId());
        String idpType = request.getHeaders().getFirst(properties.headers().idpType());

        // Persona is required
        if (persona == null || persona.isBlank()) {
            log.warn("External request missing persona header from client: {}", clientId);
            return unauthorizedResponse(exchange, "MISSING_PERSONA", "X-Persona header is required");
        }

        // User ID is required
        if (userId == null || userId.isBlank()) {
            log.warn("External request missing user-id header from client: {}", clientId);
            return unauthorizedResponse(exchange, "MISSING_USER_ID", "X-User-Id header is required");
        }

        // Validate persona is allowed
        if (!properties.allowedPersonas().contains(persona)) {
            log.warn("External request with invalid persona: {} from client: {}", persona, clientId);
            return forbiddenResponse(exchange, "INVALID_PERSONA",
                    "Persona not allowed: " + persona);
        }

        // Validate IDP type if configured
        if (!properties.trustedIdpTypes().isEmpty() &&
            (idpType == null || !properties.trustedIdpTypes().contains(idpType))) {
            log.warn("External request with untrusted IDP type: {} from client: {}", idpType, clientId);
            return forbiddenResponse(exchange, "UNTRUSTED_IDP",
                    "IDP type not trusted: " + (idpType != null ? idpType : "null"));
        }

        // Map external headers to proxy auth headers for downstream processing
        ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(request) {
            @Override
            public org.springframework.http.HttpHeaders getHeaders() {
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
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

        log.info("External auth validated - client: {}, persona: {}, userId: {}, idpType: {}",
                clientId, persona, userId, idpType);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isPublicPath(String path) {
        return path.equals("/") ||
               path.startsWith("/api/auth/") ||
               path.startsWith("/actuator/") ||
               path.equals("/health");
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String code, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                """
                {"error":"unauthorized","code":"%s","message":"%s"}""",
                code, message);

        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(body.getBytes())));
    }

    private Mono<Void> forbiddenResponse(ServerWebExchange exchange, String code, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                """
                {"error":"access_denied","code":"%s","message":"%s"}""",
                code, message);

        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(body.getBytes())));
    }
}
