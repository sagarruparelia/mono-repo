package com.example.bff.client.cache;

import com.example.bff.config.BffProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
@ConditionalOnProperty(name = "bff.cache.store", havingValue = "redis")
public class RedisClientCache<T> implements ClientCache<T> {

    private static final String KEY_PREFIX = "bff:cache:";

    private final ReactiveRedisTemplate<String, T> redisTemplate;
    private final Duration ttl;

    public RedisClientCache(
            ReactiveRedisTemplate<String, T> redisTemplate,
            BffProperties properties) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofMinutes(properties.getCache().getTtlMinutes());
        log.info("Initialized Redis client cache with TTL: {} minutes",
                properties.getCache().getTtlMinutes());
    }

    @Override
    public Mono<T> get(String key) {
        return redisTemplate.opsForValue()
                .get(keyFor(key))
                .doOnNext(v -> log.debug("Cache hit in Redis for key: {}", key))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Cache miss in Redis for key: {}", key);
                    return Mono.empty();
                }));
    }

    @Override
    public Mono<T> put(String key, T value) {
        return redisTemplate.opsForValue()
                .set(keyFor(key), value, ttl)
                .thenReturn(value)
                .doOnSuccess(v -> log.debug("Cached value in Redis for key: {}", key));
    }

    @Override
    public Mono<Void> invalidate(String key) {
        return redisTemplate.delete(keyFor(key))
                .doOnSuccess(count -> log.debug("Invalidated cache in Redis for key: {}", key))
                .then();
    }

    @Override
    public Mono<Void> clear() {
        return redisTemplate.keys(KEY_PREFIX + "*")
                .flatMap(redisTemplate::delete)
                .then()
                .doOnSuccess(v -> log.info("Cleared all cache entries from Redis"));
    }

    private String keyFor(String key) {
        return KEY_PREFIX + key;
    }
}
