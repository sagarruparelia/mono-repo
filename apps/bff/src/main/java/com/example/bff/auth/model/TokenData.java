package com.example.bff.auth.model;

import java.time.Instant;

/**
 * Stores HSID OAuth2 tokens for on-demand refresh.
 * Tokens are stored in Redis session and refreshed when needed for micro-products.
 */
public record TokenData(
        String accessToken,
        String refreshToken,
        String idToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt
) {
    /**
     * Checks if access token is expired or about to expire (within buffer).
     *
     * @param bufferSeconds seconds before expiry to consider expired
     * @return true if token needs refresh
     */
    public boolean isAccessTokenExpired(long bufferSeconds) {
        if (accessToken == null || accessTokenExpiresAt == null) {
            return true;
        }
        return Instant.now().plusSeconds(bufferSeconds).isAfter(accessTokenExpiresAt);
    }

    /**
     * Checks if refresh token is expired.
     *
     * @return true if refresh token is expired
     */
    public boolean isRefreshTokenExpired() {
        if (refreshToken == null || refreshTokenExpiresAt == null) {
            // If no expiry set, assume it's valid (some IdPs don't set expiry)
            return refreshToken == null;
        }
        return Instant.now().isAfter(refreshTokenExpiresAt);
    }

    /**
     * Checks if we have a valid refresh token.
     *
     * @return true if refresh token exists and is not expired
     */
    public boolean canRefresh() {
        return refreshToken != null && !isRefreshTokenExpired();
    }
}
