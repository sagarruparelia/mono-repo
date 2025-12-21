package com.example.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.mfe.proxy")
public record MfeProxyProperties(
        boolean enabled,
        ProxyHeaders headers,
        List<AllowedPartner> allowedPartners
) {
    public record ProxyHeaders(
            String authType,
            String partnerId,
            String enterpriseId,
            String loggedInMemberPersona,
            String loggedInMemberIdValue,
            String loggedInMemberIdType,
            String correlationId
    ) {}

    public record AllowedPartner(
            String id,
            String name,
            List<String> allowedPersonas,
            List<String> scopes,
            List<String> allowedDomains
    ) {}

    public MfeProxyProperties {
        if (headers == null) {
            headers = new ProxyHeaders(
                    "X-Auth-Type",
                    "X-Partner-Id",
                    "X-Enterprise-Id",
                    "X-Logged-In-Member-Persona",
                    "X-Logged-In-Member-Id-Value",
                    "X-Logged-In-Member-Id-Type",
                    "X-Correlation-Id"
            );
        }
        if (allowedPartners == null) {
            allowedPartners = List.of();
        }
    }
}
