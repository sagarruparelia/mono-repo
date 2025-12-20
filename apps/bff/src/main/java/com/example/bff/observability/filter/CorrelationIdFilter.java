package com.example.bff.observability.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * WebFilter that ensures every request has a correlation ID for distributed tracing.
 *
 * The filter:
 * 1. Extracts correlation ID from incoming headers (X-Correlation-Id, X-Request-Id)
 * 2. Generates a new UUID if not present
 * 3. Adds to Reactor context for propagation across async boundaries
 * 4. Adds to response headers for client visibility
 * 5. Integrates with MDC for structured logging
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements WebFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String REQUEST_PATH_KEY = "requestPath";
    public static final String REQUEST_METHOD_KEY = "requestMethod";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = extractOrGenerateCorrelationId(exchange.getRequest());
        String requestPath = exchange.getRequest().getPath().value();
        String requestMethod = exchange.getRequest().getMethod().name();

        // Add correlation ID to response headers
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, correlationId);

        // Mutate request to include correlation ID header (for downstream services)
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // Process request with MDC context for logging
        return chain.filter(mutatedExchange)
                .contextWrite(Context.of(
                        CORRELATION_ID_KEY, correlationId,
                        REQUEST_PATH_KEY, requestPath,
                        REQUEST_METHOD_KEY, requestMethod
                ))
                .doFirst(() -> {
                    MDC.put(CORRELATION_ID_KEY, correlationId);
                    MDC.put(REQUEST_PATH_KEY, requestPath);
                    MDC.put(REQUEST_METHOD_KEY, requestMethod);
                    log.debug("Request started: {} {}", requestMethod, requestPath);
                })
                .doFinally(signalType -> {
                    log.debug("Request completed: {} {} - {}", requestMethod, requestPath, signalType);
                    MDC.remove(CORRELATION_ID_KEY);
                    MDC.remove(REQUEST_PATH_KEY);
                    MDC.remove(REQUEST_METHOD_KEY);
                });
    }

    /**
     * Extract correlation ID from request headers or generate a new one.
     * Checks X-Correlation-Id first, then X-Request-Id as fallback.
     */
    private String extractOrGenerateCorrelationId(ServerHttpRequest request) {
        // Check X-Correlation-Id header first
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId.trim();
        }

        // Check X-Request-Id as fallback
        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId != null && !requestId.isBlank()) {
            return requestId.trim();
        }

        // Generate new UUID if no correlation ID found
        return UUID.randomUUID().toString();
    }

    /**
     * Utility method to get correlation ID from Reactor context.
     */
    public static Mono<String> getCorrelationId() {
        return Mono.deferContextual(ctx ->
                Mono.just(ctx.getOrDefault(CORRELATION_ID_KEY, "unknown")));
    }
}
