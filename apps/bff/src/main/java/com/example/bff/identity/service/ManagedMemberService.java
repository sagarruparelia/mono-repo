package com.example.bff.identity.service;

import com.example.bff.config.properties.ExternalApiProperties;
import com.example.bff.identity.dto.ManagedMemberResponse;
import com.example.bff.identity.dto.ManagedMemberResponse.MemberPermission;
import com.example.bff.identity.exception.IdentityServiceException;
import com.example.bff.identity.model.ManagedMember;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.util.List;
import java.util.Map;

import static com.example.bff.config.ExternalApiWebClientConfig.EXTERNAL_API_WEBCLIENT;

/**
 * Service for fetching managed members (permissions) via the Graph API.
 * Endpoint: /api/consumer/prefs/del-gr/1.0.0
 *
 * Returns list of members who have granted access to the logged-in user.
 * Only returns members with active permissions (startDate <= today <= endDate).
 */
@Slf4j
@Service
public class ManagedMemberService {

    private static final String SERVICE_NAME = "Permissions";
    private static final String X_IDENTIFIER_HEADER = "x-identifier";

    private final WebClient webClient;
    private final ExternalApiProperties apiProperties;
    private final IdentityCacheService cacheService;

    public ManagedMemberService(
            @Qualifier(EXTERNAL_API_WEBCLIENT) WebClient webClient,
            ExternalApiProperties apiProperties,
            IdentityCacheService cacheService) {
        this.webClient = webClient;
        this.apiProperties = apiProperties;
        this.cacheService = cacheService;
    }

    /**
     * Fetch managed members for a responsible party.
     * Results are cached in Redis.
     *
     * @param memberEid     Member's Enterprise ID (the responsible party)
     * @param apiIdentifier API identifier for the x-identifier header
     * @return List of managed members with active permissions
     */
    @NonNull
    public Mono<List<ManagedMember>> getManagedMembers(
            @NonNull String memberEid,
            @Nullable String apiIdentifier) {

        log.debug("Fetching managed members for memberEid: {}", memberEid);

        Mono<ManagedMemberResponse> loader = fetchFromApi(memberEid, apiIdentifier);

        return cacheService.getOrLoadPermissions(memberEid, loader, ManagedMemberResponse.class)
                .map(this::toManagedMembers);
    }

    /**
     * Fetch permissions directly from API.
     */
    private Mono<ManagedMemberResponse> fetchFromApi(String memberEid, @Nullable String apiIdentifier) {
        var retryConfig = apiProperties.retry();
        var timeout = apiProperties.permissions().timeout();

        // GraphQL query with variables to prevent injection
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

        var requestBuilder = webClient.post()
                .uri(apiProperties.permissions().path())
                .body(BodyInserters.fromValue(requestBody));

        // Add x-identifier header if available
        if (apiIdentifier != null && !apiIdentifier.isBlank()) {
            requestBuilder.header(X_IDENTIFIER_HEADER, apiIdentifier);
        }

        return requestBuilder
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new IdentityServiceException(
                                        SERVICE_NAME, response.statusCode().value(), body))))
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new IdentityServiceException(
                                        SERVICE_NAME, response.statusCode().value(), body))))
                .bodyToMono(ManagedMemberResponse.class)
                .timeout(timeout)
                .retryWhen(Retry.backoff(retryConfig.maxAttempts(), retryConfig.initialBackoff())
                        .maxBackoff(retryConfig.maxBackoff())
                        .filter(this::isRetryable)
                        .doBeforeRetry(signal -> log.warn(
                                "Retrying {} API call, attempt {}: {}",
                                SERVICE_NAME, signal.totalRetries() + 1, signal.failure().getMessage())))
                .doOnSuccess(response -> log.debug(
                        "Successfully fetched managed members for memberEid: {}", memberEid))
                .onErrorResume(e -> {
                    // Fail closed: return empty response on errors
                    log.error("Failed to fetch managed members for memberEid {}: {}", memberEid, e.getMessage());
                    return Mono.just(new ManagedMemberResponse(null));
                });
    }

    /**
     * Convert API response to list of managed members with active permissions only.
     */
    private List<ManagedMember> toManagedMembers(ManagedMemberResponse response) {
        return response.getActivePermissions().stream()
                .filter(permission -> permission.eid() != null)
                .map(this::toManagedMember)
                .toList();
    }

    /**
     * Convert a single permission to ManagedMember model.
     */
    private ManagedMember toManagedMember(MemberPermission permission) {
        return new ManagedMember(
                permission.eid(),
                permission.firstName(),
                permission.lastName(),
                permission.birthDate(),
                permission.endDate() // endDate is the permission end date
        );
    }

    /**
     * Check if error is retryable (5xx errors and timeouts).
     */
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
