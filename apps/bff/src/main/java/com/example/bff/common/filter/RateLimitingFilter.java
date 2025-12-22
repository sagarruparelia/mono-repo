package com.example.bff.common.filter;

import com.example.bff.common.util.ClientIpExtractor;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.RateLimitProperties;
import com.example.bff.config.properties.RateLimitProperties.PersonaRule;
import com.example.bff.config.properties.RateLimitProperties.RateLimitRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enforces request rate limits using a token bucket algorithm.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@ConditionalOnProperty(name = "rate-limiting.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RateLimitingFilter implements WebFilter {

    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final RateLimitProperties rateLimitProperties;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String clientIp = ClientIpExtractor.extract(exchange);

        // Check path-specific rules first
        for (RateLimitRule rule : rateLimitProperties.rules()) {
            if (pathMatcher.match(rule.pattern(), path)) {
                String bucketKey = createBucketKey(rule, clientIp, exchange);
                int limit = calculateLimit(rule);

                if (!tryConsume(bucketKey, limit)) {
                    log.warn("Rate limit exceeded for {} on path {} (rule: {})",
                            StringSanitizer.forLog(clientIp), StringSanitizer.forLog(path),
                            StringSanitizer.forLog(rule.description()));
                    return rejectRequest(exchange, rule.description());
                }
                return chain.filter(exchange);
            }
        }

        // Check persona-based rules
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .flatMap(auth -> {
                    String persona = extractPersona(auth);
                    if (persona != null) {
                        for (PersonaRule rule : rateLimitProperties.personaRules()) {
                            if (rule.personas().contains(persona)) {
                                String bucketKey = "persona:" + persona + ":" + clientIp;
                                if (!tryConsume(bucketKey, rule.requestsPerSecond())) {
                                    log.warn("Rate limit exceeded for persona {} from {}",
                                            StringSanitizer.forLog(persona), StringSanitizer.forLog(clientIp));
                                    return rejectRequest(exchange, rule.description());
                                }
                                break;
                            }
                        }
                    }
                    return chain.filter(exchange);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // No authentication - apply default limits
                    String bucketKey = "default:" + clientIp;
                    if (!tryConsume(bucketKey, rateLimitProperties.defaultLimits().requestsPerSecond())) {
                        log.warn("Rate limit exceeded for anonymous request from {}", StringSanitizer.forLog(clientIp));
                        return rejectRequest(exchange, "Default rate limit");
                    }
                    return chain.filter(exchange);
                }));
    }

    private String createBucketKey(RateLimitRule rule, String clientIp, ServerWebExchange exchange) {
        String keyType = rule.by() != null ? rule.by() : "ip";
        return switch (keyType) {
            case "partner" -> {
                String partnerId = exchange.getRequest().getHeaders().getFirst("X-Partner-ID");
                yield "partner:" + (partnerId != null ? partnerId : clientIp) + ":" + rule.pattern();
            }
            case "ip" -> "ip:" + clientIp + ":" + rule.pattern();
            default -> "ip:" + clientIp + ":" + rule.pattern();
        };
    }

    private int calculateLimit(RateLimitRule rule) {
        if (rule.requestsPerSecond() != null) {
            return rule.requestsPerSecond();
        }
        if (rule.requestsPerMinute() != null) {
            // Convert per-minute to per-second (minimum 1)
            return Math.max(1, rule.requestsPerMinute() / 60);
        }
        return rateLimitProperties.defaultLimits().requestsPerSecond();
    }

    private String extractPersona(Authentication auth) {
        if (auth instanceof OAuth2AuthenticationToken oauth2Token) {
            var attributes = oauth2Token.getPrincipal().getAttributes();
            Object persona = attributes.get("persona");
            return persona != null ? persona.toString() : null;
        }
        return null;
    }

    private boolean tryConsume(String key, int limitPerSecond) {
        TokenBucket bucket = buckets.computeIfAbsent(key,
                k -> new TokenBucket(limitPerSecond, rateLimitProperties.defaultLimits().burstCapacity()));
        return bucket.tryConsume();
    }

    private Mono<Void> rejectRequest(ServerWebExchange exchange, String reason) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().add("Retry-After", "1");
        exchange.getResponse().getHeaders().add("X-RateLimit-Reason", reason);

        String body = String.format(
                "{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many requests. Please try again later.\",\"retry_after\":1}"
        );

        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes()))
        );
    }

    private static class TokenBucket {
        private final int tokensPerSecond;
        private final int maxTokens;
        private final AtomicInteger tokens;
        private volatile Instant lastRefill;

        TokenBucket(int tokensPerSecond, int burstCapacity) {
            this.tokensPerSecond = tokensPerSecond;
            this.maxTokens = burstCapacity;
            this.tokens = new AtomicInteger(burstCapacity);
            this.lastRefill = Instant.now();
        }

        synchronized boolean tryConsume() {
            refill();
            // Use atomic update to prevent race condition between check and decrement
            int previousValue = tokens.getAndUpdate(t -> t > 0 ? t - 1 : t);
            return previousValue > 0;
        }

        private void refill() {
            Instant now = Instant.now();
            long millisSinceLastRefill = Duration.between(lastRefill, now).toMillis();

            if (millisSinceLastRefill >= 1000) {
                int tokensToAdd = (int) (millisSinceLastRefill / 1000) * tokensPerSecond;
                int newTokens = Math.min(maxTokens, tokens.get() + tokensToAdd);
                tokens.set(newTokens);
                lastRefill = now;
            }
        }
    }
}
