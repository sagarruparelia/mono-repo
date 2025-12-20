package com.example.bff.authz.abac.engine;

import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.authz.abac.policy.Policy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * ABAC Policy Engine - evaluates policies to make access control decisions.
 *
 * <p>Policy combining algorithm: First Applicable (deny-biased)
 * - Policies are evaluated in priority order
 * - First ALLOW or DENY decision wins
 * - If no policy matches, default is DENY
 *
 * <p>Policies are auto-discovered via Spring component scanning.
 * To add a new policy, create a class implementing {@link Policy} and annotate with @Component.
 */
@Slf4j
@Component
public class AbacPolicyEngine {

    private final List<Policy> policies;

    public AbacPolicyEngine(List<Policy> policies) {
        // Sort injected policies by priority (highest first)
        this.policies = policies.stream()
                .sorted(Comparator.comparingInt(Policy::getPriority).reversed())
                .toList();

        log.info("ABAC Policy Engine initialized with {} policies", this.policies.size());
        this.policies.forEach(p -> log.debug("  - {} (priority={}): {}",
                p.getPolicyId(), p.getPriority(), p.getDescription()));
    }

    /**
     * Evaluate all applicable policies and return the access decision.
     *
     * @param subject  The user/subject attributes
     * @param resource The resource being accessed
     * @param action   The action being performed
     * @return PolicyDecision with ALLOW or DENY
     */
    public PolicyDecision evaluate(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        log.debug("Evaluating access: subject={}, resource={}/{}, action={}",
                subject.userId(), resource.type(), resource.id(), action);

        for (Policy policy : policies) {
            if (!policy.appliesTo(subject, resource, action)) {
                continue;
            }

            PolicyDecision decision = policy.evaluate(subject, resource, action);

            if (decision.isAllowed()) {
                log.info("Access ALLOWED by policy {}: {} (subject={}, resource={}, action={})",
                        policy.getPolicyId(), decision.reason(),
                        subject.userId(), resource.id(), action);
                return decision;
            }

            if (decision.isDenied()) {
                log.warn("Access DENIED by policy {}: {} (subject={}, resource={}, action={})",
                        policy.getPolicyId(), decision.reason(),
                        subject.userId(), resource.id(), action);
                return decision;
            }

            // NOT_APPLICABLE - continue to next policy
        }

        // No policy matched - default deny
        log.warn("Access DENIED (no applicable policy): subject={}, resource={}, action={}",
                subject.userId(), resource.id(), action);
        return PolicyDecision.deny("DEFAULT_DENY",
                "No policy granted access for this request");
    }

    /**
     * Check if access is allowed (convenience method).
     */
    public boolean isAllowed(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        return evaluate(subject, resource, action).isAllowed();
    }

    /**
     * Get all registered policies.
     */
    public List<Policy> getPolicies() {
        return policies;
    }
}
