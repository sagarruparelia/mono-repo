package com.example.bff.health.repository;

import com.example.bff.health.model.AllergyEntity;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

/**
 * Reactive MongoDB repository for cached allergy records.
 */
public interface AllergyRepository extends ReactiveMongoRepository<AllergyEntity, String> {

    /**
     * Find cached allergy data for a member.
     * Returns the most recent cache entry if exists.
     */
    Mono<AllergyEntity> findByMemberEid(String memberEid);

    /**
     * Delete cached allergy data for a member.
     * Used for cache eviction/refresh.
     */
    Mono<Void> deleteByMemberEid(String memberEid);
}
