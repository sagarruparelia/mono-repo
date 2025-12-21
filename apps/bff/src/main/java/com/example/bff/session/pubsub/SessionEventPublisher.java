package com.example.bff.session.pubsub;

import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.SessionProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Publishes session events to Redis Pub/Sub for cross-instance communication.
 * Enables session invalidation to propagate across all BFF instances.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.session.pubsub.enabled", havingValue = "true")
public class SessionEventPublisher {

    private final ReactiveRedisOperations<String, String> redisOps;
    private final ObjectMapper objectMapper;
    private final String channel;
    private final String instanceId;

    public SessionEventPublisher(
            ReactiveRedisOperations<String, String> redisOps,
            ObjectMapper objectMapper,
            SessionProperties sessionProperties) {
        this.redisOps = redisOps;
        this.objectMapper = objectMapper;
        this.channel = sessionProperties.pubsub().channel();
        this.instanceId = generateInstanceId();
        log.info("Session event publisher initialized: channel={}, instance={}",
                channel, instanceId);
    }

    /**
     * Publishes session invalidation event.
     */
    @NonNull
    public Mono<Void> publishInvalidation(
            @NonNull String sessionId,
            @Nullable String hsidUuid,
            @NonNull String reason) {

        SessionEventMessage message = SessionEventMessage.invalidated(
                sessionId, hsidUuid, reason, instanceId);
        return publish(message);
    }

    /**
     * Publishes force logout event for a user.
     */
    @NonNull
    public Mono<Void> publishForceLogout(
            @NonNull String hsidUuid,
            @NonNull String reason) {

        SessionEventMessage message = SessionEventMessage.forceLogout(
                hsidUuid, reason, instanceId);
        return publish(message);
    }

    /**
     * Publishes session rotation event.
     */
    @NonNull
    public Mono<Void> publishRotation(
            @NonNull String oldSessionId,
            @NonNull String newSessionId,
            @Nullable String hsidUuid) {

        SessionEventMessage message = SessionEventMessage.rotated(
                oldSessionId, newSessionId, hsidUuid, instanceId);
        return publish(message);
    }

    @NonNull
    private Mono<Void> publish(@NonNull SessionEventMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            log.debug("Publishing session event: type={}, session={}, hsidUuid={}",
                    message.eventType(),
                    StringSanitizer.forLog(message.sessionId()),
                    StringSanitizer.forLog(message.hsidUuid()));

            return redisOps.convertAndSend(channel, json)
                    .doOnSuccess(count -> log.debug("Session event published to {} subscribers", count))
                    .doOnError(e -> log.error("Failed to publish session event: {}",
                            StringSanitizer.forLog(e.getMessage())))
                    .then();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize session event: {}",
                    StringSanitizer.forLog(e.getMessage()));
            return Mono.empty();
        }
    }

    /**
     * Returns the instance ID for this publisher.
     */
    @NonNull
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Generates the instance identifier for filtering self-originated messages.
     * Uses HOSTNAME env var (set by Kubernetes) or falls back to random UUID.
     */
    @NonNull
    private static String generateInstanceId() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        // Fallback for local development
        return "bff-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
