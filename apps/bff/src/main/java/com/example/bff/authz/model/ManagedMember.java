package com.example.bff.authz.model;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.time.LocalDate;

/**
 * Represents a member that the logged-in user has permission to manage/act on behalf of.
 * The logged-in user is the "delegate" (recipient of permission), and this record
 * represents the person they can act FOR (the grantor of permission).
 *
 * Used for Responsible Party personas who manage other members (dependents, etc.).
 * Future scope includes non-dependent access scenarios.
 */
public record ManagedMember(
        @NonNull String enterpriseId,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable LocalDate birthDate,
        @NonNull LocalDate permissionEndDate
) {
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

    /**
     * Check if the permission to act for this member is still active.
     *
     * @return true if today is on or before the end date
     */
    public boolean isActive() {
        return !LocalDate.now().isAfter(permissionEndDate);
    }

    /**
     * Check if the permission will expire soon (within 30 days).
     *
     * @return true if expiring within 30 days
     */
    public boolean isExpiringSoon() {
        LocalDate thirtyDaysFromNow = LocalDate.now().plusDays(30);
        return permissionEndDate.isBefore(thirtyDaysFromNow);
    }
}
