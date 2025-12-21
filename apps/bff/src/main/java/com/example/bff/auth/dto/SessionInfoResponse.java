package com.example.bff.auth.dto;

import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for session info endpoint.
 * Provides structured session data for SPA consumption.
 */
public record SessionInfoResponse(
        boolean valid,
        @Nullable String reason,
        @Nullable String hsidUuid,
        @Nullable String name,
        @Nullable String email,
        @Nullable String persona,
        boolean isParent,
        @Nullable List<String> dependentIds,
        @Nullable Instant expiresAt,
        @Nullable Instant lastActivity
) {
    /**
     * Creates an invalid session response with a reason.
     */
    public static SessionInfoResponse invalid(String reason) {
        return new SessionInfoResponse(
                false, reason,
                null, null, null, null, false, null, null, null
        );
    }

    /**
     * Creates a valid session response from session data.
     */
    public static SessionInfoResponse valid(
            String hsidUuid,
            String name,
            String email,
            String persona,
            boolean isParent,
            List<String> dependentIds,
            Instant expiresAt,
            Instant lastActivity
    ) {
        return new SessionInfoResponse(
                true, null,
                hsidUuid, name, email, persona, isParent,
                dependentIds, expiresAt, lastActivity
        );
    }
}
