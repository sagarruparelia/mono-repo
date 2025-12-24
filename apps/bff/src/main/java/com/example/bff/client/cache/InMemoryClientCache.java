package com.example.bff.client.cache;

import com.example.bff.config.BffProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(name = "bff.cache.store", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryClientCache<T> implements ClientCache<T> {

    private final ConcurrentHashMap<String, CacheEntry<T>> cache = new ConcurrentHashMap<>();
    private final Duration ttl;

    public InMemoryClientCache(BffProperties properties) {
        this.ttl = Duration.ofMinutes(properties.getCache().getTtlMinutes());
        log.info("Initialized in-memory client cache with TTL: {} minutes",
                properties.getCache().getTtlMinutes());
    }

    @Override
    public Mono<T> get(String key) {
        return Mono.justOrEmpty(cache.get(key))
                .filter(entry -> !entry.isExpired())
                .map(CacheEntry::getValue)
                .doOnNext(v -> log.debug("Cache hit for key: {}", key))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Cache miss for key: {}", key);
                    return Mono.empty();
                }));
    }

    @Override
    public Mono<T> put(String key, T value) {
        return Mono.fromSupplier(() -> {
            cache.put(key, new CacheEntry<>(value, Instant.now().plus(ttl)));
            log.debug("Cached value for key: {}", key);
            return value;
        });
    }

    @Override
    public Mono<Void> invalidate(String key) {
        return Mono.fromRunnable(() -> {
            cache.remove(key);
            log.debug("Invalidated cache for key: {}", key);
        });
    }

    @Override
    public Mono<Void> clear() {
        return Mono.fromRunnable(() -> {
            cache.clear();
            log.info("Cleared all cache entries");
        });
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredEntries() {
        int before = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removed = before - cache.size();
        if (removed > 0) {
            log.debug("Cleaned up {} expired cache entries", removed);
        }
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        T getValue() {
            return value;
        }
    }
}
