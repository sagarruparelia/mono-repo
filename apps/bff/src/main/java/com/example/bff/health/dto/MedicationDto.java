package com.example.bff.health.dto;

/**
 * DTO for medication records
 */
public record MedicationDto(
        String id,
        String name,
        String dosage,
        String frequency,
        String prescribedDate,
        String prescriber
) {}
