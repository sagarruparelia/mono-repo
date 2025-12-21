package com.example.bff.auth.model;

import com.example.bff.authz.model.DependentAccess;
import com.example.bff.authz.model.Permission;
import com.example.bff.authz.model.PermissionSet;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Unified authentication principal for all auth types.
 *
 * <p>Replaces AuthContext with a cleaner, feature-focused interface.
 * Feature packages should use this as the primary way to access auth information.</p>
 *
 * <h3>Properties:</h3>
 * <ul>
 *   <li>{@code enterpriseId} - Target member's enterprise ID</li>
 *   <li>{@code loggedInMemberIdType} - HSID, OHID, or MSID</li>
 *   <li>{@code loggedInMemberIdValue} - Operator's member ID</li>
 *   <li>{@code persona} - INDIVIDUAL_SELF, DELEGATE, CASE_WORKER, AGENT, CONFIG_SPECIALIST</li>
 *   <li>{@code activeDelegateTypes} - For DELEGATE: DAA, RPR, ROI</li>
 *   <li>{@code permissions} - Full permission set (HSID only)</li>
 *   <li>{@code sessionId} - Session ID (HSID only)</li>
 * </ul>
 *
 * @param enterpriseId          Target member's enterprise ID
 * @param loggedInMemberIdType  Type of the logged-in member ID
 * @param loggedInMemberIdValue Operator's member ID value
 * @param persona               User's persona
 * @param activeDelegateTypes   Active delegate types for DELEGATE persona
 * @param permissions           Full permission set (HSID only, null for PROXY)
 * @param sessionId             Session ID (HSID only, null for PROXY)
 */
public record AuthPrincipal(
        @NonNull String enterpriseId,
        @NonNull LoggedInMemberIdType loggedInMemberIdType,
        @NonNull String loggedInMemberIdValue,
        @NonNull Persona persona,
        @NonNull Set<DelegateType> activeDelegateTypes,
        @Nullable PermissionSet permissions,
        @Nullable String sessionId
) {
    /**
     * Exchange attribute key for storing AuthPrincipal.
     */
    public static final String EXCHANGE_ATTRIBUTE = "AUTH_PRINCIPAL";

    // ==================== Factory Methods ====================

    /**
     * Create an AuthPrincipal for INDIVIDUAL_SELF persona (HSID user accessing own data).
     *
     * @param enterpriseId Target member's enterprise ID (same as user's own EID)
     * @param hsidUuid     The HSID UUID (used as loggedInMemberIdValue)
     * @param sessionId    The session ID
     * @param permissions  User's permissions (may be empty for self-access)
     * @return AuthPrincipal for INDIVIDUAL_SELF
     */
    public static AuthPrincipal forIndividualSelf(
            @NonNull String enterpriseId,
            @NonNull String hsidUuid,
            @NonNull String sessionId,
            @Nullable PermissionSet permissions) {
        return new AuthPrincipal(
                enterpriseId,
                LoggedInMemberIdType.HSID,
                hsidUuid,
                Persona.INDIVIDUAL_SELF,
                Set.of(),  // No delegate types for self
                permissions,
                sessionId
        );
    }

    /**
     * Create an AuthPrincipal for DELEGATE persona (ResponsibleParty accessing dependent).
     *
     * @param enterpriseId    Target dependent's enterprise ID
     * @param hsidUuid        The HSID UUID (used as loggedInMemberIdValue)
     * @param sessionId       The session ID
     * @param delegateTypes   Active delegate types for this dependent
     * @param permissions     User's full permissions
     * @return AuthPrincipal for DELEGATE
     */
    public static AuthPrincipal forDelegate(
            @NonNull String enterpriseId,
            @NonNull String hsidUuid,
            @NonNull String sessionId,
            @NonNull Set<DelegateType> delegateTypes,
            @NonNull PermissionSet permissions) {
        return new AuthPrincipal(
                enterpriseId,
                LoggedInMemberIdType.HSID,
                hsidUuid,
                Persona.DELEGATE,
                Set.copyOf(delegateTypes),
                permissions,
                sessionId
        );
    }

    /**
     * Create an AuthPrincipal for CASE_WORKER persona (external via mTLS).
     *
     * @param enterpriseId        Target member's enterprise ID
     * @param loggedInMemberIdValue Operator's member ID
     * @param loggedInMemberIdType  Type of operator ID (OHID or MSID)
     * @return AuthPrincipal for CASE_WORKER
     */
    public static AuthPrincipal forCaseWorker(
            @NonNull String enterpriseId,
            @NonNull String loggedInMemberIdValue,
            @NonNull LoggedInMemberIdType loggedInMemberIdType) {
        return new AuthPrincipal(
                enterpriseId,
                loggedInMemberIdType,
                loggedInMemberIdValue,
                Persona.CASE_WORKER,
                Set.of(),
                null,
                null
        );
    }

    /**
     * Create an AuthPrincipal for AGENT persona (external via mTLS).
     *
     * @param enterpriseId        Target member's enterprise ID
     * @param loggedInMemberIdValue Operator's member ID
     * @param loggedInMemberIdType  Type of operator ID (OHID or MSID)
     * @return AuthPrincipal for AGENT
     */
    public static AuthPrincipal forAgent(
            @NonNull String enterpriseId,
            @NonNull String loggedInMemberIdValue,
            @NonNull LoggedInMemberIdType loggedInMemberIdType) {
        return new AuthPrincipal(
                enterpriseId,
                loggedInMemberIdType,
                loggedInMemberIdValue,
                Persona.AGENT,
                Set.of(),
                null,
                null
        );
    }

    /**
     * Create an AuthPrincipal for CONFIG_SPECIALIST persona (external via mTLS).
     *
     * @param enterpriseId        Target member's enterprise ID
     * @param loggedInMemberIdValue Operator's member ID
     * @param loggedInMemberIdType  Type of operator ID (OHID or MSID)
     * @return AuthPrincipal for CONFIG_SPECIALIST
     */
    public static AuthPrincipal forConfigSpecialist(
            @NonNull String enterpriseId,
            @NonNull String loggedInMemberIdValue,
            @NonNull LoggedInMemberIdType loggedInMemberIdType) {
        return new AuthPrincipal(
                enterpriseId,
                loggedInMemberIdType,
                loggedInMemberIdValue,
                Persona.CONFIG_SPECIALIST,
                Set.of(),
                null,
                null
        );
    }

    // ==================== Convenience Methods ====================

    /**
     * Check if this is HSID (session-based) authentication.
     */
    public boolean isHsidAuth() {
        return persona.isHsid();
    }

    /**
     * Check if this is PROXY (OAuth2/mTLS) authentication.
     */
    public boolean isProxyAuth() {
        return persona.isProxy();
    }

    /**
     * Check if user has a specific delegate type.
     * Always returns false for non-DELEGATE personas.
     */
    public boolean hasDelegate(DelegateType type) {
        return activeDelegateTypes.contains(type);
    }

    /**
     * Check if user has all specified delegate types.
     * Always returns false for non-DELEGATE personas unless types is empty.
     */
    public boolean hasAllDelegates(Set<DelegateType> types) {
        if (types == null || types.isEmpty()) {
            return true;
        }
        return activeDelegateTypes.containsAll(types);
    }

    /**
     * Check if a session ID is available.
     */
    public boolean hasSessionId() {
        return sessionId != null && !sessionId.isBlank();
    }

    /**
     * Check if this user can view data (has basic access).
     * For DELEGATE: requires DAA + RPR.
     * For others: always true (authorization handled elsewhere).
     */
    public boolean canView() {
        if (persona == Persona.DELEGATE) {
            return hasDelegate(DelegateType.DAA) && hasDelegate(DelegateType.RPR);
        }
        return true;
    }

    /**
     * Check if this user can access sensitive data.
     * For DELEGATE: requires DAA + RPR + ROI.
     * For CONFIG_SPECIALIST: always true.
     * For others: always false.
     */
    public boolean canAccessSensitive() {
        if (persona == Persona.DELEGATE) {
            return hasDelegate(DelegateType.DAA)
                    && hasDelegate(DelegateType.RPR)
                    && hasDelegate(DelegateType.ROI);
        }
        return persona == Persona.CONFIG_SPECIALIST;
    }

    // ==================== Helper Methods ====================

    /**
     * Extract delegate types from permissions for a specific dependent.
     *
     * @param permissions  The permission set
     * @param dependentId  The dependent's enterprise ID
     * @return Set of delegate types the user has for this dependent
     */
    public static Set<DelegateType> extractDelegateTypes(
            @Nullable PermissionSet permissions,
            @NonNull String dependentId) {
        if (permissions == null) {
            return Set.of();
        }
        return permissions.getAccessFor(dependentId)
                .map(AuthPrincipal::toDelegateTypes)
                .orElse(Set.of());
    }

    /**
     * Convert Permission set to DelegateType set.
     */
    private static Set<DelegateType> toDelegateTypes(DependentAccess access) {
        if (access == null || access.permissions() == null) {
            return Set.of();
        }
        EnumSet<DelegateType> types = EnumSet.noneOf(DelegateType.class);
        if (access.permissions().contains(Permission.DAA)) {
            types.add(DelegateType.DAA);
        }
        if (access.permissions().contains(Permission.RPR)) {
            types.add(DelegateType.RPR);
        }
        if (access.permissions().contains(Permission.ROI)) {
            types.add(DelegateType.ROI);
        }
        return types;
    }
}
