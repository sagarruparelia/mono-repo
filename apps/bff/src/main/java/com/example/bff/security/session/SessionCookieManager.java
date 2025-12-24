package com.example.bff.security.session;

import com.example.bff.config.BffProperties;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class SessionCookieManager {

    private final String cookieName;
    private final String cookieDomain;
    private final boolean secure;
    private final boolean httpOnly;
    private final String sameSite;
    private final Duration maxAge;

    public SessionCookieManager(BffProperties properties) {
        BffProperties.Session sessionProps = properties.getSession();
        this.cookieName = sessionProps.getCookieName();
        this.cookieDomain = sessionProps.getCookieDomain();
        this.secure = sessionProps.isCookieSecure();
        this.httpOnly = sessionProps.isCookieHttpOnly();
        this.sameSite = sessionProps.getCookieSameSite();
        this.maxAge = Duration.ofMinutes(sessionProps.getTimeoutMinutes());
    }

    public ResponseCookie createSessionCookie(String sessionId) {
        return ResponseCookie.from(cookieName, sessionId)
                .domain(cookieDomain)
                .path("/")
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite(sameSite)
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie createClearCookie() {
        return ResponseCookie.from(cookieName, "")
                .domain(cookieDomain)
                .path("/")
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite(sameSite)
                .maxAge(Duration.ZERO)
                .build();
    }

    public Optional<String> extractSessionId(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(cookieName);
        return Optional.ofNullable(cookie)
                .map(HttpCookie::getValue)
                .filter(value -> !value.isBlank());
    }

    public String getCookieName() {
        return cookieName;
    }
}
