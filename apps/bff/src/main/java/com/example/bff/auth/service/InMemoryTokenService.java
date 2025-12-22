package com.example.bff.auth.service;

import com.example.bff.auth.model.TokenData;
import com.example.bff.common.util.StringSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of TokenOperations for single-pod deployments.
 * Uses ConcurrentHashMap for token storage.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.cache.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryTokenService extends AbstractTokenService {

    private final ConcurrentHashMap<String, TokenData> tokenStore = new ConcurrentHashMap<>();

    public InMemoryTokenService(
            @NonNull ObjectMapper objectMapper,
            @NonNull WebClient.Builder webClientBuilder) {
        super(objectMapper, webClientBuilder);
        log.info("In-memory token service initialized (single-pod mode)");
    }

    @Override
    @NonNull
    public Mono<Void> storeTokens(@NonNull String sessionId, @NonNull TokenData tokenData) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            log.warn("Invalid session ID format in storeTokens");
            return Mono.error(new IllegalArgumentException("Invalid session ID format"));
        }

        return Mono.fromRunnable(() -> {
            tokenStore.put(sessionId, tokenData);
            log.debug("Stored tokens for session {}", StringSanitizer.forLog(sessionId));
        });
    }

    @Override
    @NonNull
    public Mono<TokenData> getTokens(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> tokenStore.get(sessionId))
                .flatMap(tokenData -> tokenData != null ? Mono.just(tokenData) : Mono.empty());
    }

    @Override
    @NonNull
    public Mono<Void> removeTokens(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> {
            tokenStore.remove(sessionId);
            log.debug("Removed tokens for session {}", StringSanitizer.forLog(sessionId));
        });
    }
}
