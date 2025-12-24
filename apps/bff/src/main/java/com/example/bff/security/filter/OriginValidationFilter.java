package com.example.bff.security.filter;

import com.example.bff.config.BffProperties;
import com.example.bff.security.exception.AuthenticationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Set;

@Slf4j
@Component
public class OriginValidationFilter implements WebFilter, Ordered {

    private static final String ORIGIN_HEADER = HttpHeaders.ORIGIN;
    private static final String REFERER_HEADER = HttpHeaders.REFERER;

    private final Set<String> allowedOrigins;
    private final String cookieDomain;

    public OriginValidationFilter(BffProperties properties) {
        this.allowedOrigins = Set.copyOf(properties.getSession().getAllowedOrigins());
        this.cookieDomain = properties.getSession().getCookieDomain();
        log.info("Origin validation configured for domain: {}, allowed origins: {}",
                cookieDomain, allowedOrigins);
    }

    @Override
    public int getOrder() {
        return -150; // Run very early, before authentication
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip validation for partner paths (mTLS handled by ALB)
        if (path.startsWith("/mfe/")) {
            return chain.filter(exchange);
        }

        // Skip validation for public paths (login, callback, actuator)
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Validate origin for browser paths
        return validateOrigin(exchange)
                .flatMap(valid -> chain.filter(exchange));
    }

    private boolean isPublicPath(String path) {
        return path.equals("/") ||
                path.equals("/login") ||
                path.startsWith("/actuator/");
    }

    private Mono<Boolean> validateOrigin(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        String origin = request.getHeaders().getFirst(ORIGIN_HEADER);
        String referer = request.getHeaders().getFirst(REFERER_HEADER);

        // Check Origin header first
        if (origin != null && !origin.isBlank()) {
            if (isAllowedOrigin(origin)) {
                log.debug("Origin validated: {}", origin);
                return Mono.just(true);
            }
            log.warn("SECURITY: Invalid origin rejected: {}", origin);
            return Mono.error(new AuthenticationException(
                    "Request origin not allowed: " + origin));
        }

        // Fall back to Referer header for same-origin requests without Origin
        if (referer != null && !referer.isBlank()) {
            String refererOrigin = extractOriginFromUrl(referer);
            if (isAllowedOrigin(refererOrigin)) {
                log.debug("Referer validated: {}", refererOrigin);
                return Mono.just(true);
            }
            log.warn("SECURITY: Invalid referer rejected: {}", referer);
            return Mono.error(new AuthenticationException(
                    "Request referer not allowed: " + referer));
        }

        // No Origin or Referer - reject for API paths
        log.warn("SECURITY: Missing origin/referer for path: {}",
                exchange.getRequest().getPath());
        return Mono.error(new AuthenticationException(
                "Missing Origin or Referer header"));
    }

    private boolean isAllowedOrigin(String origin) {
        if (origin == null) {
            return false;
        }

        // Check exact match
        if (allowedOrigins.contains(origin)) {
            return true;
        }

        // Check domain match (for subdomains)
        try {
            java.net.URI uri = java.net.URI.create(origin);
            String host = uri.getHost();
            if (host != null) {
                return host.equals(cookieDomain) || host.endsWith("." + cookieDomain);
            }
        } catch (Exception e) {
            log.debug("Failed to parse origin: {}", origin);
        }

        return false;
    }

    private String extractOriginFromUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getScheme() + "://" + uri.getHost() +
                    (uri.getPort() != -1 && uri.getPort() != 443 && uri.getPort() != 80
                            ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            return null;
        }
    }
}
