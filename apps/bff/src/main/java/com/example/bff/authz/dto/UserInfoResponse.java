package com.example.bff.authz.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * Response DTO for User Service API.
 * Endpoint: /api/identity/user/individual/v1/read
 *
 * Contains identity information including:
 * - Enterprise ID (EID)
 * - Birthdate for age calculation
 * - Other IDs for member type check (PR = Responsible Party)
 * - API identifier for Graph API calls
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserInfoResponse(
        @JsonProperty("identity") @Nullable Identity identity,
        @JsonProperty("birthdate") @Nullable String birthdate,
        @JsonProperty("identifiers") @Nullable Identifiers identifiers,
        @JsonProperty("apiIdentifier") @Nullable String apiIdentifier
) {
    /**
     * Identity section containing enterprise IDs.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Identity(
            @JsonProperty("identity_enterpriseId") @Nullable List<EnterpriseId> enterpriseIds
    ) {}

    /**
     * Enterprise ID entry.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnterpriseId(
            @JsonProperty("enterpriseId") @Nullable String enterpriseId
    ) {}

    /**
     * Identifiers section containing other IDs.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Identifiers(
            @JsonProperty("other_ids") @Nullable List<OtherId> otherIds
    ) {}

    /**
     * Other ID entry used for member type check.
     * To check if user is a Responsible Party (PR):
     * - value = "PR"
     * - sourceCode = "ABCD"
     * - type = "MemberType"
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OtherId(
            @JsonProperty("value") @Nullable String value,
            @JsonProperty("sourceCode") @Nullable String sourceCode,
            @JsonProperty("type") @Nullable String type
    ) {}

    /**
     * Extracts the primary enterprise ID from the response.
     *
     * @return Enterprise ID or null if not found
     */
    @Nullable
    public String getEnterpriseId() {
        if (identity == null || identity.enterpriseIds == null || identity.enterpriseIds.isEmpty()) {
            return null;
        }
        EnterpriseId first = identity.enterpriseIds.get(0);
        return first != null ? first.enterpriseId : null;
    }

    /**
     * Checks if the user is a Responsible Party (PR).
     * Looks for: value="PR", sourceCode="ABCD", type="MemberType"
     *
     * @return true if user is a Responsible Party
     */
    public boolean isResponsibleParty() {
        if (identifiers == null || identifiers.otherIds == null) {
            return false;
        }
        return identifiers.otherIds.stream()
                .anyMatch(id ->
                        "PR".equals(id.value()) &&
                        "ABCD".equals(id.sourceCode()) &&
                        "MemberType".equals(id.type()));
    }
}
