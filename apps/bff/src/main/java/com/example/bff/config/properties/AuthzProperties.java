package com.example.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.authz")
public record AuthzProperties(
        boolean enabled,
        PermissionsApiProperties permissionsApi,
        CacheProperties cache,
        RulesProperties rules
) {
    public AuthzProperties {
        if (permissionsApi == null) {
            permissionsApi = new PermissionsApiProperties(null, Duration.ofSeconds(5));
        }
        if (cache == null) {
            cache = new CacheProperties(true, Duration.ofMinutes(5));
        }
        if (rules == null) {
            rules = new RulesProperties(
                    new RuleConfig(new String[]{"DAA", "RPR"}),
                    new RuleConfig(new String[]{"DAA", "RPR", "ROI"})
            );
        }
    }

    public record PermissionsApiProperties(
            String url,
            Duration timeout
    ) {
        public PermissionsApiProperties {
            if (timeout == null) {
                timeout = Duration.ofSeconds(5);
            }
        }
    }

    public record CacheProperties(
            boolean enabled,
            Duration ttl
    ) {
        public CacheProperties {
            if (ttl == null) {
                ttl = Duration.ofMinutes(5);
            }
        }
    }

    public record RulesProperties(
            RuleConfig viewDependent,
            RuleConfig viewSensitive
    ) {}

    public record RuleConfig(
            String[] required
    ) {}
}
