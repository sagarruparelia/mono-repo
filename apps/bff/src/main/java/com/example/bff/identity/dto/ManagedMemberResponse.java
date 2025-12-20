package com.example.bff.identity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for Permissions Graph API.
 * Endpoint: /api/consumer/prefs/del-gr/1.0.0
 *
 * Returns list of members who have granted access to the logged-in user.
 * Each permission has a start/end date that determines if access is currently active.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ManagedMemberResponse(
        @JsonProperty("data") @Nullable PermissionsData data
) {
    /**
     * GraphQL data wrapper.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PermissionsData(
            @JsonProperty("permissions") @Nullable List<MemberPermission> permissions
    ) {}

    /**
     * Individual permission entry representing a managed member.
     *
     * Access is active when: startDate <= today <= endDate
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MemberPermission(
            @JsonProperty("eid") @Nullable String eid,
            @JsonProperty("firstName") @Nullable String firstName,
            @JsonProperty("lastName") @Nullable String lastName,
            @JsonProperty("birthDate") @Nullable LocalDate birthDate,
            @JsonProperty("email") @Nullable String email,
            @JsonProperty("delegateType") @Nullable String delegateType,
            @JsonProperty("startDate") @Nullable LocalDate startDate,
            @JsonProperty("endDate") @Nullable LocalDate endDate
    ) {
        /**
         * Check if this permission is currently active.
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
         * Get full name of the member.
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
    public List<MemberPermission> getPermissions() {
        if (data == null || data.permissions == null) {
            return List.of();
        }
        return data.permissions;
    }

    /**
     * Get only active permissions (where today is between start and end date).
     *
     * @return List of active permissions
     */
    public List<MemberPermission> getActivePermissions() {
        return getPermissions().stream()
                .filter(MemberPermission::isActive)
                .toList();
    }
}
