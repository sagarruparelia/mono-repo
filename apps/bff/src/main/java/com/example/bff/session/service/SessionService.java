package com.example.bff.session.service;

import com.example.bff.config.properties.SessionProperties;
import com.example.bff.session.model.ClientInfo;
import com.example.bff.session.model.SessionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private static final String USER_SESSION_KEY = "bff:user_session:";
    private static final String SESSION_KEY = "bff:session:";

    private final ReactiveRedisOperations<String, String> redisOps;
    private final SessionProperties sessionProperties;

    public SessionService(ReactiveRedisOperations<String, String> redisOps,
                          SessionProperties sessionProperties) {
        this.redisOps = redisOps;
        this.sessionProperties = sessionProperties;
    }

    /**
     * Invalidates any existing sessions for the user (single session enforcement)
     */
    public Mono<Void> invalidateExistingSessions(String userId) {
        String userSessionKey = USER_SESSION_KEY + userId;

        return redisOps.opsForValue().get(userSessionKey)
                .flatMap(existingSessionId -> {
                    log.info("Invalidating existing session for user {}: {}", userId, existingSessionId);
                    return redisOps.delete(SESSION_KEY + existingSessionId)
                            .then(redisOps.delete(userSessionKey));
                })
                .then();
    }

    /**
     * Creates a new session and stores it in Redis
     */
    public Mono<String> createSession(String userId, OidcUser user, String persona,
                                       List<String> dependents, ClientInfo clientInfo) {
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

        log.info("Creating session for user {}: sessionId={}, persona={}", userId, sessionId, persona);

        return redisOps.opsForHash().putAll(sessionKey, sessionData)
                .then(redisOps.expire(sessionKey, ttl))
                .then(redisOps.opsForValue().set(userSessionKey, sessionId, ttl))
                .thenReturn(sessionId);
    }

    /**
     * Retrieves session data by session ID
     */
    public Mono<SessionData> getSession(String sessionId) {
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
     * Validates session binding (IP and User-Agent)
     */
    public Mono<Boolean> validateSessionBinding(String sessionId, ClientInfo clientInfo) {
        if (!sessionProperties.binding().enabled()) {
            return Mono.just(true);
        }

        return getSession(sessionId)
                .map(session -> {
                    boolean valid = true;

                    if (sessionProperties.binding().ipAddress()) {
                        valid = valid && session.ipAddress().equals(clientInfo.ipAddress());
                        if (!session.ipAddress().equals(clientInfo.ipAddress())) {
                            log.warn("Session IP mismatch: expected={}, actual={}",
                                    session.ipAddress(), clientInfo.ipAddress());
                        }
                    }

                    if (sessionProperties.binding().userAgent()) {
                        valid = valid && session.userAgentHash().equals(clientInfo.userAgentHash());
                        if (!session.userAgentHash().equals(clientInfo.userAgentHash())) {
                            log.warn("Session User-Agent mismatch for session {}", sessionId);
                        }
                    }

                    return valid;
                })
                .defaultIfEmpty(false);
    }

    /**
     * Refreshes session TTL (sliding expiration)
     */
    public Mono<Boolean> refreshSession(String sessionId) {
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
     * Invalidates a specific session
     */
    public Mono<Void> invalidateSession(String sessionId) {
        String sessionKey = SESSION_KEY + sessionId;

        return getSession(sessionId)
                .flatMap(session -> {
                    String userSessionKey = USER_SESSION_KEY + session.userId();
                    log.info("Invalidating session {}", sessionId);
                    return redisOps.delete(sessionKey)
                            .then(redisOps.delete(userSessionKey));
                })
                .then();
    }

    /**
     * Gets the session ID for a user
     */
    public Mono<String> getSessionIdForUser(String userId) {
        String userSessionKey = USER_SESSION_KEY + userId;
        return redisOps.opsForValue().get(userSessionKey);
    }
}
