package com.example.bff.auth.service;

import com.example.bff.auth.model.TokenData;
import com.example.bff.common.util.StringSanitizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.lang.NonNull;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
public class TokenService {

    private static final String SESSION_KEY = "bff:session:";
    private static final String TOKEN_FIELD = "tokenData";
    private static final long TOKEN_EXPIRY_BUFFER_SECONDS = 60;

    private final ReactiveRedisOperations<String, String> redisOps;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    @Value("${spring.security.oauth2.client.provider.hsid.token-uri}")
    private String tokenUri;

    @Value("${spring.security.oauth2.client.registration.hsid.client-id}")
    private String clientId;

    @Value("${app.auth.hsid.revocation-uri:#{null}}")
    private String revocationUri;

    public TokenService(
            @NonNull ReactiveRedisOperations<String, String> redisOps,
            @NonNull ObjectMapper objectMapper,
            @NonNull WebClient.Builder webClientBuilder) {
        this.redisOps = redisOps;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
    }

    @NonNull
    public Mono<Void> storeTokens(@NonNull String sessionId, @NonNull TokenData tokenData) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            log.warn("Invalid session ID format in storeTokens");
            return Mono.error(new IllegalArgumentException("Invalid session ID format"));
        }

        String sessionKey = SESSION_KEY + sessionId;

        try {
            String tokenJson = objectMapper.writeValueAsString(tokenData);
            return redisOps.opsForHash()
                    .put(sessionKey, TOKEN_FIELD, tokenJson)
                    .then();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize token data: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    @NonNull
    public Mono<TokenData> getTokens(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            return Mono.empty();
        }

        String sessionKey = SESSION_KEY + sessionId;

        return redisOps.opsForHash()
                .get(sessionKey, TOKEN_FIELD)
                .cast(String.class)
                .flatMap(json -> {
                    try {
                        TokenData tokenData = objectMapper.readValue(json, TokenData.class);
                        return Mono.just(tokenData);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize token data: {}", e.getMessage());
                        return Mono.empty();
                    }
                });
    }

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
    private Mono<String> refreshToken(@NonNull String sessionId, @NonNull String refreshToken) {
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
    private Mono<String> parseAndStoreTokens(@NonNull String sessionId, @NonNull String response) {
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

    @NonNull
    public Mono<Void> removeTokens(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            return Mono.empty();
        }

        String sessionKey = SESSION_KEY + sessionId;
        return redisOps.opsForHash()
                .remove(sessionKey, TOKEN_FIELD)
                .then();
    }

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
