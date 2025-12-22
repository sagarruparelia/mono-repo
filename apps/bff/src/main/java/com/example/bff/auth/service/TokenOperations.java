package com.example.bff.auth.service;

import com.example.bff.auth.model.TokenData;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Mono;

/**
 * Interface for OAuth token management operations.
 * Implementations can use Redis (distributed) or in-memory (single pod) storage.
 */
public interface TokenOperations {

    /**
     * Stores OAuth tokens for a session.
     */
    @NonNull
    Mono<Void> storeTokens(@NonNull String sessionId, @NonNull TokenData tokenData);

    /**
     * Retrieves OAuth tokens for a session.
     */
    @NonNull
    Mono<TokenData> getTokens(@NonNull String sessionId);

    /**
     * Gets a fresh access token, refreshing if needed.
     */
    @NonNull
    Mono<String> getFreshAccessToken(@NonNull String sessionId);

    /**
     * Removes tokens from a session.
     */
    @NonNull
    Mono<Void> removeTokens(@NonNull String sessionId);

    /**
     * Revokes the refresh token at the IDP.
     */
    @NonNull
    Mono<Void> revokeRefreshToken(@NonNull String sessionId);
}
