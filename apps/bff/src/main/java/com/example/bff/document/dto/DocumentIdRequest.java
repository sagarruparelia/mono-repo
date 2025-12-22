package com.example.bff.document.dto;

import jakarta.validation.constraints.NotBlank;

public record DocumentIdRequest(
        @NotBlank String documentId
) {}
