package com.example.bff.authz.abac.policy;

import com.example.bff.authz.abac.config.AbacPolicyProperties.PolicyConditions;
import com.example.bff.authz.abac.config.AbacPolicyProperties.PolicyDefinition;
import com.example.bff.authz.abac.config.AbacPolicyProperties.ProxyRules;
import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.authz.model.Permission;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Config-driven ABAC policy implementation.
 * Replaces 8 hardcoded policy classes with a single configurable implementation.
 */
public class ConfigurablePolicy implements Policy {

    private final PolicyDefinition definition;
    private final Set<Permission> requiredPermissions;

    public ConfigurablePolicy(PolicyDefinition definition) {
        this.definition = definition;
        this.requiredPermissions = definition.requiredPermissions().stream()
                .map(Permission::valueOf)
                .collect(Collectors.toSet());
    }

    @Override
    public String getPolicyId() {
        return definition.id();
    }

    @Override
    public String getDescription() {
        return definition.description();
    }

    @Override
    public int getPriority() {
        return definition.priority();
    }

    @Override
    public boolean appliesTo(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        PolicyConditions cond = definition.conditions();
        if (cond == null) return false;

        // Check auth type
        if (cond.authType() != null) {
            boolean matches = switch (cond.authType()) {
                case "HSID" -> subject.isHsid();
                case "PROXY" -> subject.isProxy();
                default -> false;
            };
            if (!matches) return false;
        }

        // Check persona
        if (cond.persona() != null && !subject.hasPersona(cond.persona())) {
            return false;
        }

        // Check resource type
        List<String> resourceTypes = cond.getResourceTypes();
        if (!resourceTypes.isEmpty()) {
            boolean matchesType = resourceTypes.stream()
                    .anyMatch(type -> matchesResourceType(resource, type));
            if (!matchesType) return false;
        }

        // Check action
        List<String> actions = cond.getActions();
        if (!actions.isEmpty()) {
            boolean matchesAction = actions.stream()
                    .anyMatch(a -> action.name().equals(a));
            if (!matchesAction) return false;
        }

        // Check sensitive flag
        if (cond.sensitive() != null && cond.sensitive() != resource.isSensitive()) {
            return false;
        }

        return true;
    }

    @Override
    public PolicyDecision evaluate(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        if (!appliesTo(subject, resource, action)) {
            return PolicyDecision.notApplicable(getPolicyId());
        }

        // Owner check (for youth document access)
        if (definition.ownerCheck()) {
            String userId = subject.userId();
            String ownerId = resource.ownerId();
            // Use Objects.equals for null-safe comparison
            if (userId != null && userId.equals(ownerId)) {
                return PolicyDecision.allow(getPolicyId(),
                        String.format("User %s owns resource %s", userId, resource.id()));
            }
            return PolicyDecision.deny(getPolicyId(),
                    String.format("User %s does not own resource %s", userId, resource.id()));
        }

        // Proxy rules
        ProxyRules proxyRules = definition.proxyRules();
        if (proxyRules != null && subject.isProxy()) {
            return evaluateProxyRules(subject, resource, proxyRules);
        }

        // Permission-based check
        if (!requiredPermissions.isEmpty()) {
            return evaluatePermissions(subject, resource);
        }

        return PolicyDecision.allow(getPolicyId(), "Policy conditions met");
    }

    private PolicyDecision evaluateProxyRules(SubjectAttributes subject, ResourceAttributes resource, ProxyRules rules) {
        // Config-only access
        if (rules.configOnly()) {
            if (subject.isConfig()) {
                return PolicyDecision.allow(getPolicyId(),
                        String.format("Config persona has access to %s", resource.id()));
            }
            return PolicyDecision.deny(getPolicyId(),
                    String.format("Persona '%s' cannot access. Only 'config' allowed.", subject.persona()),
                    Set.of("persona"));
        }

        // Config has full access
        if (rules.configFullAccess() && subject.isConfig()) {
            return PolicyDecision.allow(getPolicyId(),
                    String.format("Config persona has full access to %s", resource.id()));
        }

        // Assignment check
        if (rules.requireAssignment()) {
            String targetId = resource.ownerId() != null ? resource.ownerId() : resource.id();
            if (subject.isAssignedTo(targetId)) {
                return PolicyDecision.allow(getPolicyId(),
                        String.format("User is assigned to %s", targetId));
            }
            return PolicyDecision.deny(getPolicyId(),
                    String.format("Persona '%s' is not assigned to %s", subject.persona(), targetId),
                    Set.of("memberId"));
        }

        return PolicyDecision.allow(getPolicyId(), "Proxy rules satisfied");
    }

    private PolicyDecision evaluatePermissions(SubjectAttributes subject, ResourceAttributes resource) {
        String targetId = resource.ownerId() != null ? resource.ownerId() : resource.id();
        Set<Permission> granted = subject.getPermissionsFor(targetId);

        if (granted.containsAll(requiredPermissions)) {
            return PolicyDecision.allow(getPolicyId(),
                    String.format("User has required permissions %s for %s", requiredPermissions, targetId));
        }

        Set<String> missing = new HashSet<>();
        for (Permission p : requiredPermissions) {
            if (!granted.contains(p)) {
                missing.add(p.name());
            }
        }

        return PolicyDecision.deny(getPolicyId(),
                String.format("Missing permissions %s for %s", missing, targetId),
                missing);
    }

    private boolean matchesResourceType(ResourceAttributes resource, String type) {
        return switch (type.toLowerCase()) {
            case "dependent" -> resource.isDependent();
            case "member" -> resource.isMember();
            case "document" -> resource.isDocument();
            default -> resource.type().name().equalsIgnoreCase(type);
        };
    }
}
