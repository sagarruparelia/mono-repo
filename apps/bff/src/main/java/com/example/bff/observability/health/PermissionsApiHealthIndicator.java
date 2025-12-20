package com.example.bff.observability.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Health indicator for the external Permissions API.
 * Checks connectivity to the permissions service used for ABAC authorization.
 */
@Slf4j
@Component
public class PermissionsApiHealthIndicator implements ReactiveHealthIndicator {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final String permissionsApiUrl;
    private final boolean authzEnabled;

    public PermissionsApiHealthIndicator(
            WebClient.Builder webClientBuilder,
            @Value("${app.authz.permissions-api.url:http://localhost:8081}") String permissionsApiUrl,
            @Value("${app.authz.enabled:true}") boolean authzEnabled) {
        this.webClient = webClientBuilder.build();
        this.permissionsApiUrl = permissionsApiUrl;
        this.authzEnabled = authzEnabled;
    }

    @Override
    public Mono<Health> health() {
        // If authz is disabled, report as UP but note it's not in use
        if (!authzEnabled) {
            return Mono.just(Health.up()
                    .withDetail("status", "disabled")
                    .withDetail("url", permissionsApiUrl)
                    .build());
        }

        // Check health endpoint of permissions API
        return webClient.get()
                .uri(permissionsApiUrl + "/actuator/health")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .map(response -> Health.up()
                        .withDetail("url", permissionsApiUrl)
                        .build())
                .onErrorResume(error -> {
                    log.warn("Permissions API health check failed: {}", error.getMessage());
                    return Mono.just(Health.down()
                            .withDetail("url", permissionsApiUrl)
                            .withDetail("error", error.getMessage())
                            .build());
                });
    }
}
