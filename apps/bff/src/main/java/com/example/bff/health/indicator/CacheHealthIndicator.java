package com.example.bff.health.indicator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Health indicator for cache backends (Redis and MongoDB).
 * Reports cache connectivity and status via /actuator/health/cache.
 */
@Slf4j
@Component("cacheHealthIndicator")
public class CacheHealthIndicator implements ReactiveHealthIndicator {

    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(5);

    @Nullable
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ReactiveMongoTemplate mongoTemplate;

    @Value("${app.cache.type:memory}")
    private String cacheType;

    public CacheHealthIndicator(
            @Nullable ReactiveRedisTemplate<String, String> redisTemplate,
            ReactiveMongoTemplate mongoTemplate) {
        this.redisTemplate = redisTemplate;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Mono<Health> health() {
        Health.Builder builder = Health.up();
        builder.withDetail("cacheType", cacheType);

        // Check Redis if in redis mode
        Mono<Void> redisCheck = Mono.empty();
        if ("redis".equals(cacheType) && redisTemplate != null) {
            redisCheck = checkRedis(builder);
        } else {
            builder.withDetail("redis", "disabled (memory mode)");
        }

        // Always check MongoDB (health data cache)
        Mono<Void> mongoCheck = checkMongo(builder);

        return redisCheck
                .then(mongoCheck)
                .then(Mono.just(builder.build()))
                .timeout(HEALTH_CHECK_TIMEOUT)
                .onErrorResume(e -> {
                    log.warn("Cache health check failed: {}", e.getMessage());
                    return Mono.just(Health.down()
                            .withDetail("error", e.getMessage())
                            .withDetail("cacheType", cacheType)
                            .build());
                });
    }

    private Mono<Void> checkRedis(Health.Builder builder) {
        return redisTemplate.getConnectionFactory()
                .getReactiveConnection()
                .ping()
                .doOnSuccess(pong -> {
                    builder.withDetail("redis", "connected");
                    builder.withDetail("redisPing", pong);
                })
                .doOnError(e -> {
                    builder.down();
                    builder.withDetail("redis", "disconnected");
                    builder.withDetail("redisError", e.getMessage());
                })
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private Mono<Void> checkMongo(Health.Builder builder) {
        return mongoTemplate.executeCommand("{ping: 1}")
                .doOnSuccess(result -> {
                    builder.withDetail("mongodb", "connected");
                })
                .doOnError(e -> {
                    builder.down();
                    builder.withDetail("mongodb", "disconnected");
                    builder.withDetail("mongoError", e.getMessage());
                })
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}
