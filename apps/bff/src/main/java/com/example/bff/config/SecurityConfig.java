package com.example.bff.config;

import com.example.bff.auth.handler.HsidAuthenticationSuccessHandler;
import com.example.bff.auth.handler.HsidLogoutSuccessHandler;
import com.example.bff.config.properties.SecurityPathsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;

@Configuration
@EnableWebFluxSecurity
@org.springframework.context.annotation.Profile("!e2e")
public class SecurityConfig {

    private final SecurityPathsProperties pathsConfig;

    public SecurityConfig(SecurityPathsProperties pathsConfig) {
        this.pathsConfig = pathsConfig;
    }

    @Bean
    public SecurityWebFilterChain securityFilterChain(
            ServerHttpSecurity http,
            HsidAuthenticationSuccessHandler authSuccessHandler,
            HsidLogoutSuccessHandler logoutSuccessHandler,
            ReactiveClientRegistrationRepository clientRegistrationRepository) {

        // PKCE authorization request resolver
        ServerOAuth2AuthorizationRequestResolver authorizationRequestResolver =
                pkceAuthorizationRequestResolver(clientRegistrationRepository);

        return http
                .authorizeExchange(auth -> {
                    // Public paths from config
                    if (pathsConfig != null && pathsConfig.getPublicPatterns() != null) {
                        pathsConfig.getPublicPatterns().forEach(pattern ->
                                auth.pathMatchers(pattern).permitAll());
                    }

                    // MFE proxy paths (will be handled by ProxyAuthFilter in Phase 3)
                    if (pathsConfig != null && pathsConfig.getProxyAuthPatterns() != null) {
                        pathsConfig.getProxyAuthPatterns().forEach(pattern ->
                                auth.pathMatchers(pattern).permitAll());
                    }

                    // All other require authentication
                    auth.anyExchange().authenticated();
                })
                .oauth2Login(oauth2 -> oauth2
                        .authorizationRequestResolver(authorizationRequestResolver)
                        .authenticationSuccessHandler(authSuccessHandler)
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(logoutSuccessHandler)
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
                        // Disable CSRF for API endpoints (they use session cookies + CORS)
                        .requireCsrfProtectionMatcher(new PathPatternParserServerWebExchangeMatcher("/api/**", HttpMethod.POST))
                )
                .build();
    }

    /**
     * Creates a PKCE-enabled authorization request resolver
     */
    private ServerOAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver(
            ReactiveClientRegistrationRepository clientRegistrationRepository) {

        DefaultServerOAuth2AuthorizationRequestResolver resolver =
                new DefaultServerOAuth2AuthorizationRequestResolver(clientRegistrationRepository);

        // Enable PKCE for all authorization requests
        resolver.setAuthorizationRequestCustomizer(customizer -> customizer
                .attributes(attrs -> attrs.put(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256"))
        );

        return resolver;
    }

}
