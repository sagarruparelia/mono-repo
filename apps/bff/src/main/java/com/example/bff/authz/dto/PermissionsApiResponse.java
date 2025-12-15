package com.example.bff.authz.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Response from the backend Permissions API.
 * Maps to the JSON response structure defined in the API contract.
 */
public record PermissionsApiResponse(
        @JsonProperty("userId") String userId,
        @JsonProperty("persona") String persona,
        @JsonProperty("dependents") List<DependentPermission> dependents,
        @JsonProperty("fetchedAt") Instant fetchedAt,
        @JsonProperty("cacheTTL") Integer cacheTTL
) {
    /**
     * Permission data for a single dependent from the API.
     */
    public record DependentPermission(
            @JsonProperty("dependentId") String dependentId,
            @JsonProperty("dependentName") String dependentName,
            @JsonProperty("permissions") Set<String> permissions,
            @JsonProperty("relationship") String relationship
    ) {}
}
