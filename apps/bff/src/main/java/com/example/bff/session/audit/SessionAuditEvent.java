package com.example.bff.session.audit;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Structured audit event for session security operations.
 * Designed for SIEM integration and security monitoring.
 */
public record SessionAuditEvent(
        // Event metadata
        @NonNull String eventId,
        @NonNull Instant timestamp,
        @Nullable String correlationId,

        // Event classification
        @NonNull EventType eventType,
        @NonNull Outcome outcome,
        @Nullable String reason,

        // Session identity (hashed for security)
        @NonNull String sessionIdHash,
        @Nullable String previousSessionIdHash,

        // User context
        @Nullable String hsidUuid,
        @Nullable String persona,

        // Device context
        @Nullable String clientIp,
        @Nullable String deviceFingerprint,

        // Request context
        @Nullable String path,
        @Nullable String method
) {
    /**
     * Session security event types for SIEM categorization.
     */
    public enum EventType {
        SESSION_CREATED,
        SESSION_ROTATED,
        SESSION_INVALIDATED,
        SESSION_REFRESHED,
        SESSION_BINDING_FAILED,
        SESSION_HIJACK_DETECTED,
        SESSION_EXPIRED
    }

    /**
     * Event outcome for security classification.
     */
    public enum Outcome {
        SUCCESS,
        FAILURE,
        BLOCKED
    }

    /**
     * Creates a session created event.
     */
    @NonNull
    public static SessionAuditEvent sessionCreated(
            @NonNull String sessionId,
            @Nullable String hsidUuid,
            @Nullable String persona,
            @Nullable String clientIp,
            @Nullable String deviceFingerprint,
            @Nullable String correlationId) {

        return new SessionAuditEvent(
                generateEventId(),
                Instant.now(),
                correlationId,
                EventType.SESSION_CREATED,
                Outcome.SUCCESS,
                null,
                hashSessionId(sessionId),
                null,
                hsidUuid,
                persona,
                clientIp,
                deviceFingerprint,
                null,
                null
        );
    }

    /**
     * Creates a session rotated event.
     */
    @NonNull
    public static SessionAuditEvent sessionRotated(
            @NonNull String oldSessionId,
            @NonNull String newSessionId,
            @Nullable String hsidUuid,
            @Nullable String persona,
            @Nullable String clientIp,
            @Nullable String deviceFingerprint,
            @Nullable String correlationId) {

        return new SessionAuditEvent(
                generateEventId(),
                Instant.now(),
                correlationId,
                EventType.SESSION_ROTATED,
                Outcome.SUCCESS,
                "Scheduled rotation",
                hashSessionId(newSessionId),
                hashSessionId(oldSessionId),
                hsidUuid,
                persona,
                clientIp,
                deviceFingerprint,
                null,
                null
        );
    }

    /**
     * Creates a session invalidated event.
     */
    @NonNull
    public static SessionAuditEvent sessionInvalidated(
            @NonNull String sessionId,
            @Nullable String hsidUuid,
            @NonNull String reason,
            @Nullable String clientIp,
            @Nullable String correlationId) {

        return new SessionAuditEvent(
                generateEventId(),
                Instant.now(),
                correlationId,
                EventType.SESSION_INVALIDATED,
                Outcome.SUCCESS,
                reason,
                hashSessionId(sessionId),
                null,
                hsidUuid,
                null,
                clientIp,
                null,
                null,
                null
        );
    }

    /**
     * Creates a session binding failure event.
     */
    @NonNull
    public static SessionAuditEvent bindingFailed(
            @NonNull String sessionId,
            @Nullable String hsidUuid,
            @NonNull String reason,
            @Nullable String clientIp,
            @Nullable String deviceFingerprint,
            @Nullable String path,
            @Nullable String method,
            @Nullable String correlationId) {

        return new SessionAuditEvent(
                generateEventId(),
                Instant.now(),
                correlationId,
                EventType.SESSION_BINDING_FAILED,
                Outcome.BLOCKED,
                reason,
                hashSessionId(sessionId),
                null,
                hsidUuid,
                null,
                clientIp,
                deviceFingerprint,
                path,
                method
        );
    }

    /**
     * Creates a session hijack detected event.
     */
    @NonNull
    public static SessionAuditEvent hijackDetected(
            @NonNull String sessionId,
            @Nullable String hsidUuid,
            @NonNull String reason,
            @Nullable String expectedIp,
            @Nullable String actualIp,
            @Nullable String deviceFingerprint,
            @Nullable String path,
            @Nullable String method,
            @Nullable String correlationId) {

        String detailReason = String.format("%s (expected IP: %s, actual IP: %s)",
                reason,
                expectedIp != null ? expectedIp : "unknown",
                actualIp != null ? actualIp : "unknown");

        return new SessionAuditEvent(
                generateEventId(),
                Instant.now(),
                correlationId,
                EventType.SESSION_HIJACK_DETECTED,
                Outcome.BLOCKED,
                detailReason,
                hashSessionId(sessionId),
                null,
                hsidUuid,
                null,
                actualIp,
                deviceFingerprint,
                path,
                method
        );
    }

    /**
     * Creates a session expired event.
     */
    @NonNull
    public static SessionAuditEvent sessionExpired(
            @NonNull String sessionId,
            @Nullable String hsidUuid,
            @Nullable String correlationId) {

        return new SessionAuditEvent(
                generateEventId(),
                Instant.now(),
                correlationId,
                EventType.SESSION_EXPIRED,
                Outcome.SUCCESS,
                "TTL expired",
                hashSessionId(sessionId),
                null,
                hsidUuid,
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Converts event to structured map for JSON logging.
     * Field names follow SIEM conventions (snake_case).
     */
    @NonNull
    public Map<String, Object> toStructuredLog() {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("event_type", "session_" + eventType.name().toLowerCase());
        log.put("event_id", eventId);
        log.put("timestamp", timestamp.toString());
        log.put("correlation_id", correlationId != null ? correlationId : "");
        log.put("session_event_type", eventType.name());
        log.put("outcome", outcome.name());
        log.put("reason", reason != null ? reason : "");
        log.put("session_id_hash", sessionIdHash);
        if (previousSessionIdHash != null) {
            log.put("previous_session_id_hash", previousSessionIdHash);
        }
        log.put("hsid_uuid", hsidUuid != null ? hsidUuid : "");
        log.put("persona", persona != null ? persona : "");
        log.put("client_ip", clientIp != null ? clientIp : "");
        log.put("device_fingerprint", deviceFingerprint != null ? deviceFingerprint : "");
        if (path != null) {
            log.put("path", path);
        }
        if (method != null) {
            log.put("method", method);
        }
        return log;
    }

    @NonNull
    private static String generateEventId() {
        return UUID.randomUUID().toString();
    }

    /**
     * SHA-256 hash of session ID for secure logging.
     * Never log raw session IDs to prevent session hijacking via log exposure.
     */
    @NonNull
    private static String hashSessionId(@NonNull String sessionId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sessionId.getBytes(StandardCharsets.UTF_8));
            // Use first 16 bytes (32 hex chars) for brevity
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
