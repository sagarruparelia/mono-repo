package com.example.bff.security.filter;

import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.AuthContextHolder;
import com.example.bff.security.context.DelegateType;
import com.example.bff.security.validator.EnterpriseIdValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class DelegateEnterpriseIdFilter implements WebFilter, Ordered {

    private static final String ENTERPRISE_ID_FIELD = "enterpriseId";

    private final ObjectMapper objectMapper;
    private final EnterpriseIdValidator enterpriseIdValidator;

    @Override
    public int getOrder() {
        return -50; // After authentication filters
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return AuthContextHolder.getContextIfPresent()
                .flatMap(ctx -> processRequest(ctx, exchange, chain))
                .switchIfEmpty(chain.filter(exchange)); // No context = pass through
    }

    private Mono<Void> processRequest(AuthContext ctx, ServerWebExchange exchange, WebFilterChain chain) {
        HttpMethod method = exchange.getRequest().getMethod();

        // For GET/HEAD/OPTIONS, pass through (handled by PersonaAuthorizationFilter)
        if (method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS) {
            return chain.filter(exchange)
                    .contextWrite(AuthContextHolder.withContext(ctx));
        }

        // For POST/PUT/DELETE, extract enterpriseId from body and validate
        return cacheBodyAndResolve(ctx, exchange, chain);
    }

    private Mono<Void> cacheBodyAndResolve(AuthContext ctx, ServerWebExchange exchange, WebFilterChain chain) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);

                    String enterpriseId = extractEnterpriseId(bytes);

                    // Delegate validation to shared component
                    return enterpriseIdValidator.resolveEnterpriseId(ctx, enterpriseId, new DelegateType[0])
                            .flatMap(updatedCtx -> {
                                // Create new request with cached body
                                ServerHttpRequest mutatedRequest = new CachedBodyServerHttpRequest(
                                        exchange.getRequest(), bytes);

                                return chain.filter(exchange.mutate().request(mutatedRequest).build())
                                        .contextWrite(AuthContextHolder.withContext(updatedCtx));
                            });
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Empty body - let validator handle the error
                    return enterpriseIdValidator.resolveEnterpriseId(ctx, null, new DelegateType[0])
                            .flatMap(updatedCtx -> chain.filter(exchange)
                                    .contextWrite(AuthContextHolder.withContext(updatedCtx)));
                }));
    }

    private String extractEnterpriseId(byte[] bytes) {
        try {
            JsonNode node = objectMapper.readTree(bytes);
            JsonNode enterpriseIdNode = node.get(ENTERPRISE_ID_FIELD);
            return enterpriseIdNode != null && !enterpriseIdNode.isNull()
                    ? enterpriseIdNode.asText()
                    : null;
        } catch (Exception e) {
            log.warn("Failed to parse request body for enterpriseId extraction", e);
            return null;
        }
    }

    private static class CachedBodyServerHttpRequest extends ServerHttpRequestDecorator {

        private final byte[] cachedBody;

        CachedBodyServerHttpRequest(ServerHttpRequest delegate, byte[] body) {
            super(delegate);
            this.cachedBody = body;
        }

        @Override
        public Flux<DataBuffer> getBody() {
            DataBuffer buffer = new DefaultDataBufferFactory().wrap(cachedBody);
            return Flux.just(buffer);
        }
    }
}
