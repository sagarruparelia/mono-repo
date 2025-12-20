package com.example.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "security.paths")
public record SecurityPathsProperties(
        List<PathConfig> publicPaths,
        List<PathConfig> proxyAuth,
        List<PathConfig> sessionAuth,
        List<PathConfig> dualAuth,
        List<PathConfig> admin
) {
    public record PathConfig(
            String pattern,
            String description,
            List<String> requiredScopes,
            List<String> requiredRoles
    ) {}

    // Convenience method for getting all public patterns
    public List<String> getPublicPatterns() {
        if (publicPaths == null) {
            return List.of();
        }
        return publicPaths.stream()
                .map(PathConfig::pattern)
                .toList();
    }

    // Convenience method for getting all proxy auth patterns
    public List<String> getProxyAuthPatterns() {
        if (proxyAuth == null) {
            return List.of();
        }
        return proxyAuth.stream()
                .map(PathConfig::pattern)
                .toList();
    }

    // Convenience method for getting all session auth patterns
    public List<String> getSessionAuthPatterns() {
        if (sessionAuth == null) {
            return List.of();
        }
        return sessionAuth.stream()
                .map(PathConfig::pattern)
                .toList();
    }

    // Convenience method for getting all dual auth patterns
    public List<String> getDualAuthPatterns() {
        if (dualAuth == null) {
            return List.of();
        }
        return dualAuth.stream()
                .map(PathConfig::pattern)
                .toList();
    }
}
