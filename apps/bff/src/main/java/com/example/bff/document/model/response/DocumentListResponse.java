package com.example.bff.document.model.response;

import java.time.Instant;
import java.util.List;

public record DocumentListResponse(
        List<DocumentSummary> documents,
        int page,
        int size,
        int totalRecords,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    public record DocumentSummary(
            String documentId,
            String filename,
            String category,
            String categoryDisplayName,
            String title,
            Long fileSizeBytes,
            String contentType,
            String status,
            String scanStatus,
            Instant createdAt
    ) {}
}
