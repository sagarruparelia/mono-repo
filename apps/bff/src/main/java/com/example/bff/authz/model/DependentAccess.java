package com.example.bff.authz.model;

import java.util.Set;

/**
 * Access permissions for a single dependent.
 * Represents the relationship tuple: parent has permissions on dependent.
 *
 * @param dependentId   Unique identifier for the dependent
 * @param dependentName Display name of the dependent
 * @param permissions   Set of permissions granted for this dependent
 * @param relationship  Type of relationship (e.g., "child", "ward")
 */
public record DependentAccess(
        String dependentId,
        String dependentName,
        Set<Permission> permissions,
        String relationship
) {
    /**
     * Check if user can view this dependent (requires DAA AND RPR).
     */
    public boolean canView() {
        return permissions != null
                && permissions.contains(Permission.DAA)
                && permissions.contains(Permission.RPR);
    }

    /**
     * Check if user has a specific permission for this dependent.
     */
    public boolean hasPermission(Permission permission) {
        return permissions != null && permissions.contains(permission);
    }

    /**
     * Check if user has all specified permissions for this dependent.
     */
    public boolean hasAllPermissions(Set<Permission> requiredPermissions) {
        if (permissions == null || requiredPermissions == null) {
            return false;
        }
        return permissions.containsAll(requiredPermissions);
    }
}
