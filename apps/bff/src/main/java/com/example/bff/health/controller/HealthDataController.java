package com.example.bff.health.controller;

import com.example.bff.auth.model.AuthContext;
import com.example.bff.auth.util.AuthContextResolver;
import com.example.bff.health.dto.AllergyResponse;
import com.example.bff.health.dto.ConditionResponse;
import com.example.bff.health.dto.HealthDataApiResponse;
import com.example.bff.health.dto.ImmunizationResponse;
import com.example.bff.health.service.HealthDataOrchestrator;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * REST controller for health data endpoints.
 * Supports dual authentication (HSID session + OAuth2 proxy).
 */
@RestController
@RequestMapping("/api/1.0.0/health")
@Validated
public class HealthDataController {

    private static final Logger LOG = LoggerFactory.getLogger(HealthDataController.class);
    private static final String MEMBER_ID_PATTERN = "^[a-zA-Z0-9_-]{1,128}$";
    private static final String X_MEMBER_ID_HEADER = "X-Member-Id";

    private final HealthDataOrchestrator orchestrator;

    public HealthDataController(HealthDataOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Get immunization records for a member.
     * GET /api/1.0.0/health/immunization?memberId={memberId}
     *
     * For HSID: memberId defaults to logged-in user, or can specify managed member
     * For Proxy: memberId from X-Member-Id header
     */
    @GetMapping("/immunization")
    public Mono<ResponseEntity<HealthDataApiResponse<ImmunizationResponse.ImmunizationDto>>> getImmunizations(
            @RequestParam(required = false)
            @Pattern(regexp = MEMBER_ID_PATTERN, message = "Invalid member ID format") String memberId,
            ServerWebExchange exchange) {

        return resolveMemberContext(exchange, memberId)
                .flatMap(context -> orchestrator.getImmunizations(context.effectiveMemberId, context.apiIdentifier)
                        .map(HealthDataApiResponse::fromImmunizations)
                        .map(ResponseEntity::ok))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Get allergy records for a member.
     * GET /api/1.0.0/health/allergy?memberId={memberId}
     */
    @GetMapping("/allergy")
    public Mono<ResponseEntity<HealthDataApiResponse<AllergyResponse.AllergyDto>>> getAllergies(
            @RequestParam(required = false)
            @Pattern(regexp = MEMBER_ID_PATTERN, message = "Invalid member ID format") String memberId,
            ServerWebExchange exchange) {

        return resolveMemberContext(exchange, memberId)
                .flatMap(context -> orchestrator.getAllergies(context.effectiveMemberId, context.apiIdentifier)
                        .map(HealthDataApiResponse::fromAllergies)
                        .map(ResponseEntity::ok))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Get condition records for a member.
     * GET /api/1.0.0/health/condition?memberId={memberId}
     */
    @GetMapping("/condition")
    public Mono<ResponseEntity<HealthDataApiResponse<ConditionResponse.ConditionDto>>> getConditions(
            @RequestParam(required = false)
            @Pattern(regexp = MEMBER_ID_PATTERN, message = "Invalid member ID format") String memberId,
            ServerWebExchange exchange) {

        return resolveMemberContext(exchange, memberId)
                .flatMap(context -> orchestrator.getConditions(context.effectiveMemberId, context.apiIdentifier)
                        .map(HealthDataApiResponse::fromConditions)
                        .map(ResponseEntity::ok))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Force refresh health data for a member (evict cache and re-fetch).
     * POST /api/1.0.0/health/refresh?memberId={memberId}
     */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<Void>> refreshHealthData(
            @RequestParam(required = false)
            @Pattern(regexp = MEMBER_ID_PATTERN, message = "Invalid member ID format") String memberId,
            ServerWebExchange exchange) {

        return resolveMemberContext(exchange, memberId)
                .flatMap(context -> orchestrator.refreshAllHealthData(
                                context.effectiveMemberId, context.apiIdentifier)
                        .thenReturn(ResponseEntity.ok().<Void>build()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Resolve member ID and API context from auth context.
     */
    private Mono<MemberContext> resolveMemberContext(
            ServerWebExchange exchange,
            String requestMemberId) {

        return Mono.fromCallable(() -> {
            AuthContext authContext = AuthContextResolver.require(exchange);

            // Determine effective member ID based on auth type
            String effectiveMemberId;
            String apiIdentifier = null;

            if (authContext.isProxy()) {
                // Proxy: Use X-Member-Id header (required)
                effectiveMemberId = exchange.getRequest().getHeaders()
                        .getFirst(X_MEMBER_ID_HEADER);

                if (effectiveMemberId == null || effectiveMemberId.isBlank()) {
                    LOG.warn("Proxy request missing X-Member-Id header");
                    return null;
                }

                // Extract API identifier if present
                apiIdentifier = exchange.getRequest().getHeaders()
                        .getFirst("X-Api-Identifier");

            } else {
                // HSID: Use request param or default to auth context's effectiveMemberId
                if (requestMemberId != null && !requestMemberId.isBlank()) {
                    effectiveMemberId = requestMemberId;
                } else {
                    effectiveMemberId = authContext.effectiveMemberId();
                }

                if (effectiveMemberId == null || effectiveMemberId.isBlank()) {
                    LOG.warn("HSID request has no member ID");
                    return null;
                }
            }

            LOG.debug("Resolved member context: memberId={}, authType={}",
                    effectiveMemberId, authContext.authType());

            return new MemberContext(effectiveMemberId, apiIdentifier);
        }).onErrorResume(e -> {
            LOG.error("Failed to resolve member context: {}", e.getMessage());
            return Mono.empty();
        });
    }

    /**
     * Context holder for resolved member info.
     */
    private record MemberContext(String effectiveMemberId, String apiIdentifier) {}
}
