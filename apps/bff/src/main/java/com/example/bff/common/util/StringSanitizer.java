package com.example.bff.common.util;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.regex.Pattern;

public final class StringSanitizer {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_@.-]{1,128}$");
    private static final int DEFAULT_LOG_MAX_LENGTH = 64;
    private static final int DEFAULT_HEADER_MAX_LENGTH = 256;

    private StringSanitizer() {}

    @NonNull
    public static String forLog(@Nullable String value) {
        return forLog(value, DEFAULT_LOG_MAX_LENGTH);
    }

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

    @NonNull
    public static String escapeJson(@NonNull String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Nullable
    public static String headerValue(@Nullable String value) {
        return headerValue(value, DEFAULT_HEADER_MAX_LENGTH);
    }

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

    public static boolean isValidUuid(@Nullable String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }

    public static boolean isValidSessionId(@Nullable String sessionId) {
        return isValidUuid(sessionId);
    }

    public static boolean isValidUserId(@Nullable String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return SAFE_ID_PATTERN.matcher(userId).matches();
    }

    public static boolean isValidSafeId(@Nullable String id) {
        return isValidUserId(id);
    }
}
