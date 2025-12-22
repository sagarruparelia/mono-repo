package com.example.bff.session.service;

import com.example.bff.authz.model.PermissionSet;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.SessionProperties;
import com.example.bff.session.audit.SessionAuditService;
import com.example.bff.session.model.ClientInfo;
import com.example.bff.session.model.SessionData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Abstract base class for session service implementations.
 * Provides shared logic for session validation and binding.
 */
@Slf4j
public abstract class AbstractSessionService implements SessionOperations {

    protected final SessionProperties sessionProperties;
    protected final ObjectMapper objectMapper;
    protected final Optional<SessionAuditService> auditService;

    protected AbstractSessionService(
            SessionProperties sessionProperties,
            ObjectMapper objectMapper,
            Optional<SessionAuditService> auditService) {
        this.sessionProperties = sessionProperties;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Override
    public boolean needsRotation(@NonNull SessionData session) {
        if (!sessionProperties.rotation().enabled()) {
            return false;
        }
        Duration sinceRotation = Duration.between(session.getEffectiveRotatedAt(), Instant.now());
        return sinceRotation.compareTo(sessionProperties.rotation().interval()) > 0;
    }

    @Override
    @NonNull
    public Mono<Void> updatePermissions(@NonNull String sessionId, @NonNull PermissionSet permissions) {
        log.debug("Updating permissions for session {}", StringSanitizer.forLog(sessionId));
        return storePermissions(sessionId, permissions);
    }

    @Override
    @NonNull
    public Mono<String> createSessionWithPermissions(
            @NonNull String hsidUuid,
            @NonNull OidcUser user,
            @Nullable String persona,
            @NonNull ClientInfo clientInfo,
            @NonNull PermissionSet permissions) {
        return createSession(hsidUuid, user, persona, permissions.getViewableManagedMemberIds(), clientInfo)
                .flatMap(sessionId -> storePermissions(sessionId, permissions).thenReturn(sessionId));
    }

    @Override
    @NonNull
    public Mono<Boolean> validateSessionBinding(@NonNull String sessionId, @NonNull ClientInfo clientInfo) {
        if (!sessionProperties.binding().enabled()) {
            return Mono.just(true);
        }

        if (!StringSanitizer.isValidSessionId(sessionId)) {
            return Mono.just(false);
        }

        return getSession(sessionId)
                .map(session -> validateBinding(session, clientInfo, sessionId))
                .defaultIfEmpty(false);
    }

    /**
     * Validates session binding against client info.
     * Checks device fingerprint first, then falls back to IP/User-Agent.
     */
    protected boolean validateBinding(
            @NonNull SessionData session,
            @NonNull ClientInfo clientInfo,
            @NonNull String sessionId) {

        // Check device fingerprint if available (preferred for Zero Trust)
        if (sessionProperties.fingerprint().enabled() && session.hasDeviceFingerprint()) {
            if (!session.deviceFingerprint().equals(clientInfo.deviceFingerprint())) {
                log.warn("Session fingerprint mismatch for session {}", StringSanitizer.forLog(sessionId));
                return false;
            }
            return true; // Fingerprint match is sufficient
        }

        // Fall back to legacy IP + User-Agent validation
        boolean valid = true;

        if (sessionProperties.binding().ipAddress()) {
            valid = session.ipAddress().equals(clientInfo.ipAddress());
            if (!valid) {
                log.warn("Session IP mismatch for session {}", StringSanitizer.forLog(sessionId));
            }
        }

        if (valid && sessionProperties.binding().userAgent()) {
            valid = session.userAgentHash().equals(clientInfo.userAgentHash());
            if (!valid) {
                log.warn("Session User-Agent mismatch for session {}", StringSanitizer.forLog(sessionId));
            }
        }

        return valid;
    }
}
