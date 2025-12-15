package com.example.bff.authz.abac.policy;

import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;

/**
 * ABAC Policy interface - defines a single access control rule.
 */
public interface Policy {

    /**
     * Unique identifier for this policy.
     */
    String getPolicyId();

    /**
     * Human-readable description of the policy.
     */
    String getDescription();

    /**
     * Priority of this policy (higher = evaluated first).
     * Used for policy ordering when multiple policies match.
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Evaluate this policy against the given attributes.
     *
     * @param subject  The user/subject attributes
     * @param resource The resource being accessed
     * @param action   The action being performed
     * @return PolicyDecision (ALLOW, DENY, or NOT_APPLICABLE)
     */
    PolicyDecision evaluate(SubjectAttributes subject, ResourceAttributes resource, Action action);

    /**
     * Check if this policy applies to the given context.
     * Used for quick filtering before full evaluation.
     */
    default boolean appliesTo(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        return true;
    }
}
