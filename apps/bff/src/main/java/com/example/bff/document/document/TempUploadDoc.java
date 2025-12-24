package com.example.bff.document.document;

import com.example.bff.document.model.UploadStatus;
import com.example.bff.security.context.MemberIdType;
import com.example.bff.security.context.Persona;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document for tracking temporary uploads.
 * TTL index automatically removes expired entries after 4 hours.
 */
@Data
@Builder
@Document(collection = "temp_uploads")
@CompoundIndex(name = "uploader_status_idx", def = "{'uploaderId': 1, 'uploadStatus': 1}")
public class TempUploadDoc {

    /**
     * Upload ID (UUID), used as the document ID.
     */
    @Id
    private String id;

    /**
     * S3 object key for the temporary file.
     */
    private String s3Key;

    /**
     * The ID value of who initiated the upload.
     */
    private String uploaderId;

    /**
     * The type of ID used by the uploader.
     */
    private MemberIdType uploaderIdType;

    /**
     * The persona of the uploader.
     */
    private Persona uploaderPersona;

    /**
     * The enterprise ID who will own the document when finalized.
     */
    private String targetOwnerEid;

    /**
     * Original filename as provided by the user.
     */
    private String originalFilename;

    /**
     * Expected MIME type of the upload.
     */
    private String contentType;

    /**
     * Expected file size in bytes.
     */
    private Long expectedFileSizeBytes;

    /**
     * Current status of the upload.
     */
    private UploadStatus uploadStatus;

    /**
     * When the upload was initiated.
     */
    @CreatedDate
    private Instant createdAt;

    /**
     * When the presigned URL expires.
     */
    private Instant presignedUrlExpiresAt;

    /**
     * When this temp upload record expires.
     * TTL index will automatically delete after this time.
     */
    @Indexed(expireAfter = "0s")
    private Instant expiresAt;
}
