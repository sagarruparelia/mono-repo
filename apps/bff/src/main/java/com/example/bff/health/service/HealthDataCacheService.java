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
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** Service for MongoDB caching of health data with configurable TTL. */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthDataCacheService {

    private final ImmunizationRepository immunizationRepository;
    private final AllergyRepository allergyRepository;
    private final ConditionRepository conditionRepository;
    private final HealthDataCacheProperties cacheProperties;

    @NonNull
    public Mono<ImmunizationEntity> getOrLoadImmunizations(
            @NonNull String memberEid,
            @NonNull Mono<ImmunizationEntity> loader) {

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

    @NonNull
    public Mono<AllergyEntity> getOrLoadAllergies(
            @NonNull String memberEid,
            @NonNull Mono<AllergyEntity> loader) {

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

    @NonNull
    public Mono<ConditionEntity> getOrLoadConditions(
            @NonNull String memberEid,
            @NonNull Mono<ConditionEntity> loader) {

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

    @NonNull
    public Mono<Void> evictAllForMember(@NonNull String memberEid) {
        log.info("Evicting all health data cache for: {}", memberEid);
        return Mono.when(
                immunizationRepository.deleteByMemberEid(memberEid),
                allergyRepository.deleteByMemberEid(memberEid),
                conditionRepository.deleteByMemberEid(memberEid)
        ).doOnSuccess(v -> log.debug("Evicted health data cache for: {}", memberEid));
    }

    @NonNull
    public Mono<Void> evictImmunizations(@NonNull String memberEid) {
        return immunizationRepository.deleteByMemberEid(memberEid);
    }

    @NonNull
    public Mono<Void> evictAllergies(@NonNull String memberEid) {
        return allergyRepository.deleteByMemberEid(memberEid);
    }

    @NonNull
    public Mono<Void> evictConditions(@NonNull String memberEid) {
        return conditionRepository.deleteByMemberEid(memberEid);
    }

    @NonNull
    public Duration getTtl() {
        return cacheProperties.ttl();
    }

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
