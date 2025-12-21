package com.example.bff.auth.model;

/**
 * Unified persona types for all authentication methods.
 *
 * <p>Replaces string-based persona handling with type-safe enum.</p>
 *
 * <h3>HSID Personas (session-based):</h3>
 * <ul>
 *   <li>{@link #INDIVIDUAL_SELF} - User accessing their own data</li>
 *   <li>{@link #DELEGATE} - ResponsibleParty accessing dependent's data</li>
 * </ul>
 *
 * <h3>PROXY Personas (OAuth2 header-based):</h3>
 * <ul>
 *   <li>{@link #CASE_WORKER} - External case worker via mTLS</li>
 *   <li>{@link #AGENT} - External agent via mTLS</li>
 *   <li>{@link #CONFIG_SPECIALIST} - Configuration specialist via mTLS</li>
 * </ul>
 */
public enum Persona {
    /**
     * HSID user accessing their own data.
     * Enterprise ID is derived from session.
     */
    INDIVIDUAL_SELF,

    /**
     * HSID ResponsibleParty accessing a dependent's data.
     * Enterprise ID comes from X-Enterprise-Id header and must be validated
     * against the user's permissions.
     */
    DELEGATE,

    /**
     * External case worker accessing member data via OAuth2/mTLS.
     */
    CASE_WORKER,

    /**
     * External agent accessing member data via OAuth2/mTLS.
     */
    AGENT,

    /**
     * Configuration specialist with elevated access via OAuth2/mTLS.
     */
    CONFIG_SPECIALIST;

    /**
     * Check if this is an HSID (session-based) persona.
     */
    public boolean isHsid() {
        return this == INDIVIDUAL_SELF || this == DELEGATE;
    }

    /**
     * Check if this is a PROXY (OAuth2/mTLS) persona.
     */
    public boolean isProxy() {
        return this == CASE_WORKER || this == AGENT || this == CONFIG_SPECIALIST;
    }

    /**
     * Convert legacy string persona to enum.
     *
     * @param legacyPersona The legacy string persona (e.g., "Self", "ResponsibleParty", "CaseWorker")
     * @return The corresponding Persona enum, or null if not recognized
     */
    public static Persona fromLegacy(String legacyPersona) {
        if (legacyPersona == null) {
            return null;
        }
        return switch (legacyPersona.toLowerCase()) {
            case "self", "individual", "individual_self" -> INDIVIDUAL_SELF;
            case "responsibleparty", "parent", "delegate" -> DELEGATE;
            case "caseworker", "case_worker" -> CASE_WORKER;
            case "agent" -> AGENT;
            case "configspecialist", "config_specialist", "config" -> CONFIG_SPECIALIST;
            default -> null;
        };
    }

    /**
     * Convert to legacy string format for compatibility with existing ABAC policies.
     */
    public String toLegacy() {
        return switch (this) {
            case INDIVIDUAL_SELF -> "Self";
            case DELEGATE -> "ResponsibleParty";
            case CASE_WORKER -> "CaseWorker";
            case AGENT -> "Agent";
            case CONFIG_SPECIALIST -> "ConfigSpecialist";
        };
    }
}
