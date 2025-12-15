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
            String memberId,
            String persona,
            String operatorId,
            String operatorName,
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
                    "X-Member-Id",
                    "X-Persona",
                    "X-Operator-Id",
                    "X-Operator-Name",
                    "X-Correlation-Id"
            );
        }
        if (allowedPartners == null) {
            allowedPartners = List.of();
        }
    }
}
