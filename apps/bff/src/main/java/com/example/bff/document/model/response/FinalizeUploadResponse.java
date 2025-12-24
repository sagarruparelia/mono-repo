package com.example.bff.document.model.response;

import java.time.Instant;

public record FinalizeUploadResponse(
        String documentId,
        String filename,
        String category,
        String status,
        Instant createdAt
) {}
