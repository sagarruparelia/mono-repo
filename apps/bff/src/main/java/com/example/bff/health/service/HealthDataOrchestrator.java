package com.example.bff.health.service;

import com.example.bff.config.properties.HealthDataCacheProperties;
import com.example.bff.health.model.AllergyEntity;
import com.example.bff.health.model.ConditionEntity;
import com.example.bff.health.model.ImmunizationEntity;
import com.example.bff.identity.model.ManagedMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/** Orchestrates health data operations including cache-first fetching and proactive background loading. */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthDataOrchestrator {

    private static final int MAX_CONCURRENT_FETCHES = 3;

    private final EcdhApiClientService ecdhApiClient;
    private final HealthDataCacheService cacheService;
    private final HealthDataCacheProperties cacheProperties;

    @NonNull
    public Mono<ImmunizationEntity> getImmunizations(@NonNull String memberEid, @Nullable String apiIdentifier) {
        Mono<ImmunizationEntity> loader = ecdhApiClient.fetchImmunizations(memberEid, apiIdentifier)
                .map(records -> ImmunizationEntity.create(memberEid, records, cacheService.getTtl()));

        return cacheService.getOrLoadImmunizations(memberEid, loader);
    }

    @NonNull
    public Mono<AllergyEntity> getAllergies(@NonNull String memberEid, @Nullable String apiIdentifier) {
        Mono<AllergyEntity> loader = ecdhApiClient.fetchAllergies(memberEid, apiIdentifier)
                .map(records -> AllergyEntity.create(memberEid, records, cacheService.getTtl()));

        return cacheService.getOrLoadAllergies(memberEid, loader);
    }

    @NonNull
    public Mono<ConditionEntity> getConditions(@NonNull String memberEid, @Nullable String apiIdentifier) {
        Mono<ConditionEntity> loader = ecdhApiClient.fetchConditions(memberEid, apiIdentifier)
                .map(records -> ConditionEntity.create(memberEid, records, cacheService.getTtl()));

        return cacheService.getOrLoadConditions(memberEid, loader);
    }

    @NonNull
    public Mono<Void> refreshAllHealthData(@NonNull String memberEid, @Nullable String apiIdentifier) {
        log.info("Refreshing all health data for member: {}", memberEid);

        return cacheService.evictAllForMember(memberEid)
                .then(fetchAllHealthDataForMember(memberEid, apiIdentifier))
                .doOnSuccess(v -> log.info("Health data refresh completed for: {}", memberEid));
    }

    /**
     * Proactively fetch health data for the logged-in user and their managed members.
     * Called after HSID session creation (fire-and-forget).
     */
    public void triggerBackgroundFetchForSession(
            @NonNull String userEid,
            @Nullable String apiIdentifier,
            @Nullable List<ManagedMember> managedMembers) {

        if (!cacheProperties.proactiveFetch().enabled()) {
            log.debug("Proactive fetch disabled, skipping for session");
            return;
        }

        List<String> eidsToFetch = new ArrayList<>();
        eidsToFetch.add(userEid);

        if (managedMembers != null) {
            managedMembers.stream()
                    .filter(ManagedMember::isActive)
                    .map(ManagedMember::eid)
                    .forEach(eidsToFetch::add);
        }

        log.info("Triggering background health data fetch for {} member(s)", eidsToFetch.size());

        Flux.fromIterable(eidsToFetch)
                .delayElements(cacheProperties.proactiveFetch().delayAfterLogin())
                .flatMap(eid -> fetchAllHealthDataForMember(eid, apiIdentifier)
                                .onErrorResume(e -> {
                                    log.warn("Background fetch failed for eid={}: {}", eid, e.getMessage());
                                    return Mono.empty();
                                }),
                        MAX_CONCURRENT_FETCHES)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        result -> log.debug("Background fetch completed for a member"),
                        error -> log.error("Background fetch error: {}", error.getMessage()),
                        () -> log.info("Background health data fetch completed for {} member(s)", eidsToFetch.size())
                );
    }

    /**
     * Proactively fetch health data for a single member.
     * Called after OAuth2 proxy connection (fire-and-forget).
     */
    public void triggerBackgroundFetchForMember(@NonNull String memberEid, @Nullable String apiIdentifier) {
        if (!cacheProperties.proactiveFetch().enabled()) {
            log.debug("Proactive fetch disabled, skipping for member: {}", memberEid);
            return;
        }

        log.info("Triggering background health data fetch for member: {}", memberEid);

        fetchAllHealthDataForMember(memberEid, apiIdentifier)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        result -> log.debug("Background fetch completed for member: {}", memberEid),
                        error -> log.warn("Background fetch failed for member {}: {}",
                                memberEid, error.getMessage())
                );
    }

    private Mono<Void> fetchAllHealthDataForMember(String memberEid, String apiIdentifier) {
        return Mono.when(
                getImmunizations(memberEid, apiIdentifier),
                getAllergies(memberEid, apiIdentifier),
                getConditions(memberEid, apiIdentifier)
        ).doOnSuccess(v -> log.debug("All health data fetched for member: {}", memberEid));
    }
}
