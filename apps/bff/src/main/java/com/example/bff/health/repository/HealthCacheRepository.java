package com.example.bff.health.repository;

import com.example.bff.health.document.HealthCacheDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

// ID format: "{resourceType}:{enterpriseId}"
@Repository
public interface HealthCacheRepository extends ReactiveMongoRepository<HealthCacheDocument, String> {
}
