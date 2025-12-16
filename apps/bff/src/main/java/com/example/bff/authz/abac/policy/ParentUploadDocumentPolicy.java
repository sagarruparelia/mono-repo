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
 * Policy: Parent can upload documents for youth with DAA + RPR.
 * Upload does not require ROI since it's adding new data, not viewing existing.
 * Parent CANNOT edit or delete documents (only upload).
 *
 * Rule: ALLOW UPLOAD on DOCUMENT WHERE
 *   subject.authType = HSID AND
 *   subject.persona = "parent" AND
 *   subject.permissions[resource.ownerId] CONTAINS DAA, RPR
 */
@Component
public class ParentUploadDocumentPolicy implements Policy {

    private static final Set<Permission> REQUIRED_PERMISSIONS =
            Set.of(Permission.DAA, Permission.RPR);

    @Override
    public String getPolicyId() {
        return "PARENT_UPLOAD_DOCUMENT";
    }

    @Override
    public String getDescription() {
        return "Parent can upload documents for youth with DAA + RPR permissions (no edit/delete)";
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
                && action == Action.UPLOAD;
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
                    String.format("Parent has required permissions %s to upload documents for youth %s",
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
                String.format("Missing permissions %s to upload documents for youth %s",
                        missing, resource.ownerId()),
                missing);
    }
}
