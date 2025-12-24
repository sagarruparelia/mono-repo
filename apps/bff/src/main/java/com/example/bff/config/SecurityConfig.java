package com.example.bff.config;

import com.example.bff.security.filter.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final MfePathRewriteFilter mfePathRewriteFilter;
    private final PartnerAuthenticationFilter partnerAuthenticationFilter;
    private final BrowserSessionFilter browserSessionFilter;
    private final DelegateEnterpriseIdFilter delegateEnterpriseIdFilter;
    private final PersonaAuthorizationFilter personaAuthorizationFilter;
    private final MfeRouteValidator mfeRouteValidator;
    private final ServerCsrfTokenRepository csrfTokenRepository;
    private final CsrfTokenSubscriptionFilter csrfTokenSubscriptionFilter;

    public SecurityConfig(
            MfePathRewriteFilter mfePathRewriteFilter,
            PartnerAuthenticationFilter partnerAuthenticationFilter,
            BrowserSessionFilter browserSessionFilter,
            DelegateEnterpriseIdFilter delegateEnterpriseIdFilter,
            PersonaAuthorizationFilter personaAuthorizationFilter,
            MfeRouteValidator mfeRouteValidator,
            ServerCsrfTokenRepository csrfTokenRepository,
            CsrfTokenSubscriptionFilter csrfTokenSubscriptionFilter) {
        this.mfePathRewriteFilter = mfePathRewriteFilter;
        this.partnerAuthenticationFilter = partnerAuthenticationFilter;
        this.browserSessionFilter = browserSessionFilter;
        this.delegateEnterpriseIdFilter = delegateEnterpriseIdFilter;
        this.personaAuthorizationFilter = personaAuthorizationFilter;
        this.mfeRouteValidator = mfeRouteValidator;
        this.csrfTokenRepository = csrfTokenRepository;
        this.csrfTokenSubscriptionFilter = csrfTokenSubscriptionFilter;
    }

    // Partner/MFE: /mfe/api/v1/** - header-based auth, mTLS validated upstream
    @Bean
    @Order(1)
    public SecurityWebFilterChain mfeSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/mfe/api/v1/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable) // API endpoint, mTLS validated upstream
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                // Custom filters
                .addFilterAt(mfePathRewriteFilter, SecurityWebFiltersOrder.FIRST)
                .addFilterAt(partnerAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(delegateEnterpriseIdFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(personaAuthorizationFilter, SecurityWebFiltersOrder.AUTHORIZATION)
                .addFilterAfter(mfeRouteValidator, SecurityWebFiltersOrder.AUTHORIZATION)
                .build();
    }

    // Browser: /api/v1/** - session cookie auth + CSRF
    @Bean
    @Order(2)
    public SecurityWebFilterChain browserSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/v1/**"))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                // Custom filters
                .addFilterAfter(csrfTokenSubscriptionFilter, SecurityWebFiltersOrder.CSRF)
                .addFilterAt(browserSessionFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(delegateEnterpriseIdFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(personaAuthorizationFilter, SecurityWebFiltersOrder.AUTHORIZATION)
                .build();
    }

    // Public: /, /login, /actuator/** - no auth
    @Bean
    @Order(3)
    public SecurityWebFilterChain publicSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers(
                        "/", "/login", "/actuator/health", "/actuator/info"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }

    // Catch-all: deny unmatched paths (security safeguard)
    @Bean
    @Order(Integer.MAX_VALUE)
    public SecurityWebFilterChain catchAllSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.anyExchange())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().denyAll())
                .build();
    }
}
