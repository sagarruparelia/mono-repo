package com.example.bff.security.resolver;

import com.example.bff.security.annotation.ResolvedAuth;
import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.AuthContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthContextArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(ResolvedAuth.class)
                && parameter.getParameterType().equals(AuthContext.class);
    }

    @Override
    public Mono<Object> resolveArgument(
            MethodParameter parameter,
            BindingContext bindingContext,
            ServerWebExchange exchange) {
        return AuthContextHolder.getContext()
                .cast(Object.class);
    }
}
