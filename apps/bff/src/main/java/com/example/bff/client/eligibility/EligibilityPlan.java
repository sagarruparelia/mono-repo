package com.example.bff.client.eligibility;

import java.time.LocalDate;

public record EligibilityPlan(
        String planCode,
        String memberId,
        LocalDate startDate,
        LocalDate termDate
) {}
