package com.example.bff.session.pubsub;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.time.Instant;

/**
 * Message for Redis Pub/Sub session events across BFF instances.
 * Used for cross-instance session invalidation and force logout.
 */
public record SessionEventMessage(
        @NonNull EventType eventType,
        @NonNull String sessionId,
        @Nullable String userId,
        @Nullable String reason,
        @NonNull Instant timestamp,
        @NonNull String originInstance
) {
    /**
     * Session event types for cross-instance communication.
     */
    public enum EventType {
        /**
         * Session was invalidated (logout, expired, binding failed).
         */
        INVALIDATED,

        /**
         * Force logout all sessions for a user (admin action, password change).
         */
        FORCE_LOGOUT,

        /**
         * Session was rotated (for cache invalidation).
         */
        ROTATED
    }

    /**
     * Creates an invalidation event.
     */
    @NonNull
    public static SessionEventMessage invalidated(
            @NonNull String sessionId,
            @Nullable String userId,
            @NonNull String reason,
            @NonNull String originInstance) {
        return new SessionEventMessage(
                EventType.INVALIDATED,
                sessionId,
                userId,
                reason,
                Instant.now(),
                originInstance
        );
    }

    /**
     * Creates a force logout event for a user.
     */
    @NonNull
    public static SessionEventMessage forceLogout(
            @NonNull String userId,
            @NonNull String reason,
            @NonNull String originInstance) {
        return new SessionEventMessage(
                EventType.FORCE_LOGOUT,
                "",  // No specific session
                userId,
                reason,
                Instant.now(),
                originInstance
        );
    }

    /**
     * Creates a session rotated event.
     */
    @NonNull
    public static SessionEventMessage rotated(
            @NonNull String oldSessionId,
            @NonNull String newSessionId,
            @Nullable String userId,
            @NonNull String originInstance) {
        return new SessionEventMessage(
                EventType.ROTATED,
                oldSessionId,
                userId,
                "Rotated to: " + newSessionId,
                Instant.now(),
                originInstance
        );
    }
}
