package com.example.bff.document.model.request;

import jakarta.validation.constraints.NotBlank;

public record DocumentDownloadRequest(
        String enterpriseId,

        @NotBlank(message = "Document ID is required")
        String documentId
) {}
