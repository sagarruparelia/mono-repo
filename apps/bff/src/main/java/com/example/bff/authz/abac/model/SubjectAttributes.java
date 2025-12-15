package com.example.bff.authz.abac.model;

import com.example.bff.authz.model.AuthType;
import com.example.bff.authz.model.Permission;
import com.example.bff.authz.model.PermissionSet;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ABAC Subject Attributes - represents the user making the request.
 * Unified model for both HSID and Proxy authentication types.
 */
public record SubjectAttributes(
        // Common attributes
        AuthType authType,
        String userId,
        String persona,

        // HSID-specific: permission tuples per dependent
        PermissionSet permissions,

        // Proxy-specific: context from headers
        String partnerId,
        String memberId,
        String operatorId,
        String operatorName
) {
    /**
     * Create subject attributes for HSID authenticated user.
     */
    public static SubjectAttributes forHsid(String userId, String persona, PermissionSet permissions) {
        return new SubjectAttributes(
                AuthType.HSID,
                userId,
                persona,
                permissions,
                null, null, null, null
        );
    }

    /**
     * Create subject attributes for Proxy authenticated user.
     */
    public static SubjectAttributes forProxy(
            String userId,
            String persona,
            String partnerId,
            String memberId,
            String operatorId,
            String operatorName) {
        return new SubjectAttributes(
                AuthType.PROXY,
                userId,
                persona,
                null,
                partnerId, memberId, operatorId, operatorName
        );
    }

    /**
     * Check if subject is HSID authenticated.
     */
    public boolean isHsid() {
        return authType == AuthType.HSID;
    }

    /**
     * Check if subject is Proxy authenticated.
     */
    public boolean isProxy() {
        return authType == AuthType.PROXY;
    }

    /**
     * Check if subject has a specific persona.
     */
    public boolean hasPersona(String targetPersona) {
        return targetPersona != null && targetPersona.equalsIgnoreCase(persona);
    }

    /**
     * Check if subject is a parent (HSID).
     */
    public boolean isParent() {
        return isHsid() && "parent".equalsIgnoreCase(persona);
    }

    /**
     * Check if subject is an agent (Proxy).
     */
    public boolean isAgent() {
        return isProxy() && "agent".equalsIgnoreCase(persona);
    }

    /**
     * Check if subject is a case worker (Proxy).
     */
    public boolean isCaseWorker() {
        return isProxy() && "case_worker".equalsIgnoreCase(persona);
    }

    /**
     * Check if subject is config/admin (Proxy).
     */
    public boolean isConfig() {
        return isProxy() && "config".equalsIgnoreCase(persona);
    }

    /**
     * Get permissions for a specific resource (HSID only).
     */
    public Set<Permission> getPermissionsFor(String resourceId) {
        if (permissions == null) {
            return Set.of();
        }
        return permissions.getAccessFor(resourceId)
                .map(access -> access.permissions())
                .orElse(Set.of());
    }

    /**
     * Check if subject has a specific permission for a resource (HSID only).
     */
    public boolean hasPermission(String resourceId, Permission permission) {
        return getPermissionsFor(resourceId).contains(permission);
    }

    /**
     * Check if subject has all required permissions for a resource (HSID only).
     */
    public boolean hasAllPermissions(String resourceId, Set<Permission> required) {
        Set<Permission> granted = getPermissionsFor(resourceId);
        return granted.containsAll(required);
    }

    /**
     * Check if subject's assigned member matches (Proxy only).
     */
    public boolean isAssignedTo(String targetMemberId) {
        return memberId != null && memberId.equals(targetMemberId);
    }

    /**
     * Check if subject's partner matches (Proxy only).
     */
    public boolean belongsToPartner(String targetPartnerId) {
        return partnerId != null && partnerId.equals(targetPartnerId);
    }
}
