package com.example.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.session")
public record SessionProperties(
        Duration timeout,
        BindingProperties binding
) {
    public record BindingProperties(
            boolean enabled,
            boolean ipAddress,
            boolean userAgent
    ) {}

    public SessionProperties {
        if (timeout == null) {
            timeout = Duration.ofMinutes(30);
        }
        if (binding == null) {
            binding = new BindingProperties(true, true, true);
        }
    }
}
