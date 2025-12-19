package com.example.bff.auth.service;

import com.example.bff.auth.model.TokenData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Service for managing HSID OAuth2 tokens.
 *
 * <p>Provides on-demand token refresh for micro-products that require
 * fresh HSID tokens. Tokens are stored in Redis session and refreshed
 * using the refresh_token when needed.
 */
@Service
public class TokenService {

    private static final Logger LOG = LoggerFactory.getLogger(TokenService.class);

    private static final String SESSION_KEY = "bff:session:";
    private static final String TOKEN_FIELD = "tokenData";
    private static final long TOKEN_EXPIRY_BUFFER_SECONDS = 60; // Refresh 60s before expiry

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final ReactiveRedisOperations<String, String> redisOps;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    @Value("${spring.security.oauth2.client.provider.hsid.token-uri}")
    private String tokenUri;

    @Value("${spring.security.oauth2.client.registration.hsid.client-id}")
    private String clientId;

    public TokenService(
            ReactiveRedisOperations<String, String> redisOps,
            ObjectMapper objectMapper,
            WebClient.Builder webClientBuilder) {
        this.redisOps = redisOps;
        this.objectMapper = objectMapper;
        this.webClient = webClientBuilder.build();
    }

    /**
     * Stores token data in the session.
     *
     * @param sessionId the session ID
     * @param tokenData the token data to store
     * @return Mono completing when stored
     */
    @NonNull
    public Mono<Void> storeTokens(@NonNull String sessionId, @NonNull TokenData tokenData) {
        if (!isValidSessionId(sessionId)) {
            LOG.warn("Invalid session ID format in storeTokens");
            return Mono.error(new IllegalArgumentException("Invalid session ID format"));
        }

        String sessionKey = SESSION_KEY + sessionId;

        try {
            String tokenJson = objectMapper.writeValueAsString(tokenData);
            return redisOps.opsForHash()
                    .put(sessionKey, TOKEN_FIELD, tokenJson)
                    .then();
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize token data: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    /**
     * Retrieves token data from the session.
     *
     * @param sessionId the session ID
     * @return Mono emitting token data if found
     */
    @NonNull
    public Mono<TokenData> getTokens(@NonNull String sessionId) {
        if (!isValidSessionId(sessionId)) {
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
                        LOG.error("Failed to deserialize token data: {}", e.getMessage());
                        return Mono.empty();
                    }
                });
    }

    /**
     * Gets a fresh access token for micro-products.
     * Refreshes the token if expired or about to expire.
     *
     * @param sessionId the session ID
     * @return Mono emitting fresh access token, or empty if unavailable
     */
    @NonNull
    public Mono<String> getFreshAccessToken(@NonNull String sessionId) {
        return getTokens(sessionId)
                .flatMap(tokenData -> {
                    if (!tokenData.isAccessTokenExpired(TOKEN_EXPIRY_BUFFER_SECONDS)) {
                        // Token still valid
                        LOG.debug("Access token still valid for session {}", sanitize(sessionId));
                        return Mono.just(tokenData.accessToken());
                    }

                    if (!tokenData.canRefresh()) {
                        // Cannot refresh - user needs to re-authenticate
                        LOG.warn("Refresh token expired for session {}, re-auth required", sanitize(sessionId));
                        return Mono.empty();
                    }

                    // Refresh the token
                    LOG.info("Refreshing access token for session {}", sanitize(sessionId));
                    return refreshToken(sessionId, tokenData.refreshToken());
                });
    }

    /**
     * Refreshes the access token using the refresh token.
     *
     * @param sessionId    the session ID
     * @param refreshToken the refresh token
     * @return Mono emitting new access token
     */
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
                .doOnError(e -> LOG.error("Token refresh failed for session {}: {}",
                        sanitize(sessionId), e.getMessage()));
    }

    /**
     * Parses token response and stores updated tokens.
     */
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
                LOG.error("No access_token in refresh response for session {}", sanitize(sessionId));
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
            LOG.error("Failed to parse token response: {}", e.getMessage());
            return Mono.empty();
        }
    }

    /**
     * Removes token data from session (on logout).
     *
     * @param sessionId the session ID
     * @return Mono completing when removed
     */
    @NonNull
    public Mono<Void> removeTokens(@NonNull String sessionId) {
        if (!isValidSessionId(sessionId)) {
            return Mono.empty();
        }

        String sessionKey = SESSION_KEY + sessionId;
        return redisOps.opsForHash()
                .remove(sessionKey, TOKEN_FIELD)
                .then();
    }

    private boolean isValidSessionId(String sessionId) {
        return sessionId != null && UUID_PATTERN.matcher(sessionId).matches();
    }

    private String sanitize(String value) {
        if (value == null) return "null";
        return value.replaceAll("[\r\n\t]", "").substring(0, Math.min(value.length(), 64));
    }
}
