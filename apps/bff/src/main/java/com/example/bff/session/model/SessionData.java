package com.example.bff.session.model;

import org.springframework.lang.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Session data stored in Redis.
 * Contains user identity, eligibility, and managed member information.
 */
public record SessionData(
        String userId,
        String email,
        String name,
        String persona,
        List<String> dependents,
        String ipAddress,
        String userAgentHash,
        Instant createdAt,
        Instant lastAccessedAt,
        // Member access fields
        @Nullable String eid,
        @Nullable String birthdate,
        boolean isResponsibleParty,
        @Nullable String apiIdentifier,
        @Nullable String eligibilityStatus,
        @Nullable String termDate,
        @Nullable String managedMembersJson,
        @Nullable String earliestPermissionEndDate
) {
    public static SessionData fromMap(Map<String, String> map) {
        return new SessionData(
                map.getOrDefault("userId", ""),
                map.getOrDefault("email", ""),
                map.getOrDefault("name", ""),
                map.getOrDefault("persona", "individual"),
                parseDependents(map.get("dependents")),
                map.getOrDefault("ipAddress", ""),
                map.getOrDefault("userAgentHash", ""),
                parseInstant(map.get("createdAt")),
                parseInstant(map.get("lastAccessedAt")),
                // Member access fields
                map.get("eid"),
                map.get("birthdate"),
                "true".equals(map.get("isResponsibleParty")),
                map.get("apiIdentifier"),
                map.get("eligibilityStatus"),
                map.get("termDate"),
                map.get("managedMembersJson"),
                map.get("earliestPermissionEndDate")
        );
    }

    /**
     * Creates a basic SessionData without member access enrichment.
     * Used for backwards compatibility.
     */
    public static SessionData basic(
            String userId,
            String email,
            String name,
            String persona,
            List<String> dependents,
            String ipAddress,
            String userAgentHash) {
        return new SessionData(
                userId, email, name, persona, dependents,
                ipAddress, userAgentHash,
                Instant.now(), Instant.now(),
                null, null, false, null, null, null, null, null
        );
    }

    private static List<String> parseDependents(String value) {
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

    public boolean isParent() {
        return "parent".equals(persona);
    }

    public boolean hasDependents() {
        return dependents != null && !dependents.isEmpty();
    }

    /**
     * Check if member access data is populated.
     */
    public boolean hasMemberAccess() {
        return eid != null && !eid.isBlank();
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
}
