package com.example.bff.health.service;

import com.example.bff.config.properties.HealthDataCacheProperties;
import com.example.bff.health.model.AllergyEntity;
import com.example.bff.health.model.ConditionEntity;
import com.example.bff.health.model.ImmunizationEntity;
import com.example.bff.health.repository.AllergyRepository;
import com.example.bff.health.repository.ConditionRepository;
import com.example.bff.health.repository.ImmunizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Service for MongoDB caching of health data.
 * Implements get-or-load pattern with configurable TTL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthDataCacheService {

    private final ImmunizationRepository immunizationRepository;
    private final AllergyRepository allergyRepository;
    private final ConditionRepository conditionRepository;
    private final HealthDataCacheProperties cacheProperties;

    /**
     * Get immunizations from cache, or fetch from loader if not cached/expired.
     */
    public Mono<ImmunizationEntity> getOrLoadImmunizations(
            String memberEid,
            Mono<ImmunizationEntity> loader) {

        if (!cacheProperties.enabled()) {
            log.debug("Cache disabled, fetching immunizations from API for: {}", memberEid);
            return loader;
        }

        return immunizationRepository.findByMemberEid(memberEid)
                .flatMap(cached -> {
                    if (cached.isExpired()) {
                        log.debug("Cache expired for immunizations: {}", memberEid);
                        return loadAndSave(memberEid, loader, immunizationRepository);
                    }
                    log.debug("Cache hit for immunizations: {}", memberEid);
                    return Mono.just(cached);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Cache miss for immunizations: {}", memberEid);
                    return loadAndSave(memberEid, loader, immunizationRepository);
                }));
    }

    /**
     * Get allergies from cache, or fetch from loader if not cached/expired.
     */
    public Mono<AllergyEntity> getOrLoadAllergies(
            String memberEid,
            Mono<AllergyEntity> loader) {

        if (!cacheProperties.enabled()) {
            log.debug("Cache disabled, fetching allergies from API for: {}", memberEid);
            return loader;
        }

        return allergyRepository.findByMemberEid(memberEid)
                .flatMap(cached -> {
                    if (cached.isExpired()) {
                        log.debug("Cache expired for allergies: {}", memberEid);
                        return loadAndSaveAllergies(memberEid, loader);
                    }
                    log.debug("Cache hit for allergies: {}", memberEid);
                    return Mono.just(cached);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Cache miss for allergies: {}", memberEid);
                    return loadAndSaveAllergies(memberEid, loader);
                }));
    }

    /**
     * Get conditions from cache, or fetch from loader if not cached/expired.
     */
    public Mono<ConditionEntity> getOrLoadConditions(
            String memberEid,
            Mono<ConditionEntity> loader) {

        if (!cacheProperties.enabled()) {
            log.debug("Cache disabled, fetching conditions from API for: {}", memberEid);
            return loader;
        }

        return conditionRepository.findByMemberEid(memberEid)
                .flatMap(cached -> {
                    if (cached.isExpired()) {
                        log.debug("Cache expired for conditions: {}", memberEid);
                        return loadAndSaveConditions(memberEid, loader);
                    }
                    log.debug("Cache hit for conditions: {}", memberEid);
                    return Mono.just(cached);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Cache miss for conditions: {}", memberEid);
                    return loadAndSaveConditions(memberEid, loader);
                }));
    }

    /**
     * Evict all health data cache for a member.
     */
    public Mono<Void> evictAllForMember(String memberEid) {
        log.info("Evicting all health data cache for: {}", memberEid);
        return Mono.when(
                immunizationRepository.deleteByMemberEid(memberEid),
                allergyRepository.deleteByMemberEid(memberEid),
                conditionRepository.deleteByMemberEid(memberEid)
        ).doOnSuccess(v -> log.debug("Evicted health data cache for: {}", memberEid));
    }

    /**
     * Evict immunizations cache for a member.
     */
    public Mono<Void> evictImmunizations(String memberEid) {
        return immunizationRepository.deleteByMemberEid(memberEid);
    }

    /**
     * Evict allergies cache for a member.
     */
    public Mono<Void> evictAllergies(String memberEid) {
        return allergyRepository.deleteByMemberEid(memberEid);
    }

    /**
     * Evict conditions cache for a member.
     */
    public Mono<Void> evictConditions(String memberEid) {
        return conditionRepository.deleteByMemberEid(memberEid);
    }

    /**
     * Get the configured TTL.
     */
    public Duration getTtl() {
        return cacheProperties.ttl();
    }

    // Private helper methods

    private Mono<ImmunizationEntity> loadAndSave(
            String memberEid,
            Mono<ImmunizationEntity> loader,
            ImmunizationRepository repository) {
        return loader
                .flatMap(entity -> repository.save(entity)
                        .doOnSuccess(saved -> log.debug(
                                "Cached {} immunization records for: {}",
                                saved.recordCount(), memberEid))
                        .onErrorResume(e -> {
                            log.warn("Failed to cache immunizations for {}: {}", memberEid, e.getMessage());
                            return Mono.just(entity);
                        }));
    }

    private Mono<AllergyEntity> loadAndSaveAllergies(
            String memberEid,
            Mono<AllergyEntity> loader) {
        return loader
                .flatMap(entity -> allergyRepository.save(entity)
                        .doOnSuccess(saved -> log.debug(
                                "Cached {} allergy records for: {}",
                                saved.recordCount(), memberEid))
                        .onErrorResume(e -> {
                            log.warn("Failed to cache allergies for {}: {}", memberEid, e.getMessage());
                            return Mono.just(entity);
                        }));
    }

    private Mono<ConditionEntity> loadAndSaveConditions(
            String memberEid,
            Mono<ConditionEntity> loader) {
        return loader
                .flatMap(entity -> conditionRepository.save(entity)
                        .doOnSuccess(saved -> log.debug(
                                "Cached {} condition records for: {}",
                                saved.recordCount(), memberEid))
                        .onErrorResume(e -> {
                            log.warn("Failed to cache conditions for {}: {}", memberEid, e.getMessage());
                            return Mono.just(entity);
                        }));
    }
}
