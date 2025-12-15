package com.example.bff.authz.abac.engine;

import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.authz.abac.policy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 */
@Component
public class AbacPolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(AbacPolicyEngine.class);

    private final List<Policy> policies;

    public AbacPolicyEngine() {
        // Register all policies, sorted by priority (highest first)
        this.policies = List.of(
                new HsidViewSensitivePolicy(),
                new HsidViewDependentPolicy(),
                new ProxyViewSensitivePolicy(),
                new ProxyViewMemberPolicy()
        ).stream()
                .sorted(Comparator.comparingInt(Policy::getPriority).reversed())
                .toList();

        log.info("ABAC Policy Engine initialized with {} policies", policies.size());
        policies.forEach(p -> log.debug("  - {} (priority={}): {}",
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
