package com.example.bff.health.service;

import com.example.bff.config.properties.HealthDataCacheProperties;
import com.example.bff.health.model.AllergyEntity;
import com.example.bff.health.model.ConditionEntity;
import com.example.bff.health.model.ImmunizationEntity;
import com.example.bff.identity.model.ManagedMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates health data operations including cache-first fetching
 * and proactive background loading.
 */
@Service
public class HealthDataOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(HealthDataOrchestrator.class);
    private static final int MAX_CONCURRENT_FETCHES = 3;

    private final EcdhApiClientService ecdhApiClient;
    private final HealthDataCacheService cacheService;
    private final HealthDataCacheProperties cacheProperties;

    public HealthDataOrchestrator(
            EcdhApiClientService ecdhApiClient,
            HealthDataCacheService cacheService,
            HealthDataCacheProperties cacheProperties) {
        this.ecdhApiClient = ecdhApiClient;
        this.cacheService = cacheService;
        this.cacheProperties = cacheProperties;
    }

    // ========== Synchronous Fetch (for API requests) ==========

    /**
     * Get immunizations for a member (cache-first, fallback to ECDH).
     */
    @NonNull
    public Mono<ImmunizationEntity> getImmunizations(
            @NonNull String memberEid,
            @Nullable String apiIdentifier) {

        Mono<ImmunizationEntity> loader = ecdhApiClient.fetchImmunizations(memberEid, apiIdentifier)
                .map(records -> ImmunizationEntity.create(memberEid, records, cacheService.getTtl()));

        return cacheService.getOrLoadImmunizations(memberEid, loader);
    }

    /**
     * Get allergies for a member (cache-first, fallback to ECDH).
     */
    @NonNull
    public Mono<AllergyEntity> getAllergies(
            @NonNull String memberEid,
            @Nullable String apiIdentifier) {

        Mono<AllergyEntity> loader = ecdhApiClient.fetchAllergies(memberEid, apiIdentifier)
                .map(records -> AllergyEntity.create(memberEid, records, cacheService.getTtl()));

        return cacheService.getOrLoadAllergies(memberEid, loader);
    }

    /**
     * Get conditions for a member (cache-first, fallback to ECDH).
     */
    @NonNull
    public Mono<ConditionEntity> getConditions(
            @NonNull String memberEid,
            @Nullable String apiIdentifier) {

        Mono<ConditionEntity> loader = ecdhApiClient.fetchConditions(memberEid, apiIdentifier)
                .map(records -> ConditionEntity.create(memberEid, records, cacheService.getTtl()));

        return cacheService.getOrLoadConditions(memberEid, loader);
    }

    /**
     * Force refresh all health data for a member (evict cache and re-fetch).
     */
    @NonNull
    public Mono<Void> refreshAllHealthData(
            @NonNull String memberEid,
            @Nullable String apiIdentifier) {

        LOG.info("Refreshing all health data for member: {}", memberEid);

        return cacheService.evictAllForMember(memberEid)
                .then(fetchAllHealthDataForMember(memberEid, apiIdentifier))
                .doOnSuccess(v -> LOG.info("Health data refresh completed for: {}", memberEid));
    }

    // ========== Background/Proactive Fetch ==========

    /**
     * Proactively fetch health data for the logged-in user and their managed members.
     * Called after HSID session creation (fire-and-forget).
     *
     * @param userEid        User's Enterprise ID
     * @param apiIdentifier  API identifier header value
     * @param managedMembers List of members the user manages (for parents)
     */
    public void triggerBackgroundFetchForSession(
            @NonNull String userEid,
            @Nullable String apiIdentifier,
            @Nullable List<ManagedMember> managedMembers) {

        if (!cacheProperties.proactiveFetch().enabled()) {
            LOG.debug("Proactive fetch disabled, skipping for session");
            return;
        }

        // Collect all EIDs to fetch (user + managed members)
        List<String> eidsToFetch = new ArrayList<>();
        eidsToFetch.add(userEid);

        if (managedMembers != null) {
            managedMembers.stream()
                    .filter(ManagedMember::isActive)
                    .map(ManagedMember::eid)
                    .forEach(eidsToFetch::add);
        }

        LOG.info("Triggering background health data fetch for {} member(s)", eidsToFetch.size());

        // Fire-and-forget with bounded parallelism
        Flux.fromIterable(eidsToFetch)
                .delayElements(cacheProperties.proactiveFetch().delayAfterLogin())
                .flatMap(eid -> fetchAllHealthDataForMember(eid, apiIdentifier)
                                .onErrorResume(e -> {
                                    LOG.warn("Background fetch failed for eid={}: {}", eid, e.getMessage());
                                    return Mono.empty();
                                }),
                        MAX_CONCURRENT_FETCHES)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        result -> LOG.debug("Background fetch completed for a member"),
                        error -> LOG.error("Background fetch error: {}", error.getMessage()),
                        () -> LOG.info("Background health data fetch completed for {} member(s)", eidsToFetch.size())
                );
    }

    /**
     * Proactively fetch health data for a single member.
     * Called after OAuth2 proxy connection (fire-and-forget).
     *
     * @param memberEid     Member's Enterprise ID (from X-Member-Id header)
     * @param apiIdentifier API identifier
     */
    public void triggerBackgroundFetchForMember(
            @NonNull String memberEid,
            @Nullable String apiIdentifier) {

        if (!cacheProperties.proactiveFetch().enabled()) {
            LOG.debug("Proactive fetch disabled, skipping for member: {}", memberEid);
            return;
        }

        LOG.info("Triggering background health data fetch for member: {}", memberEid);

        fetchAllHealthDataForMember(memberEid, apiIdentifier)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        result -> LOG.debug("Background fetch completed for member: {}", memberEid),
                        error -> LOG.warn("Background fetch failed for member {}: {}",
                                memberEid, error.getMessage())
                );
    }

    /**
     * Fetch all health data types for a member in parallel.
     */
    private Mono<Void> fetchAllHealthDataForMember(
            @NonNull String memberEid,
            @Nullable String apiIdentifier) {

        return Mono.when(
                getImmunizations(memberEid, apiIdentifier),
                getAllergies(memberEid, apiIdentifier),
                getConditions(memberEid, apiIdentifier)
        ).doOnSuccess(v -> LOG.debug("All health data fetched for member: {}", memberEid));
    }
}
