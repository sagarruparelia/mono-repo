package com.example.bff.authz.service;

import com.example.bff.common.util.RetryUtils;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.ExternalApiProperties;
import com.example.bff.authz.dto.UserInfoResponse;
import com.example.bff.authz.exception.IdentityServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import static com.example.bff.config.ExternalApiWebClientConfig.EXTERNAL_API_WEBCLIENT;

@Slf4j
@Service
public class UserInfoService {

    private static final String SERVICE_NAME = "UserService";

    private final WebClient webClient;
    private final ExternalApiProperties apiProperties;
    private final IdentityCacheOperations cacheService;

    public UserInfoService(
            @Qualifier(EXTERNAL_API_WEBCLIENT) WebClient webClient,
            ExternalApiProperties apiProperties,
            IdentityCacheOperations cacheService) {
        this.webClient = webClient;
        this.apiProperties = apiProperties;
        this.cacheService = cacheService;
    }

    @NonNull
    public Mono<UserInfoResponse> getUserInfo(@NonNull String hsidUuid) {
        log.debug("Fetching user info for hsidUuid: {}", StringSanitizer.forLog(hsidUuid));

        Mono<UserInfoResponse> loader = fetchFromApi(hsidUuid);
        return cacheService.getOrLoadUserInfo(hsidUuid, loader, UserInfoResponse.class);
    }

    private Mono<UserInfoResponse> fetchFromApi(String hsidUuid) {
        var retryConfig = apiProperties.retry();
        var timeout = apiProperties.userService().timeout();

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(apiProperties.userService().path())
                        .queryParam("hsidUuid", hsidUuid)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    if (response.statusCode() == HttpStatus.NOT_FOUND) {
                        log.warn("User not found for hsidUuid: {}", StringSanitizer.forLog(hsidUuid));
                        return Mono.error(new IdentityServiceException(
                                SERVICE_NAME, 404, "User not found"));
                    }
                    return response.bodyToMono(String.class)
                            .flatMap(body -> Mono.error(new IdentityServiceException(
                                    SERVICE_NAME, response.statusCode().value(), body)));
                })
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new IdentityServiceException(
                                        SERVICE_NAME, response.statusCode().value(), body))))
                .bodyToMono(UserInfoResponse.class)
                .timeout(timeout)
                .retryWhen(Retry.backoff(retryConfig.maxAttempts(), retryConfig.initialBackoff())
                        .maxBackoff(retryConfig.maxBackoff())
                        .filter(RetryUtils::isRetryable)
                        .doBeforeRetry(signal -> log.warn(
                                "Retrying {} API call, attempt {}: {}",
                                SERVICE_NAME, signal.totalRetries() + 1, signal.failure().getMessage())))
                .doOnSuccess(response -> log.debug(
                        "Successfully fetched user info for hsidUuid: {}", StringSanitizer.forLog(hsidUuid)))
                .doOnError(e -> log.error(
                        "Failed to fetch user info for hsidUuid {}: {}", StringSanitizer.forLog(hsidUuid), e.getMessage()));
    }
}
