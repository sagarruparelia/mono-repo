package com.example.bff.config;

import com.example.bff.security.handler.LoginHandler;
import com.example.bff.security.handler.OidcCallbackHandler;
import com.example.bff.security.resolver.AuthContextArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebFluxConfigurer {

    private final AuthContextArgumentResolver authContextArgumentResolver;

    @Override
    public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
        configurer.addCustomResolver(authContextArgumentResolver);
    }

    @Bean
    public RouterFunction<ServerResponse> publicRoutes(
            LoginHandler loginHandler,
            OidcCallbackHandler oidcCallbackHandler) {

        return RouterFunctions.route()
                // Login endpoint - redirects to HSID
                .GET("/login", loginHandler::handleLogin)
                // Root with code parameter - OIDC callback
                .GET("/", request -> {
                    if (request.queryParam("code").isPresent()) {
                        return oidcCallbackHandler.handleCallback(request);
                    }
                    // Default response for root without code
                    return ServerResponse.ok().bodyValue("BFF Application");
                })
                .build();
    }
}
