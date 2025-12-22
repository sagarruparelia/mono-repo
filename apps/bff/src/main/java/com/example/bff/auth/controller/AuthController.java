package com.example.bff.auth.controller;

import com.example.bff.auth.dto.SessionInfoResponse;
import com.example.bff.auth.service.TokenOperations;
import com.example.bff.config.properties.SessionProperties;
import com.example.bff.session.service.SessionOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String SESSION_COOKIE_NAME = "BFF_SESSION";

    private final SessionOperations sessionService;
    private final SessionProperties sessionProperties;
    @Nullable private final TokenOperations tokenService;

    @Value("${app.auth.hsid.logout-uri}")
    private String hsidLogoutUri;

    @Value("${app.auth.hsid.post-logout-redirect-uri:#{null}}")
    private String postLogoutRedirectUri;

    @PostMapping("/session")
    public Mono<ResponseEntity<SessionInfoResponse>> getSessionInfo(ServerWebExchange exchange) {
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);

        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            return Mono.just(ResponseEntity.ok(SessionInfoResponse.invalid("no_session")));
        }

        String sessionId = sessionCookie.getValue();

        return sessionService.getSession(sessionId)
                .map(session -> {
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

    @PostMapping("/session/status")
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

    @PostMapping("/session/extend")
    public Mono<ResponseEntity<Map<String, Object>>> extendSession(ServerWebExchange exchange) {
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);

        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            return Mono.just(ResponseEntity.ok(Map.of(
                    "extended", false,
                    "reason", "no_session"
            )));
        }

        String sessionId = sessionCookie.getValue();

        return sessionService.refreshSession(sessionId)
                .map(refreshed -> ResponseEntity.ok(Map.of("extended", refreshed)));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Map<String, Object>>> logout(ServerWebExchange exchange) {
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);

        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            return Mono.just(ResponseEntity.ok(Map.of(
                    "loggedOut", false,
                    "reason", "no_session"
            )));
        }

        String sessionId = sessionCookie.getValue();

        Mono<String> idTokenMono = tokenService != null
                ? tokenService.getTokens(sessionId).map(t -> t.idToken()).defaultIfEmpty("")
                : Mono.just("");

        Mono<Void> revokeTokenMono = tokenService != null
                ? tokenService.revokeRefreshToken(sessionId)
                : Mono.empty();

        return idTokenMono.flatMap(idToken ->
                revokeTokenMono
                        .then(sessionService.invalidateSession(sessionId))
                        .then(Mono.fromRunnable(() -> clearSessionCookie(exchange)))
                        .then(Mono.just(ResponseEntity.ok(buildLogoutResponse(idToken))))
        );
    }

    private void clearSessionCookie(ServerWebExchange exchange) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(SESSION_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0);

        String domain = sessionProperties.cookie().domain();
        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }

        exchange.getResponse().addCookie(builder.build());
    }

    private Map<String, Object> buildLogoutResponse(String idToken) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(hsidLogoutUri);

        if (idToken != null && !idToken.isBlank()) {
            uriBuilder.queryParam("id_token_hint", idToken);
        }

        if (postLogoutRedirectUri != null && !postLogoutRedirectUri.isBlank()) {
            uriBuilder.queryParam("post_logout_redirect_uri",
                    URLEncoder.encode(postLogoutRedirectUri, StandardCharsets.UTF_8));
        }

        String logoutUrl = uriBuilder.build().toUriString();
        log.info("Logout initiated, redirecting to HSID logout");

        return Map.of(
                "loggedOut", true,
                "redirectUrl", logoutUrl
        );
    }
}
