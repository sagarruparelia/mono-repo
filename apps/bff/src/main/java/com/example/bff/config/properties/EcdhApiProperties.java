package com.example.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.NonNull;

import java.time.Duration;

/**
 * Configuration properties for ECDH Health Data API.
 * Used for fetching immunizations, allergies, and conditions.
 */
@ConfigurationProperties(prefix = "app.ecdh-api")
public record EcdhApiProperties(
        @NonNull String baseUrl,
        @NonNull String graphPath,
        @NonNull Duration timeout,
        @NonNull RetryProperties retry
) {
    public EcdhApiProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.abc.com";
        }
        if (graphPath == null || graphPath.isBlank()) {
            graphPath = "/graph/1.0.0";
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(10);
        }
        if (retry == null) {
            retry = new RetryProperties(3, Duration.ofMillis(100), Duration.ofSeconds(2));
        }
    }

    /**
     * Returns the full URL for the GraphQL endpoint.
     */
    public String getGraphUrl() {
        return baseUrl + graphPath;
    }

    /**
     * Retry configuration for ECDH API calls.
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
                maxBackoff = Duration.ofSeconds(2);
            }
        }
    }
}
