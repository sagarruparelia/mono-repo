package com.example.bff.security.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class MfePathRewriteFilter implements WebFilter, Ordered {

    public static final String MFE_PREFIX = "/mfe";
    public static final String MFE_MARKER_ATTRIBUTE = "MFE_REQUEST";

    @Override
    public int getOrder() {
        return -200; // Very early in filter chain, before authentication
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (path.startsWith(MFE_PREFIX)) {
            String rewrittenPath = path.substring(MFE_PREFIX.length());

            // Ensure path starts with /
            if (!rewrittenPath.startsWith("/")) {
                rewrittenPath = "/" + rewrittenPath;
            }

            log.debug("Rewriting MFE path: {} -> {}", path, rewrittenPath);

            ServerHttpRequest mutatedRequest = exchange.getRequest()
                    .mutate()
                    .path(rewrittenPath)
                    .build();

            ServerWebExchange mutatedExchange = exchange.mutate()
                    .request(mutatedRequest)
                    .build();

            // Mark as MFE request for downstream validation
            mutatedExchange.getAttributes().put(MFE_MARKER_ATTRIBUTE, Boolean.TRUE);

            return chain.filter(mutatedExchange);
        }

        return chain.filter(exchange);
    }
}
