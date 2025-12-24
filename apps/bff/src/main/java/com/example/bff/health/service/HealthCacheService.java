package com.example.bff.health.service;

import com.example.bff.config.BffProperties;
import com.example.bff.health.document.CacheStatus;
import com.example.bff.health.document.HealthCacheDocument;
import com.example.bff.health.model.HealthResourceType;
import com.example.bff.health.repository.HealthCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCacheService {

    private final HealthCacheRepository cacheRepository;
    private final BffProperties properties;

    public Mono<HealthCacheDocument> getCache(HealthResourceType resourceType, String enterpriseId) {
        String cacheKey = resourceType.getCacheKey(enterpriseId);
        return cacheRepository.findById(cacheKey)
                .filter(doc -> doc.getStatus() == CacheStatus.COMPLETE)
                .doOnNext(doc -> log.debug("Cache hit for {}", cacheKey))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Cache miss for {}", cacheKey);
                    return Mono.empty();
                }));
    }

    public Mono<Boolean> cacheExists(HealthResourceType resourceType, String enterpriseId) {
        String cacheKey = resourceType.getCacheKey(enterpriseId);
        return cacheRepository.existsById(cacheKey);
    }

    // BUILDING status prevents duplicate builds
    public Mono<HealthCacheDocument> markCacheBuilding(HealthResourceType resourceType, String enterpriseId) {
        String cacheKey = resourceType.getCacheKey(enterpriseId);
        HealthCacheDocument doc = HealthCacheDocument.builder()
                .id(cacheKey)
                .resourceType(resourceType)
                .enterpriseId(enterpriseId)
                .data(List.of())
                .totalRecords(0)
                .status(CacheStatus.BUILDING)
                .createdAt(Instant.now())
                .build();

        return cacheRepository.save(doc)
                .doOnSuccess(d -> log.debug("Marked cache as BUILDING: {}", cacheKey));
    }

    public Mono<HealthCacheDocument> storeCache(
            HealthResourceType resourceType,
            String enterpriseId,
            List<Object> data) {

        String cacheKey = resourceType.getCacheKey(enterpriseId);
        HealthCacheDocument doc = HealthCacheDocument.builder()
                .id(cacheKey)
                .resourceType(resourceType)
                .enterpriseId(enterpriseId)
                .data(data)
                .totalRecords(data.size())
                .status(CacheStatus.COMPLETE)
                .createdAt(Instant.now())
                .build();

        return cacheRepository.save(doc)
                .doOnSuccess(d -> log.info("Cached {} records for {}", data.size(), cacheKey));
    }

    public Mono<HealthCacheDocument> markCacheFailed(
            HealthResourceType resourceType,
            String enterpriseId,
            String errorMessage) {

        String cacheKey = resourceType.getCacheKey(enterpriseId);
        HealthCacheDocument doc = HealthCacheDocument.builder()
                .id(cacheKey)
                .resourceType(resourceType)
                .enterpriseId(enterpriseId)
                .data(List.of())
                .totalRecords(0)
                .status(CacheStatus.FAILED)
                .errorMessage(errorMessage)
                .createdAt(Instant.now())
                .build();

        return cacheRepository.save(doc)
                .doOnSuccess(d -> log.warn("Marked cache as FAILED: {} - {}", cacheKey, errorMessage));
    }

    // Called after session creation for browser flow
    public void triggerBackgroundCacheBuild(
            String enterpriseId,
            java.util.function.BiFunction<HealthResourceType, String, Mono<Void>> cacheBuildFunction) {

        if (!properties.getHealthCache().isBackgroundBuildEnabled()) {
            log.debug("Background cache build disabled");
            return;
        }

        log.info("Triggering background cache build for enterpriseId: {}", enterpriseId);

        for (HealthResourceType resourceType : HealthResourceType.values()) {
            cacheExists(resourceType, enterpriseId)
                    .filter(exists -> !exists)
                    .flatMap(notExists -> {
                        log.debug("Starting background build for {} : {}", resourceType, enterpriseId);
                        return cacheBuildFunction.apply(resourceType, enterpriseId);
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            v -> {},
                            error -> log.error("Background cache build failed for {} : {}",
                                    resourceType, enterpriseId, error)
                    );
        }
    }

    public Mono<Void> evictCache(HealthResourceType resourceType, String enterpriseId) {
        String cacheKey = resourceType.getCacheKey(enterpriseId);
        return cacheRepository.deleteById(cacheKey)
                .doOnSuccess(v -> log.debug("Evicted cache: {}", cacheKey));
    }

    public Mono<Void> evictAllCaches(String enterpriseId) {
        return Mono.when(
                java.util.Arrays.stream(HealthResourceType.values())
                        .map(type -> evictCache(type, enterpriseId))
                        .toList()
        );
    }
}
