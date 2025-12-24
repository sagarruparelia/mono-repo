package com.example.bff.security.service;

import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.MemberIdType;
import com.example.bff.security.context.Persona;
import com.example.bff.security.exception.AuthorizationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class IdpPersonaValidator {

    private static final Map<MemberIdType, Set<Persona>> ALLOWED_PERSONAS = Map.of(
            MemberIdType.HSID, Set.of(Persona.SELF, Persona.DELEGATE),
            MemberIdType.OHID, Set.of(Persona.CASE_WORKER),
            MemberIdType.MSID, Set.of(Persona.AGENT, Persona.CONFIG_SPECIALIST)
    );

    public Mono<AuthContext> validate(AuthContext context) {
        Set<Persona> allowed = ALLOWED_PERSONAS.get(context.loggedInMemberIdType());

        if (allowed == null) {
            log.error("Unknown member ID type: {}", context.loggedInMemberIdType());
            return Mono.error(new AuthorizationException(
                    "Unknown member ID type: " + context.loggedInMemberIdType()));
        }

        if (!allowed.contains(context.persona())) {
            log.warn("Persona {} not allowed for IDP {}. Allowed: {}",
                    context.persona(), context.loggedInMemberIdType(), allowed);
            return Mono.error(new AuthorizationException(
                    String.format("Persona %s is not allowed for IDP %s. Allowed personas: %s",
                            context.persona(), context.loggedInMemberIdType(), allowed)));
        }

        log.debug("IDP-Persona validation passed: {} -> {}",
                context.loggedInMemberIdType(), context.persona());
        return Mono.just(context);
    }
}
