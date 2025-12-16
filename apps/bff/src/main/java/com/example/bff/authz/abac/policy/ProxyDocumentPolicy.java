package com.example.bff.authz.abac.policy;

import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Policy: Proxy users (agent, case_worker, config) can access documents.
 * - Config persona: Full access to all documents
 * - Agent/Case Worker: Full access only to assigned member's documents
 *
 * Rule: ALLOW any action on DOCUMENT WHERE
 *   subject.authType = PROXY AND
 *   (subject.persona = "config" OR subject.isAssignedTo(resource.ownerId))
 */
@Component
public class ProxyDocumentPolicy implements Policy {

    @Override
    public String getPolicyId() {
        return "PROXY_DOCUMENT";
    }

    @Override
    public String getDescription() {
        return "Proxy users can access documents for assigned members (config has full access)";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean appliesTo(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        return subject.isProxy() && resource.isDocument();
    }

    @Override
    public PolicyDecision evaluate(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        if (!appliesTo(subject, resource, action)) {
            return PolicyDecision.notApplicable(getPolicyId());
        }

        // Config persona has full access to all documents
        if (subject.isConfig()) {
            return PolicyDecision.allow(getPolicyId(),
                    String.format("Config persona has full access to document %s", resource.id()));
        }

        // Agent/Case Worker must be assigned to the member
        if ((subject.isAgent() || subject.isCaseWorker()) && subject.isAssignedTo(resource.ownerId())) {
            return PolicyDecision.allow(getPolicyId(),
                    String.format("Proxy user %s (%s) is assigned to youth %s",
                            subject.operatorId(), subject.persona(), resource.ownerId()));
        }

        // Deny access - not assigned to this member
        return PolicyDecision.deny(getPolicyId(),
                String.format("Proxy persona '%s' is not assigned to youth %s",
                        subject.persona(), resource.ownerId()),
                Set.of("memberId"));
    }
}
