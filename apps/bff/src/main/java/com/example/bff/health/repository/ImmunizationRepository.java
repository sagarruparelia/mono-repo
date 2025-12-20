package com.example.bff.health.repository;

import com.example.bff.health.model.ImmunizationEntity;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

/**
 * Reactive MongoDB repository for cached immunization records.
 */
public interface ImmunizationRepository extends ReactiveMongoRepository<ImmunizationEntity, String> {

    /**
     * Find cached immunization data for a member.
     * Returns the most recent cache entry if exists.
     */
    Mono<ImmunizationEntity> findByMemberEid(String memberEid);

    /**
     * Delete cached immunization data for a member.
     * Used for cache eviction/refresh.
     */
    Mono<Void> deleteByMemberEid(String memberEid);
}
