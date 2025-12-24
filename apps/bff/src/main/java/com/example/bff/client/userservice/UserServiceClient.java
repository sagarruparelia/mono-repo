package com.example.bff.client.userservice;

import com.example.bff.client.WebClientFactory;
import com.example.bff.client.cache.ClientCache;
import com.example.bff.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
public class UserServiceClient {

    private static final String CACHE_KEY_PREFIX = "user:";

    private final WebClient webClient;
    private final ClientCache<UserInfo> cache;

    public UserServiceClient(WebClientFactory webClientFactory, ClientCache<UserInfo> cache) {
        this.webClient = webClientFactory.userServiceClient();
        this.cache = cache;
    }

    public Mono<UserInfo> readUser(String hsidUuid) {
        String cacheKey = CACHE_KEY_PREFIX + hsidUuid;

        return cache.getOrCompute(cacheKey, fetchUserFromApi(hsidUuid));
    }

    private Mono<UserInfo> fetchUserFromApi(String hsidUuid) {
        log.debug("Fetching user info for hsidUuid: {}", hsidUuid);

        return webClient.post()
                .uri("/read")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("hsidUuid", hsidUuid))
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new ApiException(
                                        "UserService",
                                        response.statusCode(),
                                        "User service error",
                                        body))))
                .bodyToMono(UserInfo.class)
                .doOnNext(user -> log.debug("Retrieved user info for enterpriseId: {}", user.enterpriseId()))
                .doOnError(e -> {
                    if (!(e instanceof ApiException)) {
                        log.error("Failed to fetch user info for hsidUuid: {}", hsidUuid, e);
                    }
                });
    }
}
