package com.example.bff.session.model;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Session data stored in Redis.
 * Contains user identity, eligibility, managed member information, and Zero Trust metadata.
 *
 * Note: hsidUuid is the OIDC subject claim from HSID authentication.
 * The Redis key uses "userId" for backward compatibility with existing sessions.
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
    public static SessionData fromMap(Map<String, String> map) {
        return new SessionData(
                map.getOrDefault("userId", ""),  // Redis key "userId" maps to hsidUuid field
                map.getOrDefault("email", ""),
                map.getOrDefault("name", ""),
                map.getOrDefault("persona", "individual"),
                parseManagedMemberIds(map.get("dependents")),  // Redis key stays "dependents" for backward compat
                map.getOrDefault("ipAddress", ""),
                map.getOrDefault("userAgentHash", ""),
                parseInstant(map.get("createdAt")),
                parseInstant(map.get("lastAccessedAt")),
                // Member access fields (Redis key "eid" maps to enterpriseId field)
                map.get("eid"),
                map.get("birthdate"),
                "true".equals(map.get("isResponsibleParty")),
                map.get("eligibilityStatus"),
                map.get("termDate"),
                map.get("managedMembersJson"),
                map.get("earliestPermissionEndDate"),
                // Zero Trust fields (null-safe for existing sessions)
                map.get("deviceFingerprint"),
                parseInstantNullable(map.get("rotatedAt"))
        );
    }

    /**
     * Converts this SessionData to a map for Redis storage.
     */
    @NonNull
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        map.put("userId", hsidUuid);  // Redis key "userId" stores hsidUuid value
        map.put("email", email);
        map.put("name", name);
        map.put("persona", persona);
        if (managedMemberIds != null && !managedMemberIds.isEmpty()) {
            map.put("dependents", String.join(",", managedMemberIds));  // Redis key stays "dependents" for backward compat
        }
        map.put("ipAddress", ipAddress);
        map.put("userAgentHash", userAgentHash);
        map.put("createdAt", String.valueOf(createdAt.toEpochMilli()));
        map.put("lastAccessedAt", String.valueOf(lastAccessedAt.toEpochMilli()));
        // Member access fields (Redis key "eid" stores enterpriseId value)
        if (enterpriseId != null) map.put("eid", enterpriseId);
        if (birthdate != null) map.put("birthdate", birthdate);
        map.put("isResponsibleParty", String.valueOf(isResponsibleParty));
        if (eligibilityStatus != null) map.put("eligibilityStatus", eligibilityStatus);
        if (termDate != null) map.put("termDate", termDate);
        if (managedMembersJson != null) map.put("managedMembersJson", managedMembersJson);
        if (earliestPermissionEndDate != null) map.put("earliestPermissionEndDate", earliestPermissionEndDate);
        // Zero Trust fields
        if (deviceFingerprint != null) map.put("deviceFingerprint", deviceFingerprint);
        if (rotatedAt != null) map.put("rotatedAt", String.valueOf(rotatedAt.toEpochMilli()));
        return map;
    }

    /**
     * Creates a basic SessionData without member access enrichment.
     * Used for backwards compatibility.
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
                null, null  // Zero Trust fields
        );
    }

    private static List<String> parseManagedMemberIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.asList(value.split(","));
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Instant.now();
        }
    }

    @Nullable
    private static Instant parseInstantNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return null;
        }
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
