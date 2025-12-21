package com.example.bff.authz.service;

import com.example.bff.auth.model.DelegatePermission;
import com.example.bff.auth.model.DelegateType;
import com.example.bff.authz.dto.PermissionsApiResponse;
import com.example.bff.authz.dto.PermissionsApiResponse.DelegatePermissionEntry;
import com.example.bff.authz.model.DependentAccess;
import com.example.bff.authz.model.PermissionSet;
import com.example.bff.common.util.StringSanitizer;
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
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fetches user permissions from the delegate-graph API with fail-closed behavior.
 *
 * <p>The API returns a flat list of permissions with temporal validity (startDate, stopDate, active).
 * This service groups them by dependent (eid) and converts to the internal PermissionSet structure.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.authz.enabled", havingValue = "true")
public class PermissionsFetchService {

    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_@.-]{1,128}$");
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

    /**
     * Fetch permissions for a user from the delegate-graph API.
     *
     * @param userId The user's ID (HSID UUID)
     * @return PermissionSet with all dependents and their permissions
     */
    @NonNull
    public Mono<PermissionSet> fetchPermissions(@NonNull String userId) {
        if (!isValidUserId(userId)) {
            log.warn("Invalid user ID format in fetchPermissions");
            return Mono.just(PermissionSet.empty(userId, "individual"));
        }

        String correlationId = UUID.randomUUID().toString();

        log.debug("Fetching permissions for user {} with correlationId {}",
                StringSanitizer.forLog(userId), correlationId);

        return webClient.get()
                .uri("/api/internal/permissions/{userId}", userId)
                .header("X-Correlation-Id", correlationId)
                .retrieve()
                .bodyToMono(PermissionsApiResponse.class)
                .map(response -> toPermissionSet(userId, response))
                .doOnSuccess(p -> log.info(
                        "Fetched permissions for {} dependents for user {} (correlationId={})",
                        p.dependents() != null ? p.dependents().size() : 0,
                        StringSanitizer.forLog(userId),
                        correlationId))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Permissions API error for user {}: {} (correlationId={})",
                            StringSanitizer.forLog(userId),
                            StringSanitizer.forLog(e.getStatusCode() + " " + e.getStatusText()),
                            correlationId);
                    return Mono.just(PermissionSet.empty(userId, "individual"));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Failed to fetch permissions for user {}: {} (correlationId={})",
                            StringSanitizer.forLog(userId),
                            StringSanitizer.forLog(e.getMessage()),
                            correlationId);
                    return Mono.just(PermissionSet.empty(userId, "individual"));
                });
    }

    /**
     * Convert the flat API response to a grouped PermissionSet.
     *
     * <p>Groups permissions by dependent eid and creates Map<DelegateType, DelegatePermission> for each.
     */
    private PermissionSet toPermissionSet(String userId, PermissionsApiResponse response) {
        if (response == null || response.permissions() == null || response.permissions().isEmpty()) {
            return PermissionSet.empty(userId, "individual");
        }

        // Group permissions by eid (dependent)
        Map<String, List<DelegatePermissionEntry>> groupedByDependent = response.permissions().stream()
                .filter(Objects::nonNull)
                .filter(entry -> entry.eid() != null && !entry.eid().isBlank())
                .collect(Collectors.groupingBy(DelegatePermissionEntry::eid));

        // Convert each group to DependentAccess
        List<DependentAccess> dependents = groupedByDependent.entrySet().stream()
                .map(entry -> toDependentAccess(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        // Determine persona based on dependents
        String persona = dependents.isEmpty() ? "individual" : "parent";

        Instant expiresAt = Instant.now().plusSeconds(DEFAULT_CACHE_TTL_SECONDS);

        return new PermissionSet(
                userId,
                persona,
                dependents,
                Instant.now(),
                expiresAt
        );
    }

    /**
     * Convert a list of permission entries for one dependent to DependentAccess.
     */
    private DependentAccess toDependentAccess(String eid, List<DelegatePermissionEntry> entries) {
        Map<DelegateType, DelegatePermission> permissions = new EnumMap<>(DelegateType.class);

        for (DelegatePermissionEntry entry : entries) {
            Optional<DelegateType> typeOpt = toDelegateType(entry.delegateType());
            if (typeOpt.isPresent()) {
                DelegatePermission permission = new DelegatePermission(
                        entry.startDate(),
                        entry.stopDate(),
                        entry.active()
                );
                permissions.put(typeOpt.get(), permission);
            }
        }

        return new DependentAccess(
                eid,
                null,  // dependentName not provided by delegate-graph API
                permissions,
                null   // relationship not provided by delegate-graph API
        );
    }

    /**
     * Convert string delegate type to enum.
     */
    private Optional<DelegateType> toDelegateType(String delegateType) {
        if (delegateType == null || delegateType.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(DelegateType.valueOf(delegateType.toUpperCase()));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown delegate type: {}", StringSanitizer.forLog(delegateType));
            return Optional.empty();
        }
    }

    private boolean isValidUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return SAFE_ID_PATTERN.matcher(userId).matches();
    }
}
