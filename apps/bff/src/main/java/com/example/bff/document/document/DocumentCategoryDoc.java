package com.example.bff.document.document;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document for document categories.
 * Categories are managed by business team and stored in database
 * for flexibility without code deployments.
 */
@Data
@Builder
@Document(collection = "document_categories")
public class DocumentCategoryDoc {

    /**
     * Category ID (e.g., "PRESCRIPTION", "INSURANCE_CARD").
     * Used as reference in DocumentMetadataDoc.
     */
    @Id
    private String id;

    /**
     * Display name shown to users (e.g., "Medicine Prescription").
     */
    private String displayName;

    /**
     * Description of what documents belong in this category.
     */
    private String description;

    /**
     * Whether this category is active and available for selection.
     */
    private boolean active;

    /**
     * Sort order for displaying categories in UI.
     */
    private int sortOrder;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
