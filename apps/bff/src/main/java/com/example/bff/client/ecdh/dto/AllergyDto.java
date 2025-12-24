package com.example.bff.client.ecdh.dto;

public record AllergyDto(
        String id,
        String allergenCode,
        String allergenName,
        String allergenType,
        String severity,
        String reaction,
        String onsetDate,
        String status
) {}
