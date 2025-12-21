package com.example.bff.session.model;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Client device information for session binding and Zero Trust verification.
 * Contains IP address and device fingerprint based on multiple HTTP headers.
 */
public record ClientInfo(
        @NonNull String ipAddress,
        @Nullable String userAgent,
        @NonNull String userAgentHash,
        @Nullable String acceptLanguage,
        @Nullable String acceptEncoding,
        @NonNull String deviceFingerprint
) {
    /**
     * Creates ClientInfo with full device fingerprinting.
     * The fingerprint is a composite SHA-256 hash of all available signals.
     */
    @NonNull
    public static ClientInfo of(
            @NonNull String ipAddress,
            @Nullable String userAgent,
            @Nullable String acceptLanguage,
            @Nullable String acceptEncoding) {
        String uaHash = hashValue(userAgent);
        String fingerprint = computeFingerprint(userAgent, acceptLanguage, acceptEncoding);
        return new ClientInfo(
                ipAddress,
                sanitizeHeader(userAgent),
                uaHash,
                sanitizeHeader(acceptLanguage),
                sanitizeHeader(acceptEncoding),
                fingerprint
        );
    }

    /**
     * Creates ClientInfo with basic fingerprinting (User-Agent only).
     * Provided for backward compatibility.
     */
    @NonNull
    public static ClientInfo of(@NonNull String ipAddress, @Nullable String userAgent) {
        return of(ipAddress, userAgent, null, null);
    }

    /**
     * Computes a composite device fingerprint from multiple signals.
     * Uses SHA-256 to create a stable, non-reversible identifier.
     */
    @NonNull
    private static String computeFingerprint(
            @Nullable String userAgent,
            @Nullable String acceptLanguage,
            @Nullable String acceptEncoding) {
        // Concatenate all available signals with delimiter
        String combined = Stream.of(userAgent, acceptLanguage, acceptEncoding)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("|"));

        if (combined.isBlank()) {
            return "";
        }
        return hashValue(combined);
    }

    /**
     * SHA-256 hash of a value for secure storage and comparison.
     */
    @NonNull
    private static String hashValue(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JVM spec, so this should never happen
            throw new IllegalStateException("SHA-256 algorithm must be available in JVM", e);
        }
    }

    /**
     * Sanitizes header values for safe storage.
     * Removes control characters and limits length.
     */
    @Nullable
    private static String sanitizeHeader(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sanitized = value
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", " ")
                .trim();
        // Limit length to prevent excessive storage
        int maxLength = 500;
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
        }
        return sanitized.isBlank() ? null : sanitized;
    }

    /**
     * Validates if another ClientInfo matches this one based on fingerprint.
     * Used for session binding validation in Zero Trust model.
     */
    public boolean matchesFingerprint(@NonNull ClientInfo other) {
        // Empty fingerprints don't match (fail-closed)
        if (this.deviceFingerprint.isBlank() || other.deviceFingerprint.isBlank()) {
            return false;
        }
        return this.deviceFingerprint.equals(other.deviceFingerprint);
    }

    /**
     * Validates if IP address matches.
     */
    public boolean matchesIpAddress(@NonNull ClientInfo other) {
        return this.ipAddress.equals(other.ipAddress);
    }

    /**
     * Validates if User-Agent hash matches (legacy compatibility).
     */
    public boolean matchesUserAgentHash(@NonNull ClientInfo other) {
        if (this.userAgentHash.isBlank() || other.userAgentHash.isBlank()) {
            return false;
        }
        return this.userAgentHash.equals(other.userAgentHash);
    }
}
