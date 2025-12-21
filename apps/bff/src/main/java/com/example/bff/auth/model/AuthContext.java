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
 * <h3>Personas:</h3>
 * <ul>
 *   <li>HSID: Self, ResponsibleParty</li>
 *   <li>PROXY: CaseWorker, Agent, ConfigSpecialist</li>
 * </ul>
 *
 * @param authType              The authentication type (HSID or PROXY)
 * @param userId                Authenticated user's ID (from session or header)
 * @param effectiveMemberId     Member context for data access (may differ from userId for ResponsibleParty)
 * @param persona               User's persona (Self, ResponsibleParty, CaseWorker, Agent, ConfigSpecialist)
 * @param sessionId             Session ID for HSID (null for PROXY)
 * @param partnerId             Partner organization ID for PROXY (null for HSID)
 * @param loggedInMemberIdValue Logged-in member ID value for PROXY (from X-Logged-In-Member-Id-Value)
 * @param loggedInMemberIdType  Logged-in member ID type for PROXY (OHID or MSID)
 * @param subject               Cached SubjectAttributes for ABAC authorization
 */
public record AuthContext(
        AuthType authType,
        String userId,
        String effectiveMemberId,
        String persona,
        String sessionId,
        String partnerId,
        String loggedInMemberIdValue,
        String loggedInMemberIdType,
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
     * @param effectiveMemberId The member context (userId or selected dependent)
     * @param persona           The user's persona (Self or ResponsibleParty)
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
                null,  // loggedInMemberIdValue
                null,  // loggedInMemberIdType
                subject
        );
    }

    /**
     * Create an AuthContext for PROXY (header-based) authentication.
     *
     * @param userId                Derived user identifier
     * @param effectiveMemberId     The member being accessed (from X-Enterprise-Id header)
     * @param persona               The operator's persona (CaseWorker, Agent, ConfigSpecialist)
     * @param partnerId             The partner organization ID
     * @param loggedInMemberIdValue The logged-in member ID value (from X-Logged-In-Member-Id-Value)
     * @param loggedInMemberIdType  The logged-in member ID type (OHID or MSID)
     * @param subject               The cached SubjectAttributes for ABAC
     * @return AuthContext configured for PROXY
     */
    public static AuthContext forProxy(
            String userId,
            String effectiveMemberId,
            String persona,
            String partnerId,
            String loggedInMemberIdValue,
            String loggedInMemberIdType,
            SubjectAttributes subject
    ) {
        return new AuthContext(
                AuthType.PROXY,
                userId,
                effectiveMemberId,
                persona,
                null,  // sessionId
                partnerId,
                loggedInMemberIdValue,
                loggedInMemberIdType,
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
     * Check if user is Self persona (HSID).
     */
    public boolean isSelf() {
        return isHsid() && "Self".equalsIgnoreCase(persona);
    }

    /**
     * Check if user is a ResponsibleParty persona (HSID).
     */
    public boolean isResponsibleParty() {
        return isHsid() && "ResponsibleParty".equalsIgnoreCase(persona);
    }

    /**
     * Check if user is an Agent persona (PROXY).
     */
    public boolean isAgent() {
        return isProxy() && "Agent".equalsIgnoreCase(persona);
    }

    /**
     * Check if user is a CaseWorker persona (PROXY).
     */
    public boolean isCaseWorker() {
        return isProxy() && "CaseWorker".equalsIgnoreCase(persona);
    }

    /**
     * Check if user is a ConfigSpecialist persona (PROXY).
     */
    public boolean isConfigSpecialist() {
        return isProxy() && "ConfigSpecialist".equalsIgnoreCase(persona);
    }
}
