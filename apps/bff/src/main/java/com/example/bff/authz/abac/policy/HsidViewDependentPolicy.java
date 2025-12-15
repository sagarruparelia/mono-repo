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
 * Policy: HSID parent can view dependent if they have DAA + RPR permissions.
 *
 * Rule: ALLOW VIEW dependent WHERE
 *   subject.authType = HSID AND
 *   subject.permissions[resource.id] CONTAINS DAA AND
 *   subject.permissions[resource.id] CONTAINS RPR
 */
@Component
public class HsidViewDependentPolicy implements Policy {

    private static final Set<Permission> REQUIRED_PERMISSIONS = Set.of(Permission.DAA, Permission.RPR);

    @Override
    public String getPolicyId() {
        return "HSID_VIEW_DEPENDENT";
    }

    @Override
    public String getDescription() {
        return "HSID parent can view dependent with DAA + RPR permissions";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean appliesTo(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        return subject.isHsid()
                && resource.isDependent()
                && action == Action.VIEW
                && !resource.isSensitive();
    }

    @Override
    public PolicyDecision evaluate(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        if (!appliesTo(subject, resource, action)) {
            return PolicyDecision.notApplicable(getPolicyId());
        }

        Set<Permission> granted = subject.getPermissionsFor(resource.id());

        if (granted.containsAll(REQUIRED_PERMISSIONS)) {
            return PolicyDecision.allow(getPolicyId(),
                    String.format("User has required permissions %s for dependent %s",
                            REQUIRED_PERMISSIONS, resource.id()));
        }

        Set<String> missing = new HashSet<>();
        for (Permission p : REQUIRED_PERMISSIONS) {
            if (!granted.contains(p)) {
                missing.add(p.name());
            }
        }

        return PolicyDecision.deny(getPolicyId(),
                String.format("Missing permissions %s for dependent %s", missing, resource.id()),
                missing);
    }
}
