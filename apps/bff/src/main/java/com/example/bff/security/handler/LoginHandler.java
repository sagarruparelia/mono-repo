package com.example.bff.security.handler;

import com.example.bff.config.BffProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Component
public class LoginHandler {

    private static final String STATE_COOKIE = "oauth2_state";
    private static final String CODE_VERIFIER_COOKIE = "pkce_verifier";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String authorizationUri;
    private final String clientId;
    private final String scope;
    private final String redirectUri;
    private final String cookieDomain;
    private final boolean cookieSecure;

    public LoginHandler(
            @Value("${spring.security.oauth2.client.provider.hsid.authorization-uri}") String authorizationUri,
            @Value("${spring.security.oauth2.client.registration.hsid.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.hsid.scope:openid,profile,email}") String scope,
            @Value("${bff.oidc.redirect-uri:/}") String redirectUri,
            BffProperties properties) {
        this.authorizationUri = authorizationUri;
        this.clientId = clientId;
        this.scope = scope;
        this.redirectUri = redirectUri;
        this.cookieDomain = properties.getSession().getCookieDomain();
        this.cookieSecure = properties.getSession().isCookieSecure();
    }

    public Mono<ServerResponse> handleLogin(ServerRequest request) {
        String baseUrl = getBaseUrl(request);
        String state = generateState();
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        URI authUri = UriComponentsBuilder.fromUriString(authorizationUri)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", baseUrl + redirectUri)
                .queryParam("scope", scope)
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .toUri();

        log.debug("Redirecting to HSID authorization: {}", authUri);

        return ServerResponse.status(HttpStatus.FOUND)
                .location(authUri)
                .cookie(createSecureCookie(STATE_COOKIE, state))
                .cookie(createSecureCookie(CODE_VERIFIER_COOKIE, codeVerifier))
                .build();
    }

    private String getBaseUrl(ServerRequest request) {
        return request.uri().getScheme() + "://" + request.uri().getHost() +
                (request.uri().getPort() != -1 ? ":" + request.uri().getPort() : "");
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate code challenge", e);
        }
    }

    private org.springframework.http.ResponseCookie createSecureCookie(String name, String value) {
        return org.springframework.http.ResponseCookie.from(name, value)
                .domain(cookieDomain)
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax") // Lax required for OIDC redirect flow
                .maxAge(300) // 5 minutes
                .build();
    }
}
