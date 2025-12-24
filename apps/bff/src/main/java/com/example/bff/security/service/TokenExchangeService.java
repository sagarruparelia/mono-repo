package com.example.bff.security.service;

import com.example.bff.security.exception.AuthenticationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
public class TokenExchangeService {

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;
    private final String tokenUri;
    private final String redirectUri;

    public TokenExchangeService(
            @Value("${spring.security.oauth2.client.registration.hsid.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.hsid.client-secret}") String clientSecret,
            @Value("${spring.security.oauth2.client.provider.hsid.token-uri}") String tokenUri,
            @Value("${bff.oidc.redirect-uri:}") String redirectUri) {
        this.webClient = WebClient.builder().build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUri = tokenUri;
        this.redirectUri = redirectUri.isEmpty() ? "/" : redirectUri;
    }

    public Mono<TokenResponse> exchangeCode(String code, String codeVerifier, String baseUrl) {
        log.debug("Exchanging authorization code for tokens");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("code", code);
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("redirect_uri", baseUrl + redirectUri);

        if (codeVerifier != null && !codeVerifier.isEmpty()) {
            formData.add("code_verifier", codeVerifier);
        }

        return webClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(TokenEndpointResponse.class)
                .map(this::toTokenResponse)
                .doOnNext(response -> log.debug("Successfully exchanged code for tokens"))
                .onErrorMap(e -> new AuthenticationException("Failed to exchange authorization code", e));
    }

    private TokenResponse toTokenResponse(TokenEndpointResponse response) {
        Instant expiresAt = Instant.now().plusSeconds(response.expiresIn());

        return new TokenResponse(
                response.accessToken(),
                response.refreshToken(),
                response.idToken(),
                expiresAt
        );
    }

    private record TokenEndpointResponse(
            String access_token,
            String refresh_token,
            String id_token,
            Long expires_in,
            String token_type
    ) {
        String accessToken() {
            return access_token;
        }

        String refreshToken() {
            return refresh_token;
        }

        String idToken() {
            return id_token;
        }

        long expiresIn() {
            return expires_in != null ? expires_in : 3600;
        }
    }
}
