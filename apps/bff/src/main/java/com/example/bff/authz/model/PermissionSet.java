package com.example.bff.authz.model;

import com.example.bff.auth.model.DelegateType;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * All permissions for a user session.
 * Contains the complete set of managed member access permissions with temporal validity.
 *
 * @param userId         The user's unique identifier (HSID UUID)
 * @param persona        The user's persona (e.g., "parent", "individual")
 * @param managedMembers List of managed members with their access permissions
 * @param fetchedAt      When permissions were fetched from the backend
 * @param expiresAt      When permissions should be refreshed
 */
public record PermissionSet(
        String userId,
        String persona,
        List<ManagedMemberAccess> managedMembers,
        Instant fetchedAt,
        Instant expiresAt
) {
    /**
     * Create an empty permission set for a user with no managed members.
     */
    public static PermissionSet empty(String userId, String persona) {
        return new PermissionSet(
                userId,
                persona,
                List.of(),
                Instant.now(),
                Instant.now().plusSeconds(300) // 5 minute default TTL
        );
    }

    /**
     * Check if permissions have expired and need refresh.
     */
    @JsonIgnore
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Get access for a specific managed member.
     *
     * @param memberId The managed member's enterprise ID
     * @return Optional containing the ManagedMemberAccess if found
     */
    public Optional<ManagedMemberAccess> getAccessFor(String memberId) {
        if (managedMembers == null || memberId == null) {
            return Optional.empty();
        }
        return managedMembers.stream()
                .filter(m -> memberId.equals(m.memberId()))
                .findFirst();
    }

    /**
     * Check if user has a valid (currently active) delegate type for a managed member.
     *
     * @param memberId The managed member's enterprise ID
     * @param type     The delegate type to check
     * @return true if the permission exists and is currently valid
     */
    public boolean hasValidPermission(String memberId, DelegateType type) {
        return getAccessFor(memberId)
                .map(access -> access.hasValidPermission(type))
                .orElse(false);
    }

    /**
     * Check if user has all required valid delegate types for a managed member.
     *
     * @param memberId      The managed member's enterprise ID
     * @param requiredTypes The delegate types required
     * @return true if all permissions exist and are currently valid
     */
    public boolean hasAllValidPermissions(String memberId, Set<DelegateType> requiredTypes) {
        return getAccessFor(memberId)
                .map(access -> access.hasAllValidPermissions(requiredTypes))
                .orElse(false);
    }

    /**
     * Get list of managed members that user can currently view (have valid DAA and RPR).
     */
    @JsonIgnore
    public List<ManagedMemberAccess> getViewableManagedMembers() {
        if (managedMembers == null) {
            return List.of();
        }
        return managedMembers.stream()
                .filter(ManagedMemberAccess::canView)
                .collect(Collectors.toList());
    }

    /**
     * Get list of managed member IDs that user can currently view.
     */
    @JsonIgnore
    public List<String> getViewableManagedMemberIds() {
        return getViewableManagedMembers().stream()
                .map(ManagedMemberAccess::memberId)
                .collect(Collectors.toList());
    }

    /**
     * Check if user has parent persona.
     */
    @JsonIgnore
    public boolean isParent() {
        return "parent".equalsIgnoreCase(persona);
    }

    /**
     * Check if the user has any managed members.
     */
    @JsonIgnore
    public boolean hasManagedMembers() {
        return managedMembers != null && !managedMembers.isEmpty();
    }

    /**
     * Check if user has any managed member with valid DAA + RPR permissions.
     * This determines if the user qualifies as a DELEGATE persona.
     */
    @JsonIgnore
    public boolean hasAnyValidDelegate() {
        if (managedMembers == null) {
            return false;
        }
        return managedMembers.stream().anyMatch(ManagedMemberAccess::canView);
    }
}
