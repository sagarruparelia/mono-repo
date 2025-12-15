package com.example.bff;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BffApplicationTests {

    @Test
    void contextLoads() {
        // Basic context load test
    }

    @Configuration
    @EnableWebFluxSecurity
    static class TestSecurityConfig {

        @Bean
        @Primary
        public SecurityWebFilterChain testSecurityWebFilterChain(ServerHttpSecurity http) {
            return http
                    .authorizeExchange(auth -> auth.anyExchange().permitAll())
                    .csrf(csrf -> csrf.disable())
                    .build();
        }

        @Bean
        @Primary
        public ReactiveClientRegistrationRepository testClientRegistrationRepository() {
            ClientRegistration clientRegistration = ClientRegistration
                    .withRegistrationId("hsid")
                    .clientId("test-client")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/api/auth/callback")
                    .authorizationUri("http://localhost:8180/realms/test/protocol/openid-connect/auth")
                    .tokenUri("http://localhost:8180/realms/test/protocol/openid-connect/token")
                    .userInfoUri("http://localhost:8180/realms/test/protocol/openid-connect/userinfo")
                    .jwkSetUri("http://localhost:8180/realms/test/protocol/openid-connect/certs")
                    .userNameAttributeName("sub")
                    .scope("openid", "profile", "email")
                    .build();

            return new InMemoryReactiveClientRegistrationRepository(clientRegistration);
        }
    }
}
