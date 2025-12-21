package com.example.bff.config;

import com.example.bff.auth.resolver.AuthPrincipalArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;

/**
 * WebFlux configuration for custom argument resolvers.
 *
 * <p>Registers the {@link AuthPrincipalArgumentResolver} to enable
 * injection of {@link com.example.bff.auth.model.AuthPrincipal} into controller methods.</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebFluxConfig implements WebFluxConfigurer {

    private final AuthPrincipalArgumentResolver authPrincipalArgumentResolver;

    @Override
    public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
        configurer.addCustomResolver(authPrincipalArgumentResolver);
    }
}
