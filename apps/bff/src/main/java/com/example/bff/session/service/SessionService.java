package com.example.bff.session.service;

import com.example.bff.authz.model.PermissionSet;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.SessionProperties;
import com.example.bff.identity.model.ManagedMember;
import com.example.bff.identity.model.MemberAccess;
import com.example.bff.session.model.ClientInfo;
import com.example.bff.session.model.SessionData;
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
import java.util.UUID;

/**
 * Redis-backed session management with single-session enforcement,
 * binding validation (IP/User-Agent), and sliding expiration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
public class SessionService {

    private static final String USER_SESSION_KEY = "bff:user_session:";
    private static final String SESSION_KEY = "bff:session:";
    private static final String PERMISSIONS_FIELD = "permissions";

    private final ReactiveRedisOperations<String, String> redisOps;
    private final SessionProperties sessionProperties;
    private final ObjectMapper objectMapper;

    @NonNull
    public Mono<Void> invalidateExistingSessions(@NonNull String userId) {
        if (!StringSanitizer.isValidUserId(userId)) {
            log.warn("Invalid user ID format in invalidateExistingSessions");
            return Mono.empty();
        }

        String userSessionKey = USER_SESSION_KEY + userId;

        return redisOps.opsForValue().get(userSessionKey)
                .flatMap(existingSessionId -> {
                    log.info("Invalidating existing session for user {}: {}",
                            StringSanitizer.forLog(userId), StringSanitizer.forLog(existingSessionId));
                    return redisOps.delete(SESSION_KEY + existingSessionId)
                            .then(redisOps.delete(userSessionKey));
                })
                .then();
    }

    @NonNull
    public Mono<String> createSession(
            @NonNull String userId,
            @NonNull OidcUser user,
            @Nullable String persona,
            @Nullable List<String> dependents,
            @NonNull ClientInfo clientInfo) {

        if (!StringSanitizer.isValidUserId(userId)) {
            log.warn("Invalid user ID format in createSession");
            return Mono.error(new IllegalArgumentException("Invalid user ID format"));
        }

        String sessionId = UUID.randomUUID().toString();
        String sessionKey = SESSION_KEY + sessionId;
        String userSessionKey = USER_SESSION_KEY + userId;

        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("userId", userId);
        sessionData.put("email", user.getEmail() != null ? user.getEmail() : "");
        sessionData.put("name", user.getFullName() != null ? user.getFullName() : "");
        sessionData.put("persona", persona != null ? persona : "individual");
        sessionData.put("dependents", dependents != null ? String.join(",", dependents) : "");
        sessionData.put("ipAddress", clientInfo.ipAddress());
        sessionData.put("userAgentHash", clientInfo.userAgentHash());
        sessionData.put("createdAt", String.valueOf(Instant.now().toEpochMilli()));
        sessionData.put("lastAccessedAt", String.valueOf(Instant.now().toEpochMilli()));

        Duration ttl = sessionProperties.timeout();

        log.info("Creating session for user {}: sessionId={}, persona={}",
                StringSanitizer.forLog(userId), StringSanitizer.forLog(sessionId), StringSanitizer.forLog(persona));

        return redisOps.opsForHash().putAll(sessionKey, sessionData)
                .then(redisOps.expire(sessionKey, ttl))
                .then(redisOps.opsForValue().set(userSessionKey, sessionId, ttl))
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
    public Mono<Boolean> validateSessionBinding(@NonNull String sessionId, @NonNull ClientInfo clientInfo) {
        if (!sessionProperties.binding().enabled()) {
            return Mono.just(true);
        }

        if (!StringSanitizer.isValidSessionId(sessionId)) {
            return Mono.just(false);
        }

        return getSession(sessionId)
                .map(session -> {
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
                })
                .defaultIfEmpty(false);
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
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            log.warn("Invalid session ID format in invalidateSession");
            return Mono.empty();
        }
        String sessionKey = SESSION_KEY + sessionId;
        return getSession(sessionId)
                .flatMap(session -> {
                    log.info("Invalidating session {}", StringSanitizer.forLog(sessionId));
                    return redisOps.delete(sessionKey)
                            .then(redisOps.delete(USER_SESSION_KEY + session.userId()));
                })
                .then();
    }

    @NonNull
    public Mono<String> getSessionIdForUser(@NonNull String userId) {
        if (!StringSanitizer.isValidUserId(userId)) {
            return Mono.empty();
        }
        return redisOps.opsForValue().get(USER_SESSION_KEY + userId);
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
            @NonNull String userId, @NonNull OidcUser user, @Nullable String persona,
            @NonNull ClientInfo clientInfo, @NonNull PermissionSet permissions) {
        return createSession(userId, user, persona, permissions.getViewableDependentIds(), clientInfo)
                .flatMap(sessionId -> storePermissions(sessionId, permissions).thenReturn(sessionId));
    }

    @NonNull
    public Mono<String> createSessionWithMemberAccess(
            @NonNull String userId, @NonNull OidcUser user, @NonNull ClientInfo clientInfo,
            @NonNull MemberAccess memberAccess, @NonNull PermissionSet permissions) {

        if (!StringSanitizer.isValidUserId(userId)) {
            log.warn("Invalid user ID format in createSessionWithMemberAccess");
            return Mono.error(new IllegalArgumentException("Invalid user ID format"));
        }

        String sessionId = UUID.randomUUID().toString();
        String sessionKey = SESSION_KEY + sessionId;
        String userSessionKey = USER_SESSION_KEY + userId;

        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("userId", userId);
        sessionData.put("email", user.getEmail() != null ? user.getEmail() : "");
        sessionData.put("name", user.getFullName() != null ? user.getFullName() : "");
        sessionData.put("persona", memberAccess.getEffectivePersona());
        sessionData.put("dependents", buildDependentsString(memberAccess));
        sessionData.put("ipAddress", clientInfo.ipAddress());
        sessionData.put("userAgentHash", clientInfo.userAgentHash());
        sessionData.put("createdAt", String.valueOf(Instant.now().toEpochMilli()));
        sessionData.put("lastAccessedAt", String.valueOf(Instant.now().toEpochMilli()));
        sessionData.put("eid", memberAccess.eid());
        sessionData.put("birthdate", memberAccess.birthdate().toString());
        sessionData.put("isResponsibleParty", String.valueOf(memberAccess.isResponsibleParty()));
        if (memberAccess.apiIdentifier() != null) {
            sessionData.put("apiIdentifier", memberAccess.apiIdentifier());
        }
        sessionData.put("eligibilityStatus", memberAccess.eligibilityStatus().name());
        if (memberAccess.termDate() != null) {
            sessionData.put("termDate", memberAccess.termDate().toString());
        }
        if (memberAccess.hasActiveManagedMembers()) {
            sessionData.put("managedMembersJson", serializeManagedMembers(memberAccess.managedMembers()));
            if (memberAccess.getEarliestPermissionEndDate() != null) {
                sessionData.put("earliestPermissionEndDate", memberAccess.getEarliestPermissionEndDate().toString());
            }
        }

        Duration ttl = sessionProperties.timeout();
        log.info("Creating session with member access for user {}: sessionId={}, persona={}, eligibility={}",
                StringSanitizer.forLog(userId), StringSanitizer.forLog(sessionId),
                memberAccess.getEffectivePersona(), memberAccess.eligibilityStatus());

        return redisOps.opsForHash().putAll(sessionKey, sessionData)
                .then(redisOps.expire(sessionKey, ttl))
                .then(redisOps.opsForValue().set(userSessionKey, sessionId, ttl))
                .then(storePermissions(sessionId, permissions))
                .thenReturn(sessionId);
    }

    @NonNull
    private String buildDependentsString(@NonNull MemberAccess memberAccess) {
        if (!memberAccess.hasActiveManagedMembers()) {
            return "";
        }
        return memberAccess.managedMembers().stream()
                .map(ManagedMember::eid)
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
