package com.example.bff.auth.model;

import com.example.bff.authz.model.ManagedMemberAccess;
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
 *   <li>{@code activeDelegateTypes} - For DELEGATE: DAA, RPR, ROI (for default enterpriseId)</li>
 *   <li>{@code permissions} - Full permission set (HSID only)</li>
 *   <li>{@code sessionId} - Session ID (HSID only)</li>
 * </ul>
 *
 * @param enterpriseId          Target member's enterprise ID
 * @param loggedInMemberIdType  Type of the logged-in member ID
 * @param loggedInMemberIdValue Operator's member ID value
 * @param persona               User's persona
 * @param activeDelegateTypes   Active delegate types for default enterpriseId (convenience field)
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
     * @param delegateTypes   Active delegate types for this dependent (convenience, extracted from permissions)
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
     * Check if user has all required delegate types for a specific dependent with date validation.
     *
     * <p>This method validates permissions from the PermissionSet, checking:
     * <ul>
     *   <li>The permission exists for the dependent</li>
     *   <li>The permission is marked as active</li>
     *   <li>The current date is within the valid date range (startDate to stopDate)</li>
     * </ul>
     *
     * @param dependentId   The dependent's enterprise ID
     * @param requiredTypes The delegate types required (e.g., DAA, RPR, ROI)
     * @return true if all permissions exist and are currently valid
     */
    public boolean hasValidPermissionsFor(@NonNull String dependentId, @NonNull Set<DelegateType> requiredTypes) {
        if (permissions == null) {
            return false;
        }
        return permissions.hasAllValidPermissions(dependentId, requiredTypes);
    }

    // ==================== Helper Methods ====================

    /**
     * Extract valid delegate types from permissions for a specific dependent.
     * Only includes delegate types that are currently valid (active, within date range).
     *
     * @param permissions  The permission set
     * @param dependentId  The dependent's enterprise ID
     * @return Set of delegate types the user has valid permissions for
     */
    public static Set<DelegateType> extractDelegateTypes(
            @Nullable PermissionSet permissions,
            @NonNull String dependentId) {
        if (permissions == null) {
            return Set.of();
        }
        return permissions.getAccessFor(dependentId)
                .map(AuthPrincipal::toValidDelegateTypes)
                .orElse(Set.of());
    }

    /**
     * Convert ManagedMemberAccess permissions to Set of currently valid DelegateTypes.
     */
    private static Set<DelegateType> toValidDelegateTypes(ManagedMemberAccess access) {
        if (access == null || access.permissions() == null) {
            return Set.of();
        }
        EnumSet<DelegateType> types = EnumSet.noneOf(DelegateType.class);

        for (DelegateType type : DelegateType.values()) {
            if (access.hasValidPermission(type)) {
                types.add(type);
            }
        }

        return types;
    }
}
