package com.example.bff.authz.abac.policy;

import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Policy: Only config persona can view sensitive member data.
 *
 * Rule: ALLOW VIEW_SENSITIVE member WHERE
 *   subject.authType = PROXY AND
 *   subject.persona = "config"
 */
@Component
public class ProxyViewSensitivePolicy implements Policy {

    @Override
    public String getPolicyId() {
        return "PROXY_VIEW_SENSITIVE";
    }

    @Override
    public String getDescription() {
        return "Only config persona can view sensitive member data";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean appliesTo(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        return subject.isProxy()
                && (resource.isMember() || resource.isDependent())
                && (action == Action.VIEW_SENSITIVE || (action == Action.VIEW && resource.isSensitive()));
    }

    @Override
    public PolicyDecision evaluate(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        if (!appliesTo(subject, resource, action)) {
            return PolicyDecision.notApplicable(getPolicyId());
        }

        if (subject.isConfig()) {
            return PolicyDecision.allow(getPolicyId(),
                    String.format("Config persona can access sensitive data for member %s", resource.id()));
        }

        return PolicyDecision.deny(getPolicyId(),
                String.format("Persona '%s' cannot access sensitive data. Only 'config' persona allowed.",
                        subject.persona()),
                Set.of("persona"));
    }
}
