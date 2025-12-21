package com.example.bff.authz.filter;

import com.example.bff.auth.model.AuthPrincipal;
import com.example.bff.auth.model.DelegateType;
import com.example.bff.auth.model.Persona;
import com.example.bff.authz.annotation.RequirePersona;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * WebFilter that enforces {@link RequirePersona} annotations on controller methods.
 *
 * <p>This filter runs after {@link com.example.bff.auth.filter.DualAuthWebFilter}
 * (which creates AuthPrincipal) and validates that the authenticated user's
 * persona matches the requirements.</p>
 *
 * <p>Order: HIGHEST_PRECEDENCE + 30 (after DualAuthWebFilter at +20)</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.authz.persona-filter.enabled", havingValue = "true", matchIfMissing = true)
public class PersonaAuthorizationFilter implements WebFilter {

    private final RequestMappingHandlerMapping handlerMapping;

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        return handlerMapping.getHandler(exchange)
                .flatMap(handler -> {
                    if (handler instanceof HandlerMethod handlerMethod) {
                        RequirePersona annotation = handlerMethod.getMethodAnnotation(RequirePersona.class);
                        if (annotation != null) {
                            return validatePersona(exchange, annotation, chain);
                        }
                    }
                    return chain.filter(exchange);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Void> validatePersona(
            ServerWebExchange exchange,
            RequirePersona annotation,
            WebFilterChain chain) {

        AuthPrincipal principal = exchange.getAttribute(AuthPrincipal.EXCHANGE_ATTRIBUTE);

        // No principal means authentication failed or was skipped
        if (principal == null) {
            log.debug("No AuthPrincipal found for @RequirePersona endpoint");
            return unauthorizedResponse(exchange, "AUTHENTICATION_REQUIRED", "Authentication required");
        }

        // Check if persona is in the allowed list
        Set<Persona> allowedPersonas = Arrays.stream(annotation.value()).collect(Collectors.toSet());
        if (!allowedPersonas.contains(principal.persona())) {
            log.warn("Persona {} not allowed. Required: {}", principal.persona(), allowedPersonas);
            return forbiddenResponse(exchange, "PERSONA_NOT_ALLOWED",
                    String.format("Persona '%s' is not authorized for this resource", principal.persona()));
        }

        // For DELEGATE persona, check required delegate types
        if (principal.persona() == Persona.DELEGATE && annotation.requiredDelegates().length > 0) {
            Set<DelegateType> requiredTypes = Arrays.stream(annotation.requiredDelegates())
                    .collect(Collectors.toSet());

            if (!principal.hasAllDelegates(requiredTypes)) {
                Set<DelegateType> missing = requiredTypes.stream()
                        .filter(type -> !principal.hasDelegate(type))
                        .collect(Collectors.toSet());

                log.warn("Missing delegate types for DELEGATE persona: {}", missing);
                return forbiddenResponse(exchange, "MISSING_DELEGATE_PERMISSIONS",
                        String.format("Missing required delegate permissions: %s", missing));
            }
        }

        // Authorization passed
        log.debug("Persona authorization passed for {} on {}",
                principal.persona(), exchange.getRequest().getPath());
        return chain.filter(exchange);
    }

    @NonNull
    private Mono<Void> unauthorizedResponse(
            @NonNull ServerWebExchange exchange,
            @NonNull String code,
            @NonNull String message) {

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"error\":\"unauthorized\",\"code\":\"%s\",\"message\":\"%s\"}",
                escapeJson(code), escapeJson(message));

        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @NonNull
    private Mono<Void> forbiddenResponse(
            @NonNull ServerWebExchange exchange,
            @NonNull String code,
            @NonNull String message) {

        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"error\":\"forbidden\",\"code\":\"%s\",\"message\":\"%s\"}",
                escapeJson(code), escapeJson(message));

        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @NonNull
    private String escapeJson(@NonNull String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
