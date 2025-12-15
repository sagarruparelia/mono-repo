package com.example.bff.config;

import com.example.bff.config.properties.SecurityPathsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final SecurityPathsProperties pathsConfig;

    public SecurityConfig(SecurityPathsProperties pathsConfig) {
        this.pathsConfig = pathsConfig;
    }

    @Bean
    @ConditionalOnProperty(name = "spring.security.oauth2.client.registration.hsid.client-id")
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(auth -> {
                    // Public paths from config
                    if (pathsConfig != null && pathsConfig.getPublicPatterns() != null) {
                        pathsConfig.getPublicPatterns().forEach(pattern ->
                                auth.pathMatchers(pattern).permitAll());
                    }

                    // MFE proxy paths (will be handled by ProxyAuthFilter)
                    if (pathsConfig != null && pathsConfig.getProxyAuthPatterns() != null) {
                        pathsConfig.getProxyAuthPatterns().forEach(pattern ->
                                auth.pathMatchers(pattern).permitAll());
                    }

                    // All other require authentication
                    auth.anyExchange().authenticated();
                })
                .oauth2Login(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                )
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.security.oauth2.client.registration.hsid.client-id", matchIfMissing = true, havingValue = "")
    public SecurityWebFilterChain testSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(auth -> auth.anyExchange().permitAll())
                .csrf(csrf -> csrf.disable())
                .build();
    }
}
