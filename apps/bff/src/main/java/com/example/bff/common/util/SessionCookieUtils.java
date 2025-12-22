package com.example.bff.common.util;

import com.example.bff.config.properties.SessionProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;

/**
 * Utility methods for building secure session cookies.
 * Centralizes cookie creation with consistent security attributes.
 */
public final class SessionCookieUtils {

    public static final String SESSION_COOKIE_NAME = "BFF_SESSION";

    private SessionCookieUtils() {}

    /**
     * Creates a session cookie with the given session ID and properties.
     *
     * @param sessionId the session identifier
     * @param properties session configuration properties
     * @return a secure response cookie
     */
    @NonNull
    public static ResponseCookie create(@NonNull String sessionId, @NonNull SessionProperties properties) {
        return create(sessionId, properties.timeout(), properties.cookie().domain(), properties.cookie().sameSite());
    }

    /**
     * Creates a session cookie with explicit configuration.
     *
     * @param sessionId the session identifier
     * @param timeout cookie max age
     * @param domain optional domain for subdomain protection
     * @param sameSite SameSite attribute (Lax, Strict, None)
     * @return a secure response cookie
     */
    @NonNull
    public static ResponseCookie create(
            @NonNull String sessionId,
            @NonNull Duration timeout,
            @Nullable String domain,
            @Nullable String sameSite) {

        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(SESSION_COOKIE_NAME, sessionId)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(timeout)
                .sameSite(sameSite != null ? sameSite : "Lax");

        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }

        return builder.build();
    }

    /**
     * Creates an expired cookie to clear the session.
     *
     * @param properties session configuration properties
     * @return an expired cookie that will clear the session
     */
    @NonNull
    public static ResponseCookie createExpired(@NonNull SessionProperties properties) {
        return createExpired(properties.cookie().domain());
    }

    /**
     * Creates an expired cookie to clear the session with explicit domain.
     *
     * @param domain optional domain (must match the original cookie's domain)
     * @return an expired cookie that will clear the session
     */
    @NonNull
    public static ResponseCookie createExpired(@Nullable String domain) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(SESSION_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0);

        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }

        return builder.build();
    }

    /**
     * Adds a session cookie to the response.
     *
     * @param exchange the server web exchange
     * @param sessionId the session identifier
     * @param properties session configuration properties
     */
    public static void addSessionCookie(
            @NonNull ServerWebExchange exchange,
            @NonNull String sessionId,
            @NonNull SessionProperties properties) {
        exchange.getResponse().addCookie(create(sessionId, properties));
    }

    /**
     * Clears the session cookie by adding an expired cookie.
     *
     * @param exchange the server web exchange
     * @param properties session configuration properties
     */
    public static void clearSessionCookie(
            @NonNull ServerWebExchange exchange,
            @NonNull SessionProperties properties) {
        exchange.getResponse().addCookie(createExpired(properties));
    }
}
