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
 * Session management service for Redis-backed sessions.
 *
 * <p>Provides session lifecycle management including:
 * <ul>
 *   <li>Session creation with user and client binding</li>
 *   <li>Single-session enforcement per user</li>
 *   <li>Session binding validation (IP, User-Agent)</li>
 *   <li>Sliding expiration (TTL refresh on access)</li>
 *   <li>Permission storage within sessions</li>
 * </ul>
 *
 * @see SessionProperties
 * @see SessionData
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
public class SessionService {

    private static final String USER_SESSION_KEY = "bff:user_session:";
    private static final String SESSION_KEY = "bff:session:";
    private static final String PERMISSIONS_FIELD = "permissions";

    private final ReactiveRedisOperations<String, String> redisOps;
    private final SessionProperties sessionProperties;
    private final ObjectMapper objectMapper;

    public SessionService(
            @NonNull ReactiveRedisOperations<String, String> redisOps,
            @NonNull SessionProperties sessionProperties,
            @NonNull ObjectMapper objectMapper) {
        this.redisOps = redisOps;
        this.sessionProperties = sessionProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Invalidates any existing sessions for the user (single session enforcement).
     *
     * @param userId the user ID
     * @return Mono completing when invalidation is done
     */
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

    /**
     * Creates a new session and stores it in Redis.
     *
     * @param userId     the user ID
     * @param user       the OIDC user
     * @param persona    the user persona
     * @param dependents list of dependent IDs
     * @param clientInfo client connection info
     * @return Mono emitting the new session ID
     */
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

    /**
     * Retrieves session data by session ID.
     *
     * @param sessionId the session ID
     * @return Mono emitting session data if found
     */
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

    /**
     * Validates session binding (IP and User-Agent).
     *
     * @param sessionId  the session ID
     * @param clientInfo current client info
     * @return Mono emitting true if binding is valid
     */
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

    /**
     * Refreshes session TTL (sliding expiration).
     *
     * @param sessionId the session ID
     * @return Mono emitting true if refresh succeeded
     */
    @NonNull
    public Mono<Boolean> refreshSession(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            return Mono.just(false);
        }

        String sessionKey = SESSION_KEY + sessionId;
        Duration ttl = sessionProperties.timeout();

        return redisOps.expire(sessionKey, ttl)
                .flatMap(result -> {
                    if (result) {
                        return redisOps.opsForHash()
                                .put(sessionKey, "lastAccessedAt", String.valueOf(Instant.now().toEpochMilli()))
                                .thenReturn(true);
                    }
                    return Mono.just(false);
                });
    }

    /**
     * Invalidates a specific session.
     *
     * @param sessionId the session ID
     * @return Mono completing when invalidation is done
     */
    @NonNull
    public Mono<Void> invalidateSession(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            log.warn("Invalid session ID format in invalidateSession");
            return Mono.empty();
        }

        String sessionKey = SESSION_KEY + sessionId;

        return getSession(sessionId)
                .flatMap(session -> {
                    String userSessionKey = USER_SESSION_KEY + session.userId();
                    log.info("Invalidating session {}", StringSanitizer.forLog(sessionId));
                    return redisOps.delete(sessionKey)
                            .then(redisOps.delete(userSessionKey));
                })
                .then();
    }

    /**
     * Gets the session ID for a user.
     *
     * @param userId the user ID
     * @return Mono emitting the session ID if found
     */
    @NonNull
    public Mono<String> getSessionIdForUser(@NonNull String userId) {
        if (!StringSanitizer.isValidUserId(userId)) {
            return Mono.empty();
        }

        String userSessionKey = USER_SESSION_KEY + userId;
        return redisOps.opsForValue().get(userSessionKey);
    }

    /**
     * Stores permissions in the session.
     *
     * @param sessionId   the session ID
     * @param permissions the permission set
     * @return Mono completing when stored
     */
    @NonNull
    public Mono<Void> storePermissions(@NonNull String sessionId, @NonNull PermissionSet permissions) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            log.warn("Invalid session ID format in storePermissions");
            return Mono.error(new IllegalArgumentException("Invalid session ID format"));
        }

        String sessionKey = SESSION_KEY + sessionId;

        try {
            String permissionsJson = objectMapper.writeValueAsString(permissions);
            return redisOps.opsForHash()
                    .put(sessionKey, PERMISSIONS_FIELD, permissionsJson)
                    .then();
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize permissions for session {}: {}",
                    StringSanitizer.forLog(sessionId), StringSanitizer.forLog(e.getMessage()));
            return Mono.error(e);
        }
    }

    /**
     * Retrieves permissions from the session.
     *
     * @param sessionId the session ID
     * @return Mono emitting the permission set if found
     */
    @NonNull
    public Mono<PermissionSet> getPermissions(@NonNull String sessionId) {
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            return Mono.empty();
        }

        String sessionKey = SESSION_KEY + sessionId;

        return redisOps.opsForHash()
                .get(sessionKey, PERMISSIONS_FIELD)
                .cast(String.class)
                .flatMap(json -> {
                    try {
                        PermissionSet permissions = objectMapper.readValue(json, PermissionSet.class);
                        return Mono.just(permissions);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize permissions for session {}: {}",
                                StringSanitizer.forLog(sessionId), StringSanitizer.forLog(e.getMessage()));
                        return Mono.empty();
                    }
                });
    }

    /**
     * Updates permissions in the session (same as store, for clarity).
     *
     * @param sessionId   the session ID
     * @param permissions the permission set
     * @return Mono completing when updated
     */
    @NonNull
    public Mono<Void> updatePermissions(@NonNull String sessionId, @NonNull PermissionSet permissions) {
        log.debug("Updating permissions for session {}", StringSanitizer.forLog(sessionId));
        return storePermissions(sessionId, permissions);
    }

    /**
     * Creates a session with permissions.
     *
     * @param userId      the user ID
     * @param user        the OIDC user
     * @param persona     the user persona
     * @param clientInfo  client connection info
     * @param permissions the permission set
     * @return Mono emitting the new session ID
     */
    @NonNull
    public Mono<String> createSessionWithPermissions(
            @NonNull String userId,
            @NonNull OidcUser user,
            @Nullable String persona,
            @NonNull ClientInfo clientInfo,
            @NonNull PermissionSet permissions) {

        return createSession(userId, user, persona,
                permissions.getViewableDependentIds(), clientInfo)
                .flatMap(sessionId ->
                    storePermissions(sessionId, permissions)
                        .thenReturn(sessionId)
                );
    }

    /**
     * Creates a session with member access information.
     * This is the enriched session creation that includes eligibility and managed members.
     *
     * @param userId       the user ID (hsidUuid)
     * @param user         the OIDC user
     * @param clientInfo   client connection info
     * @param memberAccess the resolved member access data
     * @param permissions  the permission set
     * @return Mono emitting the new session ID
     */
    @NonNull
    public Mono<String> createSessionWithMemberAccess(
            @NonNull String userId,
            @NonNull OidcUser user,
            @NonNull ClientInfo clientInfo,
            @NonNull MemberAccess memberAccess,
            @NonNull PermissionSet permissions) {

        if (!StringSanitizer.isValidUserId(userId)) {
            log.warn("Invalid user ID format in createSessionWithMemberAccess");
            return Mono.error(new IllegalArgumentException("Invalid user ID format"));
        }

        String sessionId = UUID.randomUUID().toString();
        String sessionKey = SESSION_KEY + sessionId;
        String userSessionKey = USER_SESSION_KEY + userId;

        // Build session data map with member access fields
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

        // Member access fields
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

    /**
     * Builds a comma-separated string of dependent EIDs from managed members.
     */
    private String buildDependentsString(MemberAccess memberAccess) {
        if (!memberAccess.hasActiveManagedMembers()) {
            return "";
        }
        return memberAccess.managedMembers().stream()
                .map(ManagedMember::eid)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    /**
     * Serializes managed members to JSON for session storage.
     */
    private String serializeManagedMembers(java.util.List<ManagedMember> managedMembers) {
        try {
            return objectMapper.writeValueAsString(managedMembers);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize managed members: {}", e.getMessage());
            return "[]";
        }
    }
}
