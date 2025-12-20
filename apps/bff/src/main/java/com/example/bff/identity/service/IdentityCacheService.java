package com.example.bff.identity.service;

import com.example.bff.config.properties.IdentityCacheProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static com.example.bff.config.IdentityCacheConfig.IDENTITY_CACHE_TEMPLATE;
import static com.example.bff.config.properties.IdentityCacheProperties.*;

/**
 * Reactive cache service for identity API responses.
 * Uses Redis for caching with configurable TTL per cache type.
 */
@Slf4j
@Service
public class IdentityCacheService {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final IdentityCacheProperties cacheProperties;
    private final ObjectMapper objectMapper;

    public IdentityCacheService(
            @Qualifier(IDENTITY_CACHE_TEMPLATE) ReactiveRedisTemplate<String, Object> redisTemplate,
            IdentityCacheProperties cacheProperties,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Get or compute a cached value for User Service responses.
     *
     * @param key      Cache key (typically hsidUuid)
     * @param loader   Function to load the value if not cached
     * @param <T>      Value type
     * @return Cached or computed value
     */
    @NonNull
    public <T> Mono<T> getOrLoadUserInfo(@NonNull String key, @NonNull Mono<T> loader, @NonNull Class<T> type) {
        return getOrLoad(USER_SERVICE_CACHE, key, loader, type, cacheProperties.userService().ttl());
    }

    /**
     * Get or compute a cached value for Eligibility responses.
     *
     * @param key    Cache key (typically EID)
     * @param loader Function to load the value if not cached
     * @param <T>    Value type
     * @return Cached or computed value
     */
    @NonNull
    public <T> Mono<T> getOrLoadEligibility(@NonNull String key, @NonNull Mono<T> loader, @NonNull Class<T> type) {
        return getOrLoad(ELIGIBILITY_CACHE, key, loader, type, cacheProperties.eligibility().ttl());
    }

    /**
     * Get or compute a cached value for Permission responses.
     *
     * @param key    Cache key (typically EID)
     * @param loader Function to load the value if not cached
     * @param <T>    Value type
     * @return Cached or computed value
     */
    @NonNull
    public <T> Mono<T> getOrLoadPermissions(@NonNull String key, @NonNull Mono<T> loader, @NonNull Class<T> type) {
        return getOrLoad(PERMISSIONS_CACHE, key, loader, type, cacheProperties.permissions().ttl());
    }

    /**
     * Generic get-or-load implementation for reactive caching.
     * Includes graceful degradation when cache is unavailable.
     */
    private <T> Mono<T> getOrLoad(
            @NonNull String cacheName,
            @NonNull String key,
            @NonNull Mono<T> loader,
            @NonNull Class<T> type,
            @NonNull Duration ttl) {

        // Validate and sanitize cache key to prevent cache poisoning
        String sanitizedKey = sanitizeKey(key);
        String fullKey = cacheName + ":" + sanitizedKey;

        return redisTemplate.opsForValue().get(fullKey)
                .flatMap(cached -> {
                    try {
                        // Convert the cached Object back to the target type
                        T value = objectMapper.convertValue(cached, type);
                        log.debug("Cache hit for key: {}", fullKey);
                        return Mono.just(value);
                    } catch (Exception e) {
                        log.warn("Failed to deserialize cached value for key {}, evicting corrupted entry", fullKey);
                        // Delete corrupted cache entry and return empty to trigger loader
                        return redisTemplate.delete(fullKey).then(Mono.empty());
                    }
                })
                .switchIfEmpty(
                        loader.flatMap(value -> {
                            log.debug("Cache miss for key: {}, loading and caching with TTL: {}", fullKey, ttl);
                            return redisTemplate.opsForValue()
                                    .set(fullKey, value, ttl)
                                    .thenReturn(value)
                                    .onErrorResume(cacheWriteError -> {
                                        // If cache write fails, still return the loaded value
                                        log.warn("Failed to write to cache for key {}: {}", fullKey, cacheWriteError.getMessage());
                                        return Mono.just(value);
                                    });
                        })
                )
                .onErrorResume(cacheError -> {
                    // Graceful degradation: fall back to loader if cache is unavailable
                    log.warn("Cache unavailable for key {}, falling back to loader: {}", fullKey, cacheError.getMessage());
                    return loader;
                });
    }

    /**
     * Sanitizes cache key to prevent cache key collision/poisoning.
     * Removes colons and special characters that could cause key collisions.
     */
    private String sanitizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Cache key cannot be null or blank");
        }
        // Replace potentially dangerous characters that could cause key collisions
        return key.replaceAll("[:\\s\\n\\r\\t]", "_");
    }

    /**
     * Evict a specific cache entry.
     *
     * @param cacheName Cache name prefix
     * @param key       Cache key
     * @return True if evicted, false if not found
     */
    @NonNull
    public Mono<Boolean> evict(@NonNull String cacheName, @NonNull String key) {
        String fullKey = cacheName + ":" + key;
        return redisTemplate.delete(fullKey)
                .map(count -> count > 0)
                .doOnSuccess(evicted -> {
                    if (evicted) {
                        log.debug("Evicted cache entry: {}", fullKey);
                    }
                });
    }

    /**
     * Evict user info cache for a specific HSID UUID.
     */
    @NonNull
    public Mono<Boolean> evictUserInfo(@NonNull String hsidUuid) {
        return evict(USER_SERVICE_CACHE, hsidUuid);
    }

    /**
     * Evict eligibility cache for a specific EID.
     */
    @NonNull
    public Mono<Boolean> evictEligibility(@NonNull String eid) {
        return evict(ELIGIBILITY_CACHE, eid);
    }

    /**
     * Evict permissions cache for a specific EID.
     */
    @NonNull
    public Mono<Boolean> evictPermissions(@NonNull String eid) {
        return evict(PERMISSIONS_CACHE, eid);
    }
}
