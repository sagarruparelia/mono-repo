package com.example.bff.security.filter;

import com.example.bff.security.annotation.MfeEnabled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class MfeRouteValidator implements WebFilter, Ordered {

    private final RequestMappingHandlerMapping handlerMapping;

    public MfeRouteValidator(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public int getOrder() {
        return 50; // After authentication and authorization
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Boolean isMfeRequest = exchange.getAttribute(MfePathRewriteFilter.MFE_MARKER_ATTRIBUTE);

        if (!Boolean.TRUE.equals(isMfeRequest)) {
            // Not an MFE request, pass through
            return chain.filter(exchange);
        }

        return handlerMapping.getHandler(exchange)
                .filter(handler -> handler instanceof HandlerMethod)
                .cast(HandlerMethod.class)
                .flatMap(handlerMethod -> {
                    if (!hasMfeEnabled(handlerMethod)) {
                        log.warn("MFE request to non-MfeEnabled endpoint: {}",
                                exchange.getRequest().getPath());
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Endpoint not available via MFE path"));
                    }
                    return chain.filter(exchange);
                })
                .switchIfEmpty(chain.filter(exchange)); // No handler found = pass through
    }

    private boolean hasMfeEnabled(HandlerMethod method) {
        // Check method level
        if (AnnotatedElementUtils.hasAnnotation(method.getMethod(), MfeEnabled.class)) {
            return true;
        }

        // Check class level
        return AnnotatedElementUtils.hasAnnotation(method.getBeanType(), MfeEnabled.class);
    }
}
