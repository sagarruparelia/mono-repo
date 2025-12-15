package com.example.bff.common.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SessionBindingException.class)
    public ResponseEntity<Map<String, Object>> handleSessionBinding(SessionBindingException ex) {
        log.warn("Session binding failure: {}", ex.getReason());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "error", "session_invalid",
                        "message", "Session validation failed",
                        "timestamp", Instant.now().toString()
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "access_denied",
                        "message", ex.getMessage(),
                        "timestamp", Instant.now().toString()
                ));
    }

    /**
     * Handles validation errors from @Valid annotated request bodies in WebFlux.
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(WebExchangeBindException ex) {
        log.warn("Validation error: {}", ex.getMessage());

        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                        "rejectedValue", String.valueOf(error.getRejectedValue())
                ))
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("error", "validation_error");
        response.put("message", "Request validation failed");
        response.put("details", fieldErrors);
        response.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles constraint violations from @Validated service methods.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMessage());

        List<Map<String, String>> violations = ex.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", extractFieldName(violation),
                        "message", violation.getMessage(),
                        "rejectedValue", String.valueOf(violation.getInvalidValue())
                ))
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("error", "validation_error");
        response.put("message", "Request validation failed");
        response.put("details", violations);
        response.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles malformed request body or type conversion errors.
     */
    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<Map<String, Object>> handleInputException(ServerWebInputException ex) {
        log.warn("Input error: {}", ex.getMessage());

        String message = "Invalid request format";
        if (ex.getCause() != null) {
            message = ex.getCause().getMessage();
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "invalid_request",
                        "message", message,
                        "timestamp", Instant.now().toString()
                ));
    }

    /**
     * Handles illegal argument exceptions (often from invalid enum values or parameters).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "invalid_argument",
                        "message", ex.getMessage(),
                        "timestamp", Instant.now().toString()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "internal_error",
                        "message", "An unexpected error occurred",
                        "timestamp", Instant.now().toString()
                ));
    }

    /**
     * Extracts field name from constraint violation property path.
     */
    private String extractFieldName(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        // Extract the last segment of the path (e.g., "createUser.request.email" -> "email")
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }
}
