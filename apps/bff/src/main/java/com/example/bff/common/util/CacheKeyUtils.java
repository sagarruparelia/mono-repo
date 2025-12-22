package com.example.bff.common.util;

import org.springframework.lang.NonNull;

/**
 * Utility methods for cache key handling.
 * Provides sanitization to prevent cache injection attacks.
 */
public final class CacheKeyUtils {

    private CacheKeyUtils() {}

    /**
     * Sanitizes a cache key by replacing potentially dangerous characters.
     * Prevents cache key injection by removing colons (delimiter), whitespace, and control characters.
     *
     * @param key the raw cache key
     * @return sanitized key safe for use in cache operations
     * @throws IllegalArgumentException if key is null or blank
     */
    @NonNull
    public static String sanitize(@NonNull String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Cache key cannot be null or blank");
        }
        return key.replaceAll("[:\\s\\n\\r\\t]", "_");
    }
}
