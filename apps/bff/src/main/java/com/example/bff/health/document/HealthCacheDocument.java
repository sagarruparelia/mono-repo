package com.example.bff.health.document;

import com.example.bff.health.model.HealthResourceType;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

// ID format: "{resourceType}:{enterpriseId}". Future: vector embeddings for AI agent queries.
@Data
@Builder
@Document(collection = "health_cache")
public class HealthCacheDocument {

    @Id
    private String id;  // "{resourceType}:{enterpriseId}"

    private HealthResourceType resourceType;
    private String enterpriseId;
    private List<Object> data;  // Generic to support any health resource type
    private int totalRecords;
    private CacheStatus status;
    private String errorMessage;

    @CreatedDate
    @Indexed(expireAfter = "2h")
    private Instant createdAt;
}
