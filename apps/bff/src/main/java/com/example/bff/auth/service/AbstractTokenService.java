package com.example.bff.auth.service;

import com.example.bff.auth.model.TokenData;
import com.example.bff.common.util.StringSanitizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Abstract base class for token service implementations.
 * Provides shared logic for token refresh, parsing, and revocation.
 */
@Slf4j
public abstract class AbstractTokenService implements TokenOperations {

    protected static final long TOKEN_EXPIRY_BUFFER_SECONDS = 60;

    protected final ObjectMapper objectMapper;
    protected final WebClient webClient;

    @Value("${spring.security.oauth2.client.provider.hsid.token-uri}")
    protected String tokenUri;

    @Value("${spring.security.oauth2.client.registration.hsid.client-id}")
    protected String clientId;

    @Value("${app.auth.hsid.revocation-uri:#{null}}")
    protected String revocationUri;

    protected AbstractTokenService(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
    }

    @Override
    @NonNull
    public Mono<String> getFreshAccessToken(@NonNull String sessionId) {
        return getTokens(sessionId)
                .flatMap(tokenData -> {
                    if (!tokenData.isAccessTokenExpired(TOKEN_EXPIRY_BUFFER_SECONDS)) {
                        log.debug("Access token still valid for session {}", StringSanitizer.forLog(sessionId));
                        return Mono.just(tokenData.accessToken());
                    }

                    if (!tokenData.canRefresh()) {
                        log.warn("Refresh token expired for session {}, re-auth required", StringSanitizer.forLog(sessionId));
                        return Mono.empty();
                    }

                    log.info("Refreshing access token for session {}", StringSanitizer.forLog(sessionId));
                    return refreshToken(sessionId, tokenData.refreshToken());
                });
    }

    @NonNull
    protected Mono<String> refreshToken(@NonNull String sessionId, @NonNull String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("refresh_token", refreshToken);
        formData.add("client_id", clientId);

        return webClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(response -> parseAndStoreTokens(sessionId, response))
                .doOnError(e -> log.error("Token refresh failed for session {}: {}",
                        StringSanitizer.forLog(sessionId), e.getMessage()));
    }

    @NonNull
    protected Mono<String> parseAndStoreTokens(@NonNull String sessionId, @NonNull String response) {
        try {
            JsonNode json = objectMapper.readTree(response);

            String accessToken = json.path("access_token").asText(null);
            String newRefreshToken = json.path("refresh_token").asText(null);
            String idToken = json.path("id_token").asText(null);
            int expiresIn = json.path("expires_in").asInt(3600);
            int refreshExpiresIn = json.path("refresh_expires_in").asInt(0);

            if (accessToken == null) {
                log.error("No access_token in refresh response for session {}", StringSanitizer.forLog(sessionId));
                return Mono.empty();
            }

            Instant accessTokenExpiry = Instant.now().plusSeconds(expiresIn);
            Instant refreshTokenExpiry = refreshExpiresIn > 0
                    ? Instant.now().plusSeconds(refreshExpiresIn)
                    : null;

            TokenData newTokenData = new TokenData(
                    accessToken,
                    newRefreshToken,
                    idToken,
                    accessTokenExpiry,
                    refreshTokenExpiry
            );

            return storeTokens(sessionId, newTokenData)
                    .thenReturn(accessToken);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse token response: {}", e.getMessage());
            return Mono.empty();
        }
    }

    @Override
    @NonNull
    public Mono<Void> revokeRefreshToken(@NonNull String sessionId) {
        if (revocationUri == null || revocationUri.isBlank()) {
            log.debug("Token revocation skipped - no revocation URI configured");
            return Mono.empty();
        }

        if (!StringSanitizer.isValidSessionId(sessionId)) {
            return Mono.empty();
        }

        return getTokens(sessionId)
                .filter(tokenData -> tokenData.refreshToken() != null)
                .flatMap(tokenData -> {
                    MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
                    formData.add("token", tokenData.refreshToken());
                    formData.add("token_type_hint", "refresh_token");
                    formData.add("client_id", clientId);

                    return webClient.post()
                            .uri(revocationUri)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(BodyInserters.fromFormData(formData))
                            .retrieve()
                            .toBodilessEntity()
                            .doOnSuccess(r -> log.info("Refresh token revoked for session {}",
                                    StringSanitizer.forLog(sessionId)))
                            .then();
                })
                .onErrorResume(e -> {
                    log.warn("Token revocation failed for session {}: {}",
                            StringSanitizer.forLog(sessionId), e.getMessage());
                    return Mono.empty();
                });
    }
}
