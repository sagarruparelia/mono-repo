package com.example.bff.document.repository;

import com.example.bff.document.document.DocumentCategoryDoc;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Repository for document categories.
 */
@Repository
public interface DocumentCategoryRepository extends ReactiveMongoRepository<DocumentCategoryDoc, String> {

    /**
     * Find all active categories ordered by sort order.
     */
    Flux<DocumentCategoryDoc> findByActiveTrueOrderBySortOrderAsc();
}
