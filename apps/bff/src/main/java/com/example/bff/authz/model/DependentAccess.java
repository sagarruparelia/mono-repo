package com.example.bff.authz.model;

import com.example.bff.auth.model.DelegatePermission;
import com.example.bff.auth.model.DelegateType;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;
import java.util.Set;

/**
 * Access permissions for a single dependent.
 * Represents the relationship tuple: parent has permissions on dependent.
 *
 * <p>Each permission (DAA, RPR, ROI) has its own temporal validity via {@link DelegatePermission}.
 *
 * @param dependentId   Unique identifier for the dependent (enterprise ID)
 * @param dependentName Display name of the dependent
 * @param permissions   Map of delegate type to permission with temporal validity
 * @param relationship  Type of relationship (e.g., "child", "ward")
 */
public record DependentAccess(
        String dependentId,
        String dependentName,
        Map<DelegateType, DelegatePermission> permissions,
        String relationship
) {
    /**
     * Check if user has a specific delegate type that is currently valid.
     *
     * @param type The delegate type to check
     * @return {@code true} if the permission exists and is currently valid
     */
    public boolean hasValidPermission(DelegateType type) {
        if (permissions == null || type == null) {
            return false;
        }
        DelegatePermission perm = permissions.get(type);
        return perm != null && perm.isCurrentlyValid();
    }

    /**
     * Check if user has all specified delegate types that are currently valid.
     *
     * @param requiredTypes The delegate types required
     * @return {@code true} if all permissions exist and are currently valid
     */
    public boolean hasAllValidPermissions(Set<DelegateType> requiredTypes) {
        if (permissions == null || requiredTypes == null || requiredTypes.isEmpty()) {
            return requiredTypes == null || requiredTypes.isEmpty();
        }
        return requiredTypes.stream().allMatch(this::hasValidPermission);
    }

    /**
     * Check if user can view this dependent (requires valid DAA AND RPR).
     */
    @JsonIgnore
    public boolean canView() {
        return hasValidPermission(DelegateType.DAA)
                && hasValidPermission(DelegateType.RPR);
    }

    /**
     * Check if user can access sensitive data for this dependent (requires valid DAA AND RPR AND ROI).
     */
    @JsonIgnore
    public boolean canAccessSensitive() {
        return hasValidPermission(DelegateType.DAA)
                && hasValidPermission(DelegateType.RPR)
                && hasValidPermission(DelegateType.ROI);
    }

    /**
     * Get the permission details for a specific delegate type.
     *
     * @param type The delegate type
     * @return The permission details, or null if not present
     */
    public DelegatePermission getPermission(DelegateType type) {
        if (permissions == null) {
            return null;
        }
        return permissions.get(type);
    }

    /**
     * Check if a permission exists (regardless of validity).
     *
     * @param type The delegate type to check
     * @return {@code true} if the permission exists in the map
     */
    public boolean hasPermissionEntry(DelegateType type) {
        return permissions != null && permissions.containsKey(type);
    }
}
