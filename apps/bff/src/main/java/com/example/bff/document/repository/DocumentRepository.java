package com.example.bff.document.repository;

import com.example.bff.document.model.DocumentEntity;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive MongoDB repository for documents.
 */
public interface DocumentRepository extends ReactiveMongoRepository<DocumentEntity, String> {

    /**
     * Find all documents for a member, ordered by creation date (newest first).
     */
    Flux<DocumentEntity> findByMemberIdOrderByCreatedAtDesc(String memberId);

    /**
     * Count documents for a member (for limit enforcement).
     */
    Mono<Long> countByMemberId(String memberId);

    /**
     * Find a specific document by ID and member ID.
     * This ensures the document belongs to the specified member.
     */
    Mono<DocumentEntity> findByIdAndMemberId(String id, String memberId);
}
