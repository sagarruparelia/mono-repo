package com.example.bff.common.exception;

import com.example.bff.identity.exception.AgeRestrictionException;
import com.example.bff.identity.exception.IdentityServiceException;
import com.example.bff.identity.exception.NoAccessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Map;

/**
 * Global exception handler for REST controllers.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final int MAX_LOG_LENGTH = 200;
    private static final int MAX_RESPONSE_LENGTH = 100;

    @ExceptionHandler(SessionBindingException.class)
    public ResponseEntity<Map<String, Object>> handleSessionBinding(SessionBindingException ex) {
        log.warn("Session binding failure: {}", sanitizeForLog(ex.getReason()));
        return errorResponse(HttpStatus.UNAUTHORIZED, "session_invalid", "Session validation failed");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", sanitizeForLog(ex.getMessage()));
        return errorResponse(HttpStatus.FORBIDDEN, "access_denied", "Access denied");
    }

    @ExceptionHandler(AgeRestrictionException.class)
    public ResponseEntity<Map<String, Object>> handleAgeRestriction(AgeRestrictionException ex) {
        log.warn("Age restriction: {}", sanitizeForLog(ex.getMessage()));
        return errorResponse(HttpStatus.FORBIDDEN, "age_restricted", "AGE_RESTRICTION",
                "User does not meet minimum age requirement");
    }

    @ExceptionHandler(NoAccessException.class)
    public ResponseEntity<Map<String, Object>> handleNoAccess(NoAccessException ex) {
        log.warn("No access: {}", sanitizeForLog(ex.getMessage()));
        return errorResponse(HttpStatus.FORBIDDEN, "no_access", "NO_ELIGIBILITY_OR_MANAGED_MEMBERS",
                "User has no access to the system");
    }

    @ExceptionHandler(IdentityServiceException.class)
    public ResponseEntity<Map<String, Object>> handleIdentityServiceError(IdentityServiceException ex) {
        log.error("Identity service error: {}", sanitizeForLog(ex.getMessage()));
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, "identity_service_error",
                "IDENTITY_SERVICE_UNAVAILABLE", "Unable to verify identity. Please try again later.");
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(WebExchangeBindException ex) {
        log.warn("Validation error: {} field errors", ex.getBindingResult().getFieldErrorCount());
        List<Map<String, String>> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of(
                        "field", sanitizeFieldName(e.getField()),
                        "message", sanitizeResponse(e.getDefaultMessage())))
                .toList();
        return validationErrorResponse(details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation: {} violations", ex.getConstraintViolations().size());
        List<Map<String, String>> details = ex.getConstraintViolations().stream()
                .map(v -> Map.of(
                        "field", sanitizeFieldName(extractFieldName(v)),
                        "message", sanitizeResponse(v.getMessage())))
                .toList();
        return validationErrorResponse(details);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<Map<String, Object>> handleInputException(ServerWebInputException ex) {
        log.warn("Input error: {}", sanitizeForLog(ex.getMessage()));
        return errorResponse(HttpStatus.BAD_REQUEST, "invalid_request", "Invalid request format");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", sanitizeForLog(ex.getMessage()));
        return errorResponse(HttpStatus.BAD_REQUEST, "invalid_argument", "Invalid request parameter");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unhandled exception: {}", sanitizeForLog(ex.getMessage()), ex);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "An unexpected error occurred");
    }

    // --- Response builders ---

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", error,
                "message", message,
                "timestamp", Instant.now().toString()));
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String error, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", error,
                "code", code,
                "message", message,
                "timestamp", Instant.now().toString()));
    }

    private ResponseEntity<Map<String, Object>> validationErrorResponse(List<Map<String, String>> details) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "validation_error",
                "message", "Request validation failed",
                "details", details,
                "timestamp", Instant.now().toString()));
    }

    // --- Sanitization helpers ---

    private String extractFieldName(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }

    @NonNull
    private String sanitizeForLog(@Nullable String value) {
        if (value == null) return "null";
        String s = value.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return s.length() > MAX_LOG_LENGTH ? s.substring(0, MAX_LOG_LENGTH) + "..." : s;
    }

    @NonNull
    private String sanitizeFieldName(@Nullable String name) {
        if (name == null || name.isBlank()) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9._]", "").substring(0, Math.min(name.length(), 50));
    }

    @NonNull
    private String sanitizeResponse(@Nullable String message) {
        if (message == null || message.isBlank()) return "Invalid value";
        String s = message.replace("\n", " ").replace("\r", " ").replace("\t", " ");
        return s.length() > MAX_RESPONSE_LENGTH ? s.substring(0, MAX_RESPONSE_LENGTH) + "..." : s;
    }
}
