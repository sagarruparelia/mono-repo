package com.example.bff.document.model.response;

import java.time.Instant;

public record DocumentDownloadResponse(
        String presignedUrl,
        Instant expiresAt,
        String filename,
        String contentType,
        Long fileSizeBytes
) {}
