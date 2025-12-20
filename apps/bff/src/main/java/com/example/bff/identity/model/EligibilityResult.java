package com.example.bff.identity.model;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.time.LocalDate;

/**
 * Result of eligibility check for a member.
 */
public record EligibilityResult(
        @NonNull EligibilityStatus status,
        @Nullable LocalDate termDate
) {
    /**
     * Grace period in months for inactive eligibility.
     */
    public static final int GRACE_PERIOD_MONTHS = 18;

    /**
     * Create an ACTIVE eligibility result.
     */
    public static EligibilityResult active(@Nullable LocalDate termDate) {
        return new EligibilityResult(EligibilityStatus.ACTIVE, termDate);
    }

    /**
     * Create an INACTIVE eligibility result (within grace period).
     */
    public static EligibilityResult inactive(@NonNull LocalDate termDate) {
        return new EligibilityResult(EligibilityStatus.INACTIVE, termDate);
    }

    /**
     * Create an EXPIRED eligibility result (beyond grace period).
     */
    public static EligibilityResult expired(@NonNull LocalDate termDate) {
        return new EligibilityResult(EligibilityStatus.EXPIRED, termDate);
    }

    /**
     * Create a NOT_ELIGIBLE result (404 from API).
     */
    public static EligibilityResult notEligible() {
        return new EligibilityResult(EligibilityStatus.NOT_ELIGIBLE, null);
    }

    /**
     * Create an UNKNOWN result (API error).
     */
    public static EligibilityResult unknown() {
        return new EligibilityResult(EligibilityStatus.UNKNOWN, null);
    }

    /**
     * Check if user has any level of self-access based on eligibility.
     */
    public boolean hasSelfAccess() {
        return status.hasSelfAccess();
    }

    /**
     * Check if the term date is within the grace period.
     *
     * @return true if termDate + 18 months > today
     */
    public boolean isWithinGracePeriod() {
        if (termDate == null) {
            return false;
        }
        LocalDate gracePeriodEnd = termDate.plusMonths(GRACE_PERIOD_MONTHS);
        return LocalDate.now().isBefore(gracePeriodEnd);
    }
}
