package com.example.bff.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for HSID PKCE authentication configuration.
 * Verifies that:
 * 1. PKCE parameters (code_challenge, code_challenge_method) are included in authorization requests
 * 2. Client authentication method is set to 'none' (no client secret)
 * 3. S256 is used as the code challenge method
 */
class HsidPkceAuthenticationTest {

    /**
     * Creates a PKCE-configured client registration matching application.yml
     */
    private ClientRegistration createHsidPkceClientRegistration() {
        return ClientRegistration
                .withRegistrationId("hsid")
                .clientId("test-pkce-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) // PKCE - no client secret
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/api/auth/callback")
                .authorizationUri("https://hsid.example.com/oidc/authorize")
                .tokenUri("https://hsid.example.com/oidc/token")
                .userInfoUri("https://hsid.example.com/oidc/userinfo")
                .jwkSetUri("https://hsid.example.com/.well-known/jwks.json")
                .userNameAttributeName("sub")
                .scope("openid", "profile", "email")
                .build();
    }

    @Nested
    @DisplayName("PKCE Client Registration")
    class PkceClientRegistrationTests {

        @Test
        @DisplayName("HSID client should use PKCE (client-authentication-method: none)")
        void hsidClientShouldUsePkce() {
            ClientRegistration registration = createHsidPkceClientRegistration();

            assertThat(registration.getClientAuthenticationMethod())
                    .as("Client authentication method should be NONE for PKCE")
                    .isEqualTo(ClientAuthenticationMethod.NONE);
        }

        @Test
        @DisplayName("HSID client should use authorization_code grant type")
        void hsidClientShouldUseAuthorizationCodeGrant() {
            ClientRegistration registration = createHsidPkceClientRegistration();

            assertThat(registration.getAuthorizationGrantType())
                    .as("Grant type should be AUTHORIZATION_CODE")
                    .isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        }

        @Test
        @DisplayName("HSID client should have correct scopes")
        void hsidClientShouldHaveCorrectScopes() {
            ClientRegistration registration = createHsidPkceClientRegistration();

            assertThat(registration.getScopes())
                    .as("Should include openid, profile, email scopes")
                    .contains("openid", "profile", "email");
        }

        @Test
        @DisplayName("HSID client should NOT have client secret")
        void hsidClientShouldNotHaveClientSecret() {
            ClientRegistration registration = createHsidPkceClientRegistration();

            // PKCE clients don't use client secrets
            assertThat(registration.getClientSecret())
                    .as("PKCE client should not have a client secret")
                    .isNullOrEmpty();
        }

        @Test
        @DisplayName("HSID client should have correct redirect URI pattern")
        void hsidClientShouldHaveCorrectRedirectUri() {
            ClientRegistration registration = createHsidPkceClientRegistration();

            assertThat(registration.getRedirectUri())
                    .as("Redirect URI should point to /api/auth/callback")
                    .isEqualTo("{baseUrl}/api/auth/callback");
        }
    }

    @Nested
    @DisplayName("PKCE Authorization Request")
    class PkceAuthorizationRequestTests {

        @Test
        @DisplayName("PKCE resolver should include code_challenge in authorization request")
        void pkceResolverShouldIncludeCodeChallenge() {
            // Setup
            ReactiveClientRegistrationRepository clientRegistrationRepository =
                    new InMemoryReactiveClientRegistrationRepository(createHsidPkceClientRegistration());

            DefaultServerOAuth2AuthorizationRequestResolver resolver =
                    new DefaultServerOAuth2AuthorizationRequestResolver(clientRegistrationRepository);

            // Configure S256 PKCE (same as SecurityConfig)
            resolver.setAuthorizationRequestCustomizer(customizer -> customizer
                    .attributes(attrs -> attrs.put(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256"))
            );

            // Create mock request
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/oauth2/authorization/hsid")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Resolve
            OAuth2AuthorizationRequest authRequest = resolver.resolve(exchange).block();

            // Verify PKCE parameters are present
            assertThat(authRequest).isNotNull();
            assertThat(authRequest.getAdditionalParameters())
                    .as("Should include code_challenge parameter")
                    .containsKey(PkceParameterNames.CODE_CHALLENGE);
            assertThat(authRequest.getAdditionalParameters())
                    .as("Should include code_challenge_method parameter")
                    .containsKey(PkceParameterNames.CODE_CHALLENGE_METHOD);
            assertThat(authRequest.getAdditionalParameters().get(PkceParameterNames.CODE_CHALLENGE_METHOD))
                    .as("Code challenge method should be S256")
                    .isEqualTo("S256");
        }

        @Test
        @DisplayName("PKCE code_challenge should be a valid base64url string")
        void pkceCodeChallengeShouldBeValidBase64Url() {
            // Setup
            ReactiveClientRegistrationRepository clientRegistrationRepository =
                    new InMemoryReactiveClientRegistrationRepository(createHsidPkceClientRegistration());

            DefaultServerOAuth2AuthorizationRequestResolver resolver =
                    new DefaultServerOAuth2AuthorizationRequestResolver(clientRegistrationRepository);

            resolver.setAuthorizationRequestCustomizer(customizer -> customizer
                    .attributes(attrs -> attrs.put(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256"))
            );

            // Create mock request
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/oauth2/authorization/hsid")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // Resolve
            OAuth2AuthorizationRequest authRequest = resolver.resolve(exchange).block();

            // Verify code_challenge format (base64url: A-Z, a-z, 0-9, -, _)
            String codeChallenge = (String) authRequest.getAdditionalParameters()
                    .get(PkceParameterNames.CODE_CHALLENGE);

            assertThat(codeChallenge)
                    .as("Code challenge should be a non-empty base64url string")
                    .isNotBlank()
                    .matches("^[A-Za-z0-9_-]+$");

            // S256 code challenges are 43 characters (256 bits / 6 bits per char = ~43)
            assertThat(codeChallenge.length())
                    .as("S256 code challenge should be 43 characters")
                    .isEqualTo(43);
        }

        @Test
        @DisplayName("Each authorization request should have unique code_challenge")
        void eachAuthRequestShouldHaveUniqueCodeChallenge() {
            // Setup
            ReactiveClientRegistrationRepository clientRegistrationRepository =
                    new InMemoryReactiveClientRegistrationRepository(createHsidPkceClientRegistration());

            DefaultServerOAuth2AuthorizationRequestResolver resolver =
                    new DefaultServerOAuth2AuthorizationRequestResolver(clientRegistrationRepository);

            resolver.setAuthorizationRequestCustomizer(customizer -> customizer
                    .attributes(attrs -> attrs.put(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256"))
            );

            // Create two requests
            MockServerHttpRequest request1 = MockServerHttpRequest.get("/oauth2/authorization/hsid").build();
            MockServerHttpRequest request2 = MockServerHttpRequest.get("/oauth2/authorization/hsid").build();

            OAuth2AuthorizationRequest authRequest1 = resolver.resolve(MockServerWebExchange.from(request1)).block();
            OAuth2AuthorizationRequest authRequest2 = resolver.resolve(MockServerWebExchange.from(request2)).block();

            String codeChallenge1 = (String) authRequest1.getAdditionalParameters()
                    .get(PkceParameterNames.CODE_CHALLENGE);
            String codeChallenge2 = (String) authRequest2.getAdditionalParameters()
                    .get(PkceParameterNames.CODE_CHALLENGE);

            assertThat(codeChallenge1)
                    .as("Each request should generate a unique code_challenge for security")
                    .isNotEqualTo(codeChallenge2);
        }
    }

    @Nested
    @DisplayName("Authorization Request URI")
    class AuthorizationRequestUriTests {

        @Test
        @DisplayName("Authorization URI should include all required PKCE parameters")
        void authorizationUriShouldIncludePkceParameters() {
            // Setup
            ReactiveClientRegistrationRepository clientRegistrationRepository =
                    new InMemoryReactiveClientRegistrationRepository(createHsidPkceClientRegistration());

            DefaultServerOAuth2AuthorizationRequestResolver resolver =
                    new DefaultServerOAuth2AuthorizationRequestResolver(clientRegistrationRepository);

            resolver.setAuthorizationRequestCustomizer(customizer -> customizer
                    .attributes(attrs -> attrs.put(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256"))
            );

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/oauth2/authorization/hsid")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            OAuth2AuthorizationRequest authRequest = resolver.resolve(exchange).block();
            String authorizationUri = authRequest.getAuthorizationRequestUri();

            assertThat(authorizationUri)
                    .as("Authorization URI should include code_challenge")
                    .contains("code_challenge=")
                    .as("Authorization URI should include code_challenge_method=S256")
                    .contains("code_challenge_method=S256")
                    .as("Authorization URI should include response_type=code")
                    .contains("response_type=code")
                    .as("Authorization URI should include client_id")
                    .contains("client_id=test-pkce-client")
                    .as("Authorization URI should include redirect_uri")
                    .contains("redirect_uri=")
                    .as("Authorization URI should include scope")
                    .contains("scope=");
        }

        @Test
        @DisplayName("Authorization URI should NOT include client_secret")
        void authorizationUriShouldNotIncludeClientSecret() {
            // Setup
            ReactiveClientRegistrationRepository clientRegistrationRepository =
                    new InMemoryReactiveClientRegistrationRepository(createHsidPkceClientRegistration());

            DefaultServerOAuth2AuthorizationRequestResolver resolver =
                    new DefaultServerOAuth2AuthorizationRequestResolver(clientRegistrationRepository);

            resolver.setAuthorizationRequestCustomizer(customizer -> customizer
                    .attributes(attrs -> attrs.put(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256"))
            );

            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/oauth2/authorization/hsid")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            OAuth2AuthorizationRequest authRequest = resolver.resolve(exchange).block();
            String authorizationUri = authRequest.getAuthorizationRequestUri();

            assertThat(authorizationUri)
                    .as("PKCE flow should not include client_secret in authorization URI")
                    .doesNotContain("client_secret");
        }
    }
}
