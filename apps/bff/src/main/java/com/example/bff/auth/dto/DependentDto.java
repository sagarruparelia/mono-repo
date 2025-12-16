package com.example.bff.auth.dto;

/**
 * DTO for dependent (child) information
 */
public record DependentDto(
        String id,
        String name,
        String dateOfBirth
) {}
