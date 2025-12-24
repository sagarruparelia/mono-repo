package com.example.bff.document.model;

/**
 * Status of a temporary upload in progress.
 */
public enum UploadStatus {
    /**
     * Presigned URL generated, waiting for file upload to S3.
     */
    PENDING,

    /**
     * File has been uploaded to S3 temp location.
     */
    UPLOADED,

    /**
     * Upload has been finalized - file moved to permanent storage.
     */
    FINALIZED,

    /**
     * Temporary upload expired before finalization.
     */
    EXPIRED,

    /**
     * Upload or finalization failed.
     */
    FAILED
}
