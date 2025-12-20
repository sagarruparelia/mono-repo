package com.example.bff.config;

import com.example.bff.config.properties.ExternalApiProperties;
import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * WebClient configuration for external identity APIs.
 * Uses Spring OAuth2 Client for automatic client credentials token management.
 *
 * Token lifecycle is handled automatically:
 * - Fetches token on first request
 * - Caches token until expiry
 * - Refreshes token before expiry
 */
@Configuration
public class ExternalApiWebClientConfig {

    /**
     * OAuth2 client registration ID for external API.
     * Must match the registration name in application.yml.
     */
    public static final String EXTERNAL_API_CLIENT_REGISTRATION_ID = "external-api";

    /**
     * Bean qualifier for the external API WebClient.
     */
    public static final String EXTERNAL_API_WEBCLIENT = "externalApiWebClient";

    /**
     * OAuth2 authorized client manager for client credentials flow.
     * Uses InMemoryReactiveOAuth2AuthorizedClientService for token storage.
     * Handles token caching and automatic refresh.
     */
    @Bean
    public ReactiveOAuth2AuthorizedClientManager externalApiAuthorizedClientManager(
            ReactiveClientRegistrationRepository clientRegistrationRepository) {

        var clientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
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

        return authorizedClientManager;
    }

    /**
     * WebClient configured with OAuth2 client credentials for external API calls.
     * Automatically includes Bearer token in Authorization header.
     *
     * Includes production-ready configuration:
     * - Connection pool with limits
     * - Connection and response timeouts
     * - Keep-alive and idle timeout settings
     */
    @Bean(EXTERNAL_API_WEBCLIENT)
    public WebClient externalApiWebClient(
            WebClient.Builder webClientBuilder,
            ReactiveOAuth2AuthorizedClientManager authorizedClientManager,
            ExternalApiProperties externalApiProperties) {

        // Configure connection pool for production use
        ConnectionProvider connectionProvider = ConnectionProvider.builder("external-api-pool")
                .maxConnections(100)
                .pendingAcquireMaxCount(500)
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(5))
                .evictInBackground(Duration.ofSeconds(30))
                .build();

        // Configure HTTP client with timeouts
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(10))
                .keepAlive(true);

        // Configure OAuth2 filter for automatic token management
        var oauth2Filter = new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Filter.setDefaultClientRegistrationId(EXTERNAL_API_CLIENT_REGISTRATION_ID);

        return webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(externalApiProperties.baseUrl())
                .filter(oauth2Filter)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
