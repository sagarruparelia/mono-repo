package com.example.bff.authz.service;

import com.example.bff.common.util.RetryUtils;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.ExternalApiProperties;
import com.example.bff.authz.dto.ManagedMembersResponse;
import com.example.bff.authz.dto.ManagedMembersResponse.ManagedMemberEntry;
import com.example.bff.authz.exception.IdentityServiceException;
import com.example.bff.authz.model.ManagedMember;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.util.List;
import java.util.Map;

import static com.example.bff.config.ExternalApiWebClientConfig.EXTERNAL_API_WEBCLIENT;

@Slf4j
@Service
public class ManagedMembersService {

    private static final String SERVICE_NAME = "Permissions";

    private final WebClient webClient;
    private final ExternalApiProperties apiProperties;
    private final IdentityCacheOperations cacheService;

    public ManagedMembersService(
            @Qualifier(EXTERNAL_API_WEBCLIENT) WebClient webClient,
            ExternalApiProperties apiProperties,
            IdentityCacheOperations cacheService) {
        this.webClient = webClient;
        this.apiProperties = apiProperties;
        this.cacheService = cacheService;
    }

    @NonNull
    public Mono<List<ManagedMember>> getManagedMembers(@NonNull String memberEid) {
        log.debug("Fetching managed members for memberEid: {}", StringSanitizer.forLog(memberEid));

        Mono<ManagedMembersResponse> loader = fetchFromApi(memberEid);

        return cacheService.getOrLoadPermissions(memberEid, loader, ManagedMembersResponse.class)
                .map(this::toManagedMembers);
    }

    private Mono<ManagedMembersResponse> fetchFromApi(String memberEid) {
        var retryConfig = apiProperties.retry();
        var timeout = apiProperties.permissions().timeout();

        String query = """
                query GetPermissions($memberEid: String!) {
                    permissions(memberEid: $memberEid) {
                        eid
                        firstName
                        lastName
                        birthDate
                        email
                        delegateType
                        startDate
                        endDate
                    }
                }
                """;

        Map<String, Object> requestBody = Map.of(
                "query", query,
                "variables", Map.of("memberEid", memberEid)
        );

        return webClient.post()
                .uri(apiProperties.permissions().path())
                .body(BodyInserters.fromValue(requestBody))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new IdentityServiceException(
                                        SERVICE_NAME, response.statusCode().value(), body))))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new IdentityServiceException(
                                        SERVICE_NAME, response.statusCode().value(), body))))
                .bodyToMono(ManagedMembersResponse.class)
                .timeout(timeout)
                .retryWhen(Retry.backoff(retryConfig.maxAttempts(), retryConfig.initialBackoff())
                        .maxBackoff(retryConfig.maxBackoff())
                        .filter(RetryUtils::isRetryable)
                        .doBeforeRetry(signal -> log.warn(
                                "Retrying {} API call, attempt {}: {}",
                                SERVICE_NAME, signal.totalRetries() + 1, signal.failure().getMessage())))
                .doOnSuccess(response -> log.debug(
                        "Successfully fetched managed members for memberEid: {}", StringSanitizer.forLog(memberEid)))
                .onErrorResume(e -> {
                    log.error("Failed to fetch managed members for memberEid {}: {}", StringSanitizer.forLog(memberEid), e.getMessage());
                    return Mono.just(new ManagedMembersResponse(null));
                });
    }

    private List<ManagedMember> toManagedMembers(ManagedMembersResponse response) {
        return response.getActiveManagedMembers().stream()
                .filter(entry -> entry.enterpriseId() != null)
                .map(this::toManagedMember)
                .toList();
    }

    private ManagedMember toManagedMember(ManagedMemberEntry entry) {
        return new ManagedMember(
                entry.enterpriseId(),
                entry.firstName(),
                entry.lastName(),
                entry.birthDate(),
                entry.endDate()
        );
    }
}
