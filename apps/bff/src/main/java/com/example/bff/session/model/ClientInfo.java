package com.example.bff.session.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record ClientInfo(
        String ipAddress,
        String userAgent,
        String userAgentHash
) {
    public static ClientInfo of(String ipAddress, String userAgent) {
        return new ClientInfo(ipAddress, userAgent, hashUserAgent(userAgent));
    }

    private static String hashUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(userAgent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
