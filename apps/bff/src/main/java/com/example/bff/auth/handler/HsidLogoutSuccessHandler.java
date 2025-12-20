package com.example.bff.auth.handler;

import com.example.bff.session.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;

@Slf4j
@Component
public class HsidLogoutSuccessHandler implements ServerLogoutSuccessHandler {

    private static final String SESSION_COOKIE_NAME = "BFF_SESSION";

    @org.springframework.lang.Nullable
    private final SessionService sessionService;

    public HsidLogoutSuccessHandler(@org.springframework.lang.Nullable SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public Mono<Void> onLogoutSuccess(WebFilterExchange exchange, Authentication authentication) {
        // Get session ID from cookie
        HttpCookie sessionCookie = exchange.getExchange().getRequest()
                .getCookies().getFirst(SESSION_COOKIE_NAME);

        Mono<Void> invalidateSession = Mono.empty();
        if (sessionService != null && sessionCookie != null && !sessionCookie.getValue().isBlank()) {
            String sessionId = sessionCookie.getValue();
            log.info("Logging out session: {}", sessionId);
            invalidateSession = sessionService.invalidateSession(sessionId);
        }

        return invalidateSession.then(Mono.fromRunnable(() -> {
            // Clear session cookie
            ResponseCookie clearCookie = ResponseCookie.from(SESSION_COOKIE_NAME, "")
                    .httpOnly(true)
                    .secure(true)
                    .path("/")
                    .maxAge(0)
                    .build();

            exchange.getExchange().getResponse().addCookie(clearCookie);

            // Redirect to landing page
            exchange.getExchange().getResponse().setStatusCode(HttpStatus.FOUND);
            exchange.getExchange().getResponse().getHeaders().setLocation(URI.create("/"));

            log.info("Logout successful, redirecting to /");
        })).then(exchange.getExchange().getResponse().setComplete());
    }
}
