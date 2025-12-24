package com.example.bff.document.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record DocumentListRequest(
        String enterpriseId,

        @Min(value = 0, message = "Page must be 0 or greater")
        int page,

        @Min(value = 1, message = "Size must be at least 1")
        @Max(value = 50, message = "Size must not exceed 50")
        int size,

        String category
) {
    public DocumentListRequest {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
        if (size > 50) size = 50;
    }

    public DocumentListRequest(String enterpriseId, String category) {
        this(enterpriseId, 0, 20, category);
    }

    public DocumentListRequest(String enterpriseId) {
        this(enterpriseId, 0, 20, null);
    }

    public DocumentListRequest() {
        this(null, 0, 20, null);
    }
}
