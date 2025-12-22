package com.example.bff.authz.service;

import com.example.bff.auth.model.DelegatePermission;
import com.example.bff.auth.model.DelegateType;
import com.example.bff.authz.dto.PermissionsApiResponse;
import com.example.bff.authz.dto.PermissionsApiResponse.DelegatePermissionEntry;
import com.example.bff.authz.model.ManagedMemberAccess;
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

    @NonNull
    public Mono<PermissionSet> fetchPermissions(@NonNull String hsidUuid) {
        if (!isValidHsidUuid(hsidUuid)) {
            log.warn("Invalid hsidUuid format in fetchPermissions");
            return Mono.just(PermissionSet.empty(hsidUuid, "individual"));
        }

        String correlationId = UUID.randomUUID().toString();

        log.debug("Fetching permissions for hsidUuid {} with correlationId {}",
                StringSanitizer.forLog(hsidUuid), correlationId);

        return webClient.get()
                .uri("/api/internal/permissions/{hsidUuid}", hsidUuid)
                .header("X-Correlation-Id", correlationId)
                .retrieve()
                .bodyToMono(PermissionsApiResponse.class)
                .map(response -> toPermissionSet(hsidUuid, response))
                .doOnSuccess(p -> log.info(
                        "Fetched permissions for {} managed members for hsidUuid {} (correlationId={})",
                        p.managedMembers() != null ? p.managedMembers().size() : 0,
                        StringSanitizer.forLog(hsidUuid),
                        correlationId))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Permissions API error for hsidUuid {}: {} (correlationId={})",
                            StringSanitizer.forLog(hsidUuid),
                            StringSanitizer.forLog(e.getStatusCode() + " " + e.getStatusText()),
                            correlationId);
                    return Mono.just(PermissionSet.empty(hsidUuid, "individual"));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Failed to fetch permissions for hsidUuid {}: {} (correlationId={})",
                            StringSanitizer.forLog(hsidUuid),
                            StringSanitizer.forLog(e.getMessage()),
                            correlationId);
                    return Mono.just(PermissionSet.empty(hsidUuid, "individual"));
                });
    }

    private PermissionSet toPermissionSet(String hsidUuid, PermissionsApiResponse response) {
        if (response == null || response.permissions() == null || response.permissions().isEmpty()) {
            return PermissionSet.empty(hsidUuid, "individual");
        }

        Map<String, List<DelegatePermissionEntry>> groupedByMember = response.permissions().stream()
                .filter(Objects::nonNull)
                .filter(entry -> entry.enterpriseId() != null && !entry.enterpriseId().isBlank())
                .collect(Collectors.groupingBy(DelegatePermissionEntry::enterpriseId));

        List<ManagedMemberAccess> managedMembers = groupedByMember.entrySet().stream()
                .map(entry -> toManagedMemberAccess(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        String persona = managedMembers.isEmpty() ? "individual" : "parent";

        Instant expiresAt = Instant.now().plusSeconds(DEFAULT_CACHE_TTL_SECONDS);

        return new PermissionSet(
                hsidUuid,
                persona,
                managedMembers,
                Instant.now(),
                expiresAt
        );
    }

    private ManagedMemberAccess toManagedMemberAccess(String enterpriseId, List<DelegatePermissionEntry> entries) {
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

        return new ManagedMemberAccess(
                enterpriseId,
                null,
                permissions,
                null
        );
    }

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

    private boolean isValidHsidUuid(String hsidUuid) {
        if (hsidUuid == null || hsidUuid.isBlank()) {
            return false;
        }
        return SAFE_ID_PATTERN.matcher(hsidUuid).matches();
    }
}
