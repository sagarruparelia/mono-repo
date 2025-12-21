package com.example.bff.authz.model;

/**
 * Eligibility status for a member.
 * Determines what level of self-access the member has.
 */
public enum EligibilityStatus {
    /**
     * Currently eligible - full self-access.
     */
    ACTIVE,

    /**
     * Eligibility expired but within 18-month grace period.
     * Limited self-access.
     */
    INACTIVE,

    /**
     * Eligibility expired more than 18 months ago.
     * No self-access.
     */
    EXPIRED,

    /**
     * Never had eligibility (404 from Eligibility API).
     * No self-access.
     */
    NOT_ELIGIBLE,

    /**
     * Could not determine eligibility due to API error.
     * Treated as NOT_ELIGIBLE for security (fail closed).
     */
    UNKNOWN;

    /**
     * Check if this status grants any level of self-access.
     *
     * @return true if ACTIVE or INACTIVE
     */
    public boolean hasSelfAccess() {
        return this == ACTIVE || this == INACTIVE;
    }

    /**
     * Check if this status grants full self-access.
     *
     * @return true if ACTIVE
     */
    public boolean hasFullAccess() {
        return this == ACTIVE;
    }
}
