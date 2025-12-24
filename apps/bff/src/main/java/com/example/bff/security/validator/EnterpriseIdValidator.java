package com.example.bff.security.validator;

import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.DelegateType;
import com.example.bff.security.context.Persona;
import com.example.bff.security.exception.AuthorizationException;
import com.example.bff.security.exception.SecurityIncidentException;
import com.example.bff.security.session.ManagedMember;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

// SELF: uses own enterpriseId. DELEGATE: must be in managed members. Others: any (partner-validated upstream)
@Slf4j
@Component
public class EnterpriseIdValidator {

    public Mono<AuthContext> resolveEnterpriseId(
            AuthContext ctx,
            String enterpriseIdParam,
            DelegateType[] requiredDelegates) {

        return switch (ctx.persona()) {
            case SELF -> {
                // SELF uses their own enterpriseId - no param needed
                log.debug("SELF persona using own enterpriseId: {}", ctx.enterpriseId());
                yield Mono.just(ctx);
            }

            case DELEGATE -> {
                if (enterpriseIdParam == null || enterpriseIdParam.isBlank()) {
                    yield Mono.error(new AuthorizationException(
                            "enterpriseId is required for DELEGATE persona"));
                }
                yield validateDelegateAccess(ctx, enterpriseIdParam, requiredDelegates);
            }

            case AGENT, CASE_WORKER, CONFIG_SPECIALIST -> {
                if (enterpriseIdParam == null || enterpriseIdParam.isBlank()) {
                    yield Mono.error(new AuthorizationException(
                            "enterpriseId is required for " + ctx.persona() + " persona"));
                }

                AuthContext updatedCtx = ctx.withActiveEnterpriseId(enterpriseIdParam, Set.of());
                log.debug("{} accessing enterpriseId: {}", ctx.persona(), enterpriseIdParam);

                yield Mono.just(updatedCtx);
            }
        };
    }

    private Mono<AuthContext> validateDelegateAccess(
            AuthContext ctx,
            String enterpriseId,
            DelegateType[] requiredDelegates) {

        ManagedMember managedMember = ctx.managedMembersMap().get(enterpriseId);

        if (managedMember == null) {
            log.error("SECURITY INCIDENT: DELEGATE attempted access to unauthorized enterpriseId. " +
                            "loggedInMember={}, attemptedEnterpriseId={}",
                    ctx.loggedInMemberIdValue(), enterpriseId);

            return Mono.error(new SecurityIncidentException(
                    "Unauthorized access attempt to enterpriseId: " + enterpriseId,
                    ctx.loggedInMemberIdValue(),
                    enterpriseId
            ));
        }

        // Update context with active enterpriseId and delegate types for this managed member
        Set<DelegateType> activeDelegateTypes = managedMember.delegateTypes();
        AuthContext updatedCtx = ctx.withActiveEnterpriseId(enterpriseId, activeDelegateTypes);

        log.debug("DELEGATE validated for enterpriseId={}, delegateTypes={}",
                enterpriseId, activeDelegateTypes);

        return validateRequiredDelegateTypes(updatedCtx, requiredDelegates);
    }

    public Mono<AuthContext> validateRequiredDelegateTypes(AuthContext ctx, DelegateType[] requiredDelegates) {
        if (ctx.persona() != Persona.DELEGATE || requiredDelegates == null || requiredDelegates.length == 0) {
            return Mono.just(ctx);
        }

        Set<DelegateType> required = Arrays.stream(requiredDelegates)
                .collect(Collectors.toSet());

        Set<DelegateType> active = ctx.activeDelegateTypes();

        if (!active.containsAll(required)) {
            log.warn("DELEGATE missing required delegate types. Required: {}, Active: {}",
                    required, active);
            return Mono.error(new AuthorizationException(
                    "Missing required delegate types: " + required + ". Active types: " + active));
        }

        return Mono.just(ctx);
    }
}
