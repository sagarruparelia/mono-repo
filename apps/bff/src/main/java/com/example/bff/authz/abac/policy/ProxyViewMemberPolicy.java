package com.example.bff.authz.abac.policy;

import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;

import java.util.Set;

/**
 * Policy: Proxy user can view member if assigned to them or is config persona.
 *
 * Rule: ALLOW VIEW member WHERE
 *   subject.authType = PROXY AND
 *   (subject.persona = "config" OR subject.memberId = resource.id)
 */
public class ProxyViewMemberPolicy implements Policy {

    @Override
    public String getPolicyId() {
        return "PROXY_VIEW_MEMBER";
    }

    @Override
    public String getDescription() {
        return "Proxy user can view assigned member or config can view any member";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean appliesTo(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        return subject.isProxy()
                && (resource.isMember() || resource.isDependent())
                && action == Action.VIEW
                && !resource.isSensitive();
    }

    @Override
    public PolicyDecision evaluate(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        if (!appliesTo(subject, resource, action)) {
            return PolicyDecision.notApplicable(getPolicyId());
        }

        // Config persona has access to all members
        if (subject.isConfig()) {
            return PolicyDecision.allow(getPolicyId(),
                    String.format("Config persona has access to member %s", resource.id()));
        }

        // Agent/case worker can only access assigned member
        if (subject.isAssignedTo(resource.id())) {
            return PolicyDecision.allow(getPolicyId(),
                    String.format("User is assigned to member %s", resource.id()));
        }

        return PolicyDecision.deny(getPolicyId(),
                String.format("Persona '%s' is not assigned to member %s",
                        subject.persona(), resource.id()),
                Set.of("memberId"));
    }
}
