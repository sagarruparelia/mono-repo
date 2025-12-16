package com.example.bff.health.dto;

/**
 * DTO for allergy records
 */
public record AllergyDto(
        String id,
        String allergen,
        String reaction,
        String severity,  // mild, moderate, severe
        String onsetDate
) {}
