package com.example.bff.document.repository;

import com.example.bff.document.document.DocumentMetadataDoc;
import com.example.bff.document.model.DocumentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository for document metadata operations.
 */
@Repository
public interface DocumentMetadataRepository extends ReactiveMongoRepository<DocumentMetadataDoc, String> {

    /**
     * Find documents by owner and status with pagination.
     */
    Flux<DocumentMetadataDoc> findByOwnerEnterpriseIdAndStatus(
            String ownerEnterpriseId,
            DocumentStatus status,
            Pageable pageable);

    /**
     * Find documents by owner, status, and category with pagination.
     */
    Flux<DocumentMetadataDoc> findByOwnerEnterpriseIdAndStatusAndCategory(
            String ownerEnterpriseId,
            DocumentStatus status,
            String category,
            Pageable pageable);

    /**
     * Count documents by owner and status.
     */
    Mono<Long> countByOwnerEnterpriseIdAndStatus(
            String ownerEnterpriseId,
            DocumentStatus status);

    /**
     * Count documents by owner, status, and category.
     */
    Mono<Long> countByOwnerEnterpriseIdAndStatusAndCategory(
            String ownerEnterpriseId,
            DocumentStatus status,
            String category);

    /**
     * Find document by S3 key (for GuardDuty scan result updates).
     */
    Mono<DocumentMetadataDoc> findByS3Key(String s3Key);
}
