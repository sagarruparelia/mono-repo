package com.example.bff.authz.annotation;

import com.example.bff.auth.model.DelegateType;
import com.example.bff.auth.model.Persona;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which personas are allowed to access an endpoint.
 *
 * <p>This annotation is processed by {@link com.example.bff.authz.filter.PersonaAuthorizationFilter}
 * which runs after authentication is complete.</p>
 *
 * <h3>Basic Usage:</h3>
 * <pre>{@code
 * @RequirePersona({Persona.INDIVIDUAL_SELF, Persona.DELEGATE})
 * @GetMapping("/profile")
 * public Mono<ResponseEntity<Profile>> getProfile(AuthPrincipal principal) { ... }
 * }</pre>
 *
 * <h3>With Required Delegate Types:</h3>
 * <pre>{@code
 * @RequirePersona(value = {Persona.DELEGATE}, requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
 * @GetMapping("/health-records")
 * public Mono<ResponseEntity<...>> getHealthRecords(AuthPrincipal principal) { ... }
 * }</pre>
 *
 * <h3>Proxy-only Access:</h3>
 * <pre>{@code
 * @RequirePersona({Persona.CASE_WORKER, Persona.AGENT, Persona.CONFIG_SPECIALIST})
 * @GetMapping("/case-notes")
 * public Mono<ResponseEntity<...>> getCaseNotes(AuthPrincipal principal) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePersona {

    /**
     * List of personas allowed to access this endpoint.
     * The user must have one of these personas.
     */
    Persona[] value();

    /**
     * Required delegate types when persona is DELEGATE.
     * If specified, the user must have ALL listed delegate types for the target dependent.
     * Ignored for non-DELEGATE personas.
     */
    DelegateType[] requiredDelegates() default {};
}
