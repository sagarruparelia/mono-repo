package com.example.bff.health.controller;

import com.example.bff.health.model.Allergy;
import com.example.bff.health.model.HealthDataResponse;
import com.example.bff.health.model.Immunization;
import com.example.bff.health.model.PaginationRequest;
import com.example.bff.health.service.HealthService;
import com.example.bff.security.annotation.MfeEnabled;
import com.example.bff.security.annotation.RequiredPersona;
import com.example.bff.security.annotation.ResolvedAuth;
import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.Persona;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @PostMapping("/immunization/read")
    @MfeEnabled
    @RequiredPersona({Persona.SELF, Persona.DELEGATE, Persona.AGENT, Persona.CASE_WORKER, Persona.CONFIG_SPECIALIST})
    public Mono<HealthDataResponse<Immunization>> getImmunizations(
            @ResolvedAuth AuthContext auth,
            @Valid @RequestBody PaginationRequest request) {

        log.debug("POST /immunization/read - enterpriseId: {}, page: {}, size: {}",
                auth.enterpriseId(), request.page(), request.size());
        return healthService.getImmunizations(auth.enterpriseId(), request.page(), request.size());
    }

    @PostMapping("/allergy/read")
    @MfeEnabled
    @RequiredPersona({Persona.SELF, Persona.DELEGATE, Persona.AGENT, Persona.CASE_WORKER, Persona.CONFIG_SPECIALIST})
    public Mono<HealthDataResponse<Allergy>> getAllergies(
            @ResolvedAuth AuthContext auth,
            @Valid @RequestBody PaginationRequest request) {

        log.debug("POST /allergy/read - enterpriseId: {}, page: {}, size: {}",
                auth.enterpriseId(), request.page(), request.size());
        return healthService.getAllergies(auth.enterpriseId(), request.page(), request.size());
    }
}
