package com.example.bff.client.eligibility;

import com.example.bff.client.WebClientFactory;
import com.example.bff.client.cache.ClientCache;
import com.example.bff.config.BffProperties;
import com.example.bff.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class EligibilityGraphClient {

    private static final String CACHE_KEY_PREFIX = "eligibility:";
    private static final int GRACE_PERIOD_MONTHS = 18;

    private final WebClient webClient;
    private final ClientCache<List<com.example.bff.security.session.EligibilityPlan>> cache;
    private final Set<String> eligiblePlanCodes;

    public EligibilityGraphClient(
            WebClientFactory webClientFactory,
            ClientCache<List<com.example.bff.security.session.EligibilityPlan>> cache,
            BffProperties properties) {
        this.webClient = webClientFactory.eligibilityGraphClient();
        this.cache = cache;
        this.eligiblePlanCodes = Set.copyOf(properties.getEligibility().getEligiblePlanCodes());
    }

    // Filters by eligible plan codes and 18-month grace period. Sorted by start date (most recent first)
    public Flux<com.example.bff.security.session.EligibilityPlan> getEligiblePlans(String apiIdentifier) {
        String cacheKey = CACHE_KEY_PREFIX + apiIdentifier;

        return cache.getOrCompute(cacheKey, fetchEligibilityFromApi(apiIdentifier))
                .flatMapMany(Flux::fromIterable);
    }

    private Mono<List<com.example.bff.security.session.EligibilityPlan>> fetchEligibilityFromApi(String apiIdentifier) {
        log.debug("Fetching eligibility for apiIdentifier: {}", apiIdentifier);

        String query = """
                query GetEligibility {
                    eligibility {
                        plans {
                            planCode
                            memberId
                            startDate
                            termDate
                        }
                    }
                }
                """;

        Map<String, Object> request = Map.of("query", query);

        return webClient.post()
                .header("x-identifier", apiIdentifier)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new ApiException(
                                        "EligibilityGraph",
                                        response.statusCode(),
                                        "Eligibility graph API error",
                                        body))))
                .bodyToMono(EligibilityGraphResponse.class)
                .map(this::processResponse)
                .doOnNext(plans -> log.debug("Retrieved {} eligible plans for apiIdentifier: {}",
                        plans.size(), apiIdentifier))
                .doOnError(e -> {
                    if (!(e instanceof ApiException)) {
                        log.error("Failed to fetch eligibility for apiIdentifier: {}", apiIdentifier, e);
                    }
                });
    }

    private List<com.example.bff.security.session.EligibilityPlan> processResponse(EligibilityGraphResponse response) {
        if (response.getData() == null ||
                response.getData().getEligibility() == null ||
                response.getData().getEligibility().getPlans() == null) {
            return List.of();
        }

        LocalDate today = LocalDate.now();

        return response.getData().getEligibility().getPlans().stream()
                // Filter by eligible plan codes (if configured)
                .filter(plan -> eligiblePlanCodes.isEmpty() || eligiblePlanCodes.contains(plan.planCode()))
                // Filter by active or within grace period
                .filter(plan -> isActiveOrInGracePeriod(plan, today))
                // Convert to session model
                .map(this::toSessionEligibilityPlan)
                // Sort by start date, most recent first
                .sorted(Comparator.comparing(
                        com.example.bff.security.session.EligibilityPlan::startDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private boolean isActiveOrInGracePeriod(EligibilityPlan plan, LocalDate today) {
        // Plan hasn't started yet
        if (plan.startDate() != null && plan.startDate().isAfter(today)) {
            return false;
        }

        // No term date means active
        if (plan.termDate() == null) {
            return true;
        }

        // Active if not yet terminated
        if (!plan.termDate().isBefore(today)) {
            return true;
        }

        // Within 18-month grace period after termination
        LocalDate gracePeriodEnd = plan.termDate().plusMonths(GRACE_PERIOD_MONTHS);
        return !today.isAfter(gracePeriodEnd);
    }

    private com.example.bff.security.session.EligibilityPlan toSessionEligibilityPlan(EligibilityPlan plan) {
        return new com.example.bff.security.session.EligibilityPlan(
                plan.planCode(),
                plan.memberId(),
                plan.startDate(),
                plan.termDate());
    }

    @lombok.Data
    public static class EligibilityGraphResponse {
        private EligibilityData data;
    }

    @lombok.Data
    public static class EligibilityData {
        private Eligibility eligibility;
    }

    @lombok.Data
    public static class Eligibility {
        private List<EligibilityPlan> plans;
    }
}
