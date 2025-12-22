package com.example.bff.session.filter;

import com.example.bff.common.exception.SessionBindingException;
import com.example.bff.common.util.ClientIpExtractor;
import com.example.bff.common.util.SessionCookieUtils;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.SessionProperties;
import com.example.bff.session.audit.SessionAuditService;
import com.example.bff.session.model.ClientInfo;
import com.example.bff.session.service.SessionOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Zero Trust session binding filter that validates device fingerprint and handles session rotation.
 * Validates IP, User-Agent, and Accept headers to prevent session hijacking.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(name = "app.session.binding.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SessionBindingFilter implements WebFilter {

    private static final int MAX_HEADER_LENGTH = 500;

    private final SessionOperations sessionService;
    private final SessionProperties sessionProperties;
    private final Optional<SessionAuditService> auditService;

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip for public paths
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SessionCookieUtils.SESSION_COOKIE_NAME);

        // No session cookie - let Spring Security handle
        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            return chain.filter(exchange);
        }

        String sessionId = sessionCookie.getValue();

        // Validate session ID format
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            log.warn("Invalid session ID format in request");
            return invalidateAndRespond(exchange);
        }

        ClientInfo clientInfo = extractClientInfo(exchange);

        return sessionService.validateSessionBinding(sessionId, clientInfo)
                .flatMap(valid -> {
                    if (!valid) {
                        log.warn("Session binding validation failed for session {}",
                                StringSanitizer.forLog(sessionId));
                        auditService.ifPresent(audit ->
                                audit.logBindingFailed(sessionId, null,
                                        "Device fingerprint or IP mismatch",
                                        clientInfo, exchange.getRequest()));
                        return invalidateAndRespond(exchange);
                    }
                    // Check if session needs rotation (Zero Trust)
                    return checkAndRotateSession(exchange, chain, sessionId, clientInfo);
                })
                .onErrorResume(SessionBindingException.class, e -> {
                    log.warn("Session binding error: {}", StringSanitizer.forLog(e.getMessage()));
                    auditService.ifPresent(audit ->
                            audit.logBindingFailed(sessionId, null,
                                    e.getMessage(), clientInfo, exchange.getRequest()));
                    return invalidateAndRespond(exchange);
                });
    }

    /**
     * Checks if session needs rotation and performs it if necessary.
     * Sets new session cookie after rotation.
     */
    private Mono<Void> checkAndRotateSession(
            @NonNull ServerWebExchange exchange,
            @NonNull WebFilterChain chain,
            @NonNull String sessionId,
            @NonNull ClientInfo clientInfo) {

        return sessionService.getSession(sessionId)
                .flatMap(session -> {
                    if (sessionService.needsRotation(session)) {
                        // Rotate session and set new cookie
                        return sessionService.rotateSession(sessionId, clientInfo)
                                .flatMap(newSessionId -> {
                                    setSessionCookie(exchange, newSessionId);
                                    log.debug("Session rotated: {} -> {}",
                                            StringSanitizer.forLog(sessionId),
                                            StringSanitizer.forLog(newSessionId));
                                    return chain.filter(exchange);
                                })
                                .onErrorResume(e -> {
                                    // Rotation failed - continue with existing session
                                    log.warn("Session rotation failed, continuing with existing session: {}",
                                            e.getMessage());
                                    return sessionService.refreshSession(sessionId)
                                            .then(chain.filter(exchange));
                                });
                    }
                    // No rotation needed - just refresh TTL
                    return sessionService.refreshSession(sessionId)
                            .then(chain.filter(exchange));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Session not found
                    log.warn("Session not found for ID: {}", StringSanitizer.forLog(sessionId));
                    return invalidateAndRespond(exchange);
                }));
    }

    private boolean isPublicPath(@NonNull String path) {
        return "/".equals(path) ||
               path.startsWith("/api/auth/") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/api/mfe/");  // MFE uses different auth
    }

    /**
     * Extracts client information including enhanced device fingerprint.
     */
    @NonNull
    private ClientInfo extractClientInfo(@NonNull ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        String ipAddress = ClientIpExtractor.extractSimple(request);
        String userAgent = extractHeader(request, "User-Agent");
        String acceptLanguage = null;
        String acceptEncoding = null;

        // Include Accept headers if fingerprinting is enabled
        if (sessionProperties.fingerprint().enabled()) {
            if (sessionProperties.fingerprint().includeAcceptLanguage()) {
                acceptLanguage = extractHeader(request, "Accept-Language");
            }
            if (sessionProperties.fingerprint().includeAcceptEncoding()) {
                acceptEncoding = extractHeader(request, "Accept-Encoding");
            }
        }

        return ClientInfo.of(ipAddress, userAgent, acceptLanguage, acceptEncoding);
    }

    @Nullable
    private String extractHeader(@NonNull ServerHttpRequest request, @NonNull String headerName) {
        String value = request.getHeaders().getFirst(headerName);

        if (value == null || value.isBlank()) {
            return null;
        }

        // Sanitize and limit length
        String sanitized = value
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", " ");

        if (sanitized.length() > MAX_HEADER_LENGTH) {
            sanitized = sanitized.substring(0, MAX_HEADER_LENGTH);
        }

        return sanitized.isBlank() ? null : sanitized;
    }

    /**
     * Sets session cookie with secure attributes including domain for subdomain protection.
     */
    private void setSessionCookie(@NonNull ServerWebExchange exchange, @NonNull String sessionId) {
        SessionCookieUtils.addSessionCookie(exchange, sessionId, sessionProperties);
    }

    @NonNull
    private Mono<Void> invalidateAndRespond(@NonNull ServerWebExchange exchange) {
        SessionCookieUtils.clearSessionCookie(exchange, sessionProperties);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
