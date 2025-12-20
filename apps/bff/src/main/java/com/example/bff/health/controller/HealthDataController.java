package com.example.bff.health.controller;

import com.example.bff.auth.model.AuthContext;
import com.example.bff.auth.util.AuthContextResolver;
import com.example.bff.common.util.StringSanitizer;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** REST controller for health data endpoints with dual authentication (HSID session + OAuth2 proxy). */
@Slf4j
@RestController
@RequestMapping("/api/1.0.0/health")
@Validated
@RequiredArgsConstructor
public class HealthDataController {

    private static final String MEMBER_ID_PATTERN = "^[a-zA-Z0-9_-]{1,128}$";
    private static final String X_MEMBER_ID_HEADER = "X-Member-Id";
    private static final String SESSION_COOKIE = "BFF_SESSION";

    private final HealthDataOrchestrator orchestrator;
    private final AbacAuthorizationService authorizationService;

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

    @PostMapping("/refresh")
    public Mono<ResponseEntity<Void>> refreshHealthData(
            @RequestParam(required = false)
            @Pattern(regexp = MEMBER_ID_PATTERN, message = "Invalid member ID format") String memberId,
            ServerWebExchange exchange) {

        return authorizeAndExecute(exchange, memberId,
                context -> orchestrator.refreshAllHealthData(context.effectiveMemberId, context.apiIdentifier)
                        .thenReturn(ResponseEntity.ok().<Void>build()));
    }

    private <T> Mono<ResponseEntity<T>> authorizeAndExecute(
            ServerWebExchange exchange,
            String requestMemberId,
            java.util.function.Function<MemberContext, Mono<ResponseEntity<T>>> operation) {

        return resolveMemberContext(exchange, requestMemberId)
                .flatMap(context -> {
                    AuthContext authContext = AuthContextResolver.resolve(exchange).orElse(null);
                    if (authContext == null) {
                        log.warn("No auth context available");
                        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<T>build());
                    }

                    // PROXY: Skip ABAC - authorization delegated to consumer
                    if (authContext.isProxy()) {
                        log.debug("Proxy auth - skipping ABAC, authZ delegated to consumer");
                        return operation.apply(context);
                    }

                    // HSID: Apply ABAC authorization
                    return authorizeHsid(exchange, context.effectiveMemberId)
                            .<ResponseEntity<T>>flatMap(decision -> {
                                if (decision.isAllowed()) {
                                    return operation.apply(context);
                                }
                                log.warn("Authorization denied for health data access: memberId={}, reason={}",
                                        StringSanitizer.forLog(context.effectiveMemberId), decision.reason());
                                return Mono.just(this.<T>buildForbiddenResponse(decision));
                            });
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    log.warn("Could not resolve member context");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).<T>build();
                }));
    }

    private Mono<PolicyDecision> authorizeHsid(ServerWebExchange exchange, String memberId) {
        if (authorizationService == null) {
            log.debug("ABAC service not available - allowing request");
            return Mono.just(PolicyDecision.allow("ABAC_DISABLED", "ABAC authorization disabled"));
        }

        // Get session from cookie
        var sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE);
        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            log.debug("No session cookie found");
            return Mono.just(PolicyDecision.deny("NO_SESSION", "No session found"));
        }

        return authorizationService.buildHsidSubject(sessionCookie.getValue())
                .flatMap(subject -> {
                    ResourceAttributes resource = ResourceAttributes.healthData(memberId);
                    return authorizationService.authorize(subject, resource, Action.VIEW, exchange.getRequest());
                })
                .switchIfEmpty(Mono.just(PolicyDecision.deny("NO_SUBJECT", "Could not build subject")));
    }

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
                    log.warn("Proxy request missing X-Member-Id header");
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
                    log.warn("HSID request has no member ID");
                    return null;
                }
            }

            log.debug("Resolved member context: memberId={}, authType={}",
                    StringSanitizer.forLog(effectiveMemberId), authContext.authType());

            return new MemberContext(effectiveMemberId, apiIdentifier);
        }).onErrorResume(e -> {
            log.error("Failed to resolve member context: {}", e.getMessage());
            return Mono.empty();
        });
    }

    private <T> ResponseEntity<T> buildForbiddenResponse(PolicyDecision decision) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .header("X-Policy-Id", decision.policyId())
                .header("X-Policy-Reason", sanitizeHeader(decision.reason()))
                .build();
    }

    private String sanitizeHeader(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace("\n", " ").replace("\r", " ");
        return sanitized.substring(0, Math.min(sanitized.length(), 200));
    }

    private record MemberContext(String effectiveMemberId, String apiIdentifier) {}
}
