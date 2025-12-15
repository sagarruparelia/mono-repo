package com.example.bff.authz.abac.model;

import java.util.Set;

/**
 * ABAC Policy Decision - the result of evaluating a policy.
 */
public record PolicyDecision(
        Decision decision,
        String reason,
        String policyId,
        Set<String> missingAttributes
) {
    public enum Decision {
        ALLOW,
        DENY,
        NOT_APPLICABLE  // Policy doesn't apply to this request
    }

    /**
     * Create an ALLOW decision.
     */
    public static PolicyDecision allow(String policyId, String reason) {
        return new PolicyDecision(Decision.ALLOW, reason, policyId, Set.of());
    }

    /**
     * Create a DENY decision.
     */
    public static PolicyDecision deny(String policyId, String reason) {
        return new PolicyDecision(Decision.DENY, reason, policyId, Set.of());
    }

    /**
     * Create a DENY decision with missing attributes.
     */
    public static PolicyDecision deny(String policyId, String reason, Set<String> missingAttributes) {
        return new PolicyDecision(Decision.DENY, reason, policyId, missingAttributes);
    }

    /**
     * Create a NOT_APPLICABLE decision.
     */
    public static PolicyDecision notApplicable(String policyId) {
        return new PolicyDecision(Decision.NOT_APPLICABLE, "Policy not applicable", policyId, Set.of());
    }

    /**
     * Check if access is allowed.
     */
    public boolean isAllowed() {
        return decision == Decision.ALLOW;
    }

    /**
     * Check if access is denied.
     */
    public boolean isDenied() {
        return decision == Decision.DENY;
    }

    /**
     * Check if policy is not applicable.
     */
    public boolean isNotApplicable() {
        return decision == Decision.NOT_APPLICABLE;
    }
}
