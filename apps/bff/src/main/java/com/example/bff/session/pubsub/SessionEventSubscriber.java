package com.example.bff.session.pubsub;

import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.SessionProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

/**
 * Subscribes to Redis Pub/Sub session events from other BFF instances.
 * Handles session invalidation and force logout events.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.session.pubsub.enabled", havingValue = "true")
public class SessionEventSubscriber {

    private final ReactiveRedisOperations<String, String> redisOps;
    private final ObjectMapper objectMapper;
    private final String channel;
    private final String instanceId;
    private final SessionEventHandler eventHandler;

    private Disposable subscription;

    public SessionEventSubscriber(
            ReactiveRedisOperations<String, String> redisOps,
            ObjectMapper objectMapper,
            SessionProperties sessionProperties,
            SessionEventHandler eventHandler) {
        this.redisOps = redisOps;
        this.objectMapper = objectMapper;
        this.channel = sessionProperties.pubsub().channel();
        this.instanceId = getInstanceId();
        this.eventHandler = eventHandler;
        log.info("Session event subscriber initialized: channel={}, instance={}",
                channel, instanceId);
    }

    @PostConstruct
    public void subscribe() {
        subscription = redisOps.listenTo(ChannelTopic.of(channel))
                .map(ReactiveSubscription.Message::getMessage)
                .filter(this::isNotSelfOriginated)
                .subscribe(
                        this::handleMessage,
                        error -> log.error("Session event subscription error: {}",
                                StringSanitizer.forLog(error.getMessage())),
                        () -> log.warn("Session event subscription completed unexpectedly")
                );
        log.info("Subscribed to session events on channel: {}", channel);
    }

    @PreDestroy
    public void unsubscribe() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("Unsubscribed from session events");
        }
    }

    private boolean isNotSelfOriginated(@NonNull String json) {
        try {
            SessionEventMessage message = objectMapper.readValue(json, SessionEventMessage.class);
            boolean isSelf = instanceId.equals(message.originInstance());
            if (isSelf) {
                log.trace("Ignoring self-originated event: {}", message.eventType());
            }
            return !isSelf;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse session event for origin check: {}",
                    StringSanitizer.forLog(e.getMessage()));
            return false;
        }
    }

    private void handleMessage(@NonNull String json) {
        try {
            SessionEventMessage message = objectMapper.readValue(json, SessionEventMessage.class);
            log.debug("Received session event from {}: type={}, session={}, hsidUuid={}",
                    message.originInstance(),
                    message.eventType(),
                    StringSanitizer.forLog(message.sessionId()),
                    StringSanitizer.forLog(message.hsidUuid()));

            switch (message.eventType()) {
                case INVALIDATED -> eventHandler.handleInvalidation(message);
                case FORCE_LOGOUT -> eventHandler.handleForceLogout(message);
                case ROTATED -> eventHandler.handleRotation(message);
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse session event: {}",
                    StringSanitizer.forLog(e.getMessage()));
        }
    }

    @NonNull
    private static String getInstanceId() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        return "bff-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
