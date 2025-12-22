package com.example.bff.session.model;

import com.example.bff.authz.model.ManagedMember;
import com.example.bff.authz.model.MemberAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Session data stored as JSON in Redis/memory.
 * Contains user identity, eligibility, managed member information, and Zero Trust metadata.
 * Jackson handles serialization directly - no manual map conversion needed.
 */
public record SessionData(
        @NonNull String hsidUuid,
        @NonNull String email,
        @NonNull String name,
        @NonNull String persona,
        @NonNull List<String> managedMemberIds,
        @NonNull String ipAddress,
        @NonNull String userAgentHash,
        @NonNull Instant createdAt,
        @NonNull Instant lastAccessedAt,
        // Member access fields
        @Nullable String enterpriseId,
        @Nullable String birthdate,
        boolean isResponsibleParty,
        @Nullable String eligibilityStatus,
        @Nullable String termDate,
        @Nullable String managedMembersJson,
        @Nullable String earliestPermissionEndDate,
        // Zero Trust fields
        @Nullable String deviceFingerprint,
        @Nullable Instant rotatedAt
) {
    /**
     * Creates a basic SessionData without member access enrichment.
     */
    public static SessionData basic(
            String hsidUuid,
            String email,
            String name,
            String persona,
            List<String> managedMemberIds,
            String ipAddress,
            String userAgentHash) {
        return new SessionData(
                hsidUuid, email, name, persona, managedMemberIds,
                ipAddress, userAgentHash,
                Instant.now(), Instant.now(),
                null, null, false, null, null, null, null,
                null, null
        );
    }

    /**
     * Creates a SessionData with full member access data.
     */
    public static SessionData withMemberAccess(
            @NonNull String hsidUuid,
            @NonNull OidcUser user,
            @NonNull MemberAccess memberAccess,
            @NonNull ClientInfo clientInfo,
            @NonNull ObjectMapper objectMapper) {

        List<String> managedMemberIds = memberAccess.hasManagedMembers()
                ? memberAccess.managedMembers().stream()
                        .map(ManagedMember::enterpriseId)
                        .collect(Collectors.toList())
                : List.of();

        String managedMembersJson = null;
        String earliestPermissionEndDate = null;
        if (memberAccess.hasManagedMembers()) {
            try {
                managedMembersJson = objectMapper.writeValueAsString(memberAccess.managedMembers());
            } catch (JsonProcessingException e) {
                managedMembersJson = "[]";
            }
            if (memberAccess.getEarliestPermissionEndDate() != null) {
                earliestPermissionEndDate = memberAccess.getEarliestPermissionEndDate().toString();
            }
        }

        return new SessionData(
                hsidUuid,
                user.getEmail() != null ? user.getEmail() : "",
                user.getFullName() != null ? user.getFullName() : "",
                memberAccess.getEffectivePersona(),
                managedMemberIds,
                clientInfo.ipAddress(),
                clientInfo.userAgentHash(),
                Instant.now(),
                Instant.now(),
                memberAccess.enterpriseId(),
                memberAccess.birthdate().toString(),
                memberAccess.isResponsibleParty(),
                memberAccess.eligibilityStatus().name(),
                memberAccess.termDate() != null ? memberAccess.termDate().toString() : null,
                managedMembersJson,
                earliestPermissionEndDate,
                clientInfo.deviceFingerprint(),
                null
        );
    }

    /**
     * Creates a new SessionData with updated client info and rotation timestamp.
     */
    public SessionData withRotation(ClientInfo clientInfo) {
        return new SessionData(
                hsidUuid, email, name, persona, managedMemberIds,
                clientInfo.ipAddress(), clientInfo.userAgentHash(),
                createdAt, Instant.now(),
                enterpriseId, birthdate, isResponsibleParty, eligibilityStatus, termDate,
                managedMembersJson, earliestPermissionEndDate,
                clientInfo.deviceFingerprint(), Instant.now()
        );
    }

    /**
     * Creates a new SessionData with updated lastAccessedAt.
     */
    public SessionData withRefresh() {
        return new SessionData(
                hsidUuid, email, name, persona, managedMemberIds,
                ipAddress, userAgentHash,
                createdAt, Instant.now(),
                enterpriseId, birthdate, isResponsibleParty, eligibilityStatus, termDate,
                managedMembersJson, earliestPermissionEndDate,
                deviceFingerprint, rotatedAt
        );
    }

    public boolean isParent() {
        return "parent".equals(persona);
    }

    public boolean hasManagedMembers() {
        return managedMemberIds != null && !managedMemberIds.isEmpty();
    }

    /**
     * Check if member access data is populated.
     */
    public boolean hasMemberAccess() {
        return enterpriseId != null && !enterpriseId.isBlank();
    }

    /**
     * Check if eligibility is active.
     */
    public boolean hasActiveEligibility() {
        return "ACTIVE".equals(eligibilityStatus);
    }

    /**
     * Check if eligibility is within grace period (inactive but still accessible).
     */
    public boolean hasInactiveEligibility() {
        return "INACTIVE".equals(eligibilityStatus);
    }

    /**
     * Check if user has any level of self-access based on eligibility.
     */
    public boolean hasSelfAccess() {
        return hasActiveEligibility() || hasInactiveEligibility();
    }

    /**
     * Get the parsed earliest permission end date.
     */
    @Nullable
    public LocalDate getParsedEarliestPermissionEndDate() {
        if (earliestPermissionEndDate == null || earliestPermissionEndDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(earliestPermissionEndDate);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the effective rotation timestamp.
     * Falls back to createdAt if session has never been rotated.
     */
    @NonNull
    public Instant getEffectiveRotatedAt() {
        return rotatedAt != null ? rotatedAt : createdAt;
    }

    /**
     * Check if device fingerprint is available for Zero Trust validation.
     */
    public boolean hasDeviceFingerprint() {
        return deviceFingerprint != null && !deviceFingerprint.isBlank();
    }
}
