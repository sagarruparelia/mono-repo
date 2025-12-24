package com.example.bff.security.session;

import reactor.core.publisher.Mono;

public interface SessionStore {

    Mono<BffSession> findById(String sessionId);

    Mono<BffSession> save(BffSession session);

    Mono<Void> deleteById(String sessionId);

    Mono<BffSession> updateLastAccessed(String sessionId);
}
