package com.example.bff.authz.service;

import com.example.bff.authz.dto.PermissionsApiResponse;
import com.example.bff.authz.model.DependentAccess;
import com.example.bff.authz.model.Permission;
import com.example.bff.authz.model.PermissionSet;
import com.example.bff.config.properties.AuthzProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fetches user permissions from the backend Permissions API with fail-closed behavior.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.authz.enabled", havingValue = "true")
public class PermissionsFetchService {

    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_@.-]{1,128}$");
    private static final int MAX_LOG_VALUE_LENGTH = 64;
    private static final int DEFAULT_CACHE_TTL_SECONDS = 300;

    private final WebClient webClient;
    private final AuthzProperties authzProperties;

    public PermissionsFetchService(
            WebClient.Builder webClientBuilder,
            AuthzProperties authzProperties) {
        this.authzProperties = authzProperties;
        this.webClient = webClientBuilder
                .baseUrl(authzProperties.permissionsApi().url())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @NonNull
    public Mono<PermissionSet> fetchPermissions(@NonNull String userId) {
        if (!isValidUserId(userId)) {
            log.warn("Invalid user ID format in fetchPermissions");
            return Mono.just(PermissionSet.empty(userId, "individual"));
        }

        String correlationId = UUID.randomUUID().toString();

        log.debug("Fetching permissions for user {} with correlationId {}",
                sanitizeForLog(userId), correlationId);

        return webClient.get()
                .uri("/api/internal/permissions/{userId}", userId)
                .header("X-Correlation-Id", correlationId)
                .retrieve()
                .bodyToMono(PermissionsApiResponse.class)
                .map(this::toPermissionSet)
                .doOnSuccess(p -> log.info(
                        "Fetched {} dependents for user {} (correlationId={})",
                        p.dependents() != null ? p.dependents().size() : 0,
                        sanitizeForLog(userId),
                        correlationId))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Permissions API error for user {}: {} (correlationId={})",
                            sanitizeForLog(userId),
                            sanitizeForLog(e.getStatusCode() + " " + e.getStatusText()),
                            correlationId);
                    return Mono.just(PermissionSet.empty(userId, "individual"));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Failed to fetch permissions for user {}: {} (correlationId={})",
                            sanitizeForLog(userId),
                            sanitizeForLog(e.getMessage()),
                            correlationId);
                    return Mono.just(PermissionSet.empty(userId, "individual"));
                });
    }

    private PermissionSet toPermissionSet(PermissionsApiResponse response) {
        List<DependentAccess> dependents = response.dependents() != null
                ? response.dependents().stream()
                    .map(this::toDependentAccess)
                    .collect(Collectors.toList())
                : List.of();

        int ttlSeconds = response.cacheTTL() != null ? response.cacheTTL() : DEFAULT_CACHE_TTL_SECONDS;
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);

        return new PermissionSet(
                response.userId(),
                response.persona(),
                dependents,
                response.fetchedAt() != null ? response.fetchedAt() : Instant.now(),
                expiresAt
        );
    }

    private DependentAccess toDependentAccess(PermissionsApiResponse.DependentPermission dp) {
        Set<Permission> permissions = dp.permissions() != null
                ? dp.permissions().stream()
                    .map(this::toPermission)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toSet())
                : Set.of();

        return new DependentAccess(
                dp.dependentId(),
                dp.dependentName(),
                permissions,
                dp.relationship()
        );
    }

    private Optional<Permission> toPermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Permission.valueOf(permission.toUpperCase()));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown permission type: {}", sanitizeForLog(permission));
            return Optional.empty();
        }
    }

    private boolean isValidUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return SAFE_ID_PATTERN.matcher(userId).matches();
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return "null";
        }
        return value
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "")
                .substring(0, Math.min(value.length(), MAX_LOG_VALUE_LENGTH));
    }
}
