package com.example.bff.auth.model;

/**
 * Type of identifier used for the logged-in member/operator.
 *
 * <p>This indicates the source/type of the operator's identity.</p>
 */
public enum LoggedInMemberIdType {
    /**
     * HSID - Health System ID from BFF session authentication.
     * Used for INDIVIDUAL_SELF and DELEGATE personas.
     */
    HSID,

    /**
     * OHID - Ohio Health ID from external OAuth2 provider.
     * Used for PROXY personas (CASE_WORKER, AGENT, CONFIG_SPECIALIST).
     */
    OHID,

    /**
     * MSID - Member Service ID from external OAuth2 provider.
     * Used for PROXY personas (CASE_WORKER, AGENT, CONFIG_SPECIALIST).
     */
    MSID;

    /**
     * Check if this is an HSID type (session-based).
     */
    public boolean isHsid() {
        return this == HSID;
    }

    /**
     * Check if this is a proxy/external type.
     */
    public boolean isProxy() {
        return this == OHID || this == MSID;
    }

    /**
     * Parse from string (case-insensitive).
     *
     * @param value The string value to parse
     * @return The corresponding LoggedInMemberIdType, or null if not recognized
     */
    public static LoggedInMemberIdType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.toUpperCase()) {
            case "HSID" -> HSID;
            case "OHID" -> OHID;
            case "MSID" -> MSID;
            default -> null;
        };
    }
}
