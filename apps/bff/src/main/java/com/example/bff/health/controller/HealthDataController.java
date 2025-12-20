package com.example.bff.health.controller;

import com.example.bff.auth.model.AuthContext;
import com.example.bff.auth.util.AuthContextResolver;
import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.authz.abac.service.AbacAuthorizationService;
import com.example.bff.health.dto.AllergyResponse;
import com.example.bff.health.dto.ConditionResponse;
import com.example.bff.health.dto.HealthDataApiResponse;
import com.example.bff.health.dto.ImmunizationResponse;
import com.example.bff.health.service.HealthDataOrchestrator;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * REST controller for health data endpoints.
 * Supports dual authentication (HSID session + OAuth2 proxy).
 *
 * <p>Authorization:
 * <ul>
 *   <li>HSID: Uses ABAC session - individuals can view own data,
 *       parents can view dependents' data with DAA+RPR+ROI</li>
 *   <li>PROXY: Authorization delegated to consumer - BFF trusts proxy headers</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/1.0.0/health")
@Validated
public class HealthDataController {

    private static final Logger LOG = LoggerFactory.getLogger(HealthDataController.class);
    private static final String MEMBER_ID_PATTERN = "^[a-zA-Z0-9_-]{1,128}$";
    private static final String X_MEMBER_ID_HEADER = "X-Member-Id";
    private static final String SESSION_COOKIE = "BFF_SESSION";

    private final HealthDataOrchestrator orchestrator;

    @Nullable
    private final AbacAuthorizationService authorizationService;

    public HealthDataController(
            HealthDataOrchestrator orchestrator,
            @Nullable AbacAuthorizationService authorizationService) {
        this.orchestrator = orchestrator;
        this.authorizationService = authorizationService;
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

        return authorizeAndExecute(exchange, memberId,
                context -> orchestrator.getImmunizations(context.effectiveMemberId, context.apiIdentifier)
                        .map(HealthDataApiResponse::fromImmunizations)
                        .map(ResponseEntity::ok)
                        .defaultIfEmpty(ResponseEntity.notFound().build()));
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

        return authorizeAndExecute(exchange, memberId,
                context -> orchestrator.getAllergies(context.effectiveMemberId, context.apiIdentifier)
                        .map(HealthDataApiResponse::fromAllergies)
                        .map(ResponseEntity::ok)
                        .defaultIfEmpty(ResponseEntity.notFound().build()));
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

        return authorizeAndExecute(exchange, memberId,
                context -> orchestrator.getConditions(context.effectiveMemberId, context.apiIdentifier)
                        .map(HealthDataApiResponse::fromConditions)
                        .map(ResponseEntity::ok)
                        .defaultIfEmpty(ResponseEntity.notFound().build()));
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

        return authorizeAndExecute(exchange, memberId,
                context -> orchestrator.refreshAllHealthData(context.effectiveMemberId, context.apiIdentifier)
                        .thenReturn(ResponseEntity.ok().<Void>build()));
    }

    /**
     * Authorize and execute a health data operation.
     *
     * <p>Authorization behavior:
     * <ul>
     *   <li>HSID: Validates access using ABAC policies (individual can view own data,
     *       parent needs DAA+RPR+ROI for dependent's data)</li>
     *   <li>PROXY: Skips ABAC - authorization is delegated to the consumer/partner</li>
     * </ul>
     */
    private <T> Mono<ResponseEntity<T>> authorizeAndExecute(
            ServerWebExchange exchange,
            String requestMemberId,
            java.util.function.Function<MemberContext, Mono<ResponseEntity<T>>> operation) {

        return resolveMemberContext(exchange, requestMemberId)
                .flatMap(context -> {
                    AuthContext authContext = AuthContextResolver.resolve(exchange).orElse(null);
                    if (authContext == null) {
                        LOG.warn("No auth context available");
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<T>build());
                    }

                    // PROXY: Skip ABAC - authorization delegated to consumer
                    if (authContext.isProxy()) {
                        LOG.debug("Proxy auth - skipping ABAC, authZ delegated to consumer");
                        return operation.apply(context);
                    }

                    // HSID: Apply ABAC authorization
                    return authorizeHsid(exchange, context.effectiveMemberId)
                            .<ResponseEntity<T>>flatMap(decision -> {
                                if (decision.isAllowed()) {
                                    return operation.apply(context);
                                }
                                LOG.warn("Authorization denied for health data access: memberId={}, reason={}",
                                        context.effectiveMemberId, decision.reason());
                                return Mono.just(this.<T>buildForbiddenResponse(decision));
                            });
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    LOG.warn("Could not resolve member context");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).<T>build();
                }));
    }

    /**
     * Authorize HSID user access to health data.
     */
    private Mono<PolicyDecision> authorizeHsid(ServerWebExchange exchange, String memberId) {
        if (authorizationService == null) {
            LOG.debug("ABAC service not available - allowing request");
            return Mono.just(PolicyDecision.allow("ABAC_DISABLED", "ABAC authorization disabled"));
        }

        // Get session from cookie
        var sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE);
        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            LOG.debug("No session cookie found");
            return Mono.just(PolicyDecision.deny("NO_SESSION", "No session found"));
        }

        return authorizationService.buildHsidSubject(sessionCookie.getValue())
                .flatMap(subject -> {
                    ResourceAttributes resource = ResourceAttributes.healthData(memberId);
                    return authorizationService.authorize(subject, resource, Action.VIEW, exchange.getRequest());
                })
                .switchIfEmpty(Mono.just(PolicyDecision.deny("NO_SUBJECT", "Could not build subject")));
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
     * Build a forbidden response with policy decision details.
     */
    private <T> ResponseEntity<T> buildForbiddenResponse(PolicyDecision decision) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .header("X-Policy-Id", decision.policyId())
                .header("X-Policy-Reason", sanitizeHeader(decision.reason()))
                .build();
    }

    /**
     * Sanitize value for HTTP header.
     */
    private String sanitizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\n", " ").replace("\r", " ")
                .substring(0, Math.min(value.length(), 200));
    }

    /**
     * Context holder for resolved member info.
     */
    private record MemberContext(String effectiveMemberId, String apiIdentifier) {}
}
