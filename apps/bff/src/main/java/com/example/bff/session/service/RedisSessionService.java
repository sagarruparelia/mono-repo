package com.example.bff.session.service;

import com.example.bff.authz.model.PermissionSet;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.SessionProperties;
import com.example.bff.authz.model.MemberAccess;
import com.example.bff.session.audit.SessionAuditService;
import com.example.bff.session.model.ClientInfo;
import com.example.bff.session.model.SessionData;
import com.example.bff.session.pubsub.SessionEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.cache.type", havingValue = "redis")
public class RedisSessionService extends AbstractSessionService {

    private static final String USER_SESSION_KEY = "bff:user_session:";
    private static final String SESSION_KEY = "bff:session:";
    private static final String PERMISSIONS_KEY = "bff:permissions:";
    private static final String ROTATION_LOCK_KEY = "bff:rotation_lock:";
    private static final Duration ROTATION_LOCK_TTL = Duration.ofSeconds(10);

    private final ReactiveRedisOperations<String, String> redisOps;
    private final Optional<SessionEventPublisher> eventPublisher;

    public RedisSessionService(
            ReactiveRedisOperations<String, String> redisOps,
            SessionProperties sessionProperties,
            ObjectMapper objectMapper,
            Optional<SessionAuditService> auditService,
            Optional<SessionEventPublisher> eventPublisher) {
        super(sessionProperties, objectMapper, auditService);
        this.redisOps = redisOps;
        this.eventPublisher = eventPublisher;
    }

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
                            .then(redisOps.delete(PERMISSIONS_KEY + existingSessionId))
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

        log.info("Creating session for hsidUuid {}: sessionId={}, persona={}",
                StringSanitizer.forLog(hsidUuid), StringSanitizer.forLog(sessionId), StringSanitizer.forLog(persona));

        return storeSession(sessionId, sessionData, ttl)
                .then(redisOps.opsForValue().set(USER_SESSION_KEY + hsidUuid, sessionId, ttl))
                .doOnSuccess(v -> auditService.ifPresent(audit ->
                        audit.logSessionCreated(sessionId, sessionData, clientInfo, null)))
                .thenReturn(sessionId);
    }

    @NonNull
    public Mono<SessionData> getSession(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            log.debug("Invalid session ID format in getSession");
            return Mono.empty();
        }

        return redisOps.opsForValue().get(SESSION_KEY + sessionId)
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, SessionData.class));
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize session: {}", e.getMessage());
                        return Mono.empty();
                    }
                });
    }

    @NonNull
    public Mono<Boolean> refreshSession(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            return Mono.just(false);
        }

        Duration ttl = sessionProperties.timeout();
        String sessionKey = SESSION_KEY + sessionId;

        return getSession(sessionId)
                .flatMap(session -> {
                    SessionData refreshed = session.withRefresh();
                    return storeSession(sessionId, refreshed, ttl).thenReturn(true);
                })
                .defaultIfEmpty(false);
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

        return getSession(sessionId)
                .flatMap(session -> {
                    log.info("Invalidating session {}", StringSanitizer.forLog(sessionId));
                    auditService.ifPresent(audit ->
                            audit.logSessionInvalidated(sessionId, session.hsidUuid(), reason, null));
                    eventPublisher.ifPresent(publisher ->
                            publisher.publishInvalidation(sessionId, session.hsidUuid(), reason).subscribe());
                    return redisOps.delete(SESSION_KEY + sessionId)
                            .then(redisOps.delete(PERMISSIONS_KEY + sessionId))
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
    public Mono<String> rotateSession(@NonNull String oldSessionId, @NonNull ClientInfo clientInfo) {
        if (!StringSanitizer.isValidSessionId(oldSessionId)) {
            return Mono.error(new IllegalArgumentException("Invalid session ID"));
        }

        String lockKey = ROTATION_LOCK_KEY + oldSessionId;

        return redisOps.opsForValue()
                .setIfAbsent(lockKey, "locked", ROTATION_LOCK_TTL)
                .flatMap(lockAcquired -> {
                    if (Boolean.TRUE.equals(lockAcquired)) {
                        return getSession(oldSessionId)
                                .switchIfEmpty(Mono.defer(() ->
                                        redisOps.delete(lockKey)
                                                .then(Mono.error(new IllegalStateException("Session not found for rotation")))))
                                .flatMap(session -> performRotation(oldSessionId, session, clientInfo, lockKey));
                    } else {
                        log.debug("Rotation lock not acquired for session {}, checking for already rotated session",
                                StringSanitizer.forLog(oldSessionId));
                        return getSession(oldSessionId)
                                .flatMap(session -> {
                                    if (!needsRotation(session)) {
                                        return redisOps.opsForValue().get(USER_SESSION_KEY + session.hsidUuid())
                                                .defaultIfEmpty(oldSessionId);
                                    }
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
        String userSessionKey = USER_SESSION_KEY + session.hsidUuid();

        SessionData rotatedSession = session.withRotation(clientInfo);

        Duration ttl = sessionProperties.timeout();
        Duration gracePeriod = sessionProperties.rotation().gracePeriod();

        log.info("Rotating session for hsidUuid {}: {} -> {}",
                StringSanitizer.forLog(session.hsidUuid()),
                StringSanitizer.forLog(oldSessionId),
                StringSanitizer.forLog(newSessionId));

        return storeSession(newSessionId, rotatedSession, ttl)
                .then(redisOps.opsForValue().set(userSessionKey, newSessionId, ttl))
                .then(copyPermissions(oldSessionId, newSessionId, ttl))
                .then(redisOps.expire(SESSION_KEY + oldSessionId, gracePeriod))
                .doOnSuccess(v -> {
                    auditService.ifPresent(audit ->
                            audit.logSessionRotated(oldSessionId, newSessionId, session, clientInfo, null));
                    eventPublisher.ifPresent(publisher ->
                            publisher.publishRotation(oldSessionId, newSessionId, session.hsidUuid()).subscribe());
                })
                .doFinally(signal -> redisOps.delete(lockKey).subscribe())
                .thenReturn(newSessionId);
    }

    private Mono<Boolean> copyPermissions(String fromSessionId, String toSessionId, Duration ttl) {
        return redisOps.opsForValue().get(PERMISSIONS_KEY + fromSessionId)
                .flatMap(json -> redisOps.opsForValue().set(PERMISSIONS_KEY + toSessionId, json, ttl))
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
            Duration ttl = sessionProperties.timeout();
            return redisOps.opsForValue()
                    .set(PERMISSIONS_KEY + sessionId, permissionsJson, ttl)
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
        return redisOps.opsForValue()
                .get(PERMISSIONS_KEY + sessionId)
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
    public Mono<String> createSessionWithMemberAccess(
            @NonNull String hsidUuid, @NonNull OidcUser user, @NonNull ClientInfo clientInfo,
            @NonNull MemberAccess memberAccess, @NonNull PermissionSet permissions) {

        if (!StringSanitizer.isValidUserId(hsidUuid)) {
            log.warn("Invalid hsidUuid format in createSessionWithMemberAccess");
            return Mono.error(new IllegalArgumentException("Invalid hsidUuid format"));
        }

        String sessionId = UUID.randomUUID().toString();
        SessionData sessionData = SessionData.withMemberAccess(hsidUuid, user, memberAccess, clientInfo, objectMapper);

        Duration ttl = sessionProperties.timeout();
        log.info("Creating session with member access for hsidUuid {}: sessionId={}, persona={}, eligibility={}",
                StringSanitizer.forLog(hsidUuid), StringSanitizer.forLog(sessionId),
                memberAccess.getEffectivePersona(), memberAccess.eligibilityStatus());

        return storeSession(sessionId, sessionData, ttl)
                .then(redisOps.opsForValue().set(USER_SESSION_KEY + hsidUuid, sessionId, ttl))
                .then(storePermissions(sessionId, permissions))
                .doOnSuccess(v -> auditService.ifPresent(audit ->
                        audit.logSessionCreated(sessionId, sessionData, clientInfo, null)))
                .thenReturn(sessionId);
    }

    private Mono<Boolean> storeSession(String sessionId, SessionData sessionData, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(sessionData);
            return redisOps.opsForValue().set(SESSION_KEY + sessionId, json, ttl);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize session: {}", e.getMessage());
            return Mono.error(e);
        }
    }
}
