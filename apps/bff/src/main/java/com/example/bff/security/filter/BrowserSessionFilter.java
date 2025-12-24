package com.example.bff.security.filter;

import com.example.bff.config.BffProperties;
import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.AuthContextHolder;
import com.example.bff.security.exception.AuthenticationException;
import com.example.bff.security.session.BffSession;
import com.example.bff.security.session.ClientInfoExtractor;
import com.example.bff.security.session.SessionCookieManager;
import com.example.bff.security.session.SessionStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrowserSessionFilter implements WebFilter, Ordered {

    private final SessionStore sessionStore;
    private final SessionCookieManager cookieManager;
    private final ClientInfoExtractor clientInfoExtractor;
    private final BffProperties properties;

    @Override
    public int getOrder() {
        return -100; // Run early in the filter chain
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return cookieManager.extractSessionId(exchange.getRequest())
                .map(sessionId -> validateSession(sessionId, exchange, chain))
                .orElseGet(() -> Mono.error(new AuthenticationException(
                        "Missing " + cookieManager.getCookieName() + " cookie")));
    }

    private Mono<Void> validateSession(String sessionId, ServerWebExchange exchange, WebFilterChain chain) {
        return sessionStore.findById(sessionId)
                .switchIfEmpty(Mono.error(new AuthenticationException("Invalid or expired session")))
                .flatMap(session -> validateSessionBinding(session, exchange))
                .flatMap(session -> sessionStore.updateLastAccessed(sessionId).thenReturn(session))
                .map(session -> {
                    log.debug("Authenticated session for enterpriseId: {}, persona: {}",
                            session.getEnterpriseId(), session.getPersona());
                    return session.toAuthContext();
                })
                .flatMap(authContext -> continueWithContext(authContext, exchange, chain));
    }

    /**
     * Validates session binding (fingerprint and IP).
     * <p>Validation logic:</p>
     * <ul>
     *   <li>If fingerprint matches stored → Allow (even if IP changed, for mobile users)</li>
     *   <li>If fingerprint missing/mismatches AND IP matches → Allow (legacy/fallback)</li>
     *   <li>If both mismatch AND strict mode → Reject with 401</li>
     *   <li>If both mismatch AND permissive mode → Log warning, allow</li>
     * </ul>
     */
    private Mono<BffSession> validateSessionBinding(BffSession session, ServerWebExchange exchange) {
        if (!properties.getSession().isSessionBindingEnabled()) {
            return Mono.just(session);
        }

        String currentIp = clientInfoExtractor.extractClientIp(exchange.getRequest());
        String currentFingerprint = clientInfoExtractor.extractFingerprint(exchange.getRequest());

        // Fingerprint matches → Allow (covers IP change for mobile users)
        if (session.getBrowserFingerprint() != null &&
                session.getBrowserFingerprint().equals(currentFingerprint)) {
            return Mono.just(session);
        }

        // IP matches → Allow (fallback if fingerprint missing)
        if (session.getClientIp() != null &&
                session.getClientIp().equals(currentIp)) {
            return Mono.just(session);
        }

        // Both mismatch - potential session hijacking attempt
        log.warn("SESSION_BINDING_VIOLATION: sessionId={}, storedIp={}, currentIp={}, " +
                        "storedFingerprint={}, currentFingerprint={}",
                maskSessionId(session.getSessionId()),
                session.getClientIp(), currentIp,
                session.getBrowserFingerprint() != null ? "present" : "null",
                currentFingerprint != null ? "present" : "null");

        if (properties.getSession().isStrictSessionBinding()) {
            return Mono.error(new AuthenticationException("Session binding validation failed"));
        }

        // Permissive mode - log warning but allow
        return Mono.just(session);
    }

    private String maskSessionId(String sessionId) {
        if (sessionId == null || sessionId.length() <= 8) {
            return "***";
        }
        return sessionId.substring(0, 8) + "***";
    }

    private Mono<Void> continueWithContext(AuthContext authContext, ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange)
                .contextWrite(AuthContextHolder.withContext(authContext));
    }
}
