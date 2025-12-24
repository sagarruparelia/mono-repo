package com.example.bff.document.model.request;

import jakarta.validation.constraints.*;

public record InitiateUploadRequest(
        String enterpriseId,

        @NotBlank(message = "Filename is required")
        @Size(max = 255, message = "Filename must not exceed 255 characters")
        String filename,

        @NotBlank(message = "Content type is required")
        String contentType,

        @NotNull(message = "File size is required")
        @Min(value = 1, message = "File size must be at least 1 byte")
        @Max(value = 26214400, message = "File size must not exceed 25MB")
        Long fileSizeBytes
) {}
