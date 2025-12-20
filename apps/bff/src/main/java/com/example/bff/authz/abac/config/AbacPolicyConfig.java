package com.example.bff.authz.abac.config;

import com.example.bff.authz.abac.policy.ConfigurablePolicy;
import com.example.bff.authz.abac.policy.Policy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration that creates ABAC policies from YAML configuration.
 * Replaces 8 hardcoded @Component policy classes with config-driven approach.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AbacPolicyProperties.class)
public class AbacPolicyConfig {

    @Bean
    public List<Policy> abacPolicies(AbacPolicyProperties properties) {
        List<Policy> policies = properties.policies().stream()
                .map(ConfigurablePolicy::new)
                .map(p -> (Policy) p)
                .toList();

        log.info("Loaded {} ABAC policies from configuration", policies.size());
        policies.forEach(p -> log.debug("  - {} (priority={}): {}",
                p.getPolicyId(), p.getPriority(), p.getDescription()));

        return policies;
    }
}
