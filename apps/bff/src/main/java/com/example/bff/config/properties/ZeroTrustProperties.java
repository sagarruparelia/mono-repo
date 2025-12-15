package com.example.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "zero-trust")
public record ZeroTrustProperties(
        ContinuousVerification continuousVerification,
        DeviceValidation device,
        StepUpAuth stepUpAuth,
        AnomalyDetection anomalyDetection,
        AuditConfig audit
) {
    public record ContinuousVerification(
            boolean enabled,
            Checks checks,
            Signals signals
    ) {
        public record Checks(
                int intervalRequests,
                int intervalSeconds
        ) {}

        public record Signals(
                String ipChange,
                String uaChange,
                String geoVelocity,
                String timeAnomaly
        ) {}
    }

    public record DeviceValidation(
            boolean enabled,
            Validation validation
    ) {
        public record Validation(
                List<String> fingerprintHeaders,
                boolean bindToSession,
                double minTrustScore
        ) {}
    }

    public record StepUpAuth(
            boolean enabled,
            List<Trigger> triggers
    ) {
        public record Trigger(
                String pattern,
                List<String> methods,
                int maxAgeSeconds
        ) {}
    }

    public record AnomalyDetection(
            boolean enabled,
            List<Rule> rules
    ) {
        public record Rule(
                String name,
                String condition,
                String action,
                List<String> personas
        ) {}
    }

    public record AuditConfig(
            boolean enabled,
            boolean logAllRequests,
            List<String> sensitiveFieldsMask,
            int retentionDays
    ) {}
}
