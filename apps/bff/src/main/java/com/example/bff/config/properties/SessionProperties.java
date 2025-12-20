package com.example.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for Zero Trust session management.
 */
@ConfigurationProperties(prefix = "app.session")
public record SessionProperties(
        Duration timeout,
        BindingProperties binding,
        RotationProperties rotation,
        FingerprintProperties fingerprint,
        PubSubProperties pubsub,
        CookieProperties cookie
) {
    /**
     * Session binding configuration for IP and User-Agent validation.
     */
    public record BindingProperties(
            boolean enabled,
            boolean ipAddress,
            boolean userAgent
    ) {
        public BindingProperties {
            // Defaults handled in parent constructor
        }
    }

    /**
     * Session ID rotation configuration for Zero Trust.
     */
    public record RotationProperties(
            boolean enabled,
            Duration interval,
            Duration gracePeriod
    ) {
        public RotationProperties {
            if (interval == null) {
                interval = Duration.ofMinutes(15);
            }
            if (gracePeriod == null) {
                gracePeriod = Duration.ofSeconds(5);
            }
        }
    }

    /**
     * Device fingerprinting configuration for enhanced session binding.
     */
    public record FingerprintProperties(
            boolean enabled,
            boolean includeAcceptLanguage,
            boolean includeAcceptEncoding
    ) {
        public FingerprintProperties {
            // Defaults handled in parent constructor
        }
    }

    /**
     * Redis Pub/Sub configuration for cross-instance session events.
     */
    public record PubSubProperties(
            boolean enabled,
            String channel
    ) {
        public PubSubProperties {
            if (channel == null || channel.isBlank()) {
                channel = "bff:session:events";
            }
        }
    }

    /**
     * Session cookie security configuration.
     */
    public record CookieProperties(
            String domain,
            String sameSite
    ) {
        public CookieProperties {
            // domain can be null for default browser behavior (current host only)
            if (sameSite == null || sameSite.isBlank()) {
                sameSite = "Lax";
            }
        }
    }

    /**
     * Provides defaults for all configuration properties.
     */
    public SessionProperties {
        if (timeout == null) {
            timeout = Duration.ofMinutes(30);
        }
        if (binding == null) {
            binding = new BindingProperties(true, true, true);
        }
        if (rotation == null) {
            rotation = new RotationProperties(true, Duration.ofMinutes(15), Duration.ofSeconds(5));
        }
        if (fingerprint == null) {
            fingerprint = new FingerprintProperties(true, true, true);
        }
        if (pubsub == null) {
            pubsub = new PubSubProperties(true, "bff:session:events");
        }
        if (cookie == null) {
            cookie = new CookieProperties(null, "Lax");
        }
    }
}
