package com.example.bff.common;

/**
 * Shared constants used across the BFF application.
 * Centralizes magic strings to prevent duplication and improve maintainability.
 */
public final class BffConstants {

    private BffConstants() {}

    // Session cookie name
    public static final String SESSION_COOKIE_NAME = "BFF_SESSION";

    // Exchange attribute for validated enterprise ID
    public static final String VALIDATED_ENTERPRISE_ID = "VALIDATED_ENTERPRISE_ID";

    // Auth principal exchange attribute
    public static final String AUTH_PRINCIPAL_ATTRIBUTE = "AUTH_PRINCIPAL";
}
