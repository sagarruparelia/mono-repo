package com.example.bff.client.ecdh;

import com.example.bff.client.ecdh.dto.PageInfoDto;

import java.util.List;

public record EcdhPagedResponse<T>(
        PageInfoDto pageInfo,
        List<T> items
) {
    public boolean hasNextPage() {
        return pageInfo != null &&
                pageInfo.continuationToken() != null &&
                !pageInfo.continuationToken().isEmpty();
    }
}
