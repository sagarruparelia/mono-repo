package com.example.bff.health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * GraphQL response for condition query from ECDH API.
 */
public record ConditionResponse(
        @JsonProperty("data") @Nullable ConditionData data
) {
    public record ConditionData(
            @JsonProperty("getConditionByEid") @Nullable ConditionResult result
    ) {}

    public record ConditionResult(
            @JsonProperty("pageInfo") @Nullable PageInfo pageInfo,
            @JsonProperty("conditions") @Nullable List<ConditionDto> conditions
    ) {}

    public record ConditionDto(
            @JsonProperty("id") String id,
            @JsonProperty("conditionCode") String conditionCode,
            @JsonProperty("conditionName") String conditionName,
            @JsonProperty("category") String category,
            @JsonProperty("onsetDate") LocalDate onsetDate,
            @JsonProperty("abatementDate") @Nullable LocalDate abatementDate,
            @JsonProperty("clinicalStatus") String clinicalStatus,
            @JsonProperty("verificationStatus") String verificationStatus
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
     * Get the list of conditions.
     */
    public List<ConditionDto> getConditions() {
        if (data == null || data.result() == null || data.result().conditions() == null) {
            return List.of();
        }
        return data.result().conditions();
    }
}
