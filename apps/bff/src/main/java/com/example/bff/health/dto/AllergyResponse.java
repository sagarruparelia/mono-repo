package com.example.bff.health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * GraphQL response for allergy query from ECDH API.
 */
public record AllergyResponse(
        @JsonProperty("data") @Nullable AllergyData data
) {
    public record AllergyData(
            @JsonProperty("getAllergyByEid") @Nullable AllergyResult result
    ) {}

    public record AllergyResult(
            @JsonProperty("pageInfo") @Nullable PageInfo pageInfo,
            @JsonProperty("allergies") @Nullable List<AllergyDto> allergies
    ) {}

    public record AllergyDto(
            @JsonProperty("id") String id,
            @JsonProperty("allergenCode") String allergenCode,
            @JsonProperty("allergenName") String allergenName,
            @JsonProperty("category") String category,
            @JsonProperty("severity") String severity,
            @JsonProperty("reaction") String reaction,
            @JsonProperty("onsetDate") LocalDate onsetDate,
            @JsonProperty("status") String status
    ) {}

    /**
     * Get the continuation token for pagination.
     */
    @Nullable
    public String getContinuationToken() {
        if (data == null || data.result() == null || data.result().pageInfo() == null) {
            return null;
        }
        return data.result().pageInfo().continuationToken();
    }

    /**
     * Get the list of allergies.
     */
    public List<AllergyDto> getAllergies() {
        if (data == null || data.result() == null || data.result().allergies() == null) {
            return List.of();
        }
        return data.result().allergies();
    }
}
