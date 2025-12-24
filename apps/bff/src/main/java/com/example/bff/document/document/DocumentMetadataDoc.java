package com.example.bff.document.document;

import com.example.bff.document.model.DocumentStatus;
import com.example.bff.document.model.ScanStatus;
import com.example.bff.security.context.MemberIdType;
import com.example.bff.security.context.Persona;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * MongoDB document for permanent document metadata.
 * Files are stored in S3; this stores metadata and AI-extracted information.
 */
@Data
@Builder
@Document(collection = "documents")
@CompoundIndexes({
        @CompoundIndex(name = "owner_status_idx", def = "{'ownerEnterpriseId': 1, 'status': 1}"),
        @CompoundIndex(name = "owner_category_idx", def = "{'ownerEnterpriseId': 1, 'category': 1}")
})
public class DocumentMetadataDoc {

    @Id
    private String id;

    /**
     * The enterprise ID of the document owner (always the member, not the uploader).
     */
    @Indexed
    private String ownerEnterpriseId;

    /**
     * The ID value of who uploaded the document.
     * May differ from owner for DELEGATE, AGENT, CASE_WORKER uploads.
     */
    private String uploaderId;

    /**
     * The type of ID used by the uploader (HSID, MSID, OHID).
     */
    private MemberIdType uploaderIdType;

    /**
     * The persona of the uploader at time of upload.
     */
    private Persona uploaderPersona;

    /**
     * S3 bucket where the document is stored.
     */
    private String s3Bucket;

    /**
     * S3 object key (path) to the document.
     */
    private String s3Key;

    /**
     * Original filename as provided by the user.
     */
    private String originalFilename;

    /**
     * MIME type of the document.
     */
    private String contentType;

    /**
     * File size in bytes.
     */
    private Long fileSizeBytes;

    /**
     * Document category ID (references document_categories collection).
     */
    private String category;

    /**
     * User-provided title for the document.
     */
    private String title;

    /**
     * User-provided description.
     */
    private String description;

    /**
     * Tags for categorization and search.
     */
    private List<String> tags;

    /**
     * Current status of the document.
     */
    private DocumentStatus status;

    /**
     * Malware scan status.
     */
    private ScanStatus scanStatus;

    @CreatedDate
    @Indexed
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // AI/ML fields for future phases

    /**
     * AI processing status: PENDING, PROCESSING, COMPLETE, FAILED.
     */
    private String aiProcessingStatus;

    /**
     * Metadata extracted by AI (dates, names, codes, etc.).
     */
    private Map<String, Object> extractedMetadata;

    /**
     * Vector embedding for semantic search (1536 dimensions for ada-002).
     */
    private List<Double> vectorEmbedding;

    /**
     * OCR-extracted text from the document.
     */
    private String ocrText;

    /**
     * Timestamp when AI processing completed.
     */
    private Instant aiProcessedAt;
}
