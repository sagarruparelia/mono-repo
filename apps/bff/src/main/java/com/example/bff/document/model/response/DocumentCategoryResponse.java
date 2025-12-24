package com.example.bff.document.model.response;

import java.util.List;

public record DocumentCategoryResponse(
        List<CategoryInfo> categories
) {
    public record CategoryInfo(
            String id,
            String displayName,
            String description
    ) {}
}
