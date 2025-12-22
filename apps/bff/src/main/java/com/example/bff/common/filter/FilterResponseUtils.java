package com.example.bff.common.filter;

import com.example.bff.common.dto.ErrorResponse;
import com.example.bff.common.util.StringSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * Utility methods for building standardized error responses in WebFilters.
 * Uses ObjectMapper for safe JSON serialization to prevent injection attacks.
 */
@Slf4j
public final class FilterResponseUtils {

    private FilterResponseUtils() {}

    /**
     * Returns a 401 Unauthorized response with the given error details.
     */
    @NonNull
    public static Mono<Void> unauthorized(
            @NonNull ServerWebExchange exchange,
            @NonNull String code,
            @NonNull String message,
            @Nullable ObjectMapper objectMapper) {
        return error(exchange, HttpStatus.UNAUTHORIZED, ErrorResponse.Categories.AUTHENTICATION_REQUIRED,
                code, message, null, null, objectMapper);
    }

    /**
     * Returns a 401 Unauthorized response with correlation ID.
     */
    @NonNull
    public static Mono<Void> unauthorized(
            @NonNull ServerWebExchange exchange,
            @NonNull String correlationId,
            @NonNull String code,
            @NonNull String message,
            @Nullable ObjectMapper objectMapper) {
        return error(exchange, HttpStatus.UNAUTHORIZED, ErrorResponse.Categories.AUTHENTICATION_REQUIRED,
                code, message, correlationId, null, objectMapper);
    }

    /**
     * Returns a 403 Forbidden response with the given error details.
     */
    @NonNull
    public static Mono<Void> forbidden(
            @NonNull ServerWebExchange exchange,
            @NonNull String code,
            @NonNull String message,
            @Nullable ObjectMapper objectMapper) {
        return error(exchange, HttpStatus.FORBIDDEN, ErrorResponse.Categories.ACCESS_DENIED,
                code, message, null, null, objectMapper);
    }

    /**
     * Returns a 403 Forbidden response with correlation ID.
     */
    @NonNull
    public static Mono<Void> forbidden(
            @NonNull ServerWebExchange exchange,
            @NonNull String correlationId,
            @NonNull String code,
            @NonNull String message,
            @Nullable ObjectMapper objectMapper) {
        return error(exchange, HttpStatus.FORBIDDEN, ErrorResponse.Categories.ACCESS_DENIED,
                code, message, correlationId, null, objectMapper);
    }

    /**
     * Returns a generic error response with full control over parameters.
     */
    @NonNull
    public static Mono<Void> error(
            @NonNull ServerWebExchange exchange,
            @NonNull HttpStatus status,
            @NonNull String error,
            @NonNull String code,
            @NonNull String message,
            @Nullable String correlationId,
            @Nullable Map<String, Object> details,
            @Nullable ObjectMapper objectMapper) {

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String path = exchange.getRequest().getPath().value();
        String effectiveCorrelationId = correlationId != null ? correlationId : exchange.getLogPrefix();

        // Try ObjectMapper first (safe JSON serialization)
        if (objectMapper != null) {
            try {
                ErrorResponse errorResponse = new ErrorResponse(
                        error,
                        code,
                        message,
                        effectiveCorrelationId,
                        Instant.now(),
                        path,
                        details
                );
                String body = objectMapper.writeValueAsString(errorResponse);
                return writeResponse(exchange, body);
            } catch (Exception e) {
                log.warn("Failed to serialize error response with ObjectMapper: {}", e.getMessage());
                // Fall through to manual JSON building
            }
        }

        // Fallback: manually build JSON with proper escaping
        String body = buildSafeJson(error, code, message, effectiveCorrelationId);
        return writeResponse(exchange, body);
    }

    /**
     * Builds a minimal safe JSON response using StringSanitizer.escapeJson().
     * This ensures no JSON injection even without ObjectMapper.
     */
    @NonNull
    private static String buildSafeJson(
            @NonNull String error,
            @NonNull String code,
            @NonNull String message,
            @Nullable String correlationId) {

        StringBuilder json = new StringBuilder();
        json.append("{\"error\":\"").append(StringSanitizer.escapeJson(error)).append("\"");
        json.append(",\"code\":\"").append(StringSanitizer.escapeJson(code)).append("\"");
        json.append(",\"message\":\"").append(StringSanitizer.escapeJson(message)).append("\"");
        if (correlationId != null) {
            json.append(",\"correlationId\":\"").append(StringSanitizer.escapeJson(correlationId)).append("\"");
        }
        json.append("}");
        return json.toString();
    }

    @NonNull
    private static Mono<Void> writeResponse(@NonNull ServerWebExchange exchange, @NonNull String body) {
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
