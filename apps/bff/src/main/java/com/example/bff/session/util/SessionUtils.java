package com.example.bff.session.util;

import com.example.bff.authz.model.ManagedMember;
import com.example.bff.authz.model.MemberAccess;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;

import java.util.List;

/**
 * Shared utility methods for session data handling.
 * Eliminates duplication between InMemorySessionService and RedisSessionService.
 */
@Slf4j
public final class SessionUtils {

    private SessionUtils() {}

    /**
     * Builds a comma-separated string of managed member enterprise IDs.
     */
    @NonNull
    public static String buildManagedMembersString(@NonNull MemberAccess memberAccess) {
        if (!memberAccess.hasManagedMembers()) {
            return "";
        }
        return memberAccess.managedMembers().stream()
                .map(ManagedMember::enterpriseId)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    /**
     * Serializes managed members list to JSON string.
     */
    @NonNull
    public static String serializeManagedMembers(
            @NonNull List<ManagedMember> managedMembers,
            @NonNull ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(managedMembers);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize managed members: {}", e.getMessage());
            return "[]";
        }
    }
}
