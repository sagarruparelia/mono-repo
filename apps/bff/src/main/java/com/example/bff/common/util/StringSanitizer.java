package com.example.bff.common.util;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.regex.Pattern;

/**
 * Utility class for string sanitization operations.
 * Consolidates common sanitization patterns used across the BFF.
 */
public final class StringSanitizer {

    // Validation patterns
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_@.-]{1,128}$");

    // Default limits
    private static final int DEFAULT_LOG_MAX_LENGTH = 64;
    private static final int DEFAULT_HEADER_MAX_LENGTH = 256;

    private StringSanitizer() {
        // Utility class
    }

    /**
     * Sanitizes a value for safe logging by removing control characters
     * and truncating to a maximum length.
     *
     * @param value the value to sanitize
     * @return sanitized value safe for logging
     */
    @NonNull
    public static String forLog(@Nullable String value) {
        return forLog(value, DEFAULT_LOG_MAX_LENGTH);
    }

    /**
     * Sanitizes a value for safe logging with custom max length.
     *
     * @param value     the value to sanitize
     * @param maxLength maximum length of the result
     * @return sanitized value safe for logging
     */
    @NonNull
    public static String forLog(@Nullable String value, int maxLength) {
        if (value == null) {
            return "null";
        }
        String sanitized = value
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "");
        return sanitized.substring(0, Math.min(sanitized.length(), maxLength));
    }

    /**
     * Escapes special characters for JSON string values.
     *
     * @param value the value to escape
     * @return JSON-escaped value
     */
    @NonNull
    public static String escapeJson(@NonNull String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Sanitizes an HTTP header value.
     *
     * @param value the header value
     * @return sanitized value or null if input is null
     */
    @Nullable
    public static String headerValue(@Nullable String value) {
        return headerValue(value, DEFAULT_HEADER_MAX_LENGTH);
    }

    /**
     * Sanitizes an HTTP header value with custom max length.
     *
     * @param value     the header value
     * @param maxLength maximum length
     * @return sanitized value or null if input is null
     */
    @Nullable
    public static String headerValue(@Nullable String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            return trimmed.substring(0, maxLength);
        }
        return trimmed;
    }

    /**
     * Validates if a string is a valid UUID format.
     *
     * @param value the value to validate
     * @return true if valid UUID format
     */
    public static boolean isValidUuid(@Nullable String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }

    /**
     * Validates if a string is a valid session ID (UUID format).
     *
     * @param sessionId the session ID to validate
     * @return true if valid session ID format
     */
    public static boolean isValidSessionId(@Nullable String sessionId) {
        return isValidUuid(sessionId);
    }

    /**
     * Validates if a string is a valid user ID format.
     *
     * @param userId the user ID to validate
     * @return true if valid user ID format
     */
    public static boolean isValidUserId(@Nullable String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return SAFE_ID_PATTERN.matcher(userId).matches();
    }

    /**
     * Validates if a string is a valid safe identifier format.
     *
     * @param id the identifier to validate
     * @return true if valid format
     */
    public static boolean isValidSafeId(@Nullable String id) {
        return isValidUserId(id);
    }
}
