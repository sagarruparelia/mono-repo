package com.example.bff.client.ecdh.dto;

public record PageInfoDto(
        int pageNum,
        int pageSize,
        int totalPages,
        int totalRecords,
        String continuationToken
) {}
