package com.example.bff.common.exception;

import com.example.bff.identity.exception.AgeRestrictionException;
import com.example.bff.identity.exception.IdentityServiceException;
import com.example.bff.identity.exception.NoAccessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST controllers.
 *
 * <p>Provides consistent error responses across all endpoints while
 * preventing sensitive information leakage in error messages.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Limits for log sanitization
    private static final int MAX_LOG_MESSAGE_LENGTH = 200;
    private static final int MAX_RESPONSE_MESSAGE_LENGTH = 100;

    /**
     * Handles session binding failures.
     *
     * @param ex the exception
     * @return error response
     */
    @ExceptionHandler(SessionBindingException.class)
    @NonNull
    public ResponseEntity<Map<String, Object>> handleSessionBinding(@NonNull SessionBindingException ex) {
        LOG.warn("Session binding failure: {}", sanitizeForLog(ex.getReason()));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "error", "session_invalid",
                        "message", "Session validation failed",
                        "timestamp", Instant.now().toString()
                ));
    }

    /**
     * Handles access denied exceptions.
     *
     * @param ex the exception
     * @return error response
     */
    @ExceptionHandler(AccessDeniedException.class)
    @NonNull
    public ResponseEntity<Map<String, Object>> handleAccessDenied(@NonNull AccessDeniedException ex) {
        LOG.warn("Access denied: {}", sanitizeForLog(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "access_denied",
                        "message", "Access denied",
                        "timestamp", Instant.now().toString()
                ));
    }

    /**
     * Handles age restriction violations.
     *
     * @param ex the exception
     * @return error response
     */
    @ExceptionHandler(AgeRestrictionException.class)
    @NonNull
    public ResponseEntity<Map<String, Object>> handleAgeRestriction(@NonNull AgeRestrictionException ex) {
        LOG.warn("Age restriction: {}", sanitizeForLog(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "age_restricted",
                        "code", "AGE_RESTRICTION",
                        "message", "User does not meet minimum age requirement",
                        "timestamp", Instant.now().toString()
                ));
    }

    /**
     * Handles no access violations (no eligibility and no managed members).
     *
     * @param ex the exception
     * @return error response
     */
    @ExceptionHandler(NoAccessException.class)
    @NonNull
    public ResponseEntity<Map<String, Object>> handleNoAccess(@NonNull NoAccessException ex) {
        LOG.warn("No access: {}", sanitizeForLog(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "no_access",
                        "code", "NO_ELIGIBILITY_OR_MANAGED_MEMBERS",
                        "message", "User has no access to the system",
                        "timestamp", Instant.now().toString()
                ));
    }

    /**
     * Handles identity service failures.
     *
     * @param ex the exception
     * @return error response
     */
    @ExceptionHandler(IdentityServiceException.class)
    @NonNull
    public ResponseEntity<Map<String, Object>> handleIdentityServiceError(@NonNull IdentityServiceException ex) {
        LOG.error("Identity service error: {}", sanitizeForLog(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "identity_service_error",
                        "code", "IDENTITY_SERVICE_UNAVAILABLE",
                        "message", "Unable to verify identity. Please try again later.",
                        "timestamp", Instant.now().toString()
                ));
    }

    /**
     * Handles validation errors from @Valid annotated request bodies in WebFlux.
     *
     * @param ex the exception
     * @return error response with field-level details
     */
    @ExceptionHandler(WebExchangeBindException.class)
    @NonNull
    public ResponseEntity<Map<String, Object>> handleValidationErrors(@NonNull WebExchangeBindException ex) {
        LOG.warn("Validation error: {} field errors", ex.getBindingResult().getFieldErrorCount());

        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", sanitizeFieldName(error.getField()),
                        "message", error.getDefaultMessage() != null
                                ? sanitizeResponseMessage(error.getDefaultMessage())
                                : "Invalid value"
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
     *
     * @param ex the exception
     * @return error response with violation details
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @NonNull
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(@NonNull ConstraintViolationException ex) {
        LOG.warn("Constraint violation: {} violations", ex.getConstraintViolations().size());

        List<Map<String, String>> violations = ex.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", sanitizeFieldName(extractFieldName(violation)),
                        "message", sanitizeResponseMessage(violation.getMessage())
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
     *
     * @param ex the exception
     * @return error response
     */
    @ExceptionHandler(ServerWebInputException.class)
    @NonNull
    public ResponseEntity<Map<String, Object>> handleInputException(@NonNull ServerWebInputException ex) {
        LOG.warn("Input error: {}", sanitizeForLog(ex.getMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "invalid_request",
                        "message", "Invalid request format",
                        "timestamp", Instant.now().toString()
                ));
    }

    /**
     * Handles illegal argument exceptions (often from invalid enum values or parameters).
     *
     * @param ex the exception
     * @return error response
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @NonNull
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(@NonNull IllegalArgumentException ex) {
        LOG.warn("Illegal argument: {}", sanitizeForLog(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "invalid_argument",
                        "message", "Invalid request parameter",
                        "timestamp", Instant.now().toString()
                ));
    }

    /**
     * Handles all unhandled exceptions.
     *
     * @param ex the exception
     * @return generic error response
     */
    @ExceptionHandler(Exception.class)
    @NonNull
    public ResponseEntity<Map<String, Object>> handleGeneral(@NonNull Exception ex) {
        LOG.error("Unhandled exception: {}", sanitizeForLog(ex.getMessage()), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "internal_error",
                        "message", "An unexpected error occurred",
                        "timestamp", Instant.now().toString()
                ));
    }

    /**
     * Extracts field name from constraint violation property path.
     *
     * @param violation the constraint violation
     * @return the field name
     */
    @NonNull
    private String extractFieldName(@NonNull ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        // Extract the last segment of the path (e.g., "createUser.request.email" -> "email")
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }

    /**
     * Sanitizes a value for safe logging to prevent log injection.
     *
     * @param value the value to sanitize
     * @return sanitized value
     */
    @NonNull
    private String sanitizeForLog(@Nullable String value) {
        if (value == null) {
            return "null";
        }
        String sanitized = value
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

        if (sanitized.length() > MAX_LOG_MESSAGE_LENGTH) {
            return sanitized.substring(0, MAX_LOG_MESSAGE_LENGTH) + "...";
        }
        return sanitized;
    }

    /**
     * Sanitizes a field name for response.
     *
     * @param fieldName the field name
     * @return sanitized field name
     */
    @NonNull
    private String sanitizeFieldName(@Nullable String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return "unknown";
        }
        // Only allow alphanumeric, dot, and underscore
        return fieldName.replaceAll("[^a-zA-Z0-9._]", "").substring(0, Math.min(fieldName.length(), 50));
    }

    /**
     * Sanitizes a message for response to prevent information leakage.
     *
     * @param message the message
     * @return sanitized message
     */
    @NonNull
    private String sanitizeResponseMessage(@Nullable String message) {
        if (message == null || message.isBlank()) {
            return "Invalid value";
        }
        String sanitized = message
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ");

        if (sanitized.length() > MAX_RESPONSE_MESSAGE_LENGTH) {
            return sanitized.substring(0, MAX_RESPONSE_MESSAGE_LENGTH) + "...";
        }
        return sanitized;
    }
}
