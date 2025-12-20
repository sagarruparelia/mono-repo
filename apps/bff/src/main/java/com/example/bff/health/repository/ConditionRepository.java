package com.example.bff.health.repository;

import com.example.bff.health.model.ConditionEntity;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

/**
 * Reactive MongoDB repository for cached health condition records.
 */
public interface ConditionRepository extends ReactiveMongoRepository<ConditionEntity, String> {

    /**
     * Find cached condition data for a member.
     * Returns the most recent cache entry if exists.
     */
    Mono<ConditionEntity> findByMemberEid(String memberEid);

    /**
     * Delete cached condition data for a member.
     * Used for cache eviction/refresh.
     */
    Mono<Void> deleteByMemberEid(String memberEid);
}
