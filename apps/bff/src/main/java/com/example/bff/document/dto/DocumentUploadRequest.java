package com.example.bff.document.dto;

import com.example.bff.document.model.DocumentEntity.DocumentType;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for document upload metadata.
 */
public record DocumentUploadRequest(
        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        DocumentType documentType
) {
    public DocumentUploadRequest {
        if (documentType == null) {
            documentType = DocumentType.OTHER;
        }
    }

    /**
     * Create a default request.
     */
    public static DocumentUploadRequest withDefaults() {
        return new DocumentUploadRequest(null, DocumentType.OTHER);
    }
}
