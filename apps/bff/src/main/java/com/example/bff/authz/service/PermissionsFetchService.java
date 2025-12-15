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
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
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
 * Service to fetch permissions from the backend Permissions API.
 *
 * <p>Retrieves user permission sets including dependent access and
 * converts them to the internal domain model. Implements fail-closed
 * behavior on API errors.
 *
 * @see PermissionSet
 * @see AuthzProperties
 */
@Service
@ConditionalOnProperty(name = "app.authz.enabled", havingValue = "true")
public class PermissionsFetchService {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionsFetchService.class);

    // Validation patterns
    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_@.-]{1,128}$");

    // Limits
    private static final int MAX_LOG_VALUE_LENGTH = 64;
    private static final int DEFAULT_CACHE_TTL_SECONDS = 300;

    private final WebClient webClient;
    private final AuthzProperties authzProperties;

    public PermissionsFetchService(
            @NonNull WebClient.Builder webClientBuilder,
            @NonNull AuthzProperties authzProperties) {
        this.authzProperties = authzProperties;
        this.webClient = webClientBuilder
                .baseUrl(authzProperties.permissionsApi().url())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Fetch permissions for a user from the backend API.
     *
     * @param userId the user's ID
     * @return Mono emitting the user's permission set
     */
    @NonNull
    public Mono<PermissionSet> fetchPermissions(@NonNull String userId) {
        if (!isValidUserId(userId)) {
            LOG.warn("Invalid user ID format in fetchPermissions");
            return Mono.just(PermissionSet.empty(userId, "individual"));
        }

        String correlationId = UUID.randomUUID().toString();

        LOG.debug("Fetching permissions for user {} with correlationId {}",
                sanitizeForLog(userId), correlationId);

        return webClient.get()
                .uri("/api/internal/permissions/{userId}", userId)
                .header("X-Correlation-Id", correlationId)
                .retrieve()
                .bodyToMono(PermissionsApiResponse.class)
                .map(this::toPermissionSet)
                .doOnSuccess(p -> LOG.info(
                        "Fetched {} dependents for user {} (correlationId={})",
                        p.dependents() != null ? p.dependents().size() : 0,
                        sanitizeForLog(userId),
                        correlationId))
                .onErrorResume(WebClientResponseException.class, e -> {
                    LOG.error("Permissions API error for user {}: {} (correlationId={})",
                            sanitizeForLog(userId),
                            sanitizeForLog(e.getStatusCode() + " " + e.getStatusText()),
                            correlationId);
                    // Return empty permissions on API error (fail closed)
                    return Mono.just(PermissionSet.empty(userId, "individual"));
                })
                .onErrorResume(Exception.class, e -> {
                    LOG.error("Failed to fetch permissions for user {}: {} (correlationId={})",
                            sanitizeForLog(userId),
                            sanitizeForLog(e.getMessage()),
                            correlationId);
                    // Return empty permissions on error (fail closed)
                    return Mono.just(PermissionSet.empty(userId, "individual"));
                });
    }

    /**
     * Convert API response to internal PermissionSet model.
     *
     * @param response the API response
     * @return the permission set
     */
    @NonNull
    private PermissionSet toPermissionSet(@NonNull PermissionsApiResponse response) {
        List<DependentAccess> dependents = response.dependents() != null
                ? response.dependents().stream()
                    .map(this::toDependentAccess)
                    .collect(Collectors.toList())
                : List.of();

        // Calculate expiration based on cache TTL from API (default 5 minutes)
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

    /**
     * Convert API dependent permission to internal DependentAccess model.
     *
     * @param dp the dependent permission from API
     * @return the dependent access
     */
    @NonNull
    private DependentAccess toDependentAccess(@NonNull PermissionsApiResponse.DependentPermission dp) {
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

    /**
     * Convert string permission to enum.
     *
     * @param permission the permission string
     * @return Optional containing the permission if valid
     */
    @NonNull
    private Optional<Permission> toPermission(@Nullable String permission) {
        if (permission == null || permission.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Permission.valueOf(permission.toUpperCase()));
        } catch (IllegalArgumentException e) {
            LOG.warn("Unknown permission type: {}", sanitizeForLog(permission));
            return Optional.empty();
        }
    }

    /**
     * Validates user ID format.
     *
     * @param userId the user ID to validate
     * @return true if valid format
     */
    private boolean isValidUserId(@Nullable String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return SAFE_ID_PATTERN.matcher(userId).matches();
    }

    /**
     * Sanitizes a value for safe logging.
     *
     * @param value the value to sanitize
     * @return sanitized value
     */
    @NonNull
    private String sanitizeForLog(@Nullable String value) {
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
