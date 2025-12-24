package com.example.bff.client;

import com.example.bff.config.BffProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class WebClientFactory {

    private static final String HCP_CLIENT_REGISTRATION_ID = "hcp";
    private static final String ECDH_CLIENT_REGISTRATION_ID = "ecdh";

    private final WebClient.Builder hcpWebClientBuilder;
    private final WebClient.Builder ecdhWebClientBuilder;
    private final BffProperties properties;

    public WebClientFactory(
            ReactiveOAuth2AuthorizedClientManager clientManager,
            BffProperties properties) {
        this.properties = properties;

        // HCP OAuth2 filter for existing APIs
        ServerOAuth2AuthorizedClientExchangeFilterFunction hcpOauth2Filter =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(clientManager);
        hcpOauth2Filter.setDefaultClientRegistrationId(HCP_CLIENT_REGISTRATION_ID);

        this.hcpWebClientBuilder = WebClient.builder()
                .filter(hcpOauth2Filter)
                .filter(loggingFilter())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024));

        // ECDH OAuth2 filter with separate credentials
        ServerOAuth2AuthorizedClientExchangeFilterFunction ecdhOauth2Filter =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(clientManager);
        ecdhOauth2Filter.setDefaultClientRegistrationId(ECDH_CLIENT_REGISTRATION_ID);

        this.ecdhWebClientBuilder = WebClient.builder()
                .filter(ecdhOauth2Filter)
                .filter(loggingFilter())
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024));
    }

    public WebClient userServiceClient() {
        return hcpWebClientBuilder.clone()
                .baseUrl(properties.getClient().getUserService().getBaseUrl())
                .build();
    }

    public WebClient delegateGraphClient() {
        return hcpWebClientBuilder.clone()
                .baseUrl(properties.getClient().getDelegateGraph().getBaseUrl())
                .build();
    }

    public WebClient eligibilityGraphClient() {
        return hcpWebClientBuilder.clone()
                .baseUrl(properties.getClient().getEligibilityGraph().getBaseUrl())
                .build();
    }

    // ECDH uses separate OAuth2 credentials from HCP
    @Bean
    public WebClient ecdhWebClient() {
        return ecdhWebClientBuilder.clone()
                .baseUrl(properties.getClient().getEcdhGraph().getBaseUrl())
                .build();
    }

    private ExchangeFilterFunction loggingFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.debug("Request: {} {}", clientRequest.method(), clientRequest.url());
            return Mono.just(clientRequest);
        });
    }
}
