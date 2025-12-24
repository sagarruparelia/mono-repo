package com.example.bff.security.service;

import java.time.Instant;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String idToken,
        Instant expiresAt
) {
}
