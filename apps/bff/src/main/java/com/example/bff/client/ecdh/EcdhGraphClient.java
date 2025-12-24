package com.example.bff.client.ecdh;

import com.example.bff.client.ecdh.dto.AllergyDto;
import com.example.bff.client.ecdh.dto.ImmunizationDto;
import com.example.bff.client.ecdh.dto.PageInfoDto;
import com.example.bff.exception.ApiException;
import com.example.bff.health.model.HealthResourceType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class EcdhGraphClient {

    private static final int DEFAULT_PAGE_SIZE = 100;

    private final WebClient webClient;
    private final EcdhQueryLoader queryLoader;
    private final ObjectMapper objectMapper;

    public EcdhGraphClient(WebClient ecdhWebClient, EcdhQueryLoader queryLoader, ObjectMapper objectMapper) {
        this.webClient = ecdhWebClient;
        this.queryLoader = queryLoader;
        this.objectMapper = objectMapper;
    }

    public Mono<EcdhPagedResponse<ImmunizationDto>> getImmunizations(
            String enterpriseId,
            int pageNum,
            int pageSize,
            String continuationToken) {

        return executeQuery(HealthResourceType.IMMUNIZATION, enterpriseId, pageNum, pageSize, continuationToken)
                .map(node -> parsePagedResponse(node, HealthResourceType.IMMUNIZATION, new TypeReference<>() {}));
    }

    public Flux<ImmunizationDto> getAllImmunizations(String enterpriseId) {
        return fetchAllPages(HealthResourceType.IMMUNIZATION, enterpriseId, new TypeReference<>() {});
    }

    public Mono<EcdhPagedResponse<AllergyDto>> getAllergies(
            String enterpriseId,
            int pageNum,
            int pageSize,
            String continuationToken) {

        return executeQuery(HealthResourceType.ALLERGY, enterpriseId, pageNum, pageSize, continuationToken)
                .map(node -> parsePagedResponse(node, HealthResourceType.ALLERGY, new TypeReference<>() {}));
    }

    public Flux<AllergyDto> getAllAllergies(String enterpriseId) {
        return fetchAllPages(HealthResourceType.ALLERGY, enterpriseId, new TypeReference<>() {});
    }

    public Flux<Object> getAllRecords(HealthResourceType resourceType, String enterpriseId) {
        return switch (resourceType) {
            case IMMUNIZATION -> getAllImmunizations(enterpriseId).cast(Object.class);
            case ALLERGY -> getAllAllergies(enterpriseId).cast(Object.class);
        };
    }

    private Mono<JsonNode> executeQuery(
            HealthResourceType resourceType,
            String enterpriseId,
            int pageNum,
            int pageSize,
            String continuationToken) {

        String query = queryLoader.getQuery(resourceType);
        Map<String, Object> variables = buildVariables(enterpriseId, pageNum, pageSize, continuationToken);

        Map<String, Object> request = Map.of(
                "query", query,
                "variables", variables
        );

        log.debug("Executing ECDH query for {} with enterpriseId: {}, page: {}", resourceType, enterpriseId, pageNum);

        return webClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new ApiException(
                                        "ECDH",
                                        response.statusCode(),
                                        "ECDH API error for " + resourceType,
                                        body))))
                .bodyToMono(JsonNode.class)
                .map(response -> extractData(response, resourceType))
                .doOnNext(node -> log.debug("Received ECDH response for {} enterpriseId: {}", resourceType, enterpriseId))
                .doOnError(e -> {
                    if (!(e instanceof ApiException)) {
                        log.error("ECDH query failed for {} enterpriseId: {}", resourceType, enterpriseId, e);
                    }
                });
    }

    private Map<String, Object> buildVariables(String enterpriseId, int pageNum, int pageSize, String continuationToken) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("eid", enterpriseId);
        variables.put("pageNum", pageNum);
        variables.put("pageSize", pageSize);
        if (continuationToken != null && !continuationToken.isEmpty()) {
            variables.put("continuationToken", continuationToken);
        }
        return variables;
    }

    private JsonNode extractData(JsonNode response, HealthResourceType resourceType) {
        JsonNode data = response.path("data");
        if (data.isMissingNode()) {
            JsonNode errors = response.path("errors");
            String errorMessage = errors.isMissingNode() ? "No data in response" : errors.toString();
            log.error("ECDH GraphQL errors for {}: {}", resourceType, errorMessage);
            throw new ApiException("ECDH", "GraphQL error for " + resourceType + ": " + errorMessage, null);
        }

        // The query operation name follows pattern: get{ResourceType}ByEid
        String operationName = "get" + capitalize(resourceType.getQueryFileName()) + "ByEid";
        JsonNode operationData = data.path(operationName);
        if (operationData.isMissingNode()) {
            throw new ApiException("ECDH", "Missing operation '" + operationName + "' in ECDH response", null);
        }

        return operationData;
    }

    private <T> EcdhPagedResponse<T> parsePagedResponse(
            JsonNode node,
            HealthResourceType resourceType,
            TypeReference<List<T>> typeRef) {

        try {
            PageInfoDto pageInfo = objectMapper.treeToValue(node.path("pageInfo"), PageInfoDto.class);
            String dataFieldName = resourceType.getDataFieldName();
            List<T> items = objectMapper.convertValue(node.path(dataFieldName), typeRef);

            return new EcdhPagedResponse<>(pageInfo, items != null ? items : List.of());
        } catch (Exception e) {
            log.error("Failed to parse ECDH response for {}", resourceType, e);
            throw new ApiException("ECDH", "Failed to parse ECDH response for " + resourceType, e);
        }
    }

    private <T> Flux<T> fetchAllPages(
            HealthResourceType resourceType,
            String enterpriseId,
            TypeReference<List<T>> typeRef) {

        return fetchPage(resourceType, enterpriseId, 1, DEFAULT_PAGE_SIZE, null, typeRef);
    }

    private <T> Flux<T> fetchPage(
            HealthResourceType resourceType,
            String enterpriseId,
            int pageNum,
            int pageSize,
            String continuationToken,
            TypeReference<List<T>> typeRef) {

        return executeQuery(resourceType, enterpriseId, pageNum, pageSize, continuationToken)
                .flatMapMany(node -> {
                    EcdhPagedResponse<T> response = parsePagedResponse(node, resourceType, typeRef);

                    Flux<T> currentPage = Flux.fromIterable(response.items());

                    if (response.hasNextPage()) {
                        log.debug("Fetching next page {} for {} enterpriseId: {}",
                                pageNum + 1, resourceType, enterpriseId);
                        return currentPage.concatWith(
                                fetchPage(resourceType, enterpriseId, pageNum + 1, pageSize,
                                        response.pageInfo().continuationToken(), typeRef)
                        );
                    }

                    return currentPage;
                });
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
