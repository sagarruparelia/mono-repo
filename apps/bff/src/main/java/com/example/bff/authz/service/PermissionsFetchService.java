package com.example.bff.authz.service;

import com.example.bff.authz.dto.PermissionsApiResponse;
import com.example.bff.authz.model.DependentAccess;
import com.example.bff.authz.model.Permission;
import com.example.bff.authz.model.PermissionSet;
import com.example.bff.config.properties.AuthzProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service to fetch permissions from the backend Permissions API.
 */
@Service
@ConditionalOnProperty(name = "app.authz.enabled", havingValue = "true")
public class PermissionsFetchService {

    private static final Logger log = LoggerFactory.getLogger(PermissionsFetchService.class);

    private final WebClient webClient;
    private final AuthzProperties authzProperties;

    public PermissionsFetchService(WebClient.Builder webClientBuilder, AuthzProperties authzProperties) {
        this.authzProperties = authzProperties;
        this.webClient = webClientBuilder
                .baseUrl(authzProperties.permissionsApi().url())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Fetch permissions for a user from the backend API.
     *
     * @param userId The user's ID
     * @return Mono emitting the user's permission set
     */
    public Mono<PermissionSet> fetchPermissions(String userId) {
        String correlationId = UUID.randomUUID().toString();

        log.debug("Fetching permissions for user {} with correlationId {}", userId, correlationId);

        return webClient.get()
                .uri("/api/internal/permissions/{userId}", userId)
                .header("X-Correlation-Id", correlationId)
                .retrieve()
                .bodyToMono(PermissionsApiResponse.class)
                .map(this::toPermissionSet)
                .doOnSuccess(p -> log.info(
                        "Fetched {} dependents for user {} (correlationId={})",
                        p.dependents() != null ? p.dependents().size() : 0,
                        userId,
                        correlationId))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Permissions API error for user {}: {} {} (correlationId={})",
                            userId, e.getStatusCode(), e.getMessage(), correlationId);
                    // Return empty permissions on API error (fail closed)
                    return Mono.just(PermissionSet.empty(userId, "individual"));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Failed to fetch permissions for user {}: {} (correlationId={})",
                            userId, e.getMessage(), correlationId);
                    // Return empty permissions on error (fail closed)
                    return Mono.just(PermissionSet.empty(userId, "individual"));
                });
    }

    /**
     * Convert API response to internal PermissionSet model.
     */
    private PermissionSet toPermissionSet(PermissionsApiResponse response) {
        List<DependentAccess> dependents = response.dependents() != null
                ? response.dependents().stream()
                    .map(this::toDependentAccess)
                    .collect(Collectors.toList())
                : List.of();

        // Calculate expiration based on cache TTL from API (default 5 minutes)
        int ttlSeconds = response.cacheTTL() != null ? response.cacheTTL() : 300;
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);

        return new PermissionSet(
                response.userId(),
                response.persona(),
                dependents,
                response.fetchedAt() != null ? response.fetchedAt() : Instant.now(),
                expiresAt
        );
    }

    /**
     * Convert API dependent permission to internal DependentAccess model.
     */
    private DependentAccess toDependentAccess(PermissionsApiResponse.DependentPermission dp) {
        Set<Permission> permissions = dp.permissions() != null
                ? dp.permissions().stream()
                    .map(this::toPermission)
                    .filter(p -> p != null)
                    .collect(Collectors.toSet())
                : Set.of();

        return new DependentAccess(
                dp.dependentId(),
                dp.dependentName(),
                permissions,
                dp.relationship()
        );
    }

    /**
     * Convert string permission to enum.
     */
    private Permission toPermission(String permission) {
        try {
            return Permission.valueOf(permission.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown permission type: {}", permission);
            return null;
        }
    }
}
