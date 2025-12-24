package com.example.bff.security.filter;

import com.example.bff.config.BffProperties;
import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.AuthContextHolder;
import com.example.bff.security.context.Persona;
import com.example.bff.security.exception.AuthenticationException;
import com.example.bff.security.session.BffSession;
import com.example.bff.security.session.ClientInfoExtractor;
import com.example.bff.security.session.SessionCookieManager;
import com.example.bff.security.session.SessionStore;
import com.example.bff.util.BffSessionTestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BrowserSessionFilter")
class BrowserSessionFilterTest {

    private static final String COOKIE_NAME = "BFF_SESSION";
    private static final String SESSION_ID = "test-session-123";

    @Mock
    private SessionStore sessionStore;

    @Mock
    private SessionCookieManager cookieManager;

    @Mock
    private ClientInfoExtractor clientInfoExtractor;

    @Mock
    private BffProperties properties;

    @Mock
    private BffProperties.Session sessionProperties;

    private BrowserSessionFilter filter;

    @BeforeEach
    void setUp() {
        filter = new BrowserSessionFilter(sessionStore, cookieManager, clientInfoExtractor, properties);
        when(cookieManager.getCookieName()).thenReturn(COOKIE_NAME);
    }

    @Nested
    @DisplayName("cookie extraction")
    class CookieExtraction {

        @Test
        @DisplayName("should reject request when session cookie is missing")
        void shouldRejectWhenCookieMissing() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data").build());

            WebFilterChain chain = ex -> Mono.empty();

            when(cookieManager.extractSessionId(any())).thenReturn(Optional.empty());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthenticationException.class);
                        assertThat(error.getMessage()).contains("Missing");
                        assertThat(error.getMessage()).contains(COOKIE_NAME);
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("session validation")
    class SessionValidation {

        @Test
        @DisplayName("should reject when session not found in store")
        void shouldRejectWhenSessionNotFound() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data").build());

            WebFilterChain chain = ex -> Mono.empty();

            when(cookieManager.extractSessionId(any())).thenReturn(Optional.of(SESSION_ID));
            when(sessionStore.findById(SESSION_ID)).thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthenticationException.class);
                        assertThat(error.getMessage()).contains("Invalid or expired session");
                    })
                    .verify();
        }

        @Test
        @DisplayName("should authenticate valid session and propagate AuthContext")
        void shouldAuthenticateValidSession() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data")
                            .header("X-Forwarded-For", "192.168.1.100")
                            .header("X-Fingerprint", "test-fingerprint")
                            .build());

            BffSession session = BffSessionTestBuilder.aSelfSession();
            session.setSessionId(SESSION_ID);
            session.setClientIp("192.168.1.100");
            session.setBrowserFingerprint("test-fingerprint");

            AtomicReference<AuthContext> capturedContext = new AtomicReference<>();
            WebFilterChain chain = ex -> AuthContextHolder.getContext()
                    .doOnNext(capturedContext::set)
                    .then();

            when(cookieManager.extractSessionId(any())).thenReturn(Optional.of(SESSION_ID));
            when(sessionStore.findById(SESSION_ID)).thenReturn(Mono.just(session));
            when(sessionStore.updateLastAccessed(SESSION_ID)).thenReturn(Mono.just(session));
            when(properties.getSession()).thenReturn(sessionProperties);
            when(sessionProperties.isSessionBindingEnabled()).thenReturn(true);
            when(clientInfoExtractor.extractClientIp(any())).thenReturn("192.168.1.100");
            when(clientInfoExtractor.extractFingerprint(any())).thenReturn("test-fingerprint");

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(capturedContext.get()).isNotNull();
            assertThat(capturedContext.get().enterpriseId()).isEqualTo(session.getEnterpriseId());
            assertThat(capturedContext.get().persona()).isEqualTo(Persona.SELF);
        }

        @Test
        @DisplayName("should update last accessed timestamp")
        void shouldUpdateLastAccessedTimestamp() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data").build());

            BffSession session = BffSessionTestBuilder.aSelfSession();
            session.setSessionId(SESSION_ID);

            WebFilterChain chain = ex -> Mono.empty();

            when(cookieManager.extractSessionId(any())).thenReturn(Optional.of(SESSION_ID));
            when(sessionStore.findById(SESSION_ID)).thenReturn(Mono.just(session));
            when(sessionStore.updateLastAccessed(SESSION_ID)).thenReturn(Mono.just(session));
            when(properties.getSession()).thenReturn(sessionProperties);
            when(sessionProperties.isSessionBindingEnabled()).thenReturn(false);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            verify(sessionStore).updateLastAccessed(SESSION_ID);
        }
    }

    @Nested
    @DisplayName("session binding")
    class SessionBinding {

        @BeforeEach
        void setUp() {
            when(properties.getSession()).thenReturn(sessionProperties);
        }

        @Test
        @DisplayName("should skip binding validation when disabled")
        void shouldSkipBindingWhenDisabled() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data").build());

            BffSession session = BffSessionTestBuilder.aSelfSession();
            session.setSessionId(SESSION_ID);
            session.setClientIp("192.168.1.100");
            session.setBrowserFingerprint("original-fingerprint");

            WebFilterChain chain = ex -> Mono.empty();

            when(cookieManager.extractSessionId(any())).thenReturn(Optional.of(SESSION_ID));
            when(sessionStore.findById(SESSION_ID)).thenReturn(Mono.just(session));
            when(sessionStore.updateLastAccessed(SESSION_ID)).thenReturn(Mono.just(session));
            when(sessionProperties.isSessionBindingEnabled()).thenReturn(false);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            // Should not call client info extractor when binding disabled
            verify(clientInfoExtractor, never()).extractClientIp(any());
            verify(clientInfoExtractor, never()).extractFingerprint(any());
        }

        @Test
        @DisplayName("should allow when fingerprint matches (even if IP changed)")
        void shouldAllowWhenFingerprintMatches() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data").build());

            BffSession session = BffSessionTestBuilder.aSelfSession();
            session.setSessionId(SESSION_ID);
            session.setClientIp("192.168.1.100");
            session.setBrowserFingerprint("matching-fingerprint");

            WebFilterChain chain = ex -> Mono.empty();

            when(cookieManager.extractSessionId(any())).thenReturn(Optional.of(SESSION_ID));
            when(sessionStore.findById(SESSION_ID)).thenReturn(Mono.just(session));
            when(sessionStore.updateLastAccessed(SESSION_ID)).thenReturn(Mono.just(session));
            when(sessionProperties.isSessionBindingEnabled()).thenReturn(true);
            when(clientInfoExtractor.extractClientIp(any())).thenReturn("10.0.0.50"); // Different IP
            when(clientInfoExtractor.extractFingerprint(any())).thenReturn("matching-fingerprint");

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should allow when IP matches (fallback if fingerprint missing)")
        void shouldAllowWhenIpMatches() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data").build());

            BffSession session = BffSessionTestBuilder.aSelfSession();
            session.setSessionId(SESSION_ID);
            session.setClientIp("192.168.1.100");
            session.setBrowserFingerprint("original-fingerprint");

            WebFilterChain chain = ex -> Mono.empty();

            when(cookieManager.extractSessionId(any())).thenReturn(Optional.of(SESSION_ID));
            when(sessionStore.findById(SESSION_ID)).thenReturn(Mono.just(session));
            when(sessionStore.updateLastAccessed(SESSION_ID)).thenReturn(Mono.just(session));
            when(sessionProperties.isSessionBindingEnabled()).thenReturn(true);
            when(clientInfoExtractor.extractClientIp(any())).thenReturn("192.168.1.100"); // Same IP
            when(clientInfoExtractor.extractFingerprint(any())).thenReturn("different-fingerprint");

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should reject in strict mode when both fingerprint and IP mismatch")
        void shouldRejectInStrictModeWhenBothMismatch() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data").build());

            BffSession session = BffSessionTestBuilder.aSelfSession();
            session.setSessionId(SESSION_ID);
            session.setClientIp("192.168.1.100");
            session.setBrowserFingerprint("original-fingerprint");

            WebFilterChain chain = ex -> Mono.empty();

            when(cookieManager.extractSessionId(any())).thenReturn(Optional.of(SESSION_ID));
            when(sessionStore.findById(SESSION_ID)).thenReturn(Mono.just(session));
            when(sessionProperties.isSessionBindingEnabled()).thenReturn(true);
            when(sessionProperties.isStrictSessionBinding()).thenReturn(true);
            when(clientInfoExtractor.extractClientIp(any())).thenReturn("10.0.0.50"); // Different IP
            when(clientInfoExtractor.extractFingerprint(any())).thenReturn("hijacker-fingerprint"); // Different fingerprint

            StepVerifier.create(filter.filter(exchange, chain))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthenticationException.class);
                        assertThat(error.getMessage()).contains("Session binding validation failed");
                    })
                    .verify();
        }

        @Test
        @DisplayName("should allow in permissive mode when both mismatch (logs warning)")
        void shouldAllowInPermissiveModeWhenBothMismatch() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data").build());

            BffSession session = BffSessionTestBuilder.aSelfSession();
            session.setSessionId(SESSION_ID);
            session.setClientIp("192.168.1.100");
            session.setBrowserFingerprint("original-fingerprint");

            WebFilterChain chain = ex -> Mono.empty();

            when(cookieManager.extractSessionId(any())).thenReturn(Optional.of(SESSION_ID));
            when(sessionStore.findById(SESSION_ID)).thenReturn(Mono.just(session));
            when(sessionStore.updateLastAccessed(SESSION_ID)).thenReturn(Mono.just(session));
            when(sessionProperties.isSessionBindingEnabled()).thenReturn(true);
            when(sessionProperties.isStrictSessionBinding()).thenReturn(false); // Permissive mode
            when(clientInfoExtractor.extractClientIp(any())).thenReturn("10.0.0.50");
            when(clientInfoExtractor.extractFingerprint(any())).thenReturn("hijacker-fingerprint");

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("delegate session")
    class DelegateSession {

        @Test
        @DisplayName("should create AuthContext with delegate types from session")
        void shouldCreateAuthContextWithDelegateTypes() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/data").build());

            BffSession session = BffSessionTestBuilder.aDelegateSession();
            session.setSessionId(SESSION_ID);

            AtomicReference<AuthContext> capturedContext = new AtomicReference<>();
            WebFilterChain chain = ex -> AuthContextHolder.getContext()
                    .doOnNext(capturedContext::set)
                    .then();

            when(cookieManager.extractSessionId(any())).thenReturn(Optional.of(SESSION_ID));
            when(sessionStore.findById(SESSION_ID)).thenReturn(Mono.just(session));
            when(sessionStore.updateLastAccessed(SESSION_ID)).thenReturn(Mono.just(session));
            when(properties.getSession()).thenReturn(sessionProperties);
            when(sessionProperties.isSessionBindingEnabled()).thenReturn(false);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(capturedContext.get()).isNotNull();
            assertThat(capturedContext.get().persona()).isEqualTo(Persona.DELEGATE);
            assertThat(capturedContext.get().delegateTypes()).isNotEmpty();
            assertThat(capturedContext.get().managedMembersMap()).isNotEmpty();
        }
    }
}
