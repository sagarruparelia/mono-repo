package com.example.bff.authz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for Managed Members Permissions Graph API.
 * Endpoint: /api/consumer/prefs/del-gr/1.0.0
 *
 * Returns list of members who have granted permission to the logged-in user.
 * The logged-in user is the "delegate" (recipient), and these are the people
 * they can act on behalf of (the grantors).
 *
 * Each permission has a start/end date that determines if access is currently active.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ManagedMembersResponse(
        @JsonProperty("data") @Nullable PermissionsData data
) {
    /**
     * GraphQL data wrapper.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PermissionsData(
            @JsonProperty("permissions") @Nullable List<ManagedMemberEntry> permissions
    ) {}

    /**
     * Individual permission entry representing a managed member.
     *
     * Access is active when: startDate <= today <= endDate
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ManagedMemberEntry(
            @JsonProperty("eid") @Nullable String enterpriseId,
            @JsonProperty("firstName") @Nullable String firstName,
            @JsonProperty("lastName") @Nullable String lastName,
            @JsonProperty("birthDate") @Nullable LocalDate birthDate,
            @JsonProperty("email") @Nullable String email,
            @JsonProperty("delegateType") @Nullable String delegateType,
            @JsonProperty("startDate") @Nullable LocalDate startDate,
            @JsonProperty("endDate") @Nullable LocalDate endDate
    ) {
        /**
         * Check if the permission for this member is currently active.
         * Active when: startDate <= today <= endDate
         */
        public boolean isActive() {
            LocalDate today = LocalDate.now();
            if (startDate == null || endDate == null) {
                return false;
            }
            return !today.isBefore(startDate) && !today.isAfter(endDate);
        }

        /**
         * Get full name of the managed member.
         */
        @Nullable
        public String getFullName() {
            if (firstName == null && lastName == null) {
                return null;
            }
            if (firstName == null) {
                return lastName;
            }
            if (lastName == null) {
                return firstName;
            }
            return firstName + " " + lastName;
        }
    }

    /**
     * Get all permissions from the response.
     *
     * @return List of permissions or empty list if none
     */
    public List<ManagedMemberEntry> getPermissions() {
        if (data == null || data.permissions == null) {
            return List.of();
        }
        return data.permissions;
    }

    /**
     * Get only active managed members (where today is between start and end date).
     *
     * @return List of active managed members
     */
    public List<ManagedMemberEntry> getActiveManagedMembers() {
        return getPermissions().stream()
                .filter(ManagedMemberEntry::isActive)
                .toList();
    }
}
