package com.example.bff.auth.model;

/**
 * Types of delegate permissions for ResponsibleParty access.
 *
 * <p>These represent the permission tuples that a ResponsibleParty (DELEGATE persona)
 * must have to access a dependent's data.</p>
 *
 * <h3>Permission Requirements:</h3>
 * <ul>
 *   <li>Basic view access: {@link #DAA} + {@link #RPR}</li>
 *   <li>Sensitive data access: {@link #DAA} + {@link #RPR} + {@link #ROI}</li>
 * </ul>
 */
public enum DelegateType {
    /**
     * Delegate Access Authority - Legal authority to act on behalf of a dependent.
     * Required for any delegate access.
     */
    DAA,

    /**
     * Relying Party Representative - Registered representative for the dependent.
     * Required for any delegate access.
     */
    RPR,

    /**
     * Release of Information - Consent to access sensitive data.
     * Required for accessing PHI, medical records, and other sensitive information.
     */
    ROI
}
