package com.example.bff.health.document;

public enum CacheStatus {
    BUILDING,  // Background fetch in progress
    COMPLETE,  // Ready for use
    FAILED
}
