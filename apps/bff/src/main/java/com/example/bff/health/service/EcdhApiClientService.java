package com.example.bff.health.service;

import com.example.bff.config.properties.EcdhApiProperties;
import com.example.bff.health.dto.AllergyResponse;
import com.example.bff.health.dto.ConditionResponse;
import com.example.bff.health.dto.ImmunizationResponse;
import com.example.bff.health.exception.EcdhApiException;
import com.example.bff.health.model.AllergyEntity;
import com.example.bff.health.model.ConditionEntity;
import com.example.bff.health.model.ImmunizationEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static com.example.bff.config.EcdhApiWebClientConfig.ECDH_API_WEBCLIENT;

/** Service for fetching health data from ECDH GraphQL API. */
@Slf4j
@Service
public class EcdhApiClientService {

    private final WebClient webClient;
    private final EcdhApiProperties apiProperties;
    private final GraphqlQueryLoader queryLoader;

    public EcdhApiClientService(
            @Qualifier(ECDH_API_WEBCLIENT) WebClient webClient,
            EcdhApiProperties apiProperties,
            GraphqlQueryLoader queryLoader) {
        this.webClient = webClient;
        this.apiProperties = apiProperties;
        this.queryLoader = queryLoader;
    }

    @NonNull
    public Mono<List<ImmunizationEntity.ImmunizationRecord>> fetchImmunizations(
            @NonNull String memberEid) {

        log.debug("Fetching immunizations for memberEid: {}", memberEid);

        return fetchAllPages(
                queryLoader.getImmunizationQuery(),
                memberEid,
                ImmunizationResponse.class
        ).map(responses -> {
            List<ImmunizationEntity.ImmunizationRecord> allRecords = new ArrayList<>();
            for (ImmunizationResponse response : responses) {
                var immunizations = response.getImmunizations();
                if (immunizations != null) {
                    for (ImmunizationResponse.ImmunizationDto dto : immunizations) {
                        allRecords.add(mapToImmunizationRecord(dto));
                    }
                }
            }
            log.debug("Fetched {} immunization records for memberEid: {}", allRecords.size(), memberEid);
            return allRecords;
        });
    }

    @NonNull
    public Mono<List<AllergyEntity.AllergyRecord>> fetchAllergies(
            @NonNull String memberEid) {

        log.debug("Fetching allergies for memberEid: {}", memberEid);

        return fetchAllPages(
                queryLoader.getAllergyQuery(),
                memberEid,
                AllergyResponse.class
        ).map(responses -> {
            List<AllergyEntity.AllergyRecord> allRecords = new ArrayList<>();
            for (AllergyResponse response : responses) {
                var allergies = response.getAllergies();
                if (allergies != null) {
                    for (AllergyResponse.AllergyDto dto : allergies) {
                        allRecords.add(mapToAllergyRecord(dto));
                    }
                }
            }
            log.debug("Fetched {} allergy records for memberEid: {}", allRecords.size(), memberEid);
            return allRecords;
        });
    }

    @NonNull
    public Mono<List<ConditionEntity.ConditionRecord>> fetchConditions(
            @NonNull String memberEid) {

        log.debug("Fetching conditions for memberEid: {}", memberEid);

        return fetchAllPages(
                queryLoader.getConditionQuery(),
                memberEid,
                ConditionResponse.class
        ).map(responses -> {
            List<ConditionEntity.ConditionRecord> allRecords = new ArrayList<>();
            for (ConditionResponse response : responses) {
                var conditions = response.getConditions();
                if (conditions != null) {
                    for (ConditionResponse.ConditionDto dto : conditions) {
                        allRecords.add(mapToConditionRecord(dto));
                    }
                }
            }
            log.debug("Fetched {} condition records for memberEid: {}", allRecords.size(), memberEid);
            return allRecords;
        });
    }

    private <T> Mono<List<T>> fetchAllPages(
            String query,
            String memberEid,
            Class<T> responseType) {

        return fetchPage(query, memberEid, null, responseType)
                .expand(response -> {
                    String continuationToken = getContinuationToken(response);
                    if (continuationToken == null || continuationToken.isBlank()) {
                        return Mono.empty();
                    }
                    return fetchPage(query, memberEid, continuationToken, responseType);
                })
                .collectList();
    }

    private <T> Mono<T> fetchPage(
            String query,
            String memberEid,
            String continuationToken,
            Class<T> responseType) {

        Map<String, Object> variables = new HashMap<>();
        variables.put("eid", memberEid);
        if (continuationToken != null) {
            variables.put("continuationToken", continuationToken);
        }

        Map<String, Object> requestBody = Map.of(
                "query", query,
                "variables", variables
        );

        var retryConfig = apiProperties.retry();

        return webClient.post()
                .uri(apiProperties.graphPath())
                .body(BodyInserters.fromValue(requestBody))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new EcdhApiException(
                                        response.statusCode().value(), body))))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new EcdhApiException(
                                        response.statusCode().value(), body))))
                .bodyToMono(responseType)
                .timeout(apiProperties.timeout())
                .retryWhen(Retry.backoff(retryConfig.maxAttempts(), retryConfig.initialBackoff())
                        .maxBackoff(retryConfig.maxBackoff())
                        .filter(this::isRetryable)
                        .doBeforeRetry(signal -> log.warn(
                                "Retrying ECDH API call, attempt {}: {}",
                                signal.totalRetries() + 1, signal.failure().getMessage())));
    }

    private String getContinuationToken(Object response) {
        if (response instanceof ImmunizationResponse r) {
            return r.getContinuationToken();
        } else if (response instanceof AllergyResponse r) {
            return r.getContinuationToken();
        } else if (response instanceof ConditionResponse r) {
            return r.getContinuationToken();
        }
        return null;
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return ex.getStatusCode().is5xxServerError();
        }
        if (throwable instanceof EcdhApiException ex) {
            return ex.isRetryable();
        }
        return throwable instanceof TimeoutException;
    }

    private ImmunizationEntity.ImmunizationRecord mapToImmunizationRecord(
            ImmunizationResponse.ImmunizationDto dto) {
        return new ImmunizationEntity.ImmunizationRecord(
                dto.id(),
                dto.vaccineCode(),
                dto.vaccineName(),
                dto.administrationDate(),
                dto.provider(),
                dto.lotNumber(),
                dto.site(),
                dto.status()
        );
    }

    private AllergyEntity.AllergyRecord mapToAllergyRecord(
            AllergyResponse.AllergyDto dto) {
        return new AllergyEntity.AllergyRecord(
                dto.id(),
                dto.allergenCode(),
                dto.allergenName(),
                dto.category(),
                dto.severity(),
                dto.reaction(),
                dto.onsetDate(),
                dto.status()
        );
    }

    private ConditionEntity.ConditionRecord mapToConditionRecord(
            ConditionResponse.ConditionDto dto) {
        return new ConditionEntity.ConditionRecord(
                dto.id(),
                dto.conditionCode(),
                dto.conditionName(),
                dto.category(),
                dto.onsetDate(),
                dto.abatementDate(),
                dto.clinicalStatus(),
                dto.verificationStatus()
        );
    }
}
