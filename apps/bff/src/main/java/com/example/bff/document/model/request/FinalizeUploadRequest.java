package com.example.bff.document.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FinalizeUploadRequest(
        String enterpriseId,

        @NotBlank(message = "Upload ID is required")
        String uploadId,

        @NotBlank(message = "Category is required")
        String category,

        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description
) {}
