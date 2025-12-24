package com.example.bff.security.session;

import com.example.bff.config.BffProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(name = "bff.session.store", havingValue = "in-memory", matchIfMissing = true)
public class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<String, BffSession> sessions = new ConcurrentHashMap<>();
    private final Duration sessionTimeout;

    public InMemorySessionStore(BffProperties properties) {
        this.sessionTimeout = Duration.ofMinutes(properties.getSession().getTimeoutMinutes());
        log.info("Initialized in-memory session store with timeout: {} minutes",
                properties.getSession().getTimeoutMinutes());
    }

    @Override
    public Mono<BffSession> findById(String sessionId) {
        return Mono.justOrEmpty(sessions.get(sessionId))
                .filter(session -> !isExpired(session))
                .doOnNext(session -> log.debug("Found session: {}", maskSessionId(sessionId)))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Session not found or expired: {}", maskSessionId(sessionId));
                    return Mono.empty();
                }));
    }

    @Override
    public Mono<BffSession> save(BffSession session) {
        return Mono.fromSupplier(() -> {
            sessions.put(session.getSessionId(), session);
            log.debug("Saved session: {}", maskSessionId(session.getSessionId()));
            return session;
        });
    }

    @Override
    public Mono<Void> deleteById(String sessionId) {
        return Mono.fromRunnable(() -> {
            sessions.remove(sessionId);
            log.debug("Deleted session: {}", maskSessionId(sessionId));
        });
    }

    private String maskSessionId(String sessionId) {
        if (sessionId == null || sessionId.length() <= 8) {
            return "***";
        }
        return sessionId.substring(0, 8) + "***";
    }

    @Override
    public Mono<BffSession> updateLastAccessed(String sessionId) {
        return findById(sessionId)
                .map(BffSession::touch)
                .flatMap(this::save);
    }

    private boolean isExpired(BffSession session) {
        if (session.getLastAccessedAt() == null) {
            return false;
        }
        return session.getLastAccessedAt()
                .plus(sessionTimeout)
                .isBefore(Instant.now());
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredSessions() {
        int before = sessions.size();
        sessions.entrySet().removeIf(entry -> isExpired(entry.getValue()));
        int removed = before - sessions.size();
        if (removed > 0) {
            log.info("Cleaned up {} expired sessions", removed);
        }
    }
}
