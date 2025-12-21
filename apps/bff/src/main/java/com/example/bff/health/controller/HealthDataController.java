package com.example.bff.health.controller;

import com.example.bff.auth.model.AuthPrincipal;
import com.example.bff.auth.model.DelegateType;
import com.example.bff.auth.model.Persona;
import com.example.bff.authz.annotation.RequirePersona;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.health.dto.AllergyResponse;
import com.example.bff.health.dto.ConditionResponse;
import com.example.bff.health.dto.HealthDataApiResponse;
import com.example.bff.health.dto.ImmunizationResponse;
import com.example.bff.health.service.HealthDataOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final String VALIDATED_ENTERPRISE_ID = "VALIDATED_ENTERPRISE_ID";

    private final HealthDataOrchestrator orchestrator;

    /**
     * Get immunization records for the authenticated member.
     *
     * <p>Authorization handled by {@link RequirePersona} + PersonaAuthorizationFilter:
     * <ul>
     *   <li>INDIVIDUAL_SELF/PROXY: Access own data (enterpriseId from principal)</li>
     *   <li>DELEGATE: Access dependent data (enterpriseId validated from X-Enterprise-Id header)</li>
     * </ul>
     */
    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @GetMapping("/immunization")
    public Mono<ResponseEntity<HealthDataApiResponse<ImmunizationResponse.ImmunizationDto>>> getImmunizations(
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        String enterpriseId = getTargetEnterpriseId(exchange, principal);
        log.debug("Getting immunizations for enterpriseId={}", StringSanitizer.forLog(enterpriseId));

        return orchestrator.getImmunizations(enterpriseId, null)
                .map(HealthDataApiResponse::fromImmunizations)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
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

        String enterpriseId = getTargetEnterpriseId(exchange, principal);
        log.debug("Getting allergies for enterpriseId={}", StringSanitizer.forLog(enterpriseId));

        return orchestrator.getAllergies(enterpriseId, null)
                .map(HealthDataApiResponse::fromAllergies)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
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

        String enterpriseId = getTargetEnterpriseId(exchange, principal);
        log.debug("Getting conditions for enterpriseId={}", StringSanitizer.forLog(enterpriseId));

        return orchestrator.getConditions(enterpriseId, null)
                .map(HealthDataApiResponse::fromConditions)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
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

        String enterpriseId = getTargetEnterpriseId(exchange, principal);
        log.debug("Refreshing health data for enterpriseId={}", StringSanitizer.forLog(enterpriseId));

        return orchestrator.refreshAllHealthData(enterpriseId, null)
                .thenReturn(ResponseEntity.ok().<Void>build());
    }

    /**
     * Get target enterprise ID from exchange attributes or principal.
     *
     * <p>For DELEGATE persona, PersonaAuthorizationFilter validates permissions
     * and stores the validated enterprise ID in exchange attributes.</p>
     *
     * @param exchange  The server web exchange
     * @param principal The authenticated principal
     * @return The validated enterprise ID to use for data access
     */
    private String getTargetEnterpriseId(ServerWebExchange exchange, AuthPrincipal principal) {
        // Use validated ID from filter (set for DELEGATE persona)
        String validatedId = exchange.getAttribute(VALIDATED_ENTERPRISE_ID);
        if (validatedId != null && !validatedId.isBlank()) {
            return validatedId;
        }
        // Fall back to principal's enterprise ID (INDIVIDUAL_SELF, PROXY)
        return principal.enterpriseId();
    }
}
