package com.example.bff.security.filter;

import com.example.bff.security.annotation.RequiredPersona;
import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.AuthContextHolder;
import com.example.bff.security.context.DelegateType;
import com.example.bff.security.context.Persona;
import com.example.bff.security.exception.AuthorizationException;
import com.example.bff.security.validator.EnterpriseIdValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PersonaAuthorizationFilter implements WebFilter, Ordered {

    private static final String ENTERPRISE_ID_PARAM = "enterpriseId";

    private final RequestMappingHandlerMapping handlerMapping;
    private final EnterpriseIdValidator enterpriseIdValidator;

    public PersonaAuthorizationFilter(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            EnterpriseIdValidator enterpriseIdValidator) {
        this.handlerMapping = handlerMapping;
        this.enterpriseIdValidator = enterpriseIdValidator;
    }

    @Override
    public int getOrder() {
        return 0; // After authentication and delegate body validation
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return handlerMapping.getHandler(exchange)
                .filter(handler -> handler instanceof HandlerMethod)
                .cast(HandlerMethod.class)
                .flatMap(handlerMethod -> validatePersona(exchange, handlerMethod, chain))
                .switchIfEmpty(chain.filter(exchange)); // No handler found = pass through
    }

    private Mono<Void> validatePersona(
            ServerWebExchange exchange,
            HandlerMethod method,
            WebFilterChain chain) {

        RequiredPersona annotation = findAnnotation(method);

        if (annotation == null) {
            // No @RequiredPersona = no restriction
            return chain.filter(exchange);
        }

        return AuthContextHolder.getContext()
                .flatMap(ctx -> validateAndContinue(ctx, annotation, exchange, chain))
                .onErrorResume(e -> {
                    if (e instanceof AuthorizationException) {
                        return Mono.error(e);
                    }
                    // Re-wrap other errors
                    return Mono.error(new AuthorizationException("Authorization check failed", e));
                });
    }

    private Mono<Void> validateAndContinue(
            AuthContext ctx,
            RequiredPersona annotation,
            ServerWebExchange exchange,
            WebFilterChain chain) {

        Set<Persona> requiredPersonas = Arrays.stream(annotation.value())
                .collect(Collectors.toSet());

        // Check if current persona is in the allowed list
        if (!requiredPersonas.contains(ctx.persona())) {
            log.warn("Persona {} not authorized for endpoint. Required: {}",
                    ctx.persona(), requiredPersonas);
            return Mono.error(new AuthorizationException(
                    "Persona " + ctx.persona() + " is not authorized for this endpoint. " +
                            "Required personas: " + requiredPersonas));
        }

        // Resolve enterpriseId (for GET) or validate delegate types (for POST - already resolved)
        return resolveEnterpriseId(ctx, exchange, annotation.requiredDelegates())
                .flatMap(updatedCtx -> {
                    log.debug("Persona authorization passed: persona={}, enterpriseId={}, endpoint={}",
                            updatedCtx.persona(), updatedCtx.enterpriseId(), exchange.getRequest().getPath());

                    return chain.filter(exchange)
                            .contextWrite(AuthContextHolder.withContext(updatedCtx));
                });
    }

    // For GET: resolve enterpriseId from query params. For non-GET: already resolved by DelegateEnterpriseIdFilter
    private Mono<AuthContext> resolveEnterpriseId(
            AuthContext ctx,
            ServerWebExchange exchange,
            DelegateType[] requiredDelegates) {

        HttpMethod method = exchange.getRequest().getMethod();

        // For non-GET requests, body already processed by DelegateEnterpriseIdFilter
        // Just validate required delegate types
        if (method != HttpMethod.GET && method != HttpMethod.HEAD) {
            return enterpriseIdValidator.validateRequiredDelegateTypes(ctx, requiredDelegates);
        }

        // For GET requests, extract enterpriseId from query params
        String enterpriseIdParam = exchange.getRequest().getQueryParams().getFirst(ENTERPRISE_ID_PARAM);
        return enterpriseIdValidator.resolveEnterpriseId(ctx, enterpriseIdParam, requiredDelegates);
    }

    private RequiredPersona findAnnotation(HandlerMethod method) {
        // Check method first
        RequiredPersona annotation = method.getMethodAnnotation(RequiredPersona.class);
        if (annotation != null) {
            return annotation;
        }

        // Fall back to class level
        return AnnotatedElementUtils.findMergedAnnotation(
                method.getBeanType(), RequiredPersona.class);
    }
}
