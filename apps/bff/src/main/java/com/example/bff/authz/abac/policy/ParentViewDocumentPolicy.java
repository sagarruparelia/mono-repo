package com.example.bff.authz.abac.policy;

import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.authz.model.Permission;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Policy: Parent can view/list youth's documents with DAA + RPR + ROI.
 * All documents are treated as sensitive, requiring all three permissions.
 *
 * Rule: ALLOW VIEW/VIEW_SENSITIVE/LIST on DOCUMENT WHERE
 *   subject.authType = HSID AND
 *   subject.persona = "parent" AND
 *   subject.permissions[resource.ownerId] CONTAINS DAA, RPR, ROI
 */
@Component
public class ParentViewDocumentPolicy implements Policy {

    private static final Set<Permission> REQUIRED_PERMISSIONS =
            Set.of(Permission.DAA, Permission.RPR, Permission.ROI);

    @Override
    public String getPolicyId() {
        return "PARENT_VIEW_DOCUMENT";
    }

    @Override
    public String getDescription() {
        return "Parent can view youth's documents with DAA + RPR + ROI permissions";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean appliesTo(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        return subject.isHsid()
                && subject.isParent()
                && resource.isDocument()
                && (action == Action.VIEW || action == Action.VIEW_SENSITIVE || action == Action.LIST);
    }

    @Override
    public PolicyDecision evaluate(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        if (!appliesTo(subject, resource, action)) {
            return PolicyDecision.notApplicable(getPolicyId());
        }

        // Check permissions for the document owner (youth)
        Set<Permission> granted = subject.getPermissionsFor(resource.ownerId());

        if (granted.containsAll(REQUIRED_PERMISSIONS)) {
            return PolicyDecision.allow(getPolicyId(),
                    String.format("Parent has required permissions %s to view documents for youth %s",
                            REQUIRED_PERMISSIONS, resource.ownerId()));
        }

        // Identify missing permissions
        Set<String> missing = new HashSet<>();
        for (Permission p : REQUIRED_PERMISSIONS) {
            if (!granted.contains(p)) {
                missing.add(p.name());
            }
        }

        return PolicyDecision.deny(getPolicyId(),
                String.format("Missing permissions %s to view documents for youth %s",
                        missing, resource.ownerId()),
                missing);
    }
}
