package com.example.bff.security.filter;

import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.AuthContextHolder;
import com.example.bff.security.context.MemberIdType;
import com.example.bff.security.context.Persona;
import com.example.bff.security.exception.AuthenticationException;
import com.example.bff.security.exception.AuthorizationException;
import com.example.bff.security.service.IdpPersonaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PartnerAuthenticationFilter")
class PartnerAuthenticationFilterTest {

    private static final String HEADER_PERSONA = "X-Persona";
    private static final String HEADER_MEMBER_ID = "X-Member-Id";
    private static final String HEADER_MEMBER_ID_TYPE = "X-Member-Id-Type";

    @Mock
    private IdpPersonaValidator idpPersonaValidator;

    private PartnerAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new PartnerAuthenticationFilter(idpPersonaValidator);
    }

    @Nested
    @DisplayName("header validation")
    class HeaderValidation {

        @Test
        @DisplayName("should reject when X-Persona header is missing")
        void shouldRejectWhenPersonaMissing() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data")
                            .header(HEADER_MEMBER_ID, "ENT-123")
                            .header(HEADER_MEMBER_ID_TYPE, "HSID")
                            .build());

            WebFilterChain chain = ex -> Mono.empty();

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthenticationException.class);
                        assertThat(error.getMessage()).contains("Missing required header");
                        assertThat(error.getMessage()).contains(HEADER_PERSONA);
                    })
                    .verify();
        }

        @Test
        @DisplayName("should reject when X-Member-Id header is missing")
        void shouldRejectWhenMemberIdMissing() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data")
                            .header(HEADER_PERSONA, "SELF")
                            .header(HEADER_MEMBER_ID_TYPE, "HSID")
                            .build());

            WebFilterChain chain = ex -> Mono.empty();

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthenticationException.class);
                        assertThat(error.getMessage()).contains("Missing required header");
                        assertThat(error.getMessage()).contains(HEADER_MEMBER_ID);
                    })
                    .verify();
        }

        @Test
        @DisplayName("should reject when X-Member-Id-Type header is missing")
        void shouldRejectWhenMemberIdTypeMissing() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data")
                            .header(HEADER_PERSONA, "SELF")
                            .header(HEADER_MEMBER_ID, "ENT-123")
                            .build());

            WebFilterChain chain = ex -> Mono.empty();

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthenticationException.class);
                        assertThat(error.getMessage()).contains("Missing required header");
                        assertThat(error.getMessage()).contains(HEADER_MEMBER_ID_TYPE);
                    })
                    .verify();
        }

        @Test
        @DisplayName("should reject when X-Persona header is blank")
        void shouldRejectWhenPersonaBlank() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data")
                            .header(HEADER_PERSONA, "   ")
                            .header(HEADER_MEMBER_ID, "ENT-123")
                            .header(HEADER_MEMBER_ID_TYPE, "HSID")
                            .build());

            WebFilterChain chain = ex -> Mono.empty();

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthenticationException.class);
                        assertThat(error.getMessage()).contains("Missing required header");
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("enum parsing")
    class EnumParsing {

        @Test
        @DisplayName("should reject invalid persona value")
        void shouldRejectInvalidPersona() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data")
                            .header(HEADER_PERSONA, "INVALID_PERSONA")
                            .header(HEADER_MEMBER_ID, "ENT-123")
                            .header(HEADER_MEMBER_ID_TYPE, "HSID")
                            .build());

            WebFilterChain chain = ex -> Mono.empty();

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthenticationException.class);
                        assertThat(error.getMessage()).contains("Invalid persona");
                    })
                    .verify();
        }

        @Test
        @DisplayName("should reject invalid member ID type")
        void shouldRejectInvalidMemberIdType() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data")
                            .header(HEADER_PERSONA, "SELF")
                            .header(HEADER_MEMBER_ID, "ENT-123")
                            .header(HEADER_MEMBER_ID_TYPE, "INVALID_TYPE")
                            .build());

            WebFilterChain chain = ex -> Mono.empty();

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthenticationException.class);
                        assertThat(error.getMessage()).contains("Invalid member ID type");
                    })
                    .verify();
        }

        @Test
        @DisplayName("should handle case-insensitive persona with dashes")
        void shouldHandleCaseInsensitivePersona() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data")
                            .header(HEADER_PERSONA, "case-worker")  // Lowercase with dash
                            .header(HEADER_MEMBER_ID, "ENT-123")
                            .header(HEADER_MEMBER_ID_TYPE, "OHID")
                            .build());

            WebFilterChain chain = ex -> Mono.empty();

            AuthContext validatedContext = AuthContext.forPartner(
                    "ENT-123", "ENT-123", MemberIdType.OHID, Persona.CASE_WORKER);

            when(idpPersonaValidator.validate(any())).thenReturn(Mono.just(validatedContext));

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("IDP-Persona validation")
    class IdpPersonaValidation {

        @Test
        @DisplayName("should authenticate valid HSID + SELF combination")
        void shouldAuthenticateHsidSelf() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data")
                            .header(HEADER_PERSONA, "SELF")
                            .header(HEADER_MEMBER_ID, "ENT-123")
                            .header(HEADER_MEMBER_ID_TYPE, "HSID")
                            .build());

            AtomicReference<AuthContext> capturedContext = new AtomicReference<>();
            WebFilterChain chain = ex -> AuthContextHolder.getContext()
                    .doOnNext(capturedContext::set)
                    .then();

            AuthContext validatedContext = AuthContext.forPartner(
                    "ENT-123", "ENT-123", MemberIdType.HSID, Persona.SELF);

            when(idpPersonaValidator.validate(any())).thenReturn(Mono.just(validatedContext));

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(capturedContext.get()).isNotNull();
            assertThat(capturedContext.get().persona()).isEqualTo(Persona.SELF);
            assertThat(capturedContext.get().loggedInMemberIdType()).isEqualTo(MemberIdType.HSID);
            assertThat(capturedContext.get().enterpriseId()).isEqualTo("ENT-123");
        }

        @Test
        @DisplayName("should authenticate valid HSID + DELEGATE combination")
        void shouldAuthenticateHsidDelegate() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data")
                            .header(HEADER_PERSONA, "DELEGATE")
                            .header(HEADER_MEMBER_ID, "ENT-456")
                            .header(HEADER_MEMBER_ID_TYPE, "HSID")
                            .build());

            AtomicReference<AuthContext> capturedContext = new AtomicReference<>();
            WebFilterChain chain = ex -> AuthContextHolder.getContext()
                    .doOnNext(capturedContext::set)
                    .then();

            AuthContext validatedContext = AuthContext.forPartner(
                    "ENT-456", "ENT-456", MemberIdType.HSID, Persona.DELEGATE);

            when(idpPersonaValidator.validate(any())).thenReturn(Mono.just(validatedContext));

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(capturedContext.get().persona()).isEqualTo(Persona.DELEGATE);
        }

        @Test
        @DisplayName("should authenticate valid MSID + AGENT combination")
        void shouldAuthenticateMsidAgent() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data")
                            .header(HEADER_PERSONA, "AGENT")
                            .header(HEADER_MEMBER_ID, "AGENT-789")
                            .header(HEADER_MEMBER_ID_TYPE, "MSID")
                            .build());

            AtomicReference<AuthContext> capturedContext = new AtomicReference<>();
            WebFilterChain chain = ex -> AuthContextHolder.getContext()
                    .doOnNext(capturedContext::set)
                    .then();

            AuthContext validatedContext = AuthContext.forPartner(
                    "AGENT-789", "AGENT-789", MemberIdType.MSID, Persona.AGENT);

            when(idpPersonaValidator.validate(any())).thenReturn(Mono.just(validatedContext));

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(capturedContext.get().persona()).isEqualTo(Persona.AGENT);
            assertThat(capturedContext.get().loggedInMemberIdType()).isEqualTo(MemberIdType.MSID);
        }

        @Test
        @DisplayName("should authenticate valid OHID + CASE_WORKER combination")
        void shouldAuthenticateOhidCaseWorker() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data")
                            .header(HEADER_PERSONA, "CASE_WORKER")
                            .header(HEADER_MEMBER_ID, "CW-001")
                            .header(HEADER_MEMBER_ID_TYPE, "OHID")
                            .build());

            AtomicReference<AuthContext> capturedContext = new AtomicReference<>();
            WebFilterChain chain = ex -> AuthContextHolder.getContext()
                    .doOnNext(capturedContext::set)
                    .then();

            AuthContext validatedContext = AuthContext.forPartner(
                    "CW-001", "CW-001", MemberIdType.OHID, Persona.CASE_WORKER);

            when(idpPersonaValidator.validate(any())).thenReturn(Mono.just(validatedContext));

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(capturedContext.get().persona()).isEqualTo(Persona.CASE_WORKER);
            assertThat(capturedContext.get().loggedInMemberIdType()).isEqualTo(MemberIdType.OHID);
        }

        @Test
        @DisplayName("should reject invalid IDP-Persona combination")
        void shouldRejectInvalidIdpPersonaCombination() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data")
                            .header(HEADER_PERSONA, "AGENT")  // AGENT not allowed for HSID
                            .header(HEADER_MEMBER_ID, "ENT-123")
                            .header(HEADER_MEMBER_ID_TYPE, "HSID")
                            .build());

            WebFilterChain chain = ex -> Mono.empty();

            when(idpPersonaValidator.validate(any()))
                    .thenReturn(Mono.error(new AuthorizationException(
                            "Persona AGENT is not allowed for IDP HSID")));

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthorizationException.class);
                        assertThat(error.getMessage()).contains("AGENT");
                        assertThat(error.getMessage()).contains("HSID");
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("context propagation")
    class ContextPropagation {

        @Test
        @DisplayName("should propagate AuthContext through filter chain")
        void shouldPropagateAuthContext() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data")
                            .header(HEADER_PERSONA, "SELF")
                            .header(HEADER_MEMBER_ID, "ENT-999")
                            .header(HEADER_MEMBER_ID_TYPE, "HSID")
                            .build());

            AtomicReference<AuthContext> capturedContext = new AtomicReference<>();
            WebFilterChain chain = ex -> AuthContextHolder.getContext()
                    .doOnNext(capturedContext::set)
                    .then();

            AuthContext validatedContext = AuthContext.forPartner(
                    "ENT-999", "ENT-999", MemberIdType.HSID, Persona.SELF);

            when(idpPersonaValidator.validate(any())).thenReturn(Mono.just(validatedContext));

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(capturedContext.get()).isNotNull();
            assertThat(capturedContext.get().enterpriseId()).isEqualTo("ENT-999");
            assertThat(capturedContext.get().loggedInMemberIdValue()).isEqualTo("ENT-999");
        }
    }
}
