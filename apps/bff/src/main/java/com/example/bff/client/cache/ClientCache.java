package com.example.bff.client.cache;

import reactor.core.publisher.Mono;

public interface ClientCache<T> {

    Mono<T> get(String key);

    Mono<T> put(String key, T value);

    default Mono<T> getOrCompute(String key, Mono<T> supplier) {
        return get(key)
                .switchIfEmpty(supplier.flatMap(value -> put(key, value)));
    }

    Mono<Void> invalidate(String key);

    Mono<Void> clear();
}
