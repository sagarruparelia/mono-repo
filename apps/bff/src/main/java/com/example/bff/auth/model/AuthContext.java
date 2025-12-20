package com.example.bff.auth.model;

import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.authz.model.AuthType;

/**
 * Unified authentication context for both HSID (session) and PROXY (header-based) auth.
 *
 * <p>This record represents the authenticated user's context after validation by
 * DualAuthWebFilter. It provides a consistent interface for downstream components
 * regardless of authentication method.</p>
 *
 * <h3>Auth Types:</h3>
 * <ul>
 *   <li><b>HSID</b>: Browser session-based auth (BFF_SESSION cookie)</li>
 *   <li><b>PROXY</b>: Partner header-based auth via mTLS ALB</li>
 * </ul>
 *
 * @param authType          The authentication type (HSID or PROXY)
 * @param userId            Authenticated user's ID (from session or X-User-Id header)
 * @param effectiveMemberId Member context for data access (may differ from userId for parents)
 * @param persona           User's persona (individual, parent, agent, case_worker, config_specialist)
 * @param sessionId         Session ID for HSID (null for PROXY)
 * @param partnerId         Partner organization ID for PROXY (null for HSID)
 * @param operatorId        Operator ID for PROXY (null for HSID)
 * @param operatorName      Operator display name for PROXY (null for HSID)
 * @param idpType           Identity Provider type for PROXY (msid, ohid) (null for HSID)
 * @param subject           Cached SubjectAttributes for ABAC authorization
 */
public record AuthContext(
        AuthType authType,
        String userId,
        String effectiveMemberId,
        String persona,
        String sessionId,
        String partnerId,
        String operatorId,
        String operatorName,
        String idpType,
        SubjectAttributes subject
) {
    /**
     * Exchange attribute key for storing AuthContext.
     */
    public static final String EXCHANGE_ATTRIBUTE = "AUTH_CONTEXT";

    /**
     * Create an AuthContext for HSID (session-based) authentication.
     *
     * @param userId            The authenticated user's ID
     * @param effectiveMemberId The member context (userId or selected child)
     * @param persona           The user's persona (individual or parent)
     * @param sessionId         The session ID
     * @param subject           The cached SubjectAttributes for ABAC
     * @return AuthContext configured for HSID
     */
    public static AuthContext forHsid(
            String userId,
            String effectiveMemberId,
            String persona,
            String sessionId,
            SubjectAttributes subject
    ) {
        return new AuthContext(
                AuthType.HSID,
                userId,
                effectiveMemberId,
                persona,
                sessionId,
                null,  // partnerId
                null,  // operatorId
                null,  // operatorName
                null,  // idpType
                subject
        );
    }

    /**
     * Create an AuthContext for PROXY (header-based) authentication.
     *
     * @param userId            The operator's user ID (from X-User-Id header)
     * @param effectiveMemberId The member being accessed (from request body)
     * @param persona           The operator's persona (agent, case_worker, config_specialist)
     * @param partnerId         The partner organization ID
     * @param operatorId        The operator ID
     * @param operatorName      The operator display name
     * @param idpType           The IDP type (msid, ohid)
     * @param subject           The cached SubjectAttributes for ABAC
     * @return AuthContext configured for PROXY
     */
    public static AuthContext forProxy(
            String userId,
            String effectiveMemberId,
            String persona,
            String partnerId,
            String operatorId,
            String operatorName,
            String idpType,
            SubjectAttributes subject
    ) {
        return new AuthContext(
                AuthType.PROXY,
                userId,
                effectiveMemberId,
                persona,
                null,  // sessionId
                partnerId,
                operatorId,
                operatorName,
                idpType,
                subject
        );
    }

    /**
     * Check if this is HSID (session-based) authentication.
     */
    public boolean isHsid() {
        return authType == AuthType.HSID;
    }

    /**
     * Check if this is PROXY (header-based) authentication.
     */
    public boolean isProxy() {
        return authType == AuthType.PROXY;
    }

    /**
     * Check if user is viewing their own data (userId equals effectiveMemberId).
     */
    public boolean isViewingOwnData() {
        return userId != null && userId.equals(effectiveMemberId);
    }

    /**
     * Check if user has a specific persona.
     */
    public boolean hasPersona(String targetPersona) {
        return targetPersona != null && targetPersona.equalsIgnoreCase(persona);
    }

    /**
     * Check if user is an individual member (HSID).
     */
    public boolean isIndividual() {
        return isHsid() && "individual".equalsIgnoreCase(persona);
    }

    /**
     * Check if user is a responsible party/parent (HSID).
     */
    public boolean isResponsibleParty() {
        return isHsid() && "parent".equalsIgnoreCase(persona);
    }

    /**
     * Check if user is an agent (PROXY).
     */
    public boolean isAgent() {
        return isProxy() && "agent".equalsIgnoreCase(persona);
    }

    /**
     * Check if user is a case worker (PROXY).
     */
    public boolean isCaseWorker() {
        return isProxy() && "case_worker".equalsIgnoreCase(persona);
    }

    /**
     * Check if user is a config specialist (PROXY).
     */
    public boolean isConfigSpecialist() {
        return isProxy() && "config_specialist".equalsIgnoreCase(persona);
    }
}
