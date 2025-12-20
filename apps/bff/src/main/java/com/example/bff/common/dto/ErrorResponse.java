package com.example.bff.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized error response format for all API errors.
 *
 * <p>This record provides a consistent error structure for client error handling,
 * debugging, and logging across all endpoints.</p>
 *
 * <h3>Response Format:</h3>
 * <pre>{@code
 * {
 *   "error": "access_denied",
 *   "code": "MEMBER_ACCESS_DENIED",
 *   "message": "You do not have access to this member's data",
 *   "correlationId": "550e8400-e29b-41d4-a716-446655440000",
 *   "timestamp": "2024-12-19T10:30:00.000Z",
 *   "path": "/api/health/summary",
 *   "details": {
 *     "memberId": "member-123",
 *     "requiredPermissions": ["DAA", "RPR"]
 *   }
 * }
 * }</pre>
 *
 * @param error         Stable error category for client error handling
 * @param code          Specific error code for debugging/logging
 * @param message       Human-readable message for UI display
 * @param correlationId Request correlation ID for tracing
 * @param timestamp     ISO-8601 timestamp when error occurred
 * @param path          Request path that triggered the error
 * @param details       Additional context (validation errors, missing attrs, etc.)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String error,
        String code,
        String message,
        String correlationId,
        Instant timestamp,
        String path,
        Map<String, Object> details
) {
    /**
     * Create a basic error response without details.
     */
    public static ErrorResponse of(
            String error,
            String code,
            String message,
            String correlationId,
            String path
    ) {
        return new ErrorResponse(
                error,
                code,
                message,
                correlationId,
                Instant.now(),
                path,
                null
        );
    }

    /**
     * Create an error response with details.
     */
    public static ErrorResponse of(
            String error,
            String code,
            String message,
            String correlationId,
            String path,
            Map<String, Object> details
    ) {
        return new ErrorResponse(
                error,
                code,
                message,
                correlationId,
                Instant.now(),
                path,
                details
        );
    }

    /**
     * Create a new response with added details.
     */
    public ErrorResponse withDetails(Map<String, Object> newDetails) {
        return new ErrorResponse(
                error,
                code,
                message,
                correlationId,
                timestamp,
                path,
                newDetails
        );
    }

    /**
     * Common error categories.
     */
    public static final class Categories {
        public static final String ACCESS_DENIED = "access_denied";
        public static final String AUTHENTICATION_REQUIRED = "authentication_required";
        public static final String VALIDATION_ERROR = "validation_error";
        public static final String NOT_FOUND = "not_found";
        public static final String INTERNAL_ERROR = "internal_error";

        private Categories() {
        }
    }

    /**
     * Error codes for dual auth.
     */
    public static final class Codes {
        // Auth errors
        public static final String IDP_PERSONA_MISMATCH = "IDP_PERSONA_MISMATCH";
        public static final String INVALID_IDP_TYPE = "INVALID_IDP_TYPE";
        public static final String MISSING_IDP_TYPE = "MISSING_IDP_TYPE";
        public static final String SESSION_REQUIRED = "SESSION_REQUIRED";
        public static final String PROXY_REQUIRED = "PROXY_REQUIRED";
        public static final String UNAUTHORIZED = "UNAUTHORIZED";

        // Authorization errors
        public static final String MEMBER_ACCESS_DENIED = "MEMBER_ACCESS_DENIED";
        public static final String SUBCATEGORY_ACCESS_DENIED = "SUBCATEGORY_ACCESS_DENIED";
        public static final String SENSITIVE_DATA_REQUIRES_ROI = "SENSITIVE_DATA_REQUIRES_ROI";
        public static final String FORBIDDEN = "FORBIDDEN";

        // Resource errors
        public static final String MEMBER_NOT_FOUND = "MEMBER_NOT_FOUND";
        public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";

        // Validation errors
        public static final String INVALID_REQUEST = "INVALID_REQUEST";
        public static final String MISSING_REQUIRED_FIELD = "MISSING_REQUIRED_FIELD";

        private Codes() {
        }
    }
}
