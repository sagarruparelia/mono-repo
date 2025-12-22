package com.example.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for in-memory cache limits.
 * Used when app.cache.type=memory (default).
 */
@ConfigurationProperties(prefix = "app.cache.memory")
public record MemoryCacheProperties(
        int maxSessions,
        int maxIdentityEntries
) {
    public MemoryCacheProperties {
        if (maxSessions <= 0) {
            maxSessions = 10000;
        }
        if (maxIdentityEntries <= 0) {
            maxIdentityEntries = 1000;
        }
    }

    /**
     * Default properties with reasonable limits for single-pod deployment.
     */
    public static MemoryCacheProperties defaults() {
        return new MemoryCacheProperties(10000, 1000);
    }
}
