package com.example.bff.config;

import com.example.bff.config.properties.SecurityPathsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security configuration for E2E testing without OAuth2.
 *
 * <p>This config is activated when app.security.oauth2.enabled=false,
 * which allows testing the application without a real IDP.
 *
 * <p>It provides:
 * - Permit all for public paths
 * - Basic security without OAuth2 client integration
 * - External integration filter still works for mTLS ALB testing
 */
@Configuration
@EnableWebFluxSecurity
@org.springframework.context.annotation.Profile("e2e")
public class E2ESecurityConfig {

    private final SecurityPathsProperties pathsConfig;

    public E2ESecurityConfig(SecurityPathsProperties pathsConfig) {
        this.pathsConfig = pathsConfig;
    }

    @Bean
    public SecurityWebFilterChain e2eSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(auth -> {
                    // Public paths from config
                    if (pathsConfig != null && pathsConfig.getPublicPatterns() != null) {
                        pathsConfig.getPublicPatterns().forEach(pattern ->
                                auth.pathMatchers(pattern).permitAll());
                    }
                    // Actuator endpoints
                    auth.pathMatchers("/actuator/**").permitAll();
                    // All other require authentication (but no OAuth2, so will fail)
                    // For E2E testing, the ExternalAuthFilter handles auth via headers
                    auth.anyExchange().permitAll();
                })
                .csrf(csrf -> csrf.disable())
                .build();
    }
}
