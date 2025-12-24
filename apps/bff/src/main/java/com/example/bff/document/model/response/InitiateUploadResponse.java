package com.example.bff.document.model.response;

import java.time.Instant;

public record InitiateUploadResponse(
        String uploadId,
        String presignedUrl,
        Instant presignedUrlExpiresAt,
        Instant uploadExpiresAt,
        Long maxFileSizeBytes,
        String requiredContentType
) {}
