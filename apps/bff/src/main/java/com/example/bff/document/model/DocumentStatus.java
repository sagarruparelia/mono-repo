package com.example.bff.document.model;

/**
 * Status of a document in the system.
 */
public enum DocumentStatus {
    /**
     * Document is active and available for access.
     */
    ACTIVE,

    /**
     * Document has been soft-deleted by user.
     * Can be recovered if needed.
     */
    DELETED,

    /**
     * Document is awaiting malware scan.
     * Downloads are blocked until scan completes.
     */
    PENDING_SCAN,

    /**
     * Document failed malware scan and is quarantined.
     * Downloads are permanently blocked.
     */
    QUARANTINED
}
