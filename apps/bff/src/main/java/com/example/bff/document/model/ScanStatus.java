package com.example.bff.document.model;

/**
 * Status of malware scanning for a document.
 */
public enum ScanStatus {
    /**
     * Document has not been scanned yet.
     */
    NOT_SCANNED,

    /**
     * Malware scan is in progress.
     */
    SCANNING,

    /**
     * Document passed malware scan - no threats detected.
     */
    CLEAN,

    /**
     * Document failed malware scan - malware detected.
     */
    INFECTED
}
