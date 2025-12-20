package com.example.bff.health.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Generic API response wrapper for health data endpoints.
 *
 * @param <T> The type of health records
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealthDataApiResponse<T>(
        String memberEid,
        List<T> records,
        int totalRecords,
        Instant cachedAt,
        @Nullable Instant expiresAt
) {
    /**
     * Create a response from immunization entity.
     */
    public static HealthDataApiResponse<ImmunizationResponse.ImmunizationDto> fromImmunizations(
            com.example.bff.health.model.ImmunizationEntity entity) {
        List<ImmunizationResponse.ImmunizationDto> dtos = entity.records().stream()
                .map(r -> new ImmunizationResponse.ImmunizationDto(
                        r.id(),
                        r.vaccineCode(),
                        r.vaccineName(),
                        r.administrationDate(),
                        r.provider(),
                        r.lotNumber(),
                        r.site(),
                        r.status()
                ))
                .toList();

        return new HealthDataApiResponse<>(
                entity.memberEid(),
                dtos,
                dtos.size(),
                entity.fetchedAt(),
                entity.expiresAt()
        );
    }

    /**
     * Create a response from allergy entity.
     */
    public static HealthDataApiResponse<AllergyResponse.AllergyDto> fromAllergies(
            com.example.bff.health.model.AllergyEntity entity) {
        List<AllergyResponse.AllergyDto> dtos = entity.records().stream()
                .map(r -> new AllergyResponse.AllergyDto(
                        r.id(),
                        r.allergenCode(),
                        r.allergenName(),
                        r.category(),
                        r.severity(),
                        r.reaction(),
                        r.onsetDate(),
                        r.status()
                ))
                .toList();

        return new HealthDataApiResponse<>(
                entity.memberEid(),
                dtos,
                dtos.size(),
                entity.fetchedAt(),
                entity.expiresAt()
        );
    }

    /**
     * Create a response from condition entity.
     */
    public static HealthDataApiResponse<ConditionResponse.ConditionDto> fromConditions(
            com.example.bff.health.model.ConditionEntity entity) {
        List<ConditionResponse.ConditionDto> dtos = entity.records().stream()
                .map(r -> new ConditionResponse.ConditionDto(
                        r.id(),
                        r.conditionCode(),
                        r.conditionName(),
                        r.category(),
                        r.onsetDate(),
                        r.abatementDate(),
                        r.clinicalStatus(),
                        r.verificationStatus()
                ))
                .toList();

        return new HealthDataApiResponse<>(
                entity.memberEid(),
                dtos,
                dtos.size(),
                entity.fetchedAt(),
                entity.expiresAt()
        );
    }
}
