package com.example.bff.document.dto;

import com.example.bff.document.model.DocumentEntity;

import java.time.Instant;

/**
 * Document DTO for API responses.
 */
public record DocumentDto(
        String id,
        String memberId,
        String fileName,
        String contentType,
        long fileSize,
        String description,
        String documentType,
        String uploadedBy,
        String uploadedByPersona,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Create DTO from entity.
     */
    public static DocumentDto fromEntity(DocumentEntity entity) {
        return new DocumentDto(
                entity.id(),
                entity.memberId(),
                entity.originalFileName(),
                entity.contentType(),
                entity.fileSize(),
                entity.description(),
                entity.documentType().name(),
                entity.uploadedBy(),
                entity.uploadedByPersona(),
                entity.createdAt(),
                entity.updatedAt()
        );
    }
}
