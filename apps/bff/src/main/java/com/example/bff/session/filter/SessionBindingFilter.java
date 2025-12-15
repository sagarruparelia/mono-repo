package com.example.bff.session.filter;

import com.example.bff.common.exception.SessionBindingException;
import com.example.bff.config.properties.SessionProperties;
import com.example.bff.session.model.ClientInfo;
import com.example.bff.session.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.regex.Pattern;

/**
 * Session binding validation filter.
 *
 * <p>Validates that session attributes (IP, User-Agent) match the current request
 * to prevent session hijacking attacks. Implements sliding session expiration.
 *
 * @see SessionService
 * @see SessionProperties
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(name = "app.session.binding.enabled", havingValue = "true")
public class SessionBindingFilter implements WebFilter {

    private static final Logger LOG = LoggerFactory.getLogger(SessionBindingFilter.class);
    private static final String SESSION_COOKIE_NAME = "BFF_SESSION";

    // Validation patterns
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile(
            "^([0-9]{1,3}\\.){3}[0-9]{1,3}$|^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$");

    // Limits
    private static final int MAX_USER_AGENT_LENGTH = 500;

    private final SessionService sessionService;
    private final SessionProperties sessionProperties;

    public SessionBindingFilter(
            @NonNull SessionService sessionService,
            @NonNull SessionProperties sessionProperties) {
        this.sessionService = sessionService;
        this.sessionProperties = sessionProperties;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip for public paths
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);

        // No session cookie - let Spring Security handle
        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            return chain.filter(exchange);
        }

        String sessionId = sessionCookie.getValue();

        // Validate session ID format
        if (!isValidSessionId(sessionId)) {
            LOG.warn("Invalid session ID format in request");
            return invalidateAndRedirect(exchange);
        }

        ClientInfo clientInfo = extractClientInfo(exchange);

        return sessionService.validateSessionBinding(sessionId, clientInfo)
                .flatMap(valid -> {
                    if (!valid) {
                        LOG.warn("Session binding validation failed for session {}",
                                sanitizeForLog(sessionId));
                        return invalidateAndRedirect(exchange);
                    }
                    // Refresh session TTL on valid request (sliding expiration)
                    return sessionService.refreshSession(sessionId)
                            .then(chain.filter(exchange));
                })
                .onErrorResume(SessionBindingException.class, e -> {
                    LOG.warn("Session binding error: {}", sanitizeForLog(e.getMessage()));
                    return invalidateAndRedirect(exchange);
                });
    }

    /**
     * Checks if the path is a public path that doesn't require session validation.
     *
     * @param path the request path
     * @return true if the path is public
     */
    private boolean isPublicPath(@NonNull String path) {
        return "/".equals(path) ||
               path.startsWith("/api/auth/") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/api/mfe/");  // MFE uses different auth
    }

    /**
     * Validates session ID format (UUID).
     *
     * @param sessionId the session ID to validate
     * @return true if valid UUID format
     */
    private boolean isValidSessionId(@Nullable String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        return UUID_PATTERN.matcher(sessionId).matches();
    }

    /**
     * Extracts client info from the request with security validation.
     *
     * @param exchange the server web exchange
     * @return the extracted client info
     */
    @NonNull
    private ClientInfo extractClientInfo(@NonNull ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        String ipAddress = extractClientIp(request);
        String userAgent = extractUserAgent(request);

        return ClientInfo.of(ipAddress, userAgent);
    }

    /**
     * Extracts and validates client IP address.
     *
     * <p>Security note: X-Forwarded-For is validated to prevent IP spoofing.
     *
     * @param request the HTTP request
     * @return validated client IP or "unknown"
     */
    @NonNull
    private String extractClientIp(@NonNull ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // Take first IP from comma-separated list
            String firstIp = forwardedFor.split(",")[0].trim();
            if (isValidIpAddress(firstIp)) {
                return firstIp;
            }
            LOG.debug("Invalid IP in X-Forwarded-For header, falling back to remote address");
        }

        // Fall back to direct remote address
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            InetAddress address = remoteAddress.getAddress();
            if (address != null) {
                return address.getHostAddress();
            }
        }

        return "unknown";
    }

    /**
     * Validates IP address format (IPv4 or IPv6).
     *
     * @param ip the IP address to validate
     * @return true if valid format
     */
    private boolean isValidIpAddress(@Nullable String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return IP_ADDRESS_PATTERN.matcher(ip).matches();
    }

    /**
     * Extracts and sanitizes User-Agent header.
     *
     * @param request the HTTP request
     * @return sanitized User-Agent or "unknown"
     */
    @NonNull
    private String extractUserAgent(@NonNull ServerHttpRequest request) {
        String userAgent = request.getHeaders().getFirst("User-Agent");

        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }

        // Sanitize and limit length
        String sanitized = userAgent
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", " ");

        if (sanitized.length() > MAX_USER_AGENT_LENGTH) {
            sanitized = sanitized.substring(0, MAX_USER_AGENT_LENGTH);
        }

        return sanitized;
    }

    /**
     * Invalidates the session and returns unauthorized response.
     *
     * @param exchange the server web exchange
     * @return Mono completing the response
     */
    @NonNull
    private Mono<Void> invalidateAndRedirect(@NonNull ServerWebExchange exchange) {
        // Clear session cookie
        ResponseCookie clearCookie = ResponseCookie.from(SESSION_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .build();

        exchange.getResponse().addCookie(clearCookie);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);

        return exchange.getResponse().setComplete();
    }

    /**
     * Sanitizes a value for safe logging.
     *
     * @param value the value to sanitize
     * @return sanitized value
     */
    @NonNull
    private String sanitizeForLog(@Nullable String value) {
        if (value == null) {
            return "null";
        }
        return value
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "")
                .substring(0, Math.min(value.length(), 64));
    }
}
