package com.example.bff.authz.pubsub;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.time.Instant;

/**
 * Message for identity cache invalidation events.
 * Published via Redis pub/sub to propagate cache evictions across pods.
 */
public record IdentityCacheEventMessage(
        @NonNull EventType eventType,
        @NonNull String cacheName,
        @NonNull String key,
        @NonNull Instant timestamp,
        @Nullable String sourceInstanceId
) {
    public enum EventType {
        EVICT,
        EVICT_ALL
    }

    /**
     * Create an eviction event for a specific key.
     */
    public static IdentityCacheEventMessage evict(String cacheName, String key, String instanceId) {
        return new IdentityCacheEventMessage(
                EventType.EVICT,
                cacheName,
                key,
                Instant.now(),
                instanceId
        );
    }

    /**
     * Create an event to evict all entries for a cache.
     */
    public static IdentityCacheEventMessage evictAll(String cacheName, String instanceId) {
        return new IdentityCacheEventMessage(
                EventType.EVICT_ALL,
                cacheName,
                "*",
                Instant.now(),
                instanceId
        );
    }
}
