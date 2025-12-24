package com.example.bff.client.delegate;

import com.example.bff.client.WebClientFactory;
import com.example.bff.client.cache.ClientCache;
import com.example.bff.exception.ApiException;
import com.example.bff.security.context.DelegateType;
import com.example.bff.security.session.ManagedMember;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DelegateGraphClient {

    private static final String CACHE_KEY_PREFIX = "delegate:";
    private static final Set<DelegateType> REQUIRED_DELEGATE_TYPES = Set.of(DelegateType.RPR, DelegateType.DAA);

    private final WebClient webClient;
    private final ClientCache<List<ManagedMember>> cache;

    public DelegateGraphClient(WebClientFactory webClientFactory, ClientCache<List<ManagedMember>> cache) {
        this.webClient = webClientFactory.delegateGraphClient();
        this.cache = cache;
    }

    // Filters for active permissions with required delegate types (RPR + DAA)
    public Flux<ManagedMember> getManagedMembers(String enterpriseId) {
        String cacheKey = CACHE_KEY_PREFIX + enterpriseId;

        return cache.getOrCompute(cacheKey, fetchManagedMembersFromApi(enterpriseId))
                .flatMapMany(Flux::fromIterable);
    }

    private Mono<List<ManagedMember>> fetchManagedMembersFromApi(String enterpriseId) {
        log.debug("Fetching managed members for enterpriseId: {}", enterpriseId);

        String query = """
                query GetDelegatePermissions($enterpriseId: String!) {
                    delegatePermissions(enterpriseId: $enterpriseId) {
                        delegateType
                        firstName
                        lastName
                        birthDate
                        enterpriseId
                        startDate
                        stopDate
                        active
                    }
                }
                """;

        Map<String, Object> request = Map.of(
                "query", query,
                "variables", Map.of("enterpriseId", enterpriseId)
        );

        return webClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new ApiException(
                                        "DelegateGraph",
                                        response.statusCode(),
                                        "Delegate graph API error",
                                        body))))
                .bodyToMono(DelegateGraphResponse.class)
                .map(this::processResponse)
                .doOnNext(members -> log.debug("Retrieved {} managed members for enterpriseId: {}",
                        members.size(), enterpriseId))
                .doOnError(e -> {
                    if (!(e instanceof ApiException)) {
                        log.error("Failed to fetch managed members for enterpriseId: {}", enterpriseId, e);
                    }
                });
    }

    private List<ManagedMember> processResponse(DelegateGraphResponse response) {
        if (response.getData() == null || response.getData().getDelegatePermissions() == null) {
            return List.of();
        }

        // Group by enterpriseId and collect delegate types
        Map<String, List<DelegatePermission>> permissionsByMember = response.getData().getDelegatePermissions()
                .stream()
                .filter(DelegatePermission::isCurrentlyValid)
                .collect(Collectors.groupingBy(DelegatePermission::getEnterpriseId));

        // Convert to ManagedMember, filtering for those with required delegate types
        return permissionsByMember.entrySet().stream()
                .map(entry -> {
                    List<DelegatePermission> permissions = entry.getValue();
                    Set<DelegateType> delegateTypes = permissions.stream()
                            .map(DelegatePermission::getDelegateType)
                            .collect(Collectors.toSet());

                    // Take member info from first permission
                    DelegatePermission first = permissions.get(0);

                    return new ManagedMember(
                            entry.getKey(),
                            first.getFirstName(),
                            first.getLastName(),
                            first.getBirthDate(),
                            delegateTypes);
                })
                .filter(member -> member.delegateTypes().containsAll(REQUIRED_DELEGATE_TYPES))
                .toList();
    }

    @lombok.Data
    public static class DelegateGraphResponse {
        private DelegateData data;
    }

    @lombok.Data
    public static class DelegateData {
        private List<DelegatePermission> delegatePermissions;
    }
}
