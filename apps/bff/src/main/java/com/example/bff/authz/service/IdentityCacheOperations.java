package com.example.bff.authz.service;

import org.springframework.lang.NonNull;
import reactor.core.publisher.Mono;

/**
 * Interface for identity API response caching.
 * Implementations can use Redis (distributed) or in-memory (single pod) caching.
 */
public interface IdentityCacheOperations {

    /**
     * Gets or loads user info from cache.
     */
    @NonNull
    <T> Mono<T> getOrLoadUserInfo(@NonNull String key, @NonNull Mono<T> loader, @NonNull Class<T> type);

    /**
     * Gets or loads eligibility data from cache.
     */
    @NonNull
    <T> Mono<T> getOrLoadEligibility(@NonNull String key, @NonNull Mono<T> loader, @NonNull Class<T> type);

    /**
     * Gets or loads permissions data from cache.
     */
    @NonNull
    <T> Mono<T> getOrLoadPermissions(@NonNull String key, @NonNull Mono<T> loader, @NonNull Class<T> type);

    /**
     * Evicts a specific cache entry.
     */
    @NonNull
    Mono<Boolean> evict(@NonNull String cacheName, @NonNull String key);

    /**
     * Evicts user info cache entry.
     */
    @NonNull
    Mono<Boolean> evictUserInfo(@NonNull String hsidUuid);

    /**
     * Evicts eligibility cache entry.
     */
    @NonNull
    Mono<Boolean> evictEligibility(@NonNull String eid);

    /**
     * Evicts permissions cache entry.
     */
    @NonNull
    Mono<Boolean> evictPermissions(@NonNull String eid);
}
