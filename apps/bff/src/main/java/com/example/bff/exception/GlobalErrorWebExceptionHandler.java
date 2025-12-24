package com.example.bff.exception;

import com.example.bff.security.exception.AuthenticationException;
import com.example.bff.security.exception.AuthorizationException;
import com.example.bff.security.exception.SecurityIncidentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.security.web.server.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@Order(-2)  // Higher priority than DefaultErrorWebExceptionHandler
public class GlobalErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {

    public GlobalErrorWebExceptionHandler(
            ErrorAttributes errorAttributes,
            WebProperties webProperties,
            ApplicationContext applicationContext,
            ServerCodecConfigurer serverCodecConfigurer) {
        super(errorAttributes, webProperties.getResources(), applicationContext);
        this.setMessageWriters(serverCodecConfigurer.getWriters());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        Throwable error = getError(request);
        String path = request.path();

        // Authentication errors -> 401
        if (error instanceof AuthenticationException) {
            log.warn("Authentication failed: path={}, error={}", path, error.getMessage());
            return createErrorResponse(HttpStatus.UNAUTHORIZED, "authentication_error",
                    "Authentication required", path);
        }

        // Authorization errors -> 403
        if (error instanceof AuthorizationException) {
            log.warn("Authorization denied: path={}, error={}", path, error.getMessage());
            return createErrorResponse(HttpStatus.FORBIDDEN, "authorization_error",
                    "Access denied", path);
        }

        // CSRF errors -> 403
        if (error instanceof CsrfException) {
            log.warn("CSRF validation failed: path={}, error={}", path, error.getMessage());
            return createErrorResponse(HttpStatus.FORBIDDEN, "csrf_error",
                    "Invalid or missing CSRF token", path);
        }

        // Security incidents -> 403 with detailed logging
        if (error instanceof SecurityIncidentException incident) {
            log.error("SECURITY INCIDENT: type={}, loggedInMember={}, attemptedEnterpriseId={}, path={}, message={}",
                    incident.getIncidentType(),
                    incident.getLoggedInMemberIdValue(),
                    incident.getAttemptedEnterpriseId(),
                    path,
                    incident.getMessage());

            return createErrorResponse(HttpStatus.FORBIDDEN, "security_incident",
                    "Access denied due to security policy violation", path);
        }

        // API/WebClient errors -> 502 Bad Gateway
        if (error instanceof ApiException apiError) {
            log.error("External API error: service={}, status={}, path={}, error={}",
                    apiError.getServiceName(),
                    apiError.getStatusCode(),
                    path,
                    apiError.getMessage());

            return createErrorResponse(HttpStatus.BAD_GATEWAY, "external_service_error",
                    "External service unavailable", path);
        }

        // WebClient response errors -> 502
        if (error instanceof WebClientResponseException webClientError) {
            log.error("WebClient error: status={}, path={}, error={}",
                    webClientError.getStatusCode(), path, webClientError.getMessage());

            return createErrorResponse(HttpStatus.BAD_GATEWAY, "external_service_error",
                    "External service error", path);
        }

        // Validation errors -> 400
        if (error instanceof WebExchangeBindException bindException) {
            String fieldErrors = bindException.getFieldErrors().stream()
                    .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                    .collect(Collectors.joining(", "));

            log.warn("Validation failed: path={}, errors={}", path, fieldErrors);

            return createErrorResponse(HttpStatus.BAD_REQUEST, "validation_error",
                    "Validation failed: " + fieldErrors, path);
        }

        // ResponseStatusException -> use its status
        if (error instanceof ResponseStatusException statusException) {
            HttpStatus status = HttpStatus.resolve(statusException.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }

            log.warn("Response status exception: path={}, status={}, reason={}",
                    path, status, statusException.getReason());

            return createErrorResponse(status, "request_error",
                    statusException.getReason() != null ? statusException.getReason() : status.getReasonPhrase(),
                    path);
        }

        // IllegalArgumentException -> 400
        if (error instanceof IllegalArgumentException) {
            log.warn("Invalid argument: path={}, error={}", path, error.getMessage());
            return createErrorResponse(HttpStatus.BAD_REQUEST, "invalid_argument",
                    "Invalid request parameter", path);
        }

        // Default error handling -> 500
        Map<String, Object> errorAttributes = getErrorAttributes(request, ErrorAttributeOptions.defaults());
        int status = (int) errorAttributes.getOrDefault("status", 500);

        log.error("Unhandled error: path={}, status={}, error={}",
                path, status, error.getMessage(), error);

        return createErrorResponse(
                HttpStatus.valueOf(status),
                "server_error",
                "An unexpected error occurred",
                path
        );
    }

    private Mono<ServerResponse> createErrorResponse(HttpStatus status, String error, String message, String path) {
        ErrorResponse body = ErrorResponse.of(status.value(), error, message, path);

        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(body));
    }
}
