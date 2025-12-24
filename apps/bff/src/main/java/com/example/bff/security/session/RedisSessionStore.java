package com.example.bff.security.session;

import com.example.bff.config.BffProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
@ConditionalOnProperty(name = "bff.session.store", havingValue = "redis")
public class RedisSessionStore implements SessionStore {

    private static final String KEY_PREFIX = "bff:session:";

    private final ReactiveRedisTemplate<String, BffSession> redisTemplate;
    private final Duration sessionTimeout;

    public RedisSessionStore(
            ReactiveRedisTemplate<String, BffSession> redisTemplate,
            BffProperties properties) {
        this.redisTemplate = redisTemplate;
        this.sessionTimeout = Duration.ofMinutes(properties.getSession().getTimeoutMinutes());
        log.info("Initialized Redis session store with timeout: {} minutes",
                properties.getSession().getTimeoutMinutes());
    }

    @Override
    public Mono<BffSession> findById(String sessionId) {
        return redisTemplate.opsForValue()
                .get(keyFor(sessionId))
                .doOnNext(session -> log.debug("Found session in Redis: {}", sessionId))
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("Session not found in Redis: {}", sessionId);
                    return Mono.empty();
                }));
    }

    @Override
    public Mono<BffSession> save(BffSession session) {
        return redisTemplate.opsForValue()
                .set(keyFor(session.getSessionId()), session, sessionTimeout)
                .thenReturn(session)
                .doOnSuccess(s -> log.debug("Saved session to Redis: {}", session.getSessionId()));
    }

    @Override
    public Mono<Void> deleteById(String sessionId) {
        return redisTemplate.delete(keyFor(sessionId))
                .doOnSuccess(count -> log.debug("Deleted session from Redis: {}", sessionId))
                .then();
    }

    @Override
    public Mono<BffSession> updateLastAccessed(String sessionId) {
        return findById(sessionId)
                .map(BffSession::touch)
                .flatMap(this::save);
    }

    private String keyFor(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}
