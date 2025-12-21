package com.example.bff.session.audit;

import com.example.bff.common.util.StringSanitizer;
import com.example.bff.session.model.ClientInfo;
import com.example.bff.session.model.SessionData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service for publishing session security audit events in structured JSON format.
 * Uses the same AUTHZ_AUDIT logger for unified SIEM ingestion.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.session.audit.enabled", havingValue = "true", matchIfMissing = true)
public class SessionAuditService {

    // Use same logger as AuthzAuditService for unified SIEM ingestion
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUTHZ_AUDIT");

    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile(
            "^([0-9]{1,3}\\.){3}[0-9]{1,3}$|^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$");

    private static final int MAX_CORRELATION_ID_LENGTH = 64;

    private final ObjectMapper objectMapper;

    /**
     * Logs session creation event.
     */
    public void logSessionCreated(
            @NonNull String sessionId,
            @NonNull SessionData sessionData,
            @Nullable ClientInfo clientInfo,
            @Nullable ServerHttpRequest request) {

        String correlationId = extractCorrelationId(request);
        String clientIp = clientInfo != null ? clientInfo.ipAddress() : extractClientIp(request);
        String deviceFingerprint = clientInfo != null ? clientInfo.deviceFingerprint() : null;

        SessionAuditEvent event = SessionAuditEvent.sessionCreated(
                sessionId,
                sessionData.hsidUuid(),
                sessionData.persona(),
                clientIp,
                deviceFingerprint,
                correlationId
        );

        logEvent(event);
    }

    /**
     * Logs session rotation event.
     */
    public void logSessionRotated(
            @NonNull String oldSessionId,
            @NonNull String newSessionId,
            @NonNull SessionData sessionData,
            @Nullable ClientInfo clientInfo,
            @Nullable String correlationId) {

        String clientIp = clientInfo != null ? clientInfo.ipAddress() : null;
        String deviceFingerprint = clientInfo != null ? clientInfo.deviceFingerprint() : null;

        SessionAuditEvent event = SessionAuditEvent.sessionRotated(
                oldSessionId,
                newSessionId,
                sessionData.hsidUuid(),
                sessionData.persona(),
                clientIp,
                deviceFingerprint,
                correlationId
        );

        logEvent(event);
    }

    /**
     * Logs session invalidation event.
     */
    public void logSessionInvalidated(
            @NonNull String sessionId,
            @Nullable String hsidUuid,
            @NonNull String reason,
            @Nullable ServerHttpRequest request) {

        String correlationId = extractCorrelationId(request);
        String clientIp = extractClientIp(request);

        SessionAuditEvent event = SessionAuditEvent.sessionInvalidated(
                sessionId,
                hsidUuid,
                reason,
                clientIp,
                correlationId
        );

        logEvent(event);
    }

    /**
     * Logs session binding validation failure.
     */
    public void logBindingFailed(
            @NonNull String sessionId,
            @Nullable String hsidUuid,
            @NonNull String reason,
            @Nullable ClientInfo clientInfo,
            @Nullable ServerHttpRequest request) {

        String correlationId = extractCorrelationId(request);
        String clientIp = clientInfo != null ? clientInfo.ipAddress() : extractClientIp(request);
        String deviceFingerprint = clientInfo != null ? clientInfo.deviceFingerprint() : null;
        String path = request != null ? request.getPath().value() : null;
        String method = request != null && request.getMethod() != null ? request.getMethod().name() : null;

        SessionAuditEvent event = SessionAuditEvent.bindingFailed(
                sessionId,
                hsidUuid,
                reason,
                clientIp,
                deviceFingerprint,
                path,
                method,
                correlationId
        );

        logEvent(event);
    }

    /**
     * Logs potential session hijack detection.
     */
    public void logHijackDetected(
            @NonNull String sessionId,
            @Nullable String hsidUuid,
            @NonNull String reason,
            @Nullable String expectedIp,
            @Nullable String actualIp,
            @Nullable ClientInfo clientInfo,
            @Nullable ServerHttpRequest request) {

        String correlationId = extractCorrelationId(request);
        String deviceFingerprint = clientInfo != null ? clientInfo.deviceFingerprint() : null;
        String path = request != null ? request.getPath().value() : null;
        String method = request != null && request.getMethod() != null ? request.getMethod().name() : null;

        SessionAuditEvent event = SessionAuditEvent.hijackDetected(
                sessionId,
                hsidUuid,
                reason,
                expectedIp,
                actualIp,
                deviceFingerprint,
                path,
                method,
                correlationId
        );

        logEvent(event);
    }

    /**
     * Logs session expiration event.
     */
    public void logSessionExpired(
            @NonNull String sessionId,
            @Nullable String hsidUuid,
            @Nullable String correlationId) {

        SessionAuditEvent event = SessionAuditEvent.sessionExpired(
                sessionId,
                hsidUuid,
                correlationId
        );

        logEvent(event);
    }

    private void logEvent(@NonNull SessionAuditEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event.toStructuredLog());
            logByOutcome(event.outcome(), json);
        } catch (JsonProcessingException e) {
            AUDIT_LOG.error("Failed to serialize session audit event: {}",
                    StringSanitizer.forLog(e.getMessage()));
            logFallback(event);
        }
    }

    private void logByOutcome(@NonNull SessionAuditEvent.Outcome outcome, String json) {
        switch (outcome) {
            case SUCCESS -> AUDIT_LOG.info(json);
            case FAILURE -> AUDIT_LOG.warn(json);
            case BLOCKED -> AUDIT_LOG.warn(json);
        }
    }

    private void logFallback(@NonNull SessionAuditEvent event) {
        AUDIT_LOG.warn("Session {} - event={}, user={}, reason={}, ip={}",
                event.outcome(),
                event.eventType(),
                StringSanitizer.forLog(event.userId()),
                StringSanitizer.forLog(event.reason()),
                StringSanitizer.forLog(event.clientIp()));
    }

    @Nullable
    private String extractCorrelationId(@Nullable ServerHttpRequest request) {
        if (request == null) {
            return UUID.randomUUID().toString();
        }

        String correlationId = request.getHeaders().getFirst(HEADER_CORRELATION_ID);

        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }

        String sanitized = correlationId.trim();
        if (sanitized.length() > MAX_CORRELATION_ID_LENGTH) {
            return UUID.randomUUID().toString();
        }

        if (!UUID_PATTERN.matcher(sanitized).matches() &&
            !sanitized.matches("^[a-zA-Z0-9_-]+$")) {
            return UUID.randomUUID().toString();
        }

        return sanitized;
    }

    @Nullable
    private String extractClientIp(@Nullable ServerHttpRequest request) {
        if (request == null) {
            return null;
        }

        String forwardedFor = request.getHeaders().getFirst(HEADER_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String firstIp = forwardedFor.split(",")[0].trim();
            if (isValidIpAddress(firstIp)) {
                return firstIp;
            }
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return null;
    }

    private boolean isValidIpAddress(@Nullable String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return IP_ADDRESS_PATTERN.matcher(ip).matches();
    }
}
