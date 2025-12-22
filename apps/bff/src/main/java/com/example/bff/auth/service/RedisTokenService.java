package com.example.bff.auth.service;

import com.example.bff.auth.model.TokenData;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.SessionProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Redis implementation of TokenOperations.
 * Stores tokens as JSON in a separate key from sessions.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.cache.type", havingValue = "redis")
public class RedisTokenService extends AbstractTokenService {

    private static final String TOKENS_KEY = "bff:tokens:";

    private final ReactiveRedisOperations<String, String> redisOps;
    private final SessionProperties sessionProperties;

    public RedisTokenService(
            @NonNull ReactiveRedisOperations<String, String> redisOps,
            @NonNull ObjectMapper objectMapper,
            @NonNull WebClient.Builder webClientBuilder,
            @NonNull SessionProperties sessionProperties) {
        super(objectMapper, webClientBuilder);
        this.redisOps = redisOps;
        this.sessionProperties = sessionProperties;
    }

    @Override
    @NonNull
    public Mono<Void> storeTokens(@NonNull String sessionId, @NonNull TokenData tokenData) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            log.warn("Invalid session ID format in storeTokens");
            return Mono.error(new IllegalArgumentException("Invalid session ID format"));
        }

        try {
            String tokenJson = objectMapper.writeValueAsString(tokenData);
            Duration ttl = sessionProperties.timeout();
            return redisOps.opsForValue()
                    .set(TOKENS_KEY + sessionId, tokenJson, ttl)
                    .then();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize token data: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    @Override
    @NonNull
    public Mono<TokenData> getTokens(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            return Mono.empty();
        }

        return redisOps.opsForValue()
                .get(TOKENS_KEY + sessionId)
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

    @Override
    @NonNull
    public Mono<Void> removeTokens(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            return Mono.empty();
        }

        return redisOps.delete(TOKENS_KEY + sessionId).then();
    }
}
