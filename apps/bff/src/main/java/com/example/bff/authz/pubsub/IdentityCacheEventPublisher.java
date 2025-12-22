package com.example.bff.authz.pubsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Publishes identity cache eviction events to Redis pub/sub.
 * Only active in Redis mode to propagate cache invalidations across pods.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.cache.type", havingValue = "redis")
public class IdentityCacheEventPublisher {

    public static final String CACHE_EVENT_CHANNEL = "bff:cache:identity:events";

    private final ReactiveRedisOperations<String, String> redisOps;
    private final ObjectMapper objectMapper;
    private final String instanceId;

    public IdentityCacheEventPublisher(
            ReactiveRedisOperations<String, String> redisOps,
            ObjectMapper objectMapper,
            @Value("${app.instance.id:#{T(java.util.UUID).randomUUID().toString()}}") String instanceId) {
        this.redisOps = redisOps;
        this.objectMapper = objectMapper;
        this.instanceId = instanceId;
        log.info("Identity cache event publisher initialized (instance={})", instanceId);
    }

    /**
     * Publish a cache eviction event.
     */
    @NonNull
    public Mono<Void> publishEviction(@NonNull String cacheName, @NonNull String key) {
        IdentityCacheEventMessage message = IdentityCacheEventMessage.evict(cacheName, key, instanceId);
        return publish(message);
    }

    private Mono<Void> publish(IdentityCacheEventMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            return redisOps.convertAndSend(CACHE_EVENT_CHANNEL, json)
                    .doOnSuccess(count -> log.debug("Published cache event to {} subscribers: {}:{}",
                            count, message.cacheName(), message.key()))
                    .doOnError(e -> log.warn("Failed to publish cache event: {}", e.getMessage()))
                    .onErrorResume(e -> Mono.empty())
                    .then();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize cache event: {}", e.getMessage());
            return Mono.empty();
        }
    }

    /**
     * Get this instance's ID for filtering self-published events.
     */
    public String getInstanceId() {
        return instanceId;
    }
}
