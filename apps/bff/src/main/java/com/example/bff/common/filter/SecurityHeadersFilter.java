package com.example.bff.common.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Security headers filter that adds essential HTTP security headers to all responses.
 * This filter runs at highest precedence to ensure headers are added before any response.
 *
 * Headers added:
 * - X-Content-Type-Options: Prevents MIME type sniffing
 * - X-Frame-Options: Prevents clickjacking attacks
 * - X-XSS-Protection: Legacy XSS protection for older browsers
 * - Strict-Transport-Security: Enforces HTTPS connections
 * - Content-Security-Policy: Restricts resource loading to prevent XSS
 * - Referrer-Policy: Controls referrer information leakage
 * - Permissions-Policy: Restricts browser features
 * - Cache-Control: Prevents caching of sensitive data
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements WebFilter {

    // Strict CSP for API-only BFF - no inline scripts/styles needed
    private static final String CONTENT_SECURITY_POLICY =
        "default-src 'none'; " +
        "script-src 'none'; " +
        "style-src 'none'; " +
        "img-src 'none'; " +
        "font-src 'none'; " +
        "connect-src 'self'; " +
        "frame-ancestors 'none'; " +
        "form-action 'none'; " +
        "base-uri 'none'; " +
        "object-src 'none'";

    private static final String PERMISSIONS_POLICY =
        "accelerometer=(), " +
        "camera=(), " +
        "geolocation=(), " +
        "gyroscope=(), " +
        "magnetometer=(), " +
        "microphone=(), " +
        "payment=(), " +
        "usb=()";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange).doOnSubscribe(subscription -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();

            // Prevent MIME type sniffing
            headers.add("X-Content-Type-Options", "nosniff");

            // Prevent clickjacking
            headers.add("X-Frame-Options", "DENY");

            // Legacy XSS protection for older browsers
            headers.add("X-XSS-Protection", "1; mode=block");

            // Enforce HTTPS (max-age: 1 year, include subdomains, allow preload)
            headers.add("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");

            // Content Security Policy
            headers.add("Content-Security-Policy", CONTENT_SECURITY_POLICY);

            // Control referrer information
            headers.add("Referrer-Policy", "strict-origin-when-cross-origin");

            // Restrict browser features
            headers.add("Permissions-Policy", PERMISSIONS_POLICY);

            // Prevent caching of API responses with sensitive data
            if (exchange.getRequest().getPath().value().startsWith("/api/")) {
                headers.add("Cache-Control", "no-store, no-cache, must-revalidate, private");
                headers.add("Pragma", "no-cache");
            }
        });
    }
}
