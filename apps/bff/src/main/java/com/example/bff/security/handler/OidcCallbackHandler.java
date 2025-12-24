package com.example.bff.security.handler;

import com.example.bff.config.BffProperties;
import com.example.bff.health.service.HealthService;
import com.example.bff.security.exception.AuthenticationException;
import com.example.bff.security.exception.AuthorizationException;
import com.example.bff.security.service.SessionCreationService;
import com.example.bff.security.service.TokenExchangeService;
import com.example.bff.security.session.ClientInfoExtractor;
import com.example.bff.security.session.SessionCookieManager;
import com.example.bff.security.session.SessionStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class OidcCallbackHandler {

    private static final String STATE_COOKIE = "oauth2_state";
    private static final String CODE_VERIFIER_COOKIE = "pkce_verifier";

    private final TokenExchangeService tokenExchangeService;
    private final SessionCreationService sessionCreationService;
    private final SessionStore sessionStore;
    private final SessionCookieManager cookieManager;
    private final ClientInfoExtractor clientInfoExtractor;
    private final HealthService healthService;
    private final String cookieDomain;

    public OidcCallbackHandler(
            TokenExchangeService tokenExchangeService,
            SessionCreationService sessionCreationService,
            SessionStore sessionStore,
            SessionCookieManager cookieManager,
            ClientInfoExtractor clientInfoExtractor,
            HealthService healthService,
            BffProperties properties) {
        this.tokenExchangeService = tokenExchangeService;
        this.sessionCreationService = sessionCreationService;
        this.sessionStore = sessionStore;
        this.cookieManager = cookieManager;
        this.clientInfoExtractor = clientInfoExtractor;
        this.healthService = healthService;
        this.cookieDomain = properties.getSession().getCookieDomain();
    }

    public Mono<ServerResponse> handleCallback(ServerRequest request) {
        String code = request.queryParam("code").orElse(null);
        String state = request.queryParam("state").orElse(null);
        String error = request.queryParam("error").orElse(null);

        if (error != null) {
            String errorDescription = request.queryParam("error_description").orElse(error);
            log.error("OIDC callback error: {}", errorDescription);
            return createErrorResponse("Authentication failed: " + errorDescription);
        }

        if (code == null) {
            log.error("Missing authorization code in callback");
            return createErrorResponse("Missing authorization code");
        }

        // Validate state
        HttpCookie stateCookie = request.cookies().getFirst(STATE_COOKIE);
        if (stateCookie == null || !stateCookie.getValue().equals(state)) {
            log.error("State mismatch in OIDC callback");
            return createErrorResponse("Invalid state parameter");
        }

        // Get code verifier for PKCE
        HttpCookie verifierCookie = request.cookies().getFirst(CODE_VERIFIER_COOKIE);
        String codeVerifier = verifierCookie != null ? verifierCookie.getValue() : null;

        String baseUrl = getBaseUrl(request);

        // Extract client info for session binding
        String clientIp = clientInfoExtractor.extractClientIp(request.exchange().getRequest());
        String browserFingerprint = clientInfoExtractor.extractFingerprint(request.exchange().getRequest());

        return tokenExchangeService.exchangeCode(code, codeVerifier, baseUrl)
                .flatMap(tokens -> sessionCreationService.createSession(tokens, clientIp, browserFingerprint))
                .flatMap(sessionStore::save)
                .flatMap(session -> {
                    ResponseCookie sessionCookie = cookieManager.createSessionCookie(session.getSessionId());
                    ResponseCookie clearStateCookie = clearCookie(STATE_COOKIE);
                    ResponseCookie clearVerifierCookie = clearCookie(CODE_VERIFIER_COOKIE);

                    log.info("Session created successfully for enterpriseId: {}, persona: {}",
                            session.getEnterpriseId(), session.getPersona());

                    // Trigger background health cache build for browser flow
                    healthService.triggerBackgroundCacheBuild(session.getEnterpriseId());

                    return ServerResponse.status(HttpStatus.FOUND)
                            .location(URI.create("/"))
                            .cookie(sessionCookie)
                            .cookie(clearStateCookie)
                            .cookie(clearVerifierCookie)
                            .build();
                })
                .onErrorResume(AuthorizationException.class, e -> {
                    log.warn("Authorization denied: {}", e.getMessage());
                    return createErrorResponse(e.getMessage());
                })
                .onErrorResume(AuthenticationException.class, e -> {
                    log.error("Authentication failed: {}", e.getMessage());
                    return createErrorResponse("Authentication failed");
                })
                .onErrorResume(e -> {
                    log.error("Unexpected error during OIDC callback", e);
                    return createErrorResponse("An unexpected error occurred");
                });
    }

    private String getBaseUrl(ServerRequest request) {
        return request.uri().getScheme() + "://" + request.uri().getHost() +
                (request.uri().getPort() != -1 ? ":" + request.uri().getPort() : "");
    }

    private ResponseCookie clearCookie(String name) {
        return ResponseCookie.from(name, "")
                .domain(cookieDomain)
                .path("/")
                .httpOnly(true)
                .maxAge(Duration.ZERO)
                .build();
    }

    private Mono<ServerResponse> createErrorResponse(String message) {
        return ServerResponse.status(HttpStatus.FORBIDDEN)
                .bodyValue(Map.of(
                        "error", "access_denied",
                        "message", message
                ));
    }
}
