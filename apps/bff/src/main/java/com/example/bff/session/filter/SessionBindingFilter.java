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
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(name = "app.session.binding.enabled", havingValue = "true")
public class SessionBindingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionBindingFilter.class);
    private static final String SESSION_COOKIE_NAME = "BFF_SESSION";

    private final SessionService sessionService;
    private final SessionProperties sessionProperties;

    public SessionBindingFilter(SessionService sessionService, SessionProperties sessionProperties) {
        this.sessionService = sessionService;
        this.sessionProperties = sessionProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Skip for public paths
        String path = exchange.getRequest().getPath().value();
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);

        // No session cookie - let Spring Security handle
        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            return chain.filter(exchange);
        }

        String sessionId = sessionCookie.getValue();
        ClientInfo clientInfo = extractClientInfo(exchange);

        return sessionService.validateSessionBinding(sessionId, clientInfo)
                .flatMap(valid -> {
                    if (!valid) {
                        log.warn("Session binding validation failed for session {}", sessionId);
                        return invalidateAndRedirect(exchange, "Session security check failed");
                    }
                    // Refresh session TTL on valid request (sliding expiration)
                    return sessionService.refreshSession(sessionId)
                            .then(chain.filter(exchange));
                })
                .onErrorResume(SessionBindingException.class, e -> {
                    log.warn("Session binding error: {}", e.getMessage());
                    return invalidateAndRedirect(exchange, e.getMessage());
                });
    }

    private boolean isPublicPath(String path) {
        return path.equals("/") ||
               path.startsWith("/api/auth/") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/api/mfe/");  // MFE uses different auth
    }

    private ClientInfo extractClientInfo(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        String ipAddress = request.getHeaders().getFirst("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = request.getRemoteAddress() != null
                    ? request.getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
        } else {
            ipAddress = ipAddress.split(",")[0].trim();
        }

        String userAgent = request.getHeaders().getFirst("User-Agent");

        return ClientInfo.of(ipAddress, userAgent != null ? userAgent : "unknown");
    }

    private Mono<Void> invalidateAndRedirect(ServerWebExchange exchange, String reason) {
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
}
