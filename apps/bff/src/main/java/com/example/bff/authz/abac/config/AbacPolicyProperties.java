package com.example.bff.authz.abac.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Configuration properties for ABAC policies.
 * Loaded from config/abac-policies.yml
 */
@ConfigurationProperties(prefix = "abac")
public record AbacPolicyProperties(List<PolicyDefinition> policies) {

    public AbacPolicyProperties {
        if (policies == null) {
            policies = List.of();
        }
    }

    public record PolicyDefinition(
            String id,
            String description,
            int priority,
            PolicyConditions conditions,
            List<String> requiredPermissions,
            boolean ownerCheck,
            ProxyRules proxyRules
    ) {
        public PolicyDefinition {
            if (priority == 0) priority = 100;
            if (requiredPermissions == null) requiredPermissions = List.of();
        }
    }

    public record PolicyConditions(
            String authType,
            String persona,
            Object resourceType,  // String or List<String>
            Object action,        // String or List<String>
            Boolean sensitive
    ) {
        public List<String> getResourceTypes() {
            return toList(resourceType);
        }

        public List<String> getActions() {
            return toList(action);
        }

        private static List<String> toList(Object value) {
            if (value == null) return List.of();
            if (value instanceof List<?> list) {
                // Safely convert each element to String
                return list.stream()
                        .map(item -> item != null ? item.toString() : "")
                        .toList();
            }
            return List.of(value.toString());
        }
    }

    public record ProxyRules(
            boolean configFullAccess,
            boolean configOnly,
            boolean requireAssignment
    ) {}
}
