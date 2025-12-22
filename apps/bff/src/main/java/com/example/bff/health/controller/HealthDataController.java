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

@Slf4j
@RestController
@RequestMapping("/api/1.0.0/health")
@Validated
@RequiredArgsConstructor
public class HealthDataController {

    private static final String VALIDATED_ENTERPRISE_ID = "VALIDATED_ENTERPRISE_ID";

    private final HealthDataOrchestrator orchestrator;

    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @GetMapping("/immunization")
    public Mono<ResponseEntity<HealthDataApiResponse<ImmunizationResponse.ImmunizationDto>>> getImmunizations(
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        String enterpriseId = getTargetEnterpriseId(exchange, principal);
        log.debug("Getting immunizations for enterpriseId={}", StringSanitizer.forLog(enterpriseId));

        return orchestrator.getImmunizations(enterpriseId)
                .map(HealthDataApiResponse::fromImmunizations)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @GetMapping("/allergy")
    public Mono<ResponseEntity<HealthDataApiResponse<AllergyResponse.AllergyDto>>> getAllergies(
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        String enterpriseId = getTargetEnterpriseId(exchange, principal);
        log.debug("Getting allergies for enterpriseId={}", StringSanitizer.forLog(enterpriseId));

        return orchestrator.getAllergies(enterpriseId)
                .map(HealthDataApiResponse::fromAllergies)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @GetMapping("/condition")
    public Mono<ResponseEntity<HealthDataApiResponse<ConditionResponse.ConditionDto>>> getConditions(
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        String enterpriseId = getTargetEnterpriseId(exchange, principal);
        log.debug("Getting conditions for enterpriseId={}", StringSanitizer.forLog(enterpriseId));

        return orchestrator.getConditions(enterpriseId)
                .map(HealthDataApiResponse::fromConditions)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @PostMapping("/refresh")
    public Mono<ResponseEntity<Void>> refreshHealthData(
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        String enterpriseId = getTargetEnterpriseId(exchange, principal);
        log.debug("Refreshing health data for enterpriseId={}", StringSanitizer.forLog(enterpriseId));

        return orchestrator.refreshAllHealthData(enterpriseId)
                .thenReturn(ResponseEntity.ok().<Void>build());
    }

    private String getTargetEnterpriseId(ServerWebExchange exchange, AuthPrincipal principal) {
        String validatedId = exchange.getAttribute(VALIDATED_ENTERPRISE_ID);
        if (validatedId != null && !validatedId.isBlank()) {
            return validatedId;
        }
        return principal.enterpriseId();
    }
}
