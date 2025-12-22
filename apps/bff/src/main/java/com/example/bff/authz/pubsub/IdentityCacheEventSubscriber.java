package com.example.bff.authz.pubsub;

import com.example.bff.authz.service.IdentityCacheOperations;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import static com.example.bff.authz.pubsub.IdentityCacheEventPublisher.CACHE_EVENT_CHANNEL;
import static com.example.bff.config.properties.IdentityCacheProperties.*;

/**
 * Subscribes to identity cache eviction events from Redis pub/sub.
 * Invalidates local cache entries when events are received from other pods.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.cache.type", havingValue = "redis")
public class IdentityCacheEventSubscriber {

    private final ReactiveRedisOperations<String, String> redisOps;
    private final ObjectMapper objectMapper;
    private final IdentityCacheOperations cacheOperations;
    private final IdentityCacheEventPublisher publisher;

    private Disposable subscription;

    public IdentityCacheEventSubscriber(
            ReactiveRedisOperations<String, String> redisOps,
            ObjectMapper objectMapper,
            IdentityCacheOperations cacheOperations,
            IdentityCacheEventPublisher publisher) {
        this.redisOps = redisOps;
        this.objectMapper = objectMapper;
        this.cacheOperations = cacheOperations;
        this.publisher = publisher;
    }

    @PostConstruct
    public void subscribe() {
        subscription = redisOps.listenTo(ChannelTopic.of(CACHE_EVENT_CHANNEL))
                .map(ReactiveSubscription.Message::getMessage)
                .flatMap(this::handleMessage)
                .subscribe(
                        v -> {},
                        e -> log.error("Error in cache event subscription: {}", e.getMessage())
                );
        log.info("Subscribed to identity cache events on channel: {}", CACHE_EVENT_CHANNEL);
    }

    @PreDestroy
    public void unsubscribe() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("Unsubscribed from identity cache events");
        }
    }

    @NonNull
    private Mono<Void> handleMessage(@NonNull String json) {
        try {
            IdentityCacheEventMessage message = objectMapper.readValue(json, IdentityCacheEventMessage.class);

            // Ignore events from this instance
            if (publisher.getInstanceId().equals(message.sourceInstanceId())) {
                log.trace("Ignoring self-published cache event: {}:{}", message.cacheName(), message.key());
                return Mono.empty();
            }

            log.debug("Received cache event from {}: {}:{} ({})",
                    message.sourceInstanceId(), message.cacheName(), message.key(), message.eventType());

            return switch (message.eventType()) {
                case EVICT -> handleEvict(message);
                case EVICT_ALL -> handleEvictAll(message);
            };
        } catch (Exception e) {
            log.warn("Failed to parse cache event message: {}", e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<Void> handleEvict(IdentityCacheEventMessage message) {
        return switch (message.cacheName()) {
            case USER_SERVICE_CACHE -> cacheOperations.evictUserInfo(message.key()).then();
            case ELIGIBILITY_CACHE -> cacheOperations.evictEligibility(message.key()).then();
            case PERMISSIONS_CACHE -> cacheOperations.evictPermissions(message.key()).then();
            default -> {
                log.warn("Unknown cache name in event: {}", message.cacheName());
                yield Mono.empty();
            }
        };
    }

    private Mono<Void> handleEvictAll(IdentityCacheEventMessage message) {
        // For evict all, we'd need to clear entire caches
        // This is more complex with Redis as we'd need to scan and delete
        log.info("Received evict-all event for cache: {} (not implemented)", message.cacheName());
        return Mono.empty();
    }
}
