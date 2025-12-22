package com.example.bff.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Centralized cache metrics service for tracking cache operations.
 * Exposes metrics via Micrometer for Prometheus scraping.
 */
@Slf4j
@Service
public class CacheMetricsService {

    private static final String METRIC_PREFIX = "bff.cache";
    private static final String TAG_CACHE_NAME = "cache";
    private static final String TAG_CACHE_TYPE = "type";

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Counter> hitCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> missCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> evictionCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> sizeGauges = new ConcurrentHashMap<>();

    public CacheMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("Cache metrics service initialized");
    }

    /**
     * Record a cache hit.
     */
    public void recordHit(String cacheName, String cacheType) {
        getOrCreateHitCounter(cacheName, cacheType).increment();
    }

    /**
     * Record a cache miss.
     */
    public void recordMiss(String cacheName, String cacheType) {
        getOrCreateMissCounter(cacheName, cacheType).increment();
    }

    /**
     * Record a cache eviction.
     */
    public void recordEviction(String cacheName, String cacheType) {
        getOrCreateEvictionCounter(cacheName, cacheType).increment();
    }

    /**
     * Register a cache size gauge.
     */
    public void registerSizeGauge(String cacheName, String cacheType, Supplier<Long> sizeSupplier) {
        String key = cacheName + ":" + cacheType;
        if (!sizeGauges.containsKey(key)) {
            AtomicLong gauge = new AtomicLong(0);
            sizeGauges.put(key, gauge);

            meterRegistry.gauge(
                    METRIC_PREFIX + ".size",
                    Tags.of(TAG_CACHE_NAME, cacheName, TAG_CACHE_TYPE, cacheType),
                    gauge,
                    AtomicLong::get
            );

            // Update gauge periodically via a scheduled task or on-demand
            log.debug("Registered size gauge for cache: {}:{}", cacheName, cacheType);
        }
    }

    /**
     * Update the size gauge for a cache.
     */
    public void updateSize(String cacheName, String cacheType, long size) {
        String key = cacheName + ":" + cacheType;
        AtomicLong gauge = sizeGauges.get(key);
        if (gauge != null) {
            gauge.set(size);
        }
    }

    private Counter getOrCreateHitCounter(String cacheName, String cacheType) {
        String key = cacheName + ":" + cacheType;
        return hitCounters.computeIfAbsent(key, k ->
                Counter.builder(METRIC_PREFIX + ".hits")
                        .description("Number of cache hits")
                        .tags(TAG_CACHE_NAME, cacheName, TAG_CACHE_TYPE, cacheType)
                        .register(meterRegistry)
        );
    }

    private Counter getOrCreateMissCounter(String cacheName, String cacheType) {
        String key = cacheName + ":" + cacheType;
        return missCounters.computeIfAbsent(key, k ->
                Counter.builder(METRIC_PREFIX + ".misses")
                        .description("Number of cache misses")
                        .tags(TAG_CACHE_NAME, cacheName, TAG_CACHE_TYPE, cacheType)
                        .register(meterRegistry)
        );
    }

    private Counter getOrCreateEvictionCounter(String cacheName, String cacheType) {
        String key = cacheName + ":" + cacheType;
        return evictionCounters.computeIfAbsent(key, k ->
                Counter.builder(METRIC_PREFIX + ".evictions")
                        .description("Number of cache evictions")
                        .tags(TAG_CACHE_NAME, cacheName, TAG_CACHE_TYPE, cacheType)
                        .register(meterRegistry)
        );
    }

    /**
     * Get the current hit rate for a cache (hits / (hits + misses)).
     */
    public double getHitRate(String cacheName, String cacheType) {
        String key = cacheName + ":" + cacheType;
        Counter hitCounter = hitCounters.get(key);
        Counter missCounter = missCounters.get(key);

        if (hitCounter == null || missCounter == null) {
            return 0.0;
        }

        double hits = hitCounter.count();
        double misses = missCounter.count();
        double total = hits + misses;

        return total > 0 ? hits / total : 0.0;
    }
}
