package com.example.bff.auth.resolver;

import com.example.bff.auth.model.AuthPrincipal;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Resolves {@link AuthPrincipal} parameters in controller methods.
 *
 * <p>This resolver extracts AuthPrincipal from the exchange attributes where it
 * was stored by {@link com.example.bff.auth.filter.DualAuthWebFilter}.</p>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * @GetMapping("/profile")
 * public Mono<ResponseEntity<Profile>> getProfile(AuthPrincipal principal) {
 *     String memberId = principal.enterpriseId();
 *     // ...
 * }
 * }</pre>
 */
@Component
public class AuthPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(@NonNull MethodParameter parameter) {
        return AuthPrincipal.class.equals(parameter.getParameterType());
    }

    @Override
    @NonNull
    public Mono<Object> resolveArgument(
            @NonNull MethodParameter parameter,
            @NonNull BindingContext bindingContext,
            @NonNull ServerWebExchange exchange) {

        AuthPrincipal principal = exchange.getAttribute(AuthPrincipal.EXCHANGE_ATTRIBUTE);

        if (principal == null) {
            return Mono.error(new IllegalStateException(
                    "AuthPrincipal not found in exchange attributes. " +
                    "Ensure DualAuthWebFilter is running and the endpoint requires authentication."));
        }

        return Mono.just(principal);
    }
}
