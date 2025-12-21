package com.example.bff.identity.service;

import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.ExternalApiProperties;
import com.example.bff.identity.dto.ManagedMemberResponse;
import com.example.bff.identity.dto.ManagedMemberResponse.MemberPermission;
import com.example.bff.identity.exception.IdentityServiceException;
import com.example.bff.identity.model.ManagedMember;
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

import java.util.List;
import java.util.Map;

import static com.example.bff.config.ExternalApiWebClientConfig.EXTERNAL_API_WEBCLIENT;

/**
 * Service for fetching managed members (permissions) via the Graph API.
 */
@Slf4j
@Service
public class ManagedMemberService {

    private static final String SERVICE_NAME = "Permissions";

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

    @NonNull
    public Mono<List<ManagedMember>> getManagedMembers(@NonNull String memberEid) {
        log.debug("Fetching managed members for memberEid: {}", StringSanitizer.forLog(memberEid));

        Mono<ManagedMemberResponse> loader = fetchFromApi(memberEid);

        return cacheService.getOrLoadPermissions(memberEid, loader, ManagedMemberResponse.class)
                .map(this::toManagedMembers);
    }

    private Mono<ManagedMemberResponse> fetchFromApi(String memberEid) {
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
                .bodyToMono(ManagedMemberResponse.class)
                .timeout(timeout)
                .retryWhen(Retry.backoff(retryConfig.maxAttempts(), retryConfig.initialBackoff())
                        .maxBackoff(retryConfig.maxBackoff())
                        .filter(this::isRetryable)
                        .doBeforeRetry(signal -> log.warn(
                                "Retrying {} API call, attempt {}: {}",
                                SERVICE_NAME, signal.totalRetries() + 1, signal.failure().getMessage())))
                .doOnSuccess(response -> log.debug(
                        "Successfully fetched managed members for memberEid: {}", StringSanitizer.forLog(memberEid)))
                .onErrorResume(e -> {
                    // Fail closed: return empty response on errors
                    log.error("Failed to fetch managed members for memberEid {}: {}", StringSanitizer.forLog(memberEid), e.getMessage());
                    return Mono.just(new ManagedMemberResponse(null));
                });
    }

    private List<ManagedMember> toManagedMembers(ManagedMemberResponse response) {
        return response.getActivePermissions().stream()
                .filter(permission -> permission.enterpriseId() != null)
                .map(this::toManagedMember)
                .toList();
    }

    private ManagedMember toManagedMember(MemberPermission permission) {
        return new ManagedMember(
                permission.enterpriseId(),
                permission.firstName(),
                permission.lastName(),
                permission.birthDate(),
                permission.endDate() // endDate is the permission end date
        );
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
