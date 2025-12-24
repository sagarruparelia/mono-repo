package com.example.bff.health.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PaginationRequest(
        String enterpriseId,

        @Min(value = 0, message = "Page must be 0 or greater")
        int page,

        @Min(value = 1, message = "Size must be at least 1")
        @Max(value = 100, message = "Size must not exceed 100")
        int size
) {
    public PaginationRequest {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 100) size = 100;
    }

    public PaginationRequest(String enterpriseId) {
        this(enterpriseId, 0, 20);
    }

    public PaginationRequest() {
        this(null, 0, 20);
    }
}
