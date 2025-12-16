package com.example.bff.authz.abac.policy;

import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import org.springframework.stereotype.Component;

/**
 * Policy: Youth (individual persona) can access their own documents.
 * This has the highest priority for documents - owner always has full access.
 *
 * Rule: ALLOW any action on DOCUMENT WHERE
 *   subject.authType = HSID AND
 *   subject.persona = "individual" AND
 *   subject.userId = resource.ownerId
 */
@Component
public class YouthOwnerDocumentPolicy implements Policy {

    @Override
    public String getPolicyId() {
        return "YOUTH_OWNER_DOCUMENT";
    }

    @Override
    public String getDescription() {
        return "Youth (individual) can fully manage their own documents";
    }

    @Override
    public int getPriority() {
        return 150;  // Higher priority - owner check first
    }

    @Override
    public boolean appliesTo(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        return subject.isHsid()
                && subject.hasPersona("individual")
                && resource.isDocument();
    }

    @Override
    public PolicyDecision evaluate(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        if (!appliesTo(subject, resource, action)) {
            return PolicyDecision.notApplicable(getPolicyId());
        }

        // Youth can access their own documents
        if (subject.userId().equals(resource.ownerId())) {
            return PolicyDecision.allow(getPolicyId(),
                    String.format("Youth %s owns document %s", subject.userId(), resource.id()));
        }

        // Individual cannot access other members' documents
        return PolicyDecision.deny(getPolicyId(),
                String.format("Individual %s cannot access documents of member %s",
                        subject.userId(), resource.ownerId()));
    }
}
