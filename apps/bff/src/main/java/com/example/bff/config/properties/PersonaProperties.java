package com.example.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.persona")
public record PersonaProperties(
        HsidPersonaProperties hsid,
        ProxyPersonaProperties proxy
) {
    public record HsidPersonaProperties(
            String claimName,
            List<String> allowed,
            ParentProperties parent
    ) {
        public record ParentProperties(
                String dependentsClaim
        ) {}
    }

    public record ProxyPersonaProperties(
            List<String> allowed
    ) {}

    public PersonaProperties {
        if (hsid == null) {
            hsid = new HsidPersonaProperties(
                    "persona",
                    List.of("individual", "parent"),
                    new HsidPersonaProperties.ParentProperties("dependents")
            );
        }
        if (proxy == null) {
            proxy = new ProxyPersonaProperties(
                    List.of("agent", "config", "case_worker")
            );
        }
    }
}
