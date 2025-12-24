package com.example.bff.security.filter;

import com.example.bff.security.annotation.RequiredPersona;
import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.AuthContextHolder;
import com.example.bff.security.context.DelegateType;
import com.example.bff.security.context.Persona;
import com.example.bff.security.exception.AuthorizationException;
import com.example.bff.security.validator.EnterpriseIdValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.example.bff.util.AuthContextTestBuilder.anAuthContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PersonaAuthorizationFilter")
class PersonaAuthorizationFilterTest {

    @Mock
    private RequestMappingHandlerMapping handlerMapping;

    @Mock
    private EnterpriseIdValidator enterpriseIdValidator;

    private PersonaAuthorizationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new PersonaAuthorizationFilter(handlerMapping, enterpriseIdValidator);
    }

    @Nested
    @DisplayName("when no handler is found")
    class NoHandlerFound {

        @Test
        @DisplayName("should pass through to next filter")
        void shouldPassThrough() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/unknown").build());

            AtomicBoolean chainCalled = new AtomicBoolean(false);
            WebFilterChain chain = ex -> {
                chainCalled.set(true);
                return Mono.empty();
            };

            when(handlerMapping.getHandler(exchange)).thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(chainCalled.get()).isTrue();
        }
    }

    @Nested
    @DisplayName("when handler has no @RequiredPersona")
    class NoRequiredPersonaAnnotation {

        @Test
        @DisplayName("should pass through without authorization")
        void shouldPassThroughWithoutAuthorization() throws Exception {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/public").build());

            HandlerMethod handlerMethod = createHandlerMethod("publicEndpoint");

            AtomicBoolean chainCalled = new AtomicBoolean(false);
            WebFilterChain chain = ex -> {
                chainCalled.set(true);
                return Mono.empty();
            };

            when(handlerMapping.getHandler(exchange)).thenReturn(Mono.just(handlerMethod));

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(chainCalled.get()).isTrue();
        }
    }

    @Nested
    @DisplayName("when handler has @RequiredPersona")
    class WithRequiredPersonaAnnotation {

        @Test
        @DisplayName("should allow authorized persona for SELF")
        void shouldAllowAuthorizedSelfPersona() throws Exception {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/member/data").build());

            AuthContext selfCtx = anAuthContext()
                    .withPersona(Persona.SELF)
                    .withEnterpriseId("SELF-ENT-001")
                    .build();

            HandlerMethod handlerMethod = createHandlerMethod("selfOnlyEndpoint");

            AtomicBoolean chainCalled = new AtomicBoolean(false);
            WebFilterChain chain = ex -> {
                chainCalled.set(true);
                return Mono.empty();
            };

            when(handlerMapping.getHandler(exchange)).thenReturn(Mono.just(handlerMethod));
            when(enterpriseIdValidator.resolveEnterpriseId(eq(selfCtx), any(), any()))
                    .thenReturn(Mono.just(selfCtx));

            StepVerifier.create(
                            filter.filter(exchange, chain)
                                    .contextWrite(AuthContextHolder.withContext(selfCtx))
                    )
                    .verifyComplete();

            assertThat(chainCalled.get()).isTrue();
        }

        @Test
        @DisplayName("should reject unauthorized persona")
        void shouldRejectUnauthorizedPersona() throws Exception {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/member/data").build());

            // AGENT trying to access SELF-only endpoint
            AuthContext agentCtx = anAuthContext()
                    .withPersona(Persona.AGENT)
                    .build();

            HandlerMethod handlerMethod = createHandlerMethod("selfOnlyEndpoint");

            AtomicBoolean chainSubscribed = new AtomicBoolean(false);
            WebFilterChain chain = ex -> Mono.<Void>empty().doOnSubscribe(s -> chainSubscribed.set(true));

            when(handlerMapping.getHandler(exchange)).thenReturn(Mono.just(handlerMethod));

            StepVerifier.create(
                            filter.filter(exchange, chain)
                                    .contextWrite(AuthContextHolder.withContext(agentCtx))
                    )
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthorizationException.class);
                        assertThat(error.getMessage()).contains("AGENT");
                        assertThat(error.getMessage()).contains("not authorized");
                    })
                    .verify();

            assertThat(chainSubscribed.get()).isFalse();
        }

        @Test
        @DisplayName("should allow multiple valid personas")
        void shouldAllowMultipleValidPersonas() throws Exception {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/health/data").build());

            AuthContext delegateCtx = anAuthContext()
                    .withPersona(Persona.DELEGATE)
                    .withEnterpriseId("DELEGATE-ENT-001")
                    .build();

            HandlerMethod handlerMethod = createHandlerMethod("selfOrDelegateEndpoint");

            AtomicBoolean chainCalled = new AtomicBoolean(false);
            WebFilterChain chain = ex -> {
                chainCalled.set(true);
                return Mono.empty();
            };

            when(handlerMapping.getHandler(exchange)).thenReturn(Mono.just(handlerMethod));
            when(enterpriseIdValidator.resolveEnterpriseId(eq(delegateCtx), any(), any()))
                    .thenReturn(Mono.just(delegateCtx));

            StepVerifier.create(
                            filter.filter(exchange, chain)
                                    .contextWrite(AuthContextHolder.withContext(delegateCtx))
                    )
                    .verifyComplete();

            assertThat(chainCalled.get()).isTrue();
        }
    }

    @Nested
    @DisplayName("GET request enterpriseId resolution")
    class GetRequestEnterpriseIdResolution {

        @Test
        @DisplayName("should resolve enterpriseId from query param for GET")
        void shouldResolveFromQueryParamForGet() throws Exception {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/health/data?enterpriseId=MANAGED-ENT-001").build());

            AuthContext delegateCtx = anAuthContext()
                    .withPersona(Persona.DELEGATE)
                    .build();

            AuthContext resolvedCtx = anAuthContext()
                    .withPersona(Persona.DELEGATE)
                    .withEnterpriseId("MANAGED-ENT-001")
                    .withActiveDelegateTypes(Set.of(DelegateType.RPR, DelegateType.DAA))
                    .build();

            HandlerMethod handlerMethod = createHandlerMethod("selfOrDelegateEndpoint");

            AtomicBoolean chainCalled = new AtomicBoolean(false);
            WebFilterChain chain = ex -> {
                chainCalled.set(true);
                return Mono.empty();
            };

            when(handlerMapping.getHandler(exchange)).thenReturn(Mono.just(handlerMethod));
            when(enterpriseIdValidator.resolveEnterpriseId(eq(delegateCtx), eq("MANAGED-ENT-001"), any()))
                    .thenReturn(Mono.just(resolvedCtx));

            StepVerifier.create(
                            filter.filter(exchange, chain)
                                    .contextWrite(AuthContextHolder.withContext(delegateCtx))
                    )
                    .verifyComplete();

            assertThat(chainCalled.get()).isTrue();
        }
    }

    @Nested
    @DisplayName("POST request validation")
    class PostRequestValidation {

        @Test
        @DisplayName("should only validate delegate types for POST (enterpriseId already resolved)")
        void shouldOnlyValidateDelegateTypesForPost() throws Exception {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/health/data").build());

            AuthContext delegateCtx = anAuthContext()
                    .withPersona(Persona.DELEGATE)
                    .withEnterpriseId("MANAGED-ENT-001")
                    .withActiveDelegateTypes(Set.of(DelegateType.RPR, DelegateType.DAA))
                    .build();

            HandlerMethod handlerMethod = createHandlerMethod("selfOrDelegateEndpoint");

            AtomicBoolean chainCalled = new AtomicBoolean(false);
            WebFilterChain chain = ex -> {
                chainCalled.set(true);
                return Mono.empty();
            };

            when(handlerMapping.getHandler(exchange)).thenReturn(Mono.just(handlerMethod));
            when(enterpriseIdValidator.validateRequiredDelegateTypes(eq(delegateCtx), any()))
                    .thenReturn(Mono.just(delegateCtx));

            StepVerifier.create(
                            filter.filter(exchange, chain)
                                    .contextWrite(AuthContextHolder.withContext(delegateCtx))
                    )
                    .verifyComplete();

            assertThat(chainCalled.get()).isTrue();
        }
    }

    @Nested
    @DisplayName("required delegate types")
    class RequiredDelegateTypes {

        @Test
        @DisplayName("should enforce required delegate types from annotation")
        void shouldEnforceRequiredDelegateTypes() throws Exception {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/health/data?enterpriseId=MANAGED-ENT-001").build());

            AuthContext delegateCtx = anAuthContext()
                    .withPersona(Persona.DELEGATE)
                    .build();

            HandlerMethod handlerMethod = createHandlerMethod("delegateWithRequiredTypes");

            AtomicBoolean chainSubscribed = new AtomicBoolean(false);
            WebFilterChain chain = ex -> Mono.<Void>empty().doOnSubscribe(s -> chainSubscribed.set(true));

            when(handlerMapping.getHandler(exchange)).thenReturn(Mono.just(handlerMethod));
            when(enterpriseIdValidator.resolveEnterpriseId(any(), any(), any()))
                    .thenReturn(Mono.error(new AuthorizationException("Missing required delegate types")));

            StepVerifier.create(
                            filter.filter(exchange, chain)
                                    .contextWrite(AuthContextHolder.withContext(delegateCtx))
                    )
                    .expectError(AuthorizationException.class)
                    .verify();

            assertThat(chainSubscribed.get()).isFalse();
        }
    }

    // Test controller class for creating HandlerMethod instances
    static class TestController {

        public void publicEndpoint() {
        }

        @RequiredPersona(Persona.SELF)
        public void selfOnlyEndpoint() {
        }

        @RequiredPersona({Persona.SELF, Persona.DELEGATE})
        public void selfOrDelegateEndpoint() {
        }

        @RequiredPersona(value = Persona.DELEGATE, requiredDelegates = {DelegateType.RPR, DelegateType.DAA})
        public void delegateWithRequiredTypes() {
        }
    }

    private HandlerMethod createHandlerMethod(String methodName) throws Exception {
        Method method = TestController.class.getMethod(methodName);
        return new HandlerMethod(new TestController(), method);
    }
}
