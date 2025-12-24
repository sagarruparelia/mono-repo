package com.example.bff.security.session;

import java.io.Serializable;
import java.time.LocalDate;

public record EligibilityPlan(
        String planCode,
        String memberId,
        LocalDate startDate,
        LocalDate termDate
) implements Serializable {

    // Active if started, not terminated, or within 18-month grace period after termination
    public boolean isActiveOrInGracePeriod() {
        LocalDate today = LocalDate.now();

        // Plan hasn't started yet
        if (startDate.isAfter(today)) {
            return false;
        }

        // No term date means active
        if (termDate == null) {
            return true;
        }

        // Active if not yet terminated
        if (termDate.isAfter(today) || termDate.isEqual(today)) {
            return true;
        }

        // Within 18-month grace period after termination
        LocalDate gracePeriodEnd = termDate.plusMonths(18);
        return today.isBefore(gracePeriodEnd) || today.isEqual(gracePeriodEnd);
    }
}
