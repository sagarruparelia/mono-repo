package com.example.bff.health.service;

import com.example.bff.client.ecdh.EcdhGraphClient;
import com.example.bff.client.ecdh.dto.AllergyDto;
import com.example.bff.client.ecdh.dto.ImmunizationDto;
import com.example.bff.health.document.HealthCacheDocument;
import com.example.bff.health.model.Allergy;
import com.example.bff.health.model.HealthDataResponse;
import com.example.bff.health.model.HealthResourceType;
import com.example.bff.health.model.Immunization;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final HealthCacheService cacheService;
    private final EcdhGraphClient ecdhGraphClient;
    private final ObjectMapper objectMapper;

    public Mono<HealthDataResponse<Immunization>> getImmunizations(String enterpriseId, int page, int size) {
        return getHealthData(
                HealthResourceType.IMMUNIZATION,
                enterpriseId,
                page,
                normalizePageSize(size),
                this::mapToImmunization);
    }

    public Mono<HealthDataResponse<Allergy>> getAllergies(String enterpriseId, int page, int size) {
        return getHealthData(
                HealthResourceType.ALLERGY,
                enterpriseId,
                page,
                normalizePageSize(size),
                this::mapToAllergy);
    }

    public void triggerBackgroundCacheBuild(String enterpriseId) {
        cacheService.triggerBackgroundCacheBuild(enterpriseId, this::buildCacheForResource);
    }

    private Mono<Void> buildCacheForResource(HealthResourceType resourceType, String enterpriseId) {
        return cacheService.markCacheBuilding(resourceType, enterpriseId)
                .then(ecdhGraphClient.getAllRecords(resourceType, enterpriseId)
                        .collectList()
                        .flatMap(records -> cacheService.storeCache(resourceType, enterpriseId, records))
                        .then())
                .onErrorResume(error -> {
                    log.error("Cache build failed for {} : {}", resourceType, enterpriseId, error);
                    return cacheService.markCacheFailed(resourceType, enterpriseId, error.getMessage())
                            .then();
                });
    }

    private <T> Mono<HealthDataResponse<T>> getHealthData(
            HealthResourceType resourceType,
            String enterpriseId,
            int page,
            int size,
            java.util.function.Function<Object, T> mapper) {

        return cacheService.getCache(resourceType, enterpriseId)
                .map(cached -> buildResponseFromCache(cached, page, size, mapper))
                .switchIfEmpty(Mono.defer(() -> fetchFromEcdhAndTriggerCache(resourceType, enterpriseId, page, size, mapper)));
    }

    private <T> HealthDataResponse<T> buildResponseFromCache(
            HealthCacheDocument cached,
            int page,
            int size,
            java.util.function.Function<Object, T> mapper) {

        List<T> allData = cached.getData().stream()
                .map(mapper)
                .toList();

        int totalRecords = allData.size();
        int totalPages = (int) Math.ceil((double) totalRecords / size);
        int fromIndex = Math.min(page * size, totalRecords);
        int toIndex = Math.min(fromIndex + size, totalRecords);

        List<T> pageData = allData.subList(fromIndex, toIndex);

        return new HealthDataResponse<>(
                pageData,
                page,
                size,
                totalRecords,
                totalPages,
                page < totalPages - 1,
                page > 0);
    }

    private <T> Mono<HealthDataResponse<T>> fetchFromEcdhAndTriggerCache(
            HealthResourceType resourceType,
            String enterpriseId,
            int page,
            int size,
            java.util.function.Function<Object, T> mapper) {

        // Trigger background cache build (non-blocking)
        buildCacheForResource(resourceType, enterpriseId)
                .subscribe(
                        v -> {},
                        error -> log.warn("Background cache build failed for {} : {}",
                                resourceType, enterpriseId, error)
                );

        // Fetch directly from ECDH for this request
        return fetchPageFromEcdh(resourceType, enterpriseId, page, size, mapper);
    }

    private <T> Mono<HealthDataResponse<T>> fetchPageFromEcdh(
            HealthResourceType resourceType,
            String enterpriseId,
            int page,
            int size,
            java.util.function.Function<Object, T> mapper) {

        // ECDH uses 1-indexed pages
        int ecdhPage = page + 1;

        return switch (resourceType) {
            case IMMUNIZATION -> ecdhGraphClient.getImmunizations(enterpriseId, ecdhPage, size, null)
                    .map(response -> {
                        List<T> data = response.items().stream()
                                .map(mapper)
                                .toList();
                        var pageInfo = response.pageInfo();
                        return new HealthDataResponse<>(
                                data,
                                page,
                                size,
                                pageInfo.totalRecords(),
                                pageInfo.totalPages(),
                                response.hasNextPage(),
                                page > 0);
                    });
            case ALLERGY -> ecdhGraphClient.getAllergies(enterpriseId, ecdhPage, size, null)
                    .map(response -> {
                        List<T> data = response.items().stream()
                                .map(mapper)
                                .toList();
                        var pageInfo = response.pageInfo();
                        return new HealthDataResponse<>(
                                data,
                                page,
                                size,
                                pageInfo.totalRecords(),
                                pageInfo.totalPages(),
                                response.hasNextPage(),
                                page > 0);
                    });
        };
    }

    private int normalizePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private Immunization mapToImmunization(Object obj) {
        if (obj instanceof ImmunizationDto(
                String id, String vaccineCode, String vaccineName, String administrationDate, String provider,
                String lotNumber, String site, String status
        )) {
            return new Immunization(
                    id,
                    vaccineCode,
                    vaccineName,
                    parseDate(administrationDate),
                    provider,
                    lotNumber,
                    site,
                    status);
        }
        // Handle cached LinkedHashMap
        return objectMapper.convertValue(obj, Immunization.class);
    }

    private Allergy mapToAllergy(Object obj) {
        if (obj instanceof AllergyDto(
                String id, String allergenCode, String allergenName, String allergenType, String severity,
                String reaction, String onsetDate, String status
        )) {
            return new Allergy(
                    id,
                    allergenCode,
                    allergenName,
                    allergenType,
                    severity,
                    reaction,
                    parseDate(onsetDate),
                    status);
        }
        // Handle cached LinkedHashMap
        return objectMapper.convertValue(obj, Allergy.class);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.substring(0, 10)); // Handle ISO 8601 with time
        } catch (Exception ignored) {
            log.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }
}
