package com.example.bff.authz.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * All permissions for a user session.
 * Contains the complete set of dependent access permissions.
 *
 * @param userId     The user's unique identifier
 * @param persona    The user's persona (e.g., "parent", "individual")
 * @param dependents List of dependents with their access permissions
 * @param fetchedAt  When permissions were fetched from the backend
 * @param expiresAt  When permissions should be refreshed
 */
public record PermissionSet(
        String userId,
        String persona,
        List<DependentAccess> dependents,
        Instant fetchedAt,
        Instant expiresAt
) {
    /**
     * Create an empty permission set for a user with no dependents.
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
     * Get access for a specific dependent.
     */
    public Optional<DependentAccess> getAccessFor(String dependentId) {
        if (dependents == null || dependentId == null) {
            return Optional.empty();
        }
        return dependents.stream()
                .filter(d -> dependentId.equals(d.dependentId()))
                .findFirst();
    }

    /**
     * Check if user has a specific permission for a dependent.
     */
    public boolean hasPermission(String dependentId, Permission permission) {
        return getAccessFor(dependentId)
                .map(access -> access.hasPermission(permission))
                .orElse(false);
    }

    /**
     * Check if user has all required permissions for a dependent.
     */
    public boolean hasAllPermissions(String dependentId, Set<Permission> permissions) {
        return getAccessFor(dependentId)
                .map(access -> access.hasAllPermissions(permissions))
                .orElse(false);
    }

    /**
     * Get list of dependents that user can view (have both DAA and RPR).
     */
    @JsonIgnore
    public List<DependentAccess> getViewableDependents() {
        if (dependents == null) {
            return List.of();
        }
        return dependents.stream()
                .filter(DependentAccess::canView)
                .collect(Collectors.toList());
    }

    /**
     * Get list of dependent IDs that user can view.
     */
    @JsonIgnore
    public List<String> getViewableDependentIds() {
        return getViewableDependents().stream()
                .map(DependentAccess::dependentId)
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
     * Check if the user has any dependents.
     */
    @JsonIgnore
    public boolean hasDependents() {
        return dependents != null && !dependents.isEmpty();
    }
}
