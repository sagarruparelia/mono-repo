package com.example.bff.authz.service;

import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.ExternalApiProperties;
import com.example.bff.authz.dto.EligibilityResponse;
import com.example.bff.authz.exception.IdentityServiceException;
import com.example.bff.authz.model.EligibilityResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.LocalDate;
import java.util.Map;

import static com.example.bff.config.ExternalApiWebClientConfig.EXTERNAL_API_WEBCLIENT;
import static com.example.bff.authz.model.EligibilityResult.GRACE_PERIOD_MONTHS;

@Slf4j
@Service
public class EligibilityService {

    private static final String SERVICE_NAME = "Eligibility";
    private static final String X_IDENTIFIER_HEADER = "x-identifier";

    private final WebClient webClient;
    private final ExternalApiProperties apiProperties;
    private final IdentityCacheService cacheService;

    public EligibilityService(
            @Qualifier(EXTERNAL_API_WEBCLIENT) WebClient webClient,
            ExternalApiProperties apiProperties,
            IdentityCacheService cacheService) {
        this.webClient = webClient;
        this.apiProperties = apiProperties;
        this.cacheService = cacheService;
    }

    @NonNull
    public Mono<EligibilityResult> checkEligibility(@NonNull String memberEid, @Nullable String apiIdentifier) {
        log.debug("Checking eligibility for memberEid: {}", StringSanitizer.forLog(memberEid));

        Mono<EligibilityResult> loader = fetchFromApi(memberEid, apiIdentifier)
                .map(this::toEligibilityResult);

        return cacheService.getOrLoadEligibility(memberEid, loader, EligibilityResult.class);
    }

    private Mono<EligibilityResponse> fetchFromApi(String memberEid, String apiIdentifier) {
        var retryConfig = apiProperties.retry();
        var timeout = apiProperties.eligibility().timeout();

        String query = """
                query CheckEligibility($memberEid: String!) {
                    eligibility(memberEid: $memberEid) {
                        status
                        termDate
                    }
                }
                """;

        Map<String, Object> requestBody = Map.of(
                "query", query,
                "variables", Map.of("memberEid", memberEid)
        );

        var requestBuilder = webClient.post()
                .uri(apiProperties.eligibility().path())
                .body(BodyInserters.fromValue(requestBody));

        if (apiIdentifier != null && !apiIdentifier.isBlank()) {
            requestBuilder.header(X_IDENTIFIER_HEADER, apiIdentifier);
        }

        return requestBuilder
                .retrieve()
                .onStatus(status -> status == HttpStatus.NOT_FOUND, response -> {
                    log.debug("No eligibility record found for memberEid: {}", StringSanitizer.forLog(memberEid));
                    return Mono.empty();
                })
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new IdentityServiceException(
                                        SERVICE_NAME, response.statusCode().value(), body))))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new IdentityServiceException(
                                        SERVICE_NAME, response.statusCode().value(), body))))
                .bodyToMono(EligibilityResponse.class)
                .timeout(timeout)
                .retryWhen(Retry.backoff(retryConfig.maxAttempts(), retryConfig.initialBackoff())
                        .maxBackoff(retryConfig.maxBackoff())
                        .filter(this::isRetryable)
                        .doBeforeRetry(signal -> log.warn(
                                "Retrying {} API call, attempt {}: {}",
                                SERVICE_NAME, signal.totalRetries() + 1, signal.failure().getMessage())))
                .switchIfEmpty(Mono.just(new EligibilityResponse(null)))
                .doOnSuccess(response -> log.debug(
                        "Eligibility check completed for memberEid: {}", StringSanitizer.forLog(memberEid)))
                .onErrorResume(e -> {
                    // Fail closed: return UNKNOWN on errors
                    log.error("Failed to check eligibility for memberEid {}: {}", StringSanitizer.forLog(memberEid), e.getMessage());
                    return Mono.just(new EligibilityResponse(null));
                });
    }

    private EligibilityResult toEligibilityResult(EligibilityResponse response) {
        String status = response.getStatus();
        LocalDate termDate = response.getTermDate();

        if (status == null) {
            return EligibilityResult.notEligible();
        }

        if ("active".equalsIgnoreCase(status)) {
            return EligibilityResult.active(termDate);
        }

        if (termDate != null) {
            LocalDate gracePeriodEnd = termDate.plusMonths(GRACE_PERIOD_MONTHS);
            if (LocalDate.now().isBefore(gracePeriodEnd)) {
                return EligibilityResult.inactive(termDate);
            }
            return EligibilityResult.expired(termDate);
        }

        return EligibilityResult.notEligible();
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return ex.getStatusCode().is5xxServerError();
        }
        if (throwable instanceof IdentityServiceException ex) {
            return ex.getStatusCode() >= 500;
        }
        return throwable instanceof java.util.concurrent.TimeoutException;
    }
}
