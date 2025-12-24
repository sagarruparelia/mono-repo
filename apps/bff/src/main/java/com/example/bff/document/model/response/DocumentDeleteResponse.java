package com.example.bff.document.model.response;

import java.time.Instant;

public record DocumentDeleteResponse(
        boolean success,
        String documentId,
        Instant deletedAt
) {}
