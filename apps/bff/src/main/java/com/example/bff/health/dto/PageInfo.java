package com.example.bff.health.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

/**
 * Pagination information from ECDH GraphQL API responses.
 */
public record PageInfo(
        @JsonProperty("pageNum") int pageNum,
        @JsonProperty("pageSize") int pageSize,
        @JsonProperty("totalPages") int totalPages,
        @JsonProperty("totalRecords") int totalRecords,
        @JsonProperty("continuationToken") @Nullable String continuationToken
) {
    /**
     * Check if there are more pages to fetch.
     */
    public boolean hasNextPage() {
        return continuationToken != null && !continuationToken.isBlank();
    }
}
