package com.example.bff.session.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public record SessionData(
        String userId,
        String email,
        String name,
        String persona,
        List<String> dependents,
        String ipAddress,
        String userAgentHash,
        Instant createdAt,
        Instant lastAccessedAt
) {
    public static SessionData fromMap(Map<String, String> map) {
        return new SessionData(
                map.getOrDefault("userId", ""),
                map.getOrDefault("email", ""),
                map.getOrDefault("name", ""),
                map.getOrDefault("persona", "individual"),
                parseDependents(map.get("dependents")),
                map.getOrDefault("ipAddress", ""),
                map.getOrDefault("userAgentHash", ""),
                parseInstant(map.get("createdAt")),
                parseInstant(map.get("lastAccessedAt"))
        );
    }

    private static List<String> parseDependents(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.asList(value.split(","));
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Instant.now();
        }
    }

    public boolean isParent() {
        return "parent".equals(persona);
    }

    public boolean hasDependents() {
        return dependents != null && !dependents.isEmpty();
    }
}
