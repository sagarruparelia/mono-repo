package com.example.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.NonNull;

import java.time.Duration;

/**
 * Configuration properties for identity API response caching.
 * Responses are cached in Redis with configurable TTL per API.
 */
@ConfigurationProperties(prefix = "app.identity-cache")
public record IdentityCacheProperties(
        @NonNull CacheConfig userService,
        @NonNull CacheConfig eligibility,
        @NonNull CacheConfig permissions
) {
    /**
     * Cache configuration for a specific API.
     */
    public record CacheConfig(
            @NonNull Duration ttl,
            boolean enabled
    ) {
        public CacheConfig {
            if (ttl == null) {
                ttl = Duration.ofMinutes(60);
            }
        }

        /**
         * Default cache config with 60 minute TTL.
         */
        public static CacheConfig defaultConfig() {
            return new CacheConfig(Duration.ofMinutes(60), true);
        }
    }

    /**
     * Cache name constants for use with @Cacheable annotations.
     */
    public static final String USER_SERVICE_CACHE = "identity:user";
    public static final String ELIGIBILITY_CACHE = "identity:eligibility";
    public static final String PERMISSIONS_CACHE = "identity:permissions";

    /**
     * Default properties with 60 minute TTL for all APIs.
     */
    public static IdentityCacheProperties defaults() {
        return new IdentityCacheProperties(
                CacheConfig.defaultConfig(),
                CacheConfig.defaultConfig(),
                CacheConfig.defaultConfig()
        );
    }
}
