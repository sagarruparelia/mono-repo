package com.example.bff.security.filter;

import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.AuthContextHolder;
import com.example.bff.security.context.MemberIdType;
import com.example.bff.security.context.Persona;
import com.example.bff.security.exception.AuthenticationException;
import com.example.bff.security.service.IdpPersonaValidator;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class PartnerAuthenticationFilter implements WebFilter, Ordered {

    private static final String HEADER_PERSONA = "X-Persona";
    private static final String HEADER_MEMBER_ID = "X-Member-Id";
    private static final String HEADER_MEMBER_ID_TYPE = "X-Member-Id-Type";

    private final IdpPersonaValidator idpPersonaValidator;

    @Override
    public int getOrder() {
        return -100; // Run early in the filter chain
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String personaHeader = request.getHeaders().getFirst(HEADER_PERSONA);
        String memberIdHeader = request.getHeaders().getFirst(HEADER_MEMBER_ID);
        String memberIdTypeHeader = request.getHeaders().getFirst(HEADER_MEMBER_ID_TYPE);

        // Validate required headers
        if (personaHeader == null || personaHeader.isBlank()) {
            return Mono.error(new AuthenticationException("Missing required header: " + HEADER_PERSONA));
        }
        if (memberIdHeader == null || memberIdHeader.isBlank()) {
            return Mono.error(new AuthenticationException("Missing required header: " + HEADER_MEMBER_ID));
        }
        if (memberIdTypeHeader == null || memberIdTypeHeader.isBlank()) {
            return Mono.error(new AuthenticationException("Missing required header: " + HEADER_MEMBER_ID_TYPE));
        }

        // Parse enums
        Persona persona;
        MemberIdType memberIdType;
        try {
            persona = Persona.valueOf(personaHeader.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return Mono.error(new AuthenticationException("Invalid persona: " + personaHeader));
        }

        try {
            memberIdType = MemberIdType.valueOf(memberIdTypeHeader.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Mono.error(new AuthenticationException("Invalid member ID type: " + memberIdTypeHeader));
        }

        // Create AuthContext from headers
        AuthContext authContext = AuthContext.forPartner(
                memberIdHeader,  // enterpriseId
                memberIdHeader,  // loggedInMemberIdValue
                memberIdType,
                persona
        );

        // Validate IDP-Persona mapping and continue
        return idpPersonaValidator.validate(authContext)
                .doOnNext(ctx -> log.debug("Partner authenticated: persona={}, memberIdType={}, memberId={}",
                        ctx.persona(), ctx.loggedInMemberIdType(), ctx.loggedInMemberIdValue()))
                .flatMap(validatedContext -> chain.filter(exchange)
                        .contextWrite(AuthContextHolder.withContext(validatedContext)));
    }
}
