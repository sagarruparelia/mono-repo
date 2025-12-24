package com.example.bff.security.filter;

import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

// WebFlux CSRF tokens are lazily generated. This filter subscribes to trigger token generation
// and cookie setting, otherwise the CSRF cookie may not be set until explicitly accessed.
@Component
public class CsrfTokenSubscriptionFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Mono<CsrfToken> csrfToken = exchange.getAttribute(CsrfToken.class.getName());

        if (csrfToken != null) {
            // Subscribe to trigger token generation and cookie setting
            return csrfToken.then(chain.filter(exchange));
        }

        return chain.filter(exchange);
    }
}
