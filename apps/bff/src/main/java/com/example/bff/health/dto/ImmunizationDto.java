package com.example.bff.health.dto;

/**
 * DTO for immunization/vaccination records
 */
public record ImmunizationDto(
        String id,
        String name,
        String date,
        String provider,
        String lotNumber
) {}
