package com.example.bff.authz.service;

import com.example.bff.common.util.CacheKeyUtils;
import com.example.bff.config.properties.IdentityCacheProperties;
import com.example.bff.config.properties.MemoryCacheProperties;
import com.example.bff.observability.CacheMetricsService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.example.bff.config.properties.IdentityCacheProperties.*;

/**
 * In-memory implementation of IdentityCacheOperations for single-pod deployments.
 * Uses Caffeine cache with configurable TTL per cache type.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.cache.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryIdentityCacheService implements IdentityCacheOperations {

    private static final String CACHE_TYPE = "memory";

    private final Cache<String, Object> userInfoCache;
    private final Cache<String, Object> eligibilityCache;
    private final Cache<String, Object> permissionsCache;
    private final CacheMetricsService metricsService;

    public InMemoryIdentityCacheService(
            IdentityCacheProperties cacheProperties,
            MemoryCacheProperties memoryCacheProperties,
            CacheMetricsService metricsService) {

        this.metricsService = metricsService;

        int maxCacheSize = memoryCacheProperties != null
                ? memoryCacheProperties.maxIdentityEntries()
                : MemoryCacheProperties.defaults().maxIdentityEntries();

        this.userInfoCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheProperties.userService().ttl())
                .maximumSize(maxCacheSize)
                .build();

        this.eligibilityCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheProperties.eligibility().ttl())
                .maximumSize(maxCacheSize)
                .build();

        this.permissionsCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheProperties.permissions().ttl())
                .maximumSize(maxCacheSize)
                .build();

        // Register size gauges
        metricsService.registerSizeGauge(USER_SERVICE_CACHE, CACHE_TYPE, () -> userInfoCache.estimatedSize());
        metricsService.registerSizeGauge(ELIGIBILITY_CACHE, CACHE_TYPE, () -> eligibilityCache.estimatedSize());
        metricsService.registerSizeGauge(PERMISSIONS_CACHE, CACHE_TYPE, () -> permissionsCache.estimatedSize());

        log.info("In-memory identity cache service initialized (single-pod mode, max-entries={})", maxCacheSize);
    }

    @Override
    @NonNull
    public <T> Mono<T> getOrLoadUserInfo(@NonNull String key, @NonNull Mono<T> loader, @NonNull Class<T> type) {
        return getOrLoad(USER_SERVICE_CACHE, userInfoCache, key, loader, type);
    }

    @Override
    @NonNull
    public <T> Mono<T> getOrLoadEligibility(@NonNull String key, @NonNull Mono<T> loader, @NonNull Class<T> type) {
        return getOrLoad(ELIGIBILITY_CACHE, eligibilityCache, key, loader, type);
    }

    @Override
    @NonNull
    public <T> Mono<T> getOrLoadPermissions(@NonNull String key, @NonNull Mono<T> loader, @NonNull Class<T> type) {
        return getOrLoad(PERMISSIONS_CACHE, permissionsCache, key, loader, type);
    }

    @SuppressWarnings("unchecked")
    private <T> Mono<T> getOrLoad(
            String cacheName,
            Cache<String, Object> cache,
            String key,
            Mono<T> loader,
            Class<T> type) {

        String sanitizedKey = CacheKeyUtils.sanitize(key);

        return Mono.fromCallable(() -> {
            Object cached = cache.getIfPresent(sanitizedKey);
            if (cached != null) {
                try {
                    log.debug("Cache hit for key: {}:{}", cacheName, sanitizedKey);
                    metricsService.recordHit(cacheName, CACHE_TYPE);
                    return type.cast(cached);
                } catch (ClassCastException e) {
                    log.warn("Cache type mismatch for key {}:{}, evicting corrupted entry", cacheName, sanitizedKey);
                    cache.invalidate(sanitizedKey);
                    metricsService.recordEviction(cacheName, CACHE_TYPE);
                    return null;
                }
            }
            return null;
        }).flatMap(cachedValue -> {
            if (cachedValue != null) {
                return Mono.just(cachedValue);
            }
            // Cache miss - load and store
            metricsService.recordMiss(cacheName, CACHE_TYPE);
            return loader.flatMap(value -> {
                log.debug("Cache miss for key: {}:{}, loading and caching", cacheName, sanitizedKey);
                cache.put(sanitizedKey, value);
                metricsService.updateSize(cacheName, CACHE_TYPE, cache.estimatedSize());
                return Mono.just(value);
            });
        });
    }

    @Override
    @NonNull
    public Mono<Boolean> evict(@NonNull String cacheName, @NonNull String key) {
        String sanitizedKey = CacheKeyUtils.sanitize(key);
        Cache<String, Object> cache = getCacheByName(cacheName);
        if (cache != null) {
            cache.invalidate(sanitizedKey);
            metricsService.recordEviction(cacheName, CACHE_TYPE);
            metricsService.updateSize(cacheName, CACHE_TYPE, cache.estimatedSize());
            log.debug("Evicted cache entry: {}:{}", cacheName, sanitizedKey);
            return Mono.just(true);
        }
        return Mono.just(false);
    }

    private Cache<String, Object> getCacheByName(String cacheName) {
        return switch (cacheName) {
            case USER_SERVICE_CACHE -> userInfoCache;
            case ELIGIBILITY_CACHE -> eligibilityCache;
            case PERMISSIONS_CACHE -> permissionsCache;
            default -> null;
        };
    }

    @Override
    @NonNull
    public Mono<Boolean> evictUserInfo(@NonNull String hsidUuid) {
        return evict(USER_SERVICE_CACHE, hsidUuid);
    }

    @Override
    @NonNull
    public Mono<Boolean> evictEligibility(@NonNull String eid) {
        return evict(ELIGIBILITY_CACHE, eid);
    }

    @Override
    @NonNull
    public Mono<Boolean> evictPermissions(@NonNull String eid) {
        return evict(PERMISSIONS_CACHE, eid);
    }
}
