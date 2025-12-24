package com.example.bff.document.repository;

import com.example.bff.document.document.TempUploadDoc;
import com.example.bff.document.model.UploadStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository for temporary upload tracking.
 */
@Repository
public interface TempUploadRepository extends ReactiveMongoRepository<TempUploadDoc, String> {

    /**
     * Count pending uploads for a user (for rate limiting).
     */
    Mono<Long> countByUploaderIdAndUploadStatus(String uploaderId, UploadStatus uploadStatus);

    /**
     * Find pending uploads for a user.
     */
    Flux<TempUploadDoc> findByUploaderIdAndUploadStatus(String uploaderId, UploadStatus uploadStatus);

    /**
     * Find upload by S3 key.
     */
    Mono<TempUploadDoc> findByS3Key(String s3Key);
}
