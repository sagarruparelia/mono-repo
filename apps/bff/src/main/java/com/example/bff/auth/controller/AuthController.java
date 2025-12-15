package com.example.bff.auth.controller;

import com.example.bff.session.model.SessionData;
import com.example.bff.session.service.SessionService;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String SESSION_COOKIE_NAME = "BFF_SESSION";

    @Nullable
    private final SessionService sessionService;

    public AuthController(@Nullable SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * Returns current user information from OIDC token
     */
    @GetMapping("/me")
    public Mono<ResponseEntity<Map<String, Object>>> getCurrentUser(
            @AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            return Mono.just(ResponseEntity.ok(Map.of(
                    "authenticated", false
            )));
        }
        return Mono.just(ResponseEntity.ok(Map.of(
                "authenticated", true,
                "sub", user.getSubject(),
                "name", user.getFullName() != null ? user.getFullName() : "",
                "email", user.getEmail() != null ? user.getEmail() : ""
        )));
    }

    /**
     * Returns session information from Redis
     */
    @GetMapping("/session")
    public Mono<ResponseEntity<Map<String, Object>>> getSessionInfo(ServerWebExchange exchange) {
        if (sessionService == null) {
            return Mono.just(ResponseEntity.ok(Map.of(
                    "valid", false,
                    "reason", "session_service_unavailable"
            )));
        }

        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);

        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            return Mono.just(ResponseEntity.ok(Map.of(
                    "valid", false,
                    "reason", "no_session"
            )));
        }

        String sessionId = sessionCookie.getValue();

        return sessionService.getSession(sessionId)
                .map(session -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("valid", true);
                    response.put("userId", session.userId());
                    response.put("persona", session.persona());
                    response.put("name", session.name());
                    response.put("email", session.email());
                    response.put("createdAt", session.createdAt().toString());
                    response.put("lastAccessedAt", session.lastAccessedAt().toString());

                    if (session.isParent() && session.hasDependents()) {
                        response.put("dependents", session.dependents());
                    }

                    return ResponseEntity.ok(response);
                })
                .defaultIfEmpty(ResponseEntity.ok(Map.of(
                        "valid", false,
                        "reason", "session_not_found"
                )));
    }

    /**
     * Refreshes the session TTL
     */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<Map<String, Object>>> refreshSession(ServerWebExchange exchange) {
        if (sessionService == null) {
            return Mono.just(ResponseEntity.ok(Map.of(
                    "refreshed", false,
                    "reason", "session_service_unavailable"
            )));
        }

        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);

        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            return Mono.just(ResponseEntity.ok(Map.of(
                    "refreshed", false,
                    "reason", "no_session"
            )));
        }

        String sessionId = sessionCookie.getValue();

        return sessionService.refreshSession(sessionId)
                .map(refreshed -> ResponseEntity.ok(Map.of(
                        "refreshed", refreshed
                )));
    }

    /**
     * Login endpoint - redirects to HSID
     * This is handled by Spring Security OAuth2 login
     */
    @GetMapping("/login")
    public Mono<Void> login() {
        // Spring Security will redirect to HSID
        return Mono.empty();
    }

    /**
     * Callback endpoint - handles HSID response
     * This is handled by Spring Security OAuth2 callback
     */
    @GetMapping("/callback")
    public Mono<Void> callback() {
        // Spring Security will handle the callback
        return Mono.empty();
    }

    /**
     * Logout endpoint
     * This is handled by Spring Security logout
     */
    @PostMapping("/logout")
    public Mono<Void> logout() {
        // Spring Security will handle logout
        return Mono.empty();
    }
}
