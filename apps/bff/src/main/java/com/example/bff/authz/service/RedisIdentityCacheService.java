package com.example.bff.authz.service;

import com.example.bff.authz.pubsub.IdentityCacheEventPublisher;
import com.example.bff.config.properties.IdentityCacheProperties;
import com.example.bff.observability.CacheMetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static com.example.bff.config.IdentityCacheConfig.IDENTITY_CACHE_TEMPLATE;
import static com.example.bff.config.properties.IdentityCacheProperties.*;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.cache.type", havingValue = "redis")
public class RedisIdentityCacheService implements IdentityCacheOperations {

    private static final String CACHE_TYPE = "redis";

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final IdentityCacheProperties cacheProperties;
    private final ObjectMapper objectMapper;
    private final CacheMetricsService metricsService;
    @Nullable
    private final IdentityCacheEventPublisher eventPublisher;

    public RedisIdentityCacheService(
            @Qualifier(IDENTITY_CACHE_TEMPLATE) ReactiveRedisTemplate<String, Object> redisTemplate,
            IdentityCacheProperties cacheProperties,
            ObjectMapper objectMapper,
            CacheMetricsService metricsService,
            @Nullable IdentityCacheEventPublisher eventPublisher) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.eventPublisher = eventPublisher;
    }

    @NonNull
    public <T> Mono<T> getOrLoadUserInfo(@NonNull String key, @NonNull Mono<T> loader, @NonNull Class<T> type) {
        return getOrLoad(USER_SERVICE_CACHE, key, loader, type, cacheProperties.userService().ttl());
    }

    @NonNull
    public <T> Mono<T> getOrLoadEligibility(@NonNull String key, @NonNull Mono<T> loader, @NonNull Class<T> type) {
        return getOrLoad(ELIGIBILITY_CACHE, key, loader, type, cacheProperties.eligibility().ttl());
    }

    @NonNull
    public <T> Mono<T> getOrLoadPermissions(@NonNull String key, @NonNull Mono<T> loader, @NonNull Class<T> type) {
        return getOrLoad(PERMISSIONS_CACHE, key, loader, type, cacheProperties.permissions().ttl());
    }

    private <T> Mono<T> getOrLoad(
            String cacheName,
            String key,
            Mono<T> loader,
            Class<T> type,
            Duration ttl) {

        String sanitizedKey = sanitizeKey(key);
        String fullKey = cacheName + ":" + sanitizedKey;

        return redisTemplate.opsForValue().get(fullKey)
                .flatMap(cached -> {
                    try {
                        T value = objectMapper.convertValue(cached, type);
                        log.debug("Cache hit for key: {}", fullKey);
                        metricsService.recordHit(cacheName, CACHE_TYPE);
                        return Mono.just(value);
                    } catch (Exception e) {
                        log.warn("Failed to deserialize cached value for key {}, evicting corrupted entry", fullKey);
                        metricsService.recordEviction(cacheName, CACHE_TYPE);
                        return redisTemplate.delete(fullKey).then(Mono.empty());
                    }
                })
                .switchIfEmpty(
                        Mono.defer(() -> {
                            metricsService.recordMiss(cacheName, CACHE_TYPE);
                            return loader.flatMap(value -> {
                                log.debug("Cache miss for key: {}, loading and caching with TTL: {}", fullKey, ttl);
                                return redisTemplate.opsForValue()
                                        .set(fullKey, value, ttl)
                                        .thenReturn(value)
                                        .onErrorResume(cacheWriteError -> {
                                            log.warn("Failed to write to cache for key {}: {}", fullKey, cacheWriteError.getMessage());
                                            return Mono.just(value);
                                        });
                            });
                        })
                )
                .onErrorResume(cacheError -> {
                    log.warn("Cache unavailable for key {}, falling back to loader: {}", fullKey, cacheError.getMessage());
                    metricsService.recordMiss(cacheName, CACHE_TYPE);
                    return loader;
                });
    }

    private String sanitizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Cache key cannot be null or blank");
        }
        return key.replaceAll("[:\\s\\n\\r\\t]", "_");
    }

    @NonNull
    public Mono<Boolean> evict(@NonNull String cacheName, @NonNull String key) {
        String fullKey = cacheName + ":" + key;
        return redisTemplate.delete(fullKey)
                .map(count -> count > 0)
                .flatMap(evicted -> {
                    if (evicted) {
                        metricsService.recordEviction(cacheName, CACHE_TYPE);
                        log.debug("Evicted cache entry: {}", fullKey);

                        // Publish eviction event to other pods
                        if (eventPublisher != null) {
                            return eventPublisher.publishEviction(cacheName, key)
                                    .thenReturn(true);
                        }
                    }
                    return Mono.just(evicted);
                });
    }

    @NonNull
    public Mono<Boolean> evictUserInfo(@NonNull String hsidUuid) {
        return evict(USER_SERVICE_CACHE, hsidUuid);
    }

    @NonNull
    public Mono<Boolean> evictEligibility(@NonNull String eid) {
        return evict(ELIGIBILITY_CACHE, eid);
    }

    @NonNull
    public Mono<Boolean> evictPermissions(@NonNull String eid) {
        return evict(PERMISSIONS_CACHE, eid);
    }
}
