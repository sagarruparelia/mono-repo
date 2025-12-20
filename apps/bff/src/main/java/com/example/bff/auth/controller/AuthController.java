package com.example.bff.auth.controller;

import com.example.bff.auth.dto.DependentDto;
import com.example.bff.auth.service.TokenService;
import com.example.bff.session.model.SessionData;
import com.example.bff.session.service.SessionService;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** REST controller for authentication endpoints. */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String SESSION_COOKIE_NAME = "BFF_SESSION";

    private final SessionService sessionService;
    private final TokenService tokenService;

    @GetMapping("/user-info")
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

    @GetMapping("/dependents")
    public Mono<ResponseEntity<List<DependentDto>>> getDependents(ServerWebExchange exchange) {
        if (sessionService == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
        }

        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);

        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        String sessionId = sessionCookie.getValue();

        return sessionService.getSession(sessionId)
                .map(session -> {
                    // Only parent persona can access dependents
                    if (!session.isParent()) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).<List<DependentDto>>build();
                    }

                    if (!session.hasDependents()) {
                        return ResponseEntity.ok(List.<DependentDto>of());
                    }

                    // Map dependent IDs to DTOs
                    // TODO: Fetch actual names from member service
                    List<DependentDto> dependents = session.dependents().stream()
                            .map(id -> new DependentDto(
                                    id,
                                    "Child " + id.substring(0, Math.min(8, id.length())),
                                    null
                            ))
                            .toList();

                    return ResponseEntity.ok(dependents);
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

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

    @PostMapping("/token")
    public Mono<ResponseEntity<Map<String, Object>>> getFreshToken(ServerWebExchange exchange) {
        if (tokenService == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "token_service_unavailable",
                            "message", "Token service is not configured"
                    )));
        }

        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);

        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "no_session",
                            "message", "No active session"
                    )));
        }

        String sessionId = sessionCookie.getValue();

        return tokenService.getFreshAccessToken(sessionId)
                .map(accessToken -> ResponseEntity.ok(Map.<String, Object>of(
                        "access_token", accessToken,
                        "token_type", "Bearer"
                )))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "error", "token_expired",
                                "message", "Session expired, please re-authenticate",
                                "login_url", "/api/auth/login"
                        )));
    }

    @GetMapping("/login")
    public Mono<Void> login(ServerWebExchange exchange) {
        // Redirect to Spring Security's OAuth2 authorization endpoint
        // This triggers the PKCE flow with HSID
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(
                java.net.URI.create("/oauth2/authorization/hsid"));
        return exchange.getResponse().setComplete();
    }

    @GetMapping("/callback")
    public Mono<Void> callback() {
        // Spring Security will handle the callback
        return Mono.empty();
    }

    @PostMapping("/logout")
    public Mono<Void> logout() {
        // Spring Security will handle logout
        return Mono.empty();
    }
}
