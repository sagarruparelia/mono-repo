package com.example.bff.authz.model;

/**
 * Permission types for ReBAC authorization.
 *
 * <p>Access rules:
 * <ul>
 *   <li>CAN_VIEW_CHILD = DAA AND RPR</li>
 *   <li>CAN_VIEW_SENSITIVE = DAA AND RPR AND ROI</li>
 * </ul>
 */
public enum Permission {
    /**
     * Delegate Access Authority - Legal authority to act on behalf of dependent.
     */
    DAA,

    /**
     * Relying Party Representative - Registered as representative for the dependent.
     */
    RPR,

    /**
     * Release of Information - Consent to release additional sensitive data.
     */
    ROI
}
