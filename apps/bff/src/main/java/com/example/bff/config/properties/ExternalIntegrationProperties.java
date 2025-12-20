package com.example.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for external partner integration via mTLS ALB.
 *
 * <p>External partners embed MFE web components and proxy requests through
 * their backend. The partner backend authenticates via mTLS with the ALB
 * and sends user context in headers.
 *
 * <p>Flow:
 * <ol>
 *   <li>Partner site authenticates user with their IDP</li>
 *   <li>Partner proxy sends request via mTLS ALB with user headers</li>
 *   <li>BFF validates headers and processes request</li>
 * </ol>
 */
@ConfigurationProperties(prefix = "app.external-integration")
public record ExternalIntegrationProperties(
        boolean enabled,
        HeaderNames headers,
        List<String> allowedPersonas,
        List<String> trustedIdpTypes
) {
    /**
     * Header names for external integration context.
     */
    public record HeaderNames(
            String persona,
            String userId,
            String idpType,
            String clientId,
            String partnerId
    ) {}

    public ExternalIntegrationProperties {
        if (headers == null) {
            headers = new HeaderNames(
                    "X-Persona",
                    "X-User-Id",
                    "X-IDP-Type",
                    "X-Client-Id",
                    "X-Partner-Id"
            );
        }
        if (allowedPersonas == null) {
            allowedPersonas = List.of();
        }
        if (trustedIdpTypes == null) {
            trustedIdpTypes = List.of();
        }
    }
}
