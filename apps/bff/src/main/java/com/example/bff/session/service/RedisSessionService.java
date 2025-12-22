package com.example.bff.session.service;

import com.example.bff.authz.model.PermissionSet;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.SessionProperties;
import com.example.bff.authz.model.ManagedMember;
import com.example.bff.authz.model.MemberAccess;
import com.example.bff.session.audit.SessionAuditService;
import com.example.bff.session.model.ClientInfo;
import com.example.bff.session.model.SessionData;
import com.example.bff.session.pubsub.SessionEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.cache.type", havingValue = "redis")
public class RedisSessionService implements SessionOperations {

    private static final String USER_SESSION_KEY = "bff:user_session:";
    private static final String SESSION_KEY = "bff:session:";
    private static final String ROTATION_LOCK_KEY = "bff:rotation_lock:";
    private static final String PERMISSIONS_FIELD = "permissions";
    private static final String TOKEN_DATA_FIELD = "tokenData";
    private static final Duration ROTATION_LOCK_TTL = Duration.ofSeconds(10);

    private final ReactiveRedisOperations<String, String> redisOps;
    private final SessionProperties sessionProperties;
    private final ObjectMapper objectMapper;
    private final Optional<SessionAuditService> auditService;
    private final Optional<SessionEventPublisher> eventPublisher;

    @NonNull
    public Mono<Void> invalidateExistingSessions(@NonNull String hsidUuid) {
        if (!StringSanitizer.isValidUserId(hsidUuid)) {
            log.warn("Invalid hsidUuid format in invalidateExistingSessions");
            return Mono.empty();
        }

        String userSessionKey = USER_SESSION_KEY + hsidUuid;

        return redisOps.opsForValue().get(userSessionKey)
                .flatMap(existingSessionId -> {
                    log.info("Invalidating existing session for hsidUuid {}: {}",
                            StringSanitizer.forLog(hsidUuid), StringSanitizer.forLog(existingSessionId));
                    return redisOps.delete(SESSION_KEY + existingSessionId)
                            .then(redisOps.delete(userSessionKey));
                })
                .then();
    }

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

        String sessionId = UUID.randomUUID().toString();
        String sessionKey = SESSION_KEY + sessionId;
        String userSessionKey = USER_SESSION_KEY + hsidUuid;

        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("userId", hsidUuid);  // Redis key "userId" stores hsidUuid value
        sessionData.put("email", user.getEmail() != null ? user.getEmail() : "");
        sessionData.put("name", user.getFullName() != null ? user.getFullName() : "");
        sessionData.put("persona", persona != null ? persona : "individual");
        sessionData.put("dependents", dependents != null ? String.join(",", dependents) : "");
        sessionData.put("ipAddress", clientInfo.ipAddress());
        sessionData.put("userAgentHash", clientInfo.userAgentHash());
        sessionData.put("deviceFingerprint", clientInfo.deviceFingerprint());
        sessionData.put("createdAt", String.valueOf(Instant.now().toEpochMilli()));
        sessionData.put("lastAccessedAt", String.valueOf(Instant.now().toEpochMilli()));

        Duration ttl = sessionProperties.timeout();

        log.info("Creating session for hsidUuid {}: sessionId={}, persona={}",
                StringSanitizer.forLog(hsidUuid), StringSanitizer.forLog(sessionId), StringSanitizer.forLog(persona));

        return redisOps.opsForHash().putAll(sessionKey, sessionData)
                .then(redisOps.expire(sessionKey, ttl))
                .then(redisOps.opsForValue().set(userSessionKey, sessionId, ttl))
                .doOnSuccess(v -> auditService.ifPresent(audit -> {
                    SessionData session = SessionData.basic(hsidUuid, user.getEmail(), user.getFullName(),
                            persona != null ? persona : "individual",
                            dependents != null ? dependents : List.of(),
                            clientInfo.ipAddress(), clientInfo.userAgentHash());
                    audit.logSessionCreated(sessionId, session, clientInfo, null);
                }))
                .thenReturn(sessionId);
    }

    @NonNull
    public Mono<SessionData> getSession(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            log.debug("Invalid session ID format in getSession");
            return Mono.empty();
        }

        String sessionKey = SESSION_KEY + sessionId;

        return redisOps.opsForHash().entries(sessionKey)
                .collectMap(
                        entry -> (String) entry.getKey(),
                        entry -> (String) entry.getValue()
                )
                .filter(map -> !map.isEmpty())
                .map(SessionData::fromMap);
    }

    @NonNull
    public Mono<Boolean> refreshSession(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            return Mono.just(false);
        }
        String sessionKey = SESSION_KEY + sessionId;
        Duration ttl = sessionProperties.timeout();
        return redisOps.expire(sessionKey, ttl)
                .flatMap(result -> result
                        ? redisOps.opsForHash()
                                .put(sessionKey, "lastAccessedAt", String.valueOf(Instant.now().toEpochMilli()))
                                .thenReturn(true)
                        : Mono.just(false));
    }

    @NonNull
    public Mono<Void> invalidateSession(@NonNull String sessionId) {
        return invalidateSession(sessionId, "User logout");
    }

    @NonNull
    public Mono<Void> invalidateSession(@NonNull String sessionId, @NonNull String reason) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            log.warn("Invalid session ID format in invalidateSession");
            return Mono.empty();
        }
        String sessionKey = SESSION_KEY + sessionId;
        return getSession(sessionId)
                .flatMap(session -> {
                    log.info("Invalidating session {}", StringSanitizer.forLog(sessionId));
                    auditService.ifPresent(audit ->
                            audit.logSessionInvalidated(sessionId, session.hsidUuid(), reason, null));
                    // Publish invalidation event for other instances
                    eventPublisher.ifPresent(publisher ->
                            publisher.publishInvalidation(sessionId, session.hsidUuid(), reason).subscribe());
                    return redisOps.delete(sessionKey)
                            .then(redisOps.delete(USER_SESSION_KEY + session.hsidUuid()));
                })
                .then();
    }

    @NonNull
    public Mono<String> getSessionIdForUser(@NonNull String hsidUuid) {
        if (!StringSanitizer.isValidUserId(hsidUuid)) {
            return Mono.empty();
        }
        return redisOps.opsForValue().get(USER_SESSION_KEY + hsidUuid);
    }


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

    private boolean validateBinding(@NonNull SessionData session, @NonNull ClientInfo clientInfo, @NonNull String sessionId) {
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

    public boolean needsRotation(@NonNull SessionData session) {
        if (!sessionProperties.rotation().enabled()) {
            return false;
        }
        Duration sinceRotation = Duration.between(session.getEffectiveRotatedAt(), Instant.now());
        return sinceRotation.compareTo(sessionProperties.rotation().interval()) > 0;
    }

    @NonNull
    public Mono<String> rotateSession(@NonNull String oldSessionId, @NonNull ClientInfo clientInfo) {
        if (!StringSanitizer.isValidSessionId(oldSessionId)) {
            return Mono.error(new IllegalArgumentException("Invalid session ID"));
        }

        String lockKey = ROTATION_LOCK_KEY + oldSessionId;

        // Try to acquire distributed lock (SETNX with TTL)
        return redisOps.opsForValue()
                .setIfAbsent(lockKey, "locked", ROTATION_LOCK_TTL)
                .flatMap(lockAcquired -> {
                    if (Boolean.TRUE.equals(lockAcquired)) {
                        // Lock acquired - proceed with rotation
                        return getSession(oldSessionId)
                                .switchIfEmpty(Mono.defer(() -> {
                                    // Session not found, release lock and error
                                    return redisOps.delete(lockKey)
                                            .then(Mono.error(new IllegalStateException("Session not found for rotation")));
                                }))
                                .flatMap(session -> performRotation(oldSessionId, session, clientInfo, lockKey));
                    } else {
                        // Lock not acquired - rotation already in progress by another request
                        // Return the current session ID from user mapping (may already be rotated)
                        log.debug("Rotation lock not acquired for session {}, checking for already rotated session",
                                StringSanitizer.forLog(oldSessionId));
                        return getSession(oldSessionId)
                                .flatMap(session -> {
                                    // Check if recently rotated by another request
                                    if (!needsRotation(session)) {
                                        // Already rotated by concurrent request, get new session ID
                                        return redisOps.opsForValue().get(USER_SESSION_KEY + session.hsidUuid())
                                                .defaultIfEmpty(oldSessionId);
                                    }
                                    // Still needs rotation but lock held - return old session ID
                                    // The holding request will complete rotation
                                    return Mono.just(oldSessionId);
                                })
                                .defaultIfEmpty(oldSessionId);
                    }
                });
    }

    private Mono<String> performRotation(
            @NonNull String oldSessionId,
            @NonNull SessionData session,
            @NonNull ClientInfo clientInfo,
            @NonNull String lockKey) {

        String newSessionId = UUID.randomUUID().toString();
        String newSessionKey = SESSION_KEY + newSessionId;
        String oldSessionKey = SESSION_KEY + oldSessionId;
        String userSessionKey = USER_SESSION_KEY + session.hsidUuid();

        // Build new session data with updated rotation timestamp and fingerprint
        Map<String, String> sessionData = session.toMap();
        sessionData.put("rotatedAt", String.valueOf(Instant.now().toEpochMilli()));
        sessionData.put("lastAccessedAt", String.valueOf(Instant.now().toEpochMilli()));
        sessionData.put("deviceFingerprint", clientInfo.deviceFingerprint());
        sessionData.put("ipAddress", clientInfo.ipAddress());
        sessionData.put("userAgentHash", clientInfo.userAgentHash());

        Duration ttl = sessionProperties.timeout();
        Duration gracePeriod = sessionProperties.rotation().gracePeriod();

        log.info("Rotating session for hsidUuid {}: {} -> {}",
                StringSanitizer.forLog(session.hsidUuid()),
                StringSanitizer.forLog(oldSessionId),
                StringSanitizer.forLog(newSessionId));

        // Atomic rotation with lock:
        // 1. Create new session with full TTL
        // 2. Update user mapping to point to new session
        // 3. Copy tokens and permissions to new session
        // 4. Set old session TTL to grace period (allows in-flight requests)
        // 5. Release lock
        return redisOps.opsForHash().putAll(newSessionKey, sessionData)
                .then(redisOps.expire(newSessionKey, ttl))
                .then(redisOps.opsForValue().set(userSessionKey, newSessionId, ttl))
                .then(copySessionField(oldSessionId, newSessionId, PERMISSIONS_FIELD))
                .then(copySessionField(oldSessionId, newSessionId, TOKEN_DATA_FIELD))
                .then(redisOps.expire(oldSessionKey, gracePeriod))
                .doOnSuccess(v -> {
                    auditService.ifPresent(audit ->
                            audit.logSessionRotated(oldSessionId, newSessionId, session, clientInfo, null));
                    // Publish rotation event for other instances (cache invalidation)
                    eventPublisher.ifPresent(publisher ->
                            publisher.publishRotation(oldSessionId, newSessionId, session.hsidUuid()).subscribe());
                })
                .doFinally(signal -> {
                    // Always release lock after rotation attempt
                    redisOps.delete(lockKey).subscribe();
                })
                .thenReturn(newSessionId);
    }

    private Mono<Boolean> copySessionField(
            @NonNull String fromSessionId,
            @NonNull String toSessionId,
            @NonNull String fieldName) {
        String fromKey = SESSION_KEY + fromSessionId;
        String toKey = SESSION_KEY + toSessionId;

        return redisOps.opsForHash().get(fromKey, fieldName)
                .cast(String.class)
                .flatMap(value -> redisOps.opsForHash().put(toKey, fieldName, value))
                .defaultIfEmpty(false);
    }


    @NonNull
    public Mono<Void> storePermissions(@NonNull String sessionId, @NonNull PermissionSet permissions) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            log.warn("Invalid session ID format in storePermissions");
            return Mono.error(new IllegalArgumentException("Invalid session ID format"));
        }
        try {
            String permissionsJson = objectMapper.writeValueAsString(permissions);
            return redisOps.opsForHash()
                    .put(SESSION_KEY + sessionId, PERMISSIONS_FIELD, permissionsJson)
                    .then();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize permissions: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    @NonNull
    public Mono<PermissionSet> getPermissions(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            return Mono.empty();
        }
        return redisOps.opsForHash()
                .get(SESSION_KEY + sessionId, PERMISSIONS_FIELD)
                .cast(String.class)
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, PermissionSet.class));
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize permissions: {}", e.getMessage());
                        return Mono.empty();
                    }
                });
    }

    @NonNull
    public Mono<Void> updatePermissions(@NonNull String sessionId, @NonNull PermissionSet permissions) {
        log.debug("Updating permissions for session {}", StringSanitizer.forLog(sessionId));
        return storePermissions(sessionId, permissions);
    }


    @NonNull
    public Mono<String> createSessionWithPermissions(
            @NonNull String hsidUuid, @NonNull OidcUser user, @Nullable String persona,
            @NonNull ClientInfo clientInfo, @NonNull PermissionSet permissions) {
        return createSession(hsidUuid, user, persona, permissions.getViewableManagedMemberIds(), clientInfo)
                .flatMap(sessionId -> storePermissions(sessionId, permissions).thenReturn(sessionId));
    }

    @NonNull
    public Mono<String> createSessionWithMemberAccess(
            @NonNull String hsidUuid, @NonNull OidcUser user, @NonNull ClientInfo clientInfo,
            @NonNull MemberAccess memberAccess, @NonNull PermissionSet permissions) {

        if (!StringSanitizer.isValidUserId(hsidUuid)) {
            log.warn("Invalid hsidUuid format in createSessionWithMemberAccess");
            return Mono.error(new IllegalArgumentException("Invalid hsidUuid format"));
        }

        String sessionId = UUID.randomUUID().toString();
        String sessionKey = SESSION_KEY + sessionId;
        String userSessionKey = USER_SESSION_KEY + hsidUuid;

        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("userId", hsidUuid);  // Redis key "userId" stores hsidUuid value
        sessionData.put("email", user.getEmail() != null ? user.getEmail() : "");
        sessionData.put("name", user.getFullName() != null ? user.getFullName() : "");
        sessionData.put("persona", memberAccess.getEffectivePersona());
        sessionData.put("dependents", buildManagedMembersString(memberAccess));
        sessionData.put("ipAddress", clientInfo.ipAddress());
        sessionData.put("userAgentHash", clientInfo.userAgentHash());
        sessionData.put("deviceFingerprint", clientInfo.deviceFingerprint());
        sessionData.put("createdAt", String.valueOf(Instant.now().toEpochMilli()));
        sessionData.put("lastAccessedAt", String.valueOf(Instant.now().toEpochMilli()));
        sessionData.put("eid", memberAccess.enterpriseId());
        sessionData.put("birthdate", memberAccess.birthdate().toString());
        sessionData.put("isResponsibleParty", String.valueOf(memberAccess.isResponsibleParty()));
        sessionData.put("eligibilityStatus", memberAccess.eligibilityStatus().name());
        if (memberAccess.termDate() != null) {
            sessionData.put("termDate", memberAccess.termDate().toString());
        }
        if (memberAccess.hasManagedMembers()) {
            sessionData.put("managedMembersJson", serializeManagedMembers(memberAccess.managedMembers()));
            if (memberAccess.getEarliestPermissionEndDate() != null) {
                sessionData.put("earliestPermissionEndDate", memberAccess.getEarliestPermissionEndDate().toString());
            }
        }

        Duration ttl = sessionProperties.timeout();
        log.info("Creating session with member access for hsidUuid {}: sessionId={}, persona={}, eligibility={}",
                StringSanitizer.forLog(hsidUuid), StringSanitizer.forLog(sessionId),
                memberAccess.getEffectivePersona(), memberAccess.eligibilityStatus());

        return redisOps.opsForHash().putAll(sessionKey, sessionData)
                .then(redisOps.expire(sessionKey, ttl))
                .then(redisOps.opsForValue().set(userSessionKey, sessionId, ttl))
                .then(storePermissions(sessionId, permissions))
                .doOnSuccess(v -> auditService.ifPresent(audit -> {
                    SessionData session = SessionData.basic(hsidUuid, user.getEmail(), user.getFullName(),
                            memberAccess.getEffectivePersona(), List.of(),
                            clientInfo.ipAddress(), clientInfo.userAgentHash());
                    audit.logSessionCreated(sessionId, session, clientInfo, null);
                }))
                .thenReturn(sessionId);
    }


    @NonNull
    private String buildManagedMembersString(@NonNull MemberAccess memberAccess) {
        if (!memberAccess.hasManagedMembers()) {
            return "";
        }
        return memberAccess.managedMembers().stream()
                .map(ManagedMember::enterpriseId)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    @NonNull
    private String serializeManagedMembers(@NonNull List<ManagedMember> managedMembers) {
        try {
            return objectMapper.writeValueAsString(managedMembers);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize managed members: {}", e.getMessage());
            return "[]";
        }
    }
}
