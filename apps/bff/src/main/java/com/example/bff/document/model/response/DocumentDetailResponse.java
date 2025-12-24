package com.example.bff.document.model.response;

import java.time.Instant;
import java.util.List;

public record DocumentDetailResponse(
        String documentId,
        String ownerEnterpriseId,
        String filename,
        String contentType,
        Long fileSizeBytes,
        String category,
        String categoryDisplayName,
        String title,
        String description,
        List<String> tags,
        String status,
        String scanStatus,
        UploaderInfo uploader,
        Instant createdAt,
        Instant updatedAt
) {
    public record UploaderInfo(
            String uploaderId,
            String uploaderIdType,
            String uploaderPersona
    ) {}
}
