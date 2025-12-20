package com.example.bff.health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * GraphQL response for immunization query from ECDH API.
 */
public record ImmunizationResponse(
        @JsonProperty("data") @Nullable ImmunizationData data
) {
    public record ImmunizationData(
            @JsonProperty("getImmunizationByEid") @Nullable ImmunizationResult result
    ) {}

    public record ImmunizationResult(
            @JsonProperty("pageInfo") @Nullable PageInfo pageInfo,
            @JsonProperty("immunizations") @Nullable List<ImmunizationDto> immunizations
    ) {}

    public record ImmunizationDto(
            @JsonProperty("id") String id,
            @JsonProperty("vaccineCode") String vaccineCode,
            @JsonProperty("vaccineName") String vaccineName,
            @JsonProperty("administrationDate") LocalDate administrationDate,
            @JsonProperty("provider") String provider,
            @JsonProperty("lotNumber") String lotNumber,
            @JsonProperty("site") String site,
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
     * Get the list of immunizations.
     */
    public List<ImmunizationDto> getImmunizations() {
        if (data == null || data.result() == null || data.result().immunizations() == null) {
            return List.of();
        }
        return data.result().immunizations();
    }
}
