package com.example.bff.auth.handler;

import com.example.bff.common.util.SessionCookieUtils;
import com.example.bff.config.properties.SessionProperties;
import com.example.bff.session.service.SessionOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;

@Slf4j
@Component
public class HsidLogoutSuccessHandler implements ServerLogoutSuccessHandler {

    @Nullable
    private final SessionOperations sessionService;
    private final SessionProperties sessionProperties;

    public HsidLogoutSuccessHandler(
            @Nullable SessionOperations sessionService,
            SessionProperties sessionProperties) {
        this.sessionService = sessionService;
        this.sessionProperties = sessionProperties;
    }

    @Override
    public Mono<Void> onLogoutSuccess(WebFilterExchange exchange, Authentication authentication) {
        // Get session ID from cookie
        HttpCookie sessionCookie = exchange.getExchange().getRequest()
                .getCookies().getFirst(SessionCookieUtils.SESSION_COOKIE_NAME);

        Mono<Void> invalidateSession = Mono.empty();
        if (sessionService != null && sessionCookie != null && !sessionCookie.getValue().isBlank()) {
            String sessionId = sessionCookie.getValue();
            log.info("Logging out session: {}", sessionId);
            invalidateSession = sessionService.invalidateSession(sessionId);
        }

        return invalidateSession.then(Mono.fromRunnable(() -> {
            SessionCookieUtils.clearSessionCookie(exchange.getExchange(), sessionProperties);

            // Redirect to landing page
            exchange.getExchange().getResponse().setStatusCode(HttpStatus.FOUND);
            exchange.getExchange().getResponse().getHeaders().setLocation(URI.create("/"));

            log.info("Logout successful, redirecting to /");
        })).then(exchange.getExchange().getResponse().setComplete());
    }
}
