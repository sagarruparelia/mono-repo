package com.example.bff.observability.filter;

import com.example.bff.common.util.ClientIpExtractor;
import com.example.bff.common.util.StringSanitizer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * WebFilter that logs HTTP requests with timing and records metrics.
 * Runs after CorrelationIdFilter to have access to correlation ID.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RequestLoggingFilter implements WebFilter {

    private final MeterRegistry meterRegistry;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Skip actuator endpoints for less noise
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        Instant startTime = Instant.now();
        String method = exchange.getRequest().getMethod().name();
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
        String clientIp = ClientIpExtractor.extractSimple(exchange);

        return chain.filter(exchange)
                .doFirst(() -> {
                    MDC.put("clientIp", StringSanitizer.forLog(clientIp));
                    MDC.put("userAgent", userAgent != null ? truncate(userAgent, 100) : "unknown");
                    log.info("Incoming request: {} {}", method, StringSanitizer.forLog(path));
                })
                .doFinally(signalType -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    ServerHttpResponse response = exchange.getResponse();
                    HttpStatus status = response.getStatusCode() != null
                            ? HttpStatus.resolve(response.getStatusCode().value())
                            : HttpStatus.OK;
                    int statusCode = status != null ? status.value() : 200;

                    // Record metrics
                    recordRequestMetrics(method, path, statusCode, duration);

                    // Log completion
                    MDC.put("responseStatus", String.valueOf(statusCode));
                    MDC.put("durationMs", String.valueOf(duration.toMillis()));

                    String sanitizedPath = StringSanitizer.forLog(path);
                    if (statusCode >= 500) {
                        log.error("Request completed: {} {} - {} in {}ms",
                                method, sanitizedPath, statusCode, duration.toMillis());
                    } else if (statusCode >= 400) {
                        log.warn("Request completed: {} {} - {} in {}ms",
                                method, sanitizedPath, statusCode, duration.toMillis());
                    } else {
                        log.info("Request completed: {} {} - {} in {}ms",
                                method, sanitizedPath, statusCode, duration.toMillis());
                    }

                    MDC.remove("clientIp");
                    MDC.remove("userAgent");
                    MDC.remove("responseStatus");
                    MDC.remove("durationMs");
                });
    }

    private void recordRequestMetrics(String method, String path, int statusCode, Duration duration) {
        // Normalize path for metrics (remove IDs)
        String normalizedPath = normalizePath(path);

        Timer.builder("http.server.requests.custom")
                .tag("method", method)
                .tag("uri", normalizedPath)
                .tag("status", String.valueOf(statusCode))
                .tag("outcome", getOutcome(statusCode))
                .register(meterRegistry)
                .record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * Normalize path by replacing IDs with placeholders for better metric aggregation.
     */
    private String normalizePath(String path) {
        return path
                // Replace UUIDs
                .replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "{id}")
                // Replace numeric IDs
                .replaceAll("/\\d+", "/{id}")
                // Limit path depth for cardinality
                .replaceAll("^(/[^/]+/[^/]+/[^/]+/[^/]+).*", "$1");
    }

    private String getOutcome(int statusCode) {
        if (statusCode < 200) return "INFORMATIONAL";
        if (statusCode < 300) return "SUCCESS";
        if (statusCode < 400) return "REDIRECTION";
        if (statusCode < 500) return "CLIENT_ERROR";
        return "SERVER_ERROR";
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }
}
