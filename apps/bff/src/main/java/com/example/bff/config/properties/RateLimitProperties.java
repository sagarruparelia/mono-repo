package com.example.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "rate-limiting")
public record RateLimitProperties(
        boolean enabled,
        DefaultLimits defaultLimits,
        List<RateLimitRule> rules,
        List<PersonaRule> personaRules
) {
    public record DefaultLimits(
            int requestsPerSecond,
            int burstCapacity
    ) {}

    public record RateLimitRule(
            String pattern,
            Integer requestsPerSecond,
            Integer requestsPerMinute,
            String by,
            String description
    ) {}

    public record PersonaRule(
            List<String> personas,
            int requestsPerSecond,
            String description
    ) {}

    public RateLimitProperties {
        if (defaultLimits == null) {
            defaultLimits = new DefaultLimits(10, 20);
        }
        if (rules == null) {
            rules = List.of();
        }
        if (personaRules == null) {
            personaRules = List.of();
        }
    }
}
