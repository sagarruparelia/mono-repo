package com.example.bff.auth.controller;

import com.example.bff.auth.dto.SessionInfoResponse;
import com.example.bff.config.properties.SessionProperties;
import com.example.bff.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST controller for authentication endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /login - Initiate OAuth2 PKCE flow with HSID</li>
 *   <li>GET /session - Get full session info</li>
 *   <li>GET /check - Quick session validity check</li>
 *   <li>POST /refresh - Extend session TTL</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String SESSION_COOKIE_NAME = "BFF_SESSION";

    private final SessionService sessionService;
    private final SessionProperties sessionProperties;

    /**
     * Initiate OAuth2 PKCE flow with HSID.
     */
    @GetMapping("/login")
    public Mono<Void> login(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(
                java.net.URI.create("/oauth2/authorization/hsid"));
        return exchange.getResponse().setComplete();
    }

    /**
     * Get full session info for the authenticated user.
     */
    @GetMapping("/session")
    public Mono<ResponseEntity<SessionInfoResponse>> getSessionInfo(ServerWebExchange exchange) {
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);

        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            return Mono.just(ResponseEntity.ok(SessionInfoResponse.invalid("no_session")));
        }

        String sessionId = sessionCookie.getValue();

        return sessionService.getSession(sessionId)
                .map(session -> {
                    // Calculate session expiry
                    Instant expiresAt = session.lastAccessedAt().plus(sessionProperties.timeout());

                    return ResponseEntity.ok(SessionInfoResponse.valid(
                            session.hsidUuid(),
                            session.name(),
                            session.email(),
                            session.persona(),
                            session.isParent(),
                            session.hasManagedMembers() ? session.managedMemberIds() : List.of(),
                            expiresAt,
                            session.lastAccessedAt()
                    ));
                })
                .defaultIfEmpty(ResponseEntity.ok(SessionInfoResponse.invalid("session_not_found")));
    }

    /**
     * Quick session validity check for SPA route guards.
     */
    @GetMapping("/check")
    public Mono<ResponseEntity<Map<String, Object>>> checkSession(ServerWebExchange exchange) {
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);

        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            return Mono.just(ResponseEntity.ok(Map.of("valid", false)));
        }

        String sessionId = sessionCookie.getValue();

        return sessionService.getSession(sessionId)
                .map(session -> {
                    long ageSeconds = Duration.between(session.lastAccessedAt(), Instant.now()).getSeconds();
                    long timeoutSeconds = sessionProperties.timeout().getSeconds();
                    long expiresIn = Math.max(0, timeoutSeconds - ageSeconds);

                    return ResponseEntity.ok(Map.<String, Object>of(
                            "valid", true,
                            "expiresIn", expiresIn,
                            "persona", session.persona()
                    ));
                })
                .defaultIfEmpty(ResponseEntity.ok(Map.of("valid", false)));
    }

    /**
     * Refresh session TTL.
     */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<Map<String, Object>>> refreshSession(ServerWebExchange exchange) {
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);

        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            return Mono.just(ResponseEntity.ok(Map.of(
                    "refreshed", false,
                    "reason", "no_session"
            )));
        }

        String sessionId = sessionCookie.getValue();

        return sessionService.refreshSession(sessionId)
                .map(refreshed -> ResponseEntity.ok(Map.of("refreshed", refreshed)));
    }

}
