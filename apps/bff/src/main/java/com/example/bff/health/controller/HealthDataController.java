package com.example.bff.health.controller;

import com.example.bff.auth.model.AuthPrincipal;
import com.example.bff.auth.model.DelegateType;
import com.example.bff.auth.model.Persona;
import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.authz.abac.service.AbacAuthorizationService;
import com.example.bff.authz.annotation.RequirePersona;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.health.dto.AllergyResponse;
import com.example.bff.health.dto.ConditionResponse;
import com.example.bff.health.dto.HealthDataApiResponse;
import com.example.bff.health.dto.ImmunizationResponse;
import com.example.bff.health.service.HealthDataOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * REST controller for health data endpoints with dual authentication (HSID session + OAuth2 proxy).
 *
 * <p>Uses {@link RequirePersona} for declarative persona-based authorization.
 * Health data access requires specific delegate permissions for DELEGATE persona.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/1.0.0/health")
@Validated
@RequiredArgsConstructor
public class HealthDataController {

    private final HealthDataOrchestrator orchestrator;
    private final AbacAuthorizationService authorizationService;

    /**
     * Get immunization records for the authenticated member.
     */
    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @GetMapping("/immunization")
    public Mono<ResponseEntity<HealthDataApiResponse<ImmunizationResponse.ImmunizationDto>>> getImmunizations(
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        return authorizeAndExecute(principal, exchange,
                () -> orchestrator.getImmunizations(principal.enterpriseId(), null)
                        .map(HealthDataApiResponse::fromImmunizations)
                        .map(ResponseEntity::ok)
                        .defaultIfEmpty(ResponseEntity.notFound().build()));
    }

    /**
     * Get allergy records for the authenticated member.
     */
    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @GetMapping("/allergy")
    public Mono<ResponseEntity<HealthDataApiResponse<AllergyResponse.AllergyDto>>> getAllergies(
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        return authorizeAndExecute(principal, exchange,
                () -> orchestrator.getAllergies(principal.enterpriseId(), null)
                        .map(HealthDataApiResponse::fromAllergies)
                        .map(ResponseEntity::ok)
                        .defaultIfEmpty(ResponseEntity.notFound().build()));
    }

    /**
     * Get condition records for the authenticated member.
     */
    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @GetMapping("/condition")
    public Mono<ResponseEntity<HealthDataApiResponse<ConditionResponse.ConditionDto>>> getConditions(
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        return authorizeAndExecute(principal, exchange,
                () -> orchestrator.getConditions(principal.enterpriseId(), null)
                        .map(HealthDataApiResponse::fromConditions)
                        .map(ResponseEntity::ok)
                        .defaultIfEmpty(ResponseEntity.notFound().build()));
    }

    /**
     * Refresh all health data for the authenticated member.
     */
    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @PostMapping("/refresh")
    public Mono<ResponseEntity<Void>> refreshHealthData(
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        return authorizeAndExecute(principal, exchange,
                () -> orchestrator.refreshAllHealthData(principal.enterpriseId(), null)
                        .thenReturn(ResponseEntity.ok().<Void>build()));
    }

    /**
     * Authorize and execute an operation.
     * - PROXY: Skip ABAC (authorization delegated to consumer)
     * - HSID: Apply ABAC authorization using SubjectAttributes
     */
    private <T> Mono<ResponseEntity<T>> authorizeAndExecute(
            AuthPrincipal principal,
            ServerWebExchange exchange,
            java.util.function.Supplier<Mono<ResponseEntity<T>>> operation) {

        log.debug("Health data access: persona={}, enterpriseId={}",
                principal.persona(), StringSanitizer.forLog(principal.enterpriseId()));

        // PROXY: Skip ABAC - authorization delegated to consumer
        if (principal.isProxyAuth()) {
            log.debug("Proxy auth - skipping ABAC, authZ delegated to consumer");
            return operation.get();
        }

        // HSID: Apply ABAC authorization
        return authorizeHsid(principal, exchange)
                .<ResponseEntity<T>>flatMap(decision -> {
                    if (decision.isAllowed()) {
                        return operation.get();
                    }
                    log.warn("Authorization denied for health data access: memberId={}, reason={}",
                            StringSanitizer.forLog(principal.enterpriseId()), decision.reason());
                    return Mono.just(this.<T>buildForbiddenResponse(decision));
                });
    }

    /**
     * Authorize HSID user using ABAC.
     */
    private Mono<PolicyDecision> authorizeHsid(AuthPrincipal principal, ServerWebExchange exchange) {
        if (authorizationService == null) {
            log.debug("ABAC service not available - allowing request");
            return Mono.just(PolicyDecision.allow("ABAC_DISABLED", "ABAC authorization disabled"));
        }

        // Build SubjectAttributes from AuthPrincipal
        SubjectAttributes subject = SubjectAttributes.fromPrincipal(principal);
        ResourceAttributes resource = ResourceAttributes.healthData(principal.enterpriseId());

        return authorizationService.authorize(subject, resource, Action.VIEW, exchange.getRequest())
                .switchIfEmpty(Mono.just(PolicyDecision.deny("NO_DECISION", "ABAC returned no decision")));
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
}
