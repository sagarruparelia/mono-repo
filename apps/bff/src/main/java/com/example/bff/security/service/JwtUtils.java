package com.example.bff.security.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;

public final class JwtUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JwtUtils() {
        // Utility class
    }

    public static String extractSubClaim(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid JWT token format");
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode claims = OBJECT_MAPPER.readTree(payload);

            JsonNode sub = claims.get("sub");
            if (sub == null || sub.isNull()) {
                throw new IllegalArgumentException("Missing 'sub' claim in ID token");
            }

            return sub.asText();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to extract 'sub' claim from ID token", e);
        }
    }

    /**
     * Extracts a specific claim from a JWT token.
     *
     * @param token     the JWT token
     * @param claimName the claim name to extract
     * @return the claim value as a string, or null if not present
     */
    public static String extractClaim(String token, String claimName) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode claims = OBJECT_MAPPER.readTree(payload);

            JsonNode claim = claims.get(claimName);
            return claim != null && !claim.isNull() ? claim.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
