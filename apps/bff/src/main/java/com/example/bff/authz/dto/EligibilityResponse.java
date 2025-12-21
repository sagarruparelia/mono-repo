package com.example.bff.authz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

import java.time.LocalDate;

/**
 * Response DTO for Eligibility Graph API.
 * Endpoint: /graph/1.0.0
 *
 * Contains eligibility status and termination date.
 * A 404 response means the user has no eligibility (NOT_ELIGIBLE).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EligibilityResponse(
        @JsonProperty("data") @Nullable EligibilityData data
) {
    /**
     * GraphQL data wrapper.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EligibilityData(
            @JsonProperty("eligibility") @Nullable Eligibility eligibility
    ) {}

    /**
     * Eligibility details.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Eligibility(
            @JsonProperty("status") @Nullable String status,
            @JsonProperty("termDate") @Nullable LocalDate termDate
    ) {
        /**
         * Check if eligibility is currently active.
         */
        public boolean isActive() {
            return "active".equalsIgnoreCase(status);
        }
    }

    /**
     * Extracts eligibility status from the response.
     *
     * @return Status string or null if not found
     */
    @Nullable
    public String getStatus() {
        if (data == null || data.eligibility == null) {
            return null;
        }
        return data.eligibility.status();
    }

    /**
     * Extracts term date from the response.
     *
     * @return Term date or null if not found
     */
    @Nullable
    public LocalDate getTermDate() {
        if (data == null || data.eligibility == null) {
            return null;
        }
        return data.eligibility.termDate();
    }

    /**
     * Check if eligibility is active.
     */
    public boolean isActive() {
        return data != null && data.eligibility != null && data.eligibility.isActive();
    }
}
