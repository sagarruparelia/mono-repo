package com.example.bff.config;

import com.example.bff.config.properties.EcdhApiProperties;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Qualifier;
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
 * WebClient configuration for ECDH Health Data API.
 * Uses separate OAuth2 client credentials from the external identity API.
 *
 * Token lifecycle is handled automatically:
 * - Fetches token on first request
 * - Caches token until expiry
 * - Refreshes token before expiry
 */
@Configuration
public class EcdhApiWebClientConfig {

    /**
     * OAuth2 client registration ID for ECDH API.
     * Must match the registration name in application.yml.
     */
    public static final String ECDH_API_CLIENT_REGISTRATION_ID = "ecdh-api";

    /**
     * Bean qualifier for the ECDH API WebClient.
     */
    public static final String ECDH_API_WEBCLIENT = "ecdhApiWebClient";

    /**
     * OAuth2 authorized client manager for ECDH API client credentials flow.
     * Uses InMemoryReactiveOAuth2AuthorizedClientService for token storage.
     * Handles token caching and automatic refresh.
     */
    @Bean
    public ReactiveOAuth2AuthorizedClientManager ecdhApiAuthorizedClientManager(
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
     * WebClient configured with OAuth2 client credentials for ECDH API calls.
     * Automatically includes Bearer token in Authorization header.
     *
     * Includes production-ready configuration:
     * - Connection pool with limits
     * - Connection and response timeouts
     * - Keep-alive and idle timeout settings
     */
    @Bean
    @Qualifier(ECDH_API_WEBCLIENT)
    public WebClient ecdhApiWebClient(
            WebClient.Builder webClientBuilder,
            @Qualifier("ecdhApiAuthorizedClientManager") ReactiveOAuth2AuthorizedClientManager authorizedClientManager,
            EcdhApiProperties ecdhApiProperties) {

        // Configure connection pool for production use
        ConnectionProvider connectionProvider = ConnectionProvider.builder("ecdh-api-pool")
                .maxConnections(50)
                .pendingAcquireMaxCount(250)
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(5))
                .evictInBackground(Duration.ofSeconds(30))
                .build();

        // Configure HTTP client with timeouts
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(ecdhApiProperties.timeout())
                .keepAlive(true);

        // Configure OAuth2 filter for automatic token management
        var oauth2Filter = new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Filter.setDefaultClientRegistrationId(ECDH_API_CLIENT_REGISTRATION_ID);

        return webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(ecdhApiProperties.baseUrl())
                .filter(oauth2Filter)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
