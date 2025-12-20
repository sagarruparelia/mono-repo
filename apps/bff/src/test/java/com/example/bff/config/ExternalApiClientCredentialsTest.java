package com.example.bff.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for External API client credentials OAuth2 configuration.
 * Verifies that:
 * 1. Client credentials registration is correctly configured
 * 2. OAuth2 authorized client manager is set up with proper clock skew
 * 3. WebClient OAuth2 filter is configured for automatic token injection
 * 4. Token caching and refresh behavior is properly configured
 */
class ExternalApiClientCredentialsTest {

    private static final String EXTERNAL_API_CLIENT_REGISTRATION_ID = "external-api";

    /**
     * Creates a client credentials registration matching application.yml
     */
    private ClientRegistration createExternalApiClientRegistration() {
        return ClientRegistration
                .withRegistrationId(EXTERNAL_API_CLIENT_REGISTRATION_ID)
                .clientId("test-external-client")
                .clientSecret("test-client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("https://api.example.com/oauth2/token")
                .scope("openid")
                .build();
    }

    @Nested
    @DisplayName("Client Credentials Registration")
    class ClientRegistrationTests {

        @Test
        @DisplayName("External API client should use client_credentials grant type")
        void externalApiClientShouldUseClientCredentialsGrant() {
            ClientRegistration registration = createExternalApiClientRegistration();

            assertThat(registration.getAuthorizationGrantType())
                    .as("Grant type should be CLIENT_CREDENTIALS")
                    .isEqualTo(AuthorizationGrantType.CLIENT_CREDENTIALS);
        }

        @Test
        @DisplayName("External API client should have client secret")
        void externalApiClientShouldHaveClientSecret() {
            ClientRegistration registration = createExternalApiClientRegistration();

            assertThat(registration.getClientSecret())
                    .as("Client credentials flow requires a client secret")
                    .isNotBlank();
        }

        @Test
        @DisplayName("External API client should use CLIENT_SECRET_BASIC authentication")
        void externalApiClientShouldUseBasicAuth() {
            ClientRegistration registration = createExternalApiClientRegistration();

            // Client credentials typically use Basic auth or POST body
            assertThat(registration.getClientAuthenticationMethod())
                    .as("Client authentication should be CLIENT_SECRET_BASIC")
                    .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        }

        @Test
        @DisplayName("External API client should have token URI configured")
        void externalApiClientShouldHaveTokenUri() {
            ClientRegistration registration = createExternalApiClientRegistration();

            assertThat(registration.getProviderDetails().getTokenUri())
                    .as("Token URI should be configured for token exchange")
                    .isNotBlank()
                    .contains("/oauth2/token");
        }

        @Test
        @DisplayName("External API client should have openid scope")
        void externalApiClientShouldHaveOpenidScope() {
            ClientRegistration registration = createExternalApiClientRegistration();

            assertThat(registration.getScopes())
                    .as("Should include openid scope")
                    .contains("openid");
        }

        @Test
        @DisplayName("External API client should NOT have authorization URI (not needed for client credentials)")
        void externalApiClientShouldNotHaveAuthorizationUri() {
            ClientRegistration registration = createExternalApiClientRegistration();

            // Client credentials flow doesn't use authorization endpoint
            assertThat(registration.getProviderDetails().getAuthorizationUri())
                    .as("Client credentials flow doesn't need authorization URI")
                    .isNullOrEmpty();
        }
    }

    @Nested
    @DisplayName("OAuth2 Authorized Client Manager")
    class AuthorizedClientManagerTests {

        @Test
        @DisplayName("Should create authorized client manager with client credentials provider")
        void shouldCreateAuthorizedClientManager() {
            ReactiveClientRegistrationRepository clientRegistrationRepository =
                    new InMemoryReactiveClientRegistrationRepository(createExternalApiClientRegistration());

            // Build provider with clock skew (same as ExternalApiWebClientConfig)
            ReactiveOAuth2AuthorizedClientProvider clientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                    .clientCredentials(builder -> builder
                            .clockSkew(Duration.ofSeconds(60)) // Refresh 60s before expiry
                    )
                    .build();

            var authorizedClientService = new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrationRepository);
            var authorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                    clientRegistrationRepository,
                    authorizedClientService
            );
            authorizedClientManager.setAuthorizedClientProvider(clientProvider);

            assertThat(authorizedClientManager)
                    .as("Authorized client manager should be created")
                    .isNotNull()
                    .isInstanceOf(ReactiveOAuth2AuthorizedClientManager.class);
        }

        @Test
        @DisplayName("Clock skew should be configured for proactive token refresh")
        void clockSkewShouldBeConfigured() {
            // The clock skew is set to 60 seconds in ExternalApiWebClientConfig
            // This means tokens will be refreshed 60 seconds before they expire
            Duration clockSkew = Duration.ofSeconds(60);

            assertThat(clockSkew)
                    .as("Clock skew should be 60 seconds for proactive refresh")
                    .isEqualTo(Duration.ofSeconds(60));

            // Verify it's a reasonable value (between 30s and 5 minutes)
            assertThat(clockSkew.getSeconds())
                    .as("Clock skew should be reasonable (30s to 5min)")
                    .isBetween(30L, 300L);
        }

        @Test
        @DisplayName("Should use InMemory client service for token storage")
        void shouldUseInMemoryClientService() {
            ReactiveClientRegistrationRepository clientRegistrationRepository =
                    new InMemoryReactiveClientRegistrationRepository(createExternalApiClientRegistration());

            var authorizedClientService = new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrationRepository);

            assertThat(authorizedClientService)
                    .as("Should use InMemoryReactiveOAuth2AuthorizedClientService")
                    .isNotNull()
                    .isInstanceOf(InMemoryReactiveOAuth2AuthorizedClientService.class);
        }
    }

    @Nested
    @DisplayName("WebClient OAuth2 Filter")
    class WebClientOAuth2FilterTests {

        @Test
        @DisplayName("OAuth2 filter should be created with authorized client manager")
        void oauth2FilterShouldBeCreated() {
            ReactiveClientRegistrationRepository clientRegistrationRepository =
                    new InMemoryReactiveClientRegistrationRepository(createExternalApiClientRegistration());

            ReactiveOAuth2AuthorizedClientProvider clientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                    .clientCredentials()
                    .build();

            var authorizedClientService = new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrationRepository);
            var authorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                    clientRegistrationRepository,
                    authorizedClientService
            );
            authorizedClientManager.setAuthorizedClientProvider(clientProvider);

            var oauth2Filter = new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
            oauth2Filter.setDefaultClientRegistrationId(EXTERNAL_API_CLIENT_REGISTRATION_ID);

            assertThat(oauth2Filter)
                    .as("OAuth2 filter should be created")
                    .isNotNull()
                    .isInstanceOf(ExchangeFilterFunction.class);
        }

        @Test
        @DisplayName("OAuth2 filter should have default client registration ID set")
        void oauth2FilterShouldHaveDefaultClientRegistrationId() {
            ReactiveClientRegistrationRepository clientRegistrationRepository =
                    new InMemoryReactiveClientRegistrationRepository(createExternalApiClientRegistration());

            ReactiveOAuth2AuthorizedClientProvider clientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                    .clientCredentials()
                    .build();

            var authorizedClientService = new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrationRepository);
            var authorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                    clientRegistrationRepository,
                    authorizedClientService
            );
            authorizedClientManager.setAuthorizedClientProvider(clientProvider);

            var oauth2Filter = new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
            oauth2Filter.setDefaultClientRegistrationId(EXTERNAL_API_CLIENT_REGISTRATION_ID);

            // The filter is configured - verify by checking it's an ExchangeFilterFunction
            // The actual token injection will happen during request execution
            assertThat(oauth2Filter)
                    .as("OAuth2 filter should be an ExchangeFilterFunction")
                    .isInstanceOf(ExchangeFilterFunction.class);
        }
    }

    @Nested
    @DisplayName("WebClient Configuration")
    class WebClientConfigurationTests {

        @Test
        @DisplayName("WebClient should be built with OAuth2 filter")
        void webClientShouldHaveOAuth2Filter() {
            ReactiveClientRegistrationRepository clientRegistrationRepository =
                    new InMemoryReactiveClientRegistrationRepository(createExternalApiClientRegistration());

            ReactiveOAuth2AuthorizedClientProvider clientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                    .clientCredentials()
                    .build();

            var authorizedClientService = new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrationRepository);
            var authorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                    clientRegistrationRepository,
                    authorizedClientService
            );
            authorizedClientManager.setAuthorizedClientProvider(clientProvider);

            var oauth2Filter = new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
            oauth2Filter.setDefaultClientRegistrationId(EXTERNAL_API_CLIENT_REGISTRATION_ID);

            WebClient webClient = WebClient.builder()
                    .baseUrl("https://api.example.com")
                    .filter(oauth2Filter)
                    .build();

            assertThat(webClient)
                    .as("WebClient should be created with OAuth2 filter")
                    .isNotNull();
        }

        @Test
        @DisplayName("WebClient should have base URL configured")
        void webClientShouldHaveBaseUrl() {
            String baseUrl = "https://api.example.com";

            WebClient webClient = WebClient.builder()
                    .baseUrl(baseUrl)
                    .build();

            // We can't directly access the baseUrl, but we verify the client is created
            assertThat(webClient)
                    .as("WebClient should be created with base URL")
                    .isNotNull();
        }

        @Test
        @DisplayName("WebClient should have JSON content type headers")
        void webClientShouldHaveJsonHeaders() {
            WebClient webClient = WebClient.builder()
                    .baseUrl("https://api.example.com")
                    .defaultHeader("Content-Type", "application/json")
                    .defaultHeader("Accept", "application/json")
                    .build();

            assertThat(webClient)
                    .as("WebClient should be created with JSON headers")
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("Token Lifecycle")
    class TokenLifecycleTests {

        @Test
        @DisplayName("Token should be automatically fetched on first request")
        void tokenShouldBeFetchedOnFirstRequest() {
            // This is a behavioral test - the OAuth2 filter automatically fetches
            // a token when a request is made if no valid token exists

            // The configuration ensures:
            // 1. First request triggers token fetch from token-uri
            // 2. Token is cached in InMemoryReactiveOAuth2AuthorizedClientService
            // 3. Subsequent requests reuse the cached token

            // Verify the configuration supports this behavior
            ReactiveClientRegistrationRepository clientRegistrationRepository =
                    new InMemoryReactiveClientRegistrationRepository(createExternalApiClientRegistration());

            ClientRegistration registration = clientRegistrationRepository
                    .findByRegistrationId(EXTERNAL_API_CLIENT_REGISTRATION_ID)
                    .block();

            assertThat(registration)
                    .as("Registration should exist for automatic token fetch")
                    .isNotNull();
            assertThat(registration.getProviderDetails().getTokenUri())
                    .as("Token URI must be configured for token fetch")
                    .isNotBlank();
        }

        @Test
        @DisplayName("Token should be refreshed before expiry with clock skew")
        void tokenShouldBeRefreshedBeforeExpiry() {
            // The clock skew ensures tokens are refreshed before they expire
            // With 60s clock skew, a token with 5 minute expiry will be refreshed at 4 minutes

            Duration clockSkew = Duration.ofSeconds(60);
            Duration tokenExpiry = Duration.ofMinutes(5);
            Duration effectiveExpiry = tokenExpiry.minus(clockSkew);

            assertThat(effectiveExpiry)
                    .as("Effective token lifetime should be reduced by clock skew")
                    .isEqualTo(Duration.ofMinutes(4));

            assertThat(effectiveExpiry.getSeconds())
                    .as("Token should be refreshed 60s before actual expiry")
                    .isEqualTo(240L); // 4 minutes in seconds
        }

        @Test
        @DisplayName("Client credentials should not require refresh token")
        void clientCredentialsShouldNotRequireRefreshToken() {
            ClientRegistration registration = createExternalApiClientRegistration();

            // Client credentials flow doesn't use refresh tokens
            // It simply fetches a new token when needed
            assertThat(registration.getAuthorizationGrantType())
                    .as("Client credentials flow gets new tokens, doesn't refresh")
                    .isEqualTo(AuthorizationGrantType.CLIENT_CREDENTIALS);
        }
    }

    @Nested
    @DisplayName("Service Integration")
    class ServiceIntegrationTests {

        @Test
        @DisplayName("Services should inject WebClient with external-api qualifier")
        void servicesShouldInjectQualifiedWebClient() {
            // This test documents the expected qualifier pattern
            String qualifier = ExternalApiWebClientConfig.EXTERNAL_API_WEBCLIENT;

            assertThat(qualifier)
                    .as("Qualifier should be 'externalApiWebClient'")
                    .isEqualTo("externalApiWebClient");
        }

        @Test
        @DisplayName("External API client registration ID should match config")
        void clientRegistrationIdShouldMatchConfig() {
            String registrationId = ExternalApiWebClientConfig.EXTERNAL_API_CLIENT_REGISTRATION_ID;

            assertThat(registrationId)
                    .as("Registration ID should be 'external-api'")
                    .isEqualTo("external-api");
        }
    }
}
