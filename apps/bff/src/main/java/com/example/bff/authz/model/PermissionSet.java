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
 * Contains the complete set of dependent access permissions with temporal validity.
 *
 * @param userId     The user's unique identifier (HSID UUID)
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
     *
     * @param dependentId The dependent's enterprise ID
     * @return Optional containing the DependentAccess if found
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
     * Check if user has a valid (currently active) delegate type for a dependent.
     *
     * @param dependentId The dependent's enterprise ID
     * @param type        The delegate type to check
     * @return true if the permission exists and is currently valid
     */
    public boolean hasValidPermission(String dependentId, DelegateType type) {
        return getAccessFor(dependentId)
                .map(access -> access.hasValidPermission(type))
                .orElse(false);
    }

    /**
     * Check if user has all required valid delegate types for a dependent.
     *
     * @param dependentId   The dependent's enterprise ID
     * @param requiredTypes The delegate types required
     * @return true if all permissions exist and are currently valid
     */
    public boolean hasAllValidPermissions(String dependentId, Set<DelegateType> requiredTypes) {
        return getAccessFor(dependentId)
                .map(access -> access.hasAllValidPermissions(requiredTypes))
                .orElse(false);
    }

    /**
     * Get list of dependents that user can currently view (have valid DAA and RPR).
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
     * Get list of dependent IDs that user can currently view.
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

    /**
     * Check if user has any dependent with valid DAA + RPR permissions.
     * This determines if the user qualifies as a DELEGATE persona.
     */
    @JsonIgnore
    public boolean hasAnyValidDelegate() {
        if (dependents == null) {
            return false;
        }
        return dependents.stream().anyMatch(DependentAccess::canView);
    }
}
