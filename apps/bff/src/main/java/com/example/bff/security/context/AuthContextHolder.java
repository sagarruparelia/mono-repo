package com.example.bff.security.context;

import com.example.bff.security.exception.AuthenticationException;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.function.Function;

public final class AuthContextHolder {

    private static final String AUTH_CONTEXT_KEY = AuthContext.class.getName();

    private AuthContextHolder() {
        // Utility class
    }

    public static Mono<AuthContext> getContext() {
        return Mono.deferContextual(ctx -> {
            if (ctx.hasKey(AUTH_CONTEXT_KEY)) {
                return Mono.just(ctx.get(AUTH_CONTEXT_KEY));
            }
            return Mono.error(new AuthenticationException(
                    "No AuthContext found in reactive context"));
        });
    }

    public static Mono<AuthContext> getContextIfPresent() {
        return Mono.deferContextual(ctx -> {
            if (ctx.hasKey(AUTH_CONTEXT_KEY)) {
                return Mono.just(ctx.get(AUTH_CONTEXT_KEY));
            }
            return Mono.empty();
        });
    }

    public static Function<Context, Context> withContext(AuthContext authContext) {
        return context -> context.put(AUTH_CONTEXT_KEY, authContext);
    }

    public static Mono<Boolean> hasContext() {
        return Mono.deferContextual(ctx -> Mono.just(ctx.hasKey(AUTH_CONTEXT_KEY)));
    }
}
