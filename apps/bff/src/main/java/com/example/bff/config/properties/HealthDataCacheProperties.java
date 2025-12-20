package com.example.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.NonNull;

import java.time.Duration;

/**
 * Configuration properties for health data MongoDB caching.
 * Supports TTL-based expiration and proactive fetching.
 */
@ConfigurationProperties(prefix = "app.health-data-cache")
public record HealthDataCacheProperties(
        boolean enabled,
        @NonNull Duration ttl,
        @NonNull CollectionConfig immunizations,
        @NonNull CollectionConfig allergies,
        @NonNull CollectionConfig conditions,
        @NonNull ProactiveFetchConfig proactiveFetch
) {
    public HealthDataCacheProperties {
        if (ttl == null) {
            ttl = Duration.ofHours(24);
        }
        if (immunizations == null) {
            immunizations = new CollectionConfig("immunizations");
        }
        if (allergies == null) {
            allergies = new CollectionConfig("allergies");
        }
        if (conditions == null) {
            conditions = new CollectionConfig("conditions");
        }
        if (proactiveFetch == null) {
            proactiveFetch = new ProactiveFetchConfig(true, Duration.ofMillis(500));
        }
    }

    /**
     * MongoDB collection configuration.
     */
    public record CollectionConfig(@NonNull String collection) {
        public CollectionConfig {
            if (collection == null || collection.isBlank()) {
                throw new IllegalArgumentException("Collection name is required");
            }
        }
    }

    /**
     * Configuration for proactive background fetching of health data.
     */
    public record ProactiveFetchConfig(
            boolean enabled,
            @NonNull Duration delayAfterLogin
    ) {
        public ProactiveFetchConfig {
            if (delayAfterLogin == null) {
                delayAfterLogin = Duration.ofMillis(500);
            }
        }
    }

    /**
     * Returns default configuration for testing or fallback.
     */
    public static HealthDataCacheProperties defaults() {
        return new HealthDataCacheProperties(
                true,
                Duration.ofHours(24),
                new CollectionConfig("immunizations"),
                new CollectionConfig("allergies"),
                new CollectionConfig("conditions"),
                new ProactiveFetchConfig(true, Duration.ofMillis(500))
        );
    }
}
