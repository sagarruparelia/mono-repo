package com.example.bff.health.model;

import java.util.List;

public record HealthDataResponse<T>(
        List<T> data,
        int page,
        int size,
        long totalRecords,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {}
