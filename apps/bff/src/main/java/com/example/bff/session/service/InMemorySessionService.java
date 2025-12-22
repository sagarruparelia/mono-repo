package com.example.bff.session.service;

import com.example.bff.authz.model.MemberAccess;
import com.example.bff.authz.model.PermissionSet;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.MemoryCacheProperties;
import com.example.bff.config.properties.SessionProperties;
import com.example.bff.session.audit.SessionAuditService;
import com.example.bff.session.model.ClientInfo;
import com.example.bff.session.model.SessionData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory implementation of SessionOperations for single-pod deployments.
 * Uses ConcurrentHashMap for thread-safe storage and ReentrantLock for rotation.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.cache.type", havingValue = "memory", matchIfMissing = true)
public class InMemorySessionService extends AbstractSessionService {

    private final ConcurrentHashMap<String, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userSessionMapping = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PermissionSet> permissionsStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> rotationLocks = new ConcurrentHashMap<>();

    private final MemoryCacheProperties memoryCacheProperties;

    public InMemorySessionService(
            SessionProperties sessionProperties,
            MemoryCacheProperties memoryCacheProperties,
            ObjectMapper objectMapper,
            Optional<SessionAuditService> auditService) {
        super(sessionProperties, objectMapper, auditService);
        this.memoryCacheProperties = memoryCacheProperties != null
                ? memoryCacheProperties
                : MemoryCacheProperties.defaults();
        log.info("In-memory session service initialized (single-pod mode, max-sessions={})",
                this.memoryCacheProperties.maxSessions());
    }

    /**
     * Check if cache is at capacity and evict oldest if needed.
     */
    private void enforceMaxSessions() {
        int maxSessions = memoryCacheProperties.maxSessions();
        while (sessions.size() >= maxSessions) {
            String oldestSessionId = sessions.entrySet().stream()
                    .min((a, b) -> a.getValue().data().lastAccessedAt()
                            .compareTo(b.getValue().data().lastAccessedAt()))
                    .map(Map.Entry::getKey)
                    .orElse(null);

            if (oldestSessionId != null) {
                log.warn("Session cache at capacity ({}), evicting oldest session: {}",
                        maxSessions, StringSanitizer.forLog(oldestSessionId));
                sessions.remove(oldestSessionId);
                permissionsStore.remove(oldestSessionId);
                userSessionMapping.values().remove(oldestSessionId);
            } else {
                break;
            }
        }
    }

    /**
     * Session entry with expiry tracking.
     */
    private record SessionEntry(SessionData data, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Cleanup expired sessions every minute.
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredSessions() {
        int removed = 0;
        for (var entry : sessions.entrySet()) {
            if (entry.getValue().isExpired()) {
                String sessionId = entry.getKey();
                sessions.remove(sessionId);
                permissionsStore.remove(sessionId);
                rotationLocks.remove(sessionId);
                removed++;
            }
        }
        userSessionMapping.entrySet().removeIf(entry -> !sessions.containsKey(entry.getValue()));
        if (removed > 0) {
            log.debug("Cleaned up {} expired sessions", removed);
        }
    }

    @Override
    @NonNull
    public Mono<Void> invalidateExistingSessions(@NonNull String hsidUuid) {
        if (!StringSanitizer.isValidUserId(hsidUuid)) {
            log.warn("Invalid hsidUuid format in invalidateExistingSessions");
            return Mono.empty();
        }

        String existingSessionId = userSessionMapping.remove(hsidUuid);
        if (existingSessionId != null) {
            log.info("Invalidating existing session for hsidUuid {}: {}",
                    StringSanitizer.forLog(hsidUuid), StringSanitizer.forLog(existingSessionId));
            sessions.remove(existingSessionId);
            permissionsStore.remove(existingSessionId);
            rotationLocks.remove(existingSessionId);
        }
        return Mono.empty();
    }

    @Override
    @NonNull
    public Mono<String> createSession(
            @NonNull String hsidUuid,
            @NonNull OidcUser user,
            @Nullable String persona,
            @Nullable List<String> dependents,
            @NonNull ClientInfo clientInfo) {

        if (!StringSanitizer.isValidUserId(hsidUuid)) {
            log.warn("Invalid hsidUuid format in createSession");
            return Mono.error(new IllegalArgumentException("Invalid hsidUuid format"));
        }

        enforceMaxSessions();

        String sessionId = UUID.randomUUID().toString();

        SessionData sessionData = SessionData.basic(
                hsidUuid,
                user.getEmail() != null ? user.getEmail() : "",
                user.getFullName() != null ? user.getFullName() : "",
                persona != null ? persona : "individual",
                dependents != null ? dependents : List.of(),
                clientInfo.ipAddress(),
                clientInfo.userAgentHash()
        );

        Duration ttl = sessionProperties.timeout();
        Instant expiresAt = Instant.now().plus(ttl);

        sessions.put(sessionId, new SessionEntry(sessionData, expiresAt));
        userSessionMapping.put(hsidUuid, sessionId);

        log.info("Creating in-memory session for hsidUuid {}: sessionId={}, persona={}",
                StringSanitizer.forLog(hsidUuid), StringSanitizer.forLog(sessionId), StringSanitizer.forLog(persona));

        auditService.ifPresent(audit -> audit.logSessionCreated(sessionId, sessionData, clientInfo, null));

        return Mono.just(sessionId);
    }

    @Override
    @NonNull
    public Mono<SessionData> getSession(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            log.debug("Invalid session ID format in getSession");
            return Mono.empty();
        }

        SessionEntry entry = sessions.get(sessionId);
        if (entry == null || entry.isExpired()) {
            if (entry != null) {
                sessions.remove(sessionId);
                permissionsStore.remove(sessionId);
            }
            return Mono.empty();
        }

        return Mono.just(entry.data());
    }

    @Override
    @NonNull
    public Mono<Boolean> refreshSession(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            return Mono.just(false);
        }

        SessionEntry entry = sessions.get(sessionId);
        if (entry == null || entry.isExpired()) {
            return Mono.just(false);
        }

        Duration ttl = sessionProperties.timeout();
        Instant newExpiry = Instant.now().plus(ttl);
        SessionData refreshed = entry.data().withRefresh();
        sessions.put(sessionId, new SessionEntry(refreshed, newExpiry));

        return Mono.just(true);
    }

    @Override
    @NonNull
    public Mono<Void> invalidateSession(@NonNull String sessionId) {
        return invalidateSession(sessionId, "User logout");
    }

    @Override
    @NonNull
    public Mono<Void> invalidateSession(@NonNull String sessionId, @NonNull String reason) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            log.warn("Invalid session ID format in invalidateSession");
            return Mono.empty();
        }

        SessionEntry entry = sessions.remove(sessionId);
        if (entry != null) {
            log.info("Invalidating session {}", StringSanitizer.forLog(sessionId));
            userSessionMapping.values().remove(sessionId);
            permissionsStore.remove(sessionId);
            rotationLocks.remove(sessionId);

            auditService.ifPresent(audit ->
                    audit.logSessionInvalidated(sessionId, entry.data().hsidUuid(), reason, null));
        }
        return Mono.empty();
    }

    @Override
    @NonNull
    public Mono<String> getSessionIdForUser(@NonNull String hsidUuid) {
        if (!StringSanitizer.isValidUserId(hsidUuid)) {
            return Mono.empty();
        }
        String sessionId = userSessionMapping.get(hsidUuid);
        return sessionId != null ? Mono.just(sessionId) : Mono.empty();
    }

    @Override
    @NonNull
    public Mono<String> rotateSession(@NonNull String oldSessionId, @NonNull ClientInfo clientInfo) {
        if (!StringSanitizer.isValidSessionId(oldSessionId)) {
            return Mono.error(new IllegalArgumentException("Invalid session ID"));
        }

        ReentrantLock lock = rotationLocks.computeIfAbsent(oldSessionId, k -> new ReentrantLock());

        if (lock.tryLock()) {
            try {
                SessionEntry entry = sessions.get(oldSessionId);
                if (entry == null || entry.isExpired()) {
                    return Mono.error(new IllegalStateException("Session not found for rotation"));
                }

                return performRotation(oldSessionId, entry.data(), clientInfo);
            } finally {
                lock.unlock();
            }
        } else {
            SessionEntry entry = sessions.get(oldSessionId);
            if (entry != null && !needsRotation(entry.data())) {
                String newSessionId = userSessionMapping.get(entry.data().hsidUuid());
                return Mono.just(newSessionId != null ? newSessionId : oldSessionId);
            }
            return Mono.just(oldSessionId);
        }
    }

    private Mono<String> performRotation(
            @NonNull String oldSessionId,
            @NonNull SessionData session,
            @NonNull ClientInfo clientInfo) {

        String newSessionId = UUID.randomUUID().toString();

        SessionData rotatedSession = session.withRotation(clientInfo);
        Duration ttl = sessionProperties.timeout();
        Instant expiresAt = Instant.now().plus(ttl);

        // Copy permissions to new session
        PermissionSet permissions = permissionsStore.get(oldSessionId);
        if (permissions != null) {
            permissionsStore.put(newSessionId, permissions);
        }

        // Store new session
        sessions.put(newSessionId, new SessionEntry(rotatedSession, expiresAt));
        userSessionMapping.put(session.hsidUuid(), newSessionId);

        // Keep old session for grace period
        Duration gracePeriod = sessionProperties.rotation().gracePeriod();
        Instant graceExpiry = Instant.now().plus(gracePeriod);
        sessions.put(oldSessionId, new SessionEntry(session, graceExpiry));

        log.info("Rotated session for hsidUuid {}: {} -> {}",
                StringSanitizer.forLog(session.hsidUuid()),
                StringSanitizer.forLog(oldSessionId),
                StringSanitizer.forLog(newSessionId));

        auditService.ifPresent(audit ->
                audit.logSessionRotated(oldSessionId, newSessionId, session, clientInfo, null));

        return Mono.just(newSessionId);
    }

    @Override
    @NonNull
    public Mono<Void> storePermissions(@NonNull String sessionId, @NonNull PermissionSet permissions) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            log.warn("Invalid session ID format in storePermissions");
            return Mono.error(new IllegalArgumentException("Invalid session ID format"));
        }
        permissionsStore.put(sessionId, permissions);
        return Mono.empty();
    }

    @Override
    @NonNull
    public Mono<PermissionSet> getPermissions(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            return Mono.empty();
        }
        PermissionSet permissions = permissionsStore.get(sessionId);
        return permissions != null ? Mono.just(permissions) : Mono.empty();
    }

    @Override
    @NonNull
    public Mono<String> createSessionWithMemberAccess(
            @NonNull String hsidUuid, @NonNull OidcUser user, @NonNull ClientInfo clientInfo,
            @NonNull MemberAccess memberAccess, @NonNull PermissionSet permissions) {

        if (!StringSanitizer.isValidUserId(hsidUuid)) {
            log.warn("Invalid hsidUuid format in createSessionWithMemberAccess");
            return Mono.error(new IllegalArgumentException("Invalid hsidUuid format"));
        }

        enforceMaxSessions();

        String sessionId = UUID.randomUUID().toString();

        SessionData sessionData = SessionData.withMemberAccess(hsidUuid, user, memberAccess, clientInfo, objectMapper);
        Duration ttl = sessionProperties.timeout();
        Instant expiresAt = Instant.now().plus(ttl);

        sessions.put(sessionId, new SessionEntry(sessionData, expiresAt));
        userSessionMapping.put(hsidUuid, sessionId);
        permissionsStore.put(sessionId, permissions);

        log.info("Creating in-memory session with member access for hsidUuid {}: sessionId={}, persona={}, eligibility={}",
                StringSanitizer.forLog(hsidUuid), StringSanitizer.forLog(sessionId),
                memberAccess.getEffectivePersona(), memberAccess.eligibilityStatus());

        auditService.ifPresent(audit -> audit.logSessionCreated(sessionId, sessionData, clientInfo, null));

        return Mono.just(sessionId);
    }
}
