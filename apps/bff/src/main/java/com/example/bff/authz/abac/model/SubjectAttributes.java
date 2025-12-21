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
 *
 * <h3>Personas:</h3>
 * <ul>
 *   <li>HSID: Self, ResponsibleParty</li>
 *   <li>PROXY: CaseWorker, Agent, ConfigSpecialist</li>
 * </ul>
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
        String enterpriseId,
        String loggedInMemberIdValue,
        String loggedInMemberIdType
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
     *
     * @param userId                Derived user identifier
     * @param persona               CaseWorker, Agent, or ConfigSpecialist
     * @param partnerId             Partner organization ID
     * @param enterpriseId          Target member's enterprise ID (from X-Enterprise-Id)
     * @param loggedInMemberIdValue Logged-in member ID value (from X-Logged-In-Member-Id-Value)
     * @param loggedInMemberIdType  Logged-in member ID type (OHID or MSID)
     */
    public static SubjectAttributes forProxy(
            String userId,
            String persona,
            String partnerId,
            String enterpriseId,
            String loggedInMemberIdValue,
            String loggedInMemberIdType) {
        return new SubjectAttributes(
                AuthType.PROXY,
                userId,
                persona,
                null,
                partnerId, enterpriseId, loggedInMemberIdValue, loggedInMemberIdType
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
     * Check if subject is Self persona (HSID).
     */
    public boolean isSelf() {
        return isHsid() && "Self".equalsIgnoreCase(persona);
    }

    /**
     * Check if subject is ResponsibleParty persona (HSID).
     */
    public boolean isResponsibleParty() {
        return isHsid() && "ResponsibleParty".equalsIgnoreCase(persona);
    }

    /**
     * Check if subject is an Agent persona (Proxy).
     */
    public boolean isAgent() {
        return isProxy() && "Agent".equalsIgnoreCase(persona);
    }

    /**
     * Check if subject is a CaseWorker persona (Proxy).
     */
    public boolean isCaseWorker() {
        return isProxy() && "CaseWorker".equalsIgnoreCase(persona);
    }

    /**
     * Check if subject is a ConfigSpecialist persona (Proxy).
     */
    public boolean isConfigSpecialist() {
        return isProxy() && "ConfigSpecialist".equalsIgnoreCase(persona);
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
     * Check if subject's assigned enterprise ID matches (Proxy only).
     */
    public boolean isAssignedTo(String targetEnterpriseId) {
        return enterpriseId != null && enterpriseId.equals(targetEnterpriseId);
    }

    /**
     * Check if subject's partner matches (Proxy only).
     */
    public boolean belongsToPartner(String targetPartnerId) {
        return partnerId != null && partnerId.equals(targetPartnerId);
    }
}
