package com.example.bff.client.ecdh.dto;

public record ImmunizationDto(
        String id,
        String vaccineCode,
        String vaccineName,
        String administrationDate,
        String provider,
        String lotNumber,
        String site,
        String status
) {}
