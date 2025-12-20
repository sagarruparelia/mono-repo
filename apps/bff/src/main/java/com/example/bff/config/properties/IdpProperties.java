package com.example.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.Set;

/**
 * Configuration for Identity Provider (IDP) to Persona mapping.
 *
 * <p>Defines which personas are allowed for each IDP type used in PROXY authentication.
 * The DualAuthWebFilter validates that the persona from X-Persona header is allowed
 * for the IDP type specified in X-IDP-Type header.</p>
 *
 * <h3>Configuration:</h3>
 * <pre>{@code
 * idp:
 *   persona-mapping:
 *     ohid:
 *       - case_worker
 *     msid:
 *       - agent
 *       - config_specialist
 * }</pre>
 *
 * @param personaMapping Map of IDP type to allowed personas
 */
@ConfigurationProperties(prefix = "idp")
public record IdpProperties(
        Map<String, Set<String>> personaMapping
) {
    /**
     * Check if a persona is allowed for the given IDP type.
     *
     * @param idpType The IDP type (e.g., "ohid", "msid")
     * @param persona The persona to check
     * @return true if the persona is allowed for the IDP
     */
    public boolean isPersonaAllowed(String idpType, String persona) {
        if (personaMapping == null || idpType == null || persona == null) {
            return false;
        }
        Set<String> allowedPersonas = personaMapping.get(idpType.toLowerCase());
        return allowedPersonas != null && allowedPersonas.contains(persona.toLowerCase());
    }

    /**
     * Get allowed personas for an IDP type.
     *
     * @param idpType The IDP type
     * @return Set of allowed personas, or empty set if IDP not configured
     */
    public Set<String> getAllowedPersonas(String idpType) {
        if (personaMapping == null || idpType == null) {
            return Set.of();
        }
        return personaMapping.getOrDefault(idpType.toLowerCase(), Set.of());
    }

    /**
     * Check if an IDP type is recognized.
     *
     * @param idpType The IDP type to check
     * @return true if the IDP type is configured
     */
    public boolean isValidIdpType(String idpType) {
        return personaMapping != null &&
               idpType != null &&
               personaMapping.containsKey(idpType.toLowerCase());
    }
}
