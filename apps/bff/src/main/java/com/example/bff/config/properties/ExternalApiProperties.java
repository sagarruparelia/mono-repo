package com.example.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.NonNull;

import java.time.Duration;

/**
 * Configuration properties for external identity APIs.
 * Used for User Service, Eligibility, and Permission APIs.
 */
@ConfigurationProperties(prefix = "app.external-api")
public record ExternalApiProperties(
        @NonNull String baseUrl,
        @NonNull UserServiceProperties userService,
        @NonNull EligibilityProperties eligibility,
        @NonNull PermissionsProperties permissions,
        @NonNull RetryProperties retry
) {
    /**
     * User Service API configuration.
     * Endpoint: /api/identity/user/individual/v1/read
     */
    public record UserServiceProperties(
            @NonNull String path,
            @NonNull Duration timeout
    ) {
        public UserServiceProperties {
            if (path == null || path.isBlank()) {
                path = "/api/identity/user/individual/v1/read";
            }
            if (timeout == null) {
                timeout = Duration.ofSeconds(5);
            }
        }
    }

    /**
     * Eligibility Graph API configuration.
     * Endpoint: /graph/1.0.0
     */
    public record EligibilityProperties(
            @NonNull String path,
            @NonNull Duration timeout
    ) {
        public EligibilityProperties {
            if (path == null || path.isBlank()) {
                path = "/graph/1.0.0";
            }
            if (timeout == null) {
                timeout = Duration.ofSeconds(5);
            }
        }
    }

    /**
     * Permissions Graph API configuration for managed members.
     * Endpoint: /api/consumer/prefs/del-gr/1.0.0
     */
    public record PermissionsProperties(
            @NonNull String path,
            @NonNull Duration timeout
    ) {
        public PermissionsProperties {
            if (path == null || path.isBlank()) {
                path = "/api/consumer/prefs/del-gr/1.0.0";
            }
            if (timeout == null) {
                timeout = Duration.ofSeconds(5);
            }
        }
    }

    /**
     * Retry configuration for external API calls.
     */
    public record RetryProperties(
            int maxAttempts,
            @NonNull Duration initialBackoff,
            @NonNull Duration maxBackoff
    ) {
        public RetryProperties {
            if (maxAttempts <= 0) {
                maxAttempts = 3;
            }
            if (initialBackoff == null) {
                initialBackoff = Duration.ofMillis(100);
            }
            if (maxBackoff == null) {
                maxBackoff = Duration.ofSeconds(1);
            }
        }
    }
}
