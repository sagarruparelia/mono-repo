package com.example.bff.auth.util;

import com.example.bff.auth.model.AuthContext;
import org.springframework.web.server.ServerWebExchange;

import java.util.Optional;

/**
 * Utility for extracting AuthContext from ServerWebExchange.
 *
 * <p>AuthContext is populated by DualAuthWebFilter and stored in exchange attributes.
 * This utility provides convenient methods for controllers and filters to access it.</p>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * // Optional access
 * AuthContextResolver.resolve(exchange)
 *     .ifPresent(auth -> log.info("User: {}", auth.userId()));
 *
 * // Required access (throws if missing)
 * AuthContext auth = AuthContextResolver.require(exchange);
 *
 * // Get effective member ID for data access
 * String memberId = AuthContextResolver.requireMemberId(exchange);
 * }</pre>
 */
public final class AuthContextResolver {

    private AuthContextResolver() {
        // Utility class
    }

    /**
     * Resolve AuthContext from exchange attributes if present.
     *
     * @param exchange The server web exchange
     * @return Optional containing AuthContext if present
     */
    public static Optional<AuthContext> resolve(ServerWebExchange exchange) {
        return Optional.ofNullable(
                exchange.getAttribute(AuthContext.EXCHANGE_ATTRIBUTE)
        );
    }

    /**
     * Require AuthContext from exchange attributes.
     *
     * @param exchange The server web exchange
     * @return The AuthContext
     * @throws IllegalStateException if AuthContext is not present
     */
    public static AuthContext require(ServerWebExchange exchange) {
        return resolve(exchange)
                .orElseThrow(() -> new IllegalStateException(
                        "AuthContext not found in exchange. Ensure DualAuthWebFilter is configured."
                ));
    }

    /**
     * Get the effective member ID for data access.
     *
     * <p>For HSID: Returns userId or selected child ID.
     * For PROXY: Returns the member ID from request.</p>
     *
     * @param exchange The server web exchange
     * @return The effective member ID
     * @throws IllegalStateException if AuthContext is not present or memberId is null
     */
    public static String requireMemberId(ServerWebExchange exchange) {
        AuthContext auth = require(exchange);
        String memberId = auth.effectiveMemberId();
        if (memberId == null || memberId.isBlank()) {
            throw new IllegalStateException("effectiveMemberId is not set in AuthContext");
        }
        return memberId;
    }

    /**
     * Check if the exchange has a valid AuthContext.
     *
     * @param exchange The server web exchange
     * @return true if AuthContext is present
     */
    public static boolean isAuthenticated(ServerWebExchange exchange) {
        return resolve(exchange).isPresent();
    }

    /**
     * Store AuthContext in exchange attributes.
     *
     * @param exchange    The server web exchange
     * @param authContext The AuthContext to store
     */
    public static void store(ServerWebExchange exchange, AuthContext authContext) {
        exchange.getAttributes().put(AuthContext.EXCHANGE_ATTRIBUTE, authContext);
    }
}
