package com.example.bff.health.model;

import java.time.LocalDate;

public record Immunization(
        String id,
        String vaccineCode,
        String vaccineName,
        LocalDate administrationDate,
        String provider,
        String lotNumber,
        String site,
        String status
) {}
