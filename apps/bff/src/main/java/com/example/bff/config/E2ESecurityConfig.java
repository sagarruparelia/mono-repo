package com.example.bff.config;

import com.example.bff.config.properties.SecurityPathsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import lombok.RequiredArgsConstructor;

/** E2E security config without OAuth2. Activated with spring.profiles.active=e2e. */
@Configuration
@EnableWebFluxSecurity
@org.springframework.context.annotation.Profile("e2e")
@RequiredArgsConstructor
public class E2ESecurityConfig {

    private final SecurityPathsProperties pathsConfig;

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
