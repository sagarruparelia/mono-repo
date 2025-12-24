package com.example.bff.health.model;

import java.time.LocalDate;

public record Allergy(
        String id,
        String allergenCode,
        String allergenName,
        String allergenType,
        String severity,
        String reaction,
        LocalDate onsetDate,
        String status
) {}
