package com.example.bff.session.service;

import com.example.bff.authz.model.MemberAccess;
import com.example.bff.authz.model.PermissionSet;
import com.example.bff.session.model.ClientInfo;
import com.example.bff.session.model.SessionData;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Interface for session management operations.
 * Implementations can use Redis (distributed) or in-memory (single pod) storage.
 */
public interface SessionOperations {

    /**
     * Invalidates any existing sessions for the given user.
     */
    @NonNull
    Mono<Void> invalidateExistingSessions(@NonNull String hsidUuid);

    /**
     * Creates a new session for the user.
     */
    @NonNull
    Mono<String> createSession(
            @NonNull String hsidUuid,
            @NonNull OidcUser user,
            @Nullable String persona,
            @Nullable List<String> dependents,
            @NonNull ClientInfo clientInfo);

    /**
     * Retrieves session data by session ID.
     */
    @NonNull
    Mono<SessionData> getSession(@NonNull String sessionId);

    /**
     * Refreshes the session TTL and updates last accessed time.
     */
    @NonNull
    Mono<Boolean> refreshSession(@NonNull String sessionId);

    /**
     * Invalidates a session with default reason.
     */
    @NonNull
    Mono<Void> invalidateSession(@NonNull String sessionId);

    /**
     * Invalidates a session with a specific reason.
     */
    @NonNull
    Mono<Void> invalidateSession(@NonNull String sessionId, @NonNull String reason);

    /**
     * Gets the active session ID for a user.
     */
    @NonNull
    Mono<String> getSessionIdForUser(@NonNull String hsidUuid);

    /**
     * Validates that the session binding (device fingerprint, IP, UA) matches.
     */
    @NonNull
    Mono<Boolean> validateSessionBinding(@NonNull String sessionId, @NonNull ClientInfo clientInfo);

    /**
     * Checks if a session needs rotation based on time elapsed.
     */
    boolean needsRotation(@NonNull SessionData session);

    /**
     * Rotates a session ID for security.
     */
    @NonNull
    Mono<String> rotateSession(@NonNull String oldSessionId, @NonNull ClientInfo clientInfo);

    /**
     * Stores permissions in the session.
     */
    @NonNull
    Mono<Void> storePermissions(@NonNull String sessionId, @NonNull PermissionSet permissions);

    /**
     * Retrieves permissions from the session.
     */
    @NonNull
    Mono<PermissionSet> getPermissions(@NonNull String sessionId);

    /**
     * Updates permissions in the session.
     */
    @NonNull
    Mono<Void> updatePermissions(@NonNull String sessionId, @NonNull PermissionSet permissions);

    /**
     * Creates a session with permissions in one operation.
     */
    @NonNull
    Mono<String> createSessionWithPermissions(
            @NonNull String hsidUuid,
            @NonNull OidcUser user,
            @Nullable String persona,
            @NonNull ClientInfo clientInfo,
            @NonNull PermissionSet permissions);

    /**
     * Creates a session with full member access and permissions.
     */
    @NonNull
    Mono<String> createSessionWithMemberAccess(
            @NonNull String hsidUuid,
            @NonNull OidcUser user,
            @NonNull ClientInfo clientInfo,
            @NonNull MemberAccess memberAccess,
            @NonNull PermissionSet permissions);
}
