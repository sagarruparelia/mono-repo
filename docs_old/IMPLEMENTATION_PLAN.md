# Implementation Plan

## Version Summary

| Component | Version | Release Date |
|-----------|---------|--------------|
| Spring Boot | 3.5.8 | Nov 2025 |
| Spring Security | 6.5.6 | Via Boot |
| Spring Session | 3.5.3 | Via Boot |
| Valkey | 8.1.5 | Dec 2025 |
| React | 19.2.3 | Current |
| Zustand | 5.0.9 | Latest |
| React Query | 5.90.12 | Latest |

---

## Phase Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          IMPLEMENTATION TIMELINE                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  Phase 1          Phase 2          Phase 3          Phase 4          Phase 5   │
│  Foundation       HSID Auth        Session &        MFE Integration  Zero Trust│
│                                    Proxy Auth                                   │
│  ┌─────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐  │
│  │ Backend │     │  OIDC   │     │ Valkey  │     │ Web     │     │ Cont.   │  │
│  │ Setup   │ ──► │  PKCE   │ ──► │ Session │ ──► │Component│ ──► │ Verify  │  │
│  │ Frontend│     │  Flow   │     │ OAuth2  │     │ State   │     │ Audit   │  │
│  │ Setup   │     │         │     │ CC      │     │ Mgmt    │     │ Rate    │  │
│  └─────────┘     └─────────┘     └─────────┘     └─────────┘     └─────────┘  │
│                                                                                 │
│  Deliverables:   Deliverables:   Deliverables:   Deliverables:   Deliverables:│
│  - BFF scaffold  - Login flow    - Hybrid sess   - mfe-summary    - Device val│
│  - Dependencies  - Callback      - IP/UA bind    - mfe-profile    - Step-up   │
│  - Config files  - Single sess   - Proxy filter  - Zustand store  - Anomaly   │
│  - web-cl routes - Persona map   - Partner val   - React Query    - Audit log │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Foundation

### Objective
Set up project infrastructure, dependencies, and configuration files.

### 1.1 Backend Setup (BFF)

#### Update pom.xml
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.8</version>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>bff</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>bff</name>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <!-- Core -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Security: OAuth2 Client (HSID PKCE) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-client</artifactId>
        </dependency>

        <!-- Security: Resource Server (Proxy JWT validation) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>

        <!-- Session: Valkey/Redis -->
        <dependency>
            <groupId>org.springframework.session</groupId>
            <artifactId>spring-session-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Configuration Processor -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Lombok (optional - reduces boilerplate) -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

#### Create Configuration Structure
```
apps/bff/src/main/resources/
├── application.yml                 # Main config
├── application-local.yml           # Local dev
├── application-dev.yml             # Dev environment
├── application-prod.yml            # Production
└── config/
    ├── security-paths.yml          # Externalized security rules
    ├── rate-limiting.yml           # Rate limit rules
    └── zero-trust.yml              # Zero trust settings
```

#### Base application.yml
```yaml
spring:
  application:
    name: bff
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}

  # Import external config files
  config:
    import:
      - classpath:config/security-paths.yml
      - classpath:config/rate-limiting.yml
      - classpath:config/zero-trust.yml

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when_authorized
```

#### Create Package Structure
```
apps/bff/src/main/java/com/example/bff/
├── BffApplication.java
├── config/
│   ├── SecurityConfig.java
│   ├── SessionConfig.java
│   ├── WebClientConfig.java
│   └── properties/
│       ├── SecurityPathsProperties.java
│       ├── SessionProperties.java
│       ├── MfeProxyProperties.java
│       ├── PersonaProperties.java
│       ├── RateLimitProperties.java
│       └── ZeroTrustProperties.java
├── auth/
│   ├── controller/
│   ├── service/
│   ├── filter/
│   └── dto/
├── mfe/
│   ├── controller/
│   ├── service/
│   └── filter/
├── session/
│   ├── service/
│   └── model/
└── common/
    ├── exception/
    └── util/
```

### 1.2 Frontend Setup (web-cl)

#### Install Dependencies
```bash
# State management
npm install zustand@5.0.9

# Server state
npm install @tanstack/react-query@5.90.12

# Fix vulnerability
npm install @nxrocks/nx-spring-boot@8.1.0 --save-dev

# Optional: DevTools
npm install -D @tanstack/react-query-devtools@5.90.12
```

#### Create Frontend Structure
```
apps/web-cl/src/
├── main.tsx
├── app/
│   ├── App.tsx
│   ├── providers/
│   │   ├── index.tsx               # Combined providers
│   │   ├── QueryProvider.tsx       # React Query
│   │   └── AuthProvider.tsx        # Auth context
│   ├── routes/
│   │   ├── index.tsx               # Route config
│   │   ├── public/
│   │   │   └── LandingPage.tsx
│   │   └── protected/
│   │       ├── Dashboard.tsx
│   │       ├── Summary.tsx
│   │       └── Profile.tsx
│   ├── guards/
│   │   └── AuthGuard.tsx
│   ├── stores/
│   │   ├── auth.store.ts
│   │   └── ui.store.ts
│   ├── hooks/
│   │   ├── useAuth.ts
│   │   └── useSession.ts
│   └── api/
│       ├── client.ts               # Axios/fetch wrapper
│       └── endpoints.ts            # API definitions
└── libs/
    └── shared-state/               # Shared between web-cl and MFEs
        ├── src/
        │   ├── stores/
        │   ├── queries/
        │   └── types/
        └── package.json
```

### 1.3 Deliverables Checklist

- [ ] BFF pom.xml updated with Spring Boot 3.5.8
- [ ] BFF package structure created
- [ ] Configuration files created (yml)
- [ ] Properties classes scaffolded
- [ ] Frontend dependencies installed
- [ ] Frontend folder structure created
- [ ] Shared state library initialized
- [ ] Local Valkey 8.1.5 running (Docker)

---

## Phase 2: HSID Authentication

### Objective
Implement OIDC PKCE flow with HSID, single session enforcement, persona mapping.

### 2.1 HSID OAuth2 Client Configuration

#### application.yml (Auth section)
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          hsid:
            client-id: ${HSID_CLIENT_ID}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/api/auth/callback"
            scope: openid,profile,email
            client-authentication-method: none    # PKCE
        provider:
          hsid:
            issuer-uri: ${HSID_ISSUER_URI}
            authorization-uri: ${HSID_AUTH_URI}
            token-uri: ${HSID_TOKEN_URI}
            jwk-set-uri: ${HSID_JWKS_URI}
            user-info-uri: ${HSID_USERINFO_URI}
            user-name-attribute: sub
```

### 2.2 Security Configuration

```java
// config/SecurityConfig.java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final SecurityPathsProperties pathsConfig;

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(auth -> {
                // Public paths from config
                pathsConfig.getPublic().forEach(p ->
                    auth.pathMatchers(p.getPattern()).permitAll());

                // All other require auth
                auth.anyExchange().authenticated();
            })
            .oauth2Login(oauth2 -> oauth2
                .authenticationSuccessHandler(hsidSuccessHandler())
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(logoutSuccessHandler())
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
            )
            .build();
    }

    @Bean
    public ServerAuthenticationSuccessHandler hsidSuccessHandler() {
        return new HsidAuthenticationSuccessHandler(sessionService, personaConfig);
    }
}
```

### 2.3 Authentication Success Handler (Single Session + Persona)

```java
// auth/handler/HsidAuthenticationSuccessHandler.java
@Component
@RequiredArgsConstructor
public class HsidAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    private final SessionService sessionService;
    private final PersonaProperties personaConfig;

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange exchange,
                                               Authentication authentication) {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        OidcUser oidcUser = (OidcUser) token.getPrincipal();

        String userId = oidcUser.getSubject();

        // Extract persona from HSID claims
        String persona = oidcUser.getClaimAsString(personaConfig.getHsid().getClaimName());
        List<String> dependents = null;

        if ("parent".equals(persona)) {
            dependents = oidcUser.getClaimAsStringList(
                personaConfig.getHsid().getParent().getDependentsClaim());
        }

        // Invalidate any existing session for this user (single session)
        return sessionService.invalidateExistingSessions(userId)
            .then(sessionService.createSession(
                userId,
                oidcUser,
                persona,
                dependents,
                extractClientInfo(exchange.getExchange())
            ))
            .then(redirectToApp(exchange));
    }
}
```

### 2.4 Session Service (Single Session Enforcement)

```java
// session/service/SessionService.java
@Service
@RequiredArgsConstructor
public class SessionService {

    private final ReactiveRedisOperations<String, String> redisOps;
    private final SessionProperties sessionConfig;

    private static final String USER_SESSION_KEY = "user_session:";
    private static final String SESSION_KEY = "session:";

    public Mono<Void> invalidateExistingSessions(String userId) {
        String userSessionKey = USER_SESSION_KEY + userId;

        return redisOps.opsForValue().get(userSessionKey)
            .flatMap(existingSessionId ->
                redisOps.delete(SESSION_KEY + existingSessionId)
                    .then(redisOps.delete(userSessionKey))
            )
            .then();
    }

    public Mono<String> createSession(String userId, OidcUser user,
                                       String persona, List<String> dependents,
                                       ClientInfo clientInfo) {
        String sessionId = UUID.randomUUID().toString();
        String sessionKey = SESSION_KEY + sessionId;
        String userSessionKey = USER_SESSION_KEY + userId;

        Map<String, String> sessionData = Map.of(
            "userId", userId,
            "email", user.getEmail(),
            "name", user.getFullName(),
            "persona", persona,
            "dependents", dependents != null ? String.join(",", dependents) : "",
            "ipAddress", clientInfo.getIpAddress(),
            "userAgentHash", clientInfo.getUserAgentHash(),
            "createdAt", String.valueOf(Instant.now().toEpochMilli())
        );

        Duration ttl = sessionConfig.getTimeout();

        return redisOps.opsForHash().putAll(sessionKey, sessionData)
            .then(redisOps.expire(sessionKey, ttl))
            .then(redisOps.opsForValue().set(userSessionKey, sessionId, ttl))
            .thenReturn(sessionId);
    }
}
```

### 2.5 Deliverables Checklist

- [ ] HSID OAuth2 client configured
- [ ] SecurityConfig with oauth2Login
- [ ] Authentication success handler
- [ ] Single session enforcement
- [ ] Persona extraction from HSID claims
- [ ] Session creation in Valkey
- [ ] Logout handler (session cleanup)
- [ ] Frontend login redirect flow
- [ ] Frontend callback handler

---

## Phase 3: Session Management & Proxy Auth

### Objective
Implement hybrid session with IP/UA binding, OAuth2 CC validation for MFE proxy.

### 3.1 Session Binding Filter

```java
// session/filter/SessionBindingFilter.java
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SessionBindingFilter implements WebFilter {

    private final SessionProperties sessionConfig;
    private final ReactiveRedisOperations<String, String> redisOps;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!sessionConfig.getBinding().isEnabled()) {
            return chain.filter(exchange);
        }

        return extractSessionId(exchange)
            .flatMap(sessionId -> validateAndRefresh(sessionId, exchange))
            .then(chain.filter(exchange))
            .onErrorResume(SessionBindingException.class, e ->
                invalidateAndRedirect(exchange, e.getMessage()));
    }

    private Mono<Void> validateAndRefresh(String sessionId, ServerWebExchange exchange) {
        String sessionKey = "session:" + sessionId;

        return redisOps.opsForHash().entries(sessionKey)
            .collectMap(e -> (String) e.getKey(), e -> (String) e.getValue())
            .flatMap(sessionData -> {
                // Validate IP binding
                if (sessionConfig.getBinding().isIpAddress()) {
                    String sessionIp = sessionData.get("ipAddress");
                    String requestIp = extractClientIp(exchange);
                    if (!sessionIp.equals(requestIp)) {
                        return Mono.error(new SessionBindingException("IP_MISMATCH"));
                    }
                }

                // Validate User-Agent binding
                if (sessionConfig.getBinding().isUserAgent()) {
                    String sessionUaHash = sessionData.get("userAgentHash");
                    String requestUaHash = hashUserAgent(exchange);
                    if (!sessionUaHash.equals(requestUaHash)) {
                        return Mono.error(new SessionBindingException("UA_MISMATCH"));
                    }
                }

                // Refresh TTL (sliding expiration)
                return redisOps.expire(sessionKey, sessionConfig.getTimeout())
                    .then(updateLastAccessed(sessionKey));
            });
    }
}
```

### 3.2 OAuth2 Resource Server Configuration (Proxy)

```yaml
# application.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OAUTH2_CC_ISSUER_URI}
          audiences: bff-api
```

### 3.3 Proxy Auth Filter

```java
// mfe/filter/ProxyAuthFilter.java
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mfe.proxy.enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class ProxyAuthFilter implements WebFilter {

    private final MfeProxyProperties proxyConfig;
    private final PersonaProperties personaConfig;
    private final ReactiveJwtDecoder jwtDecoder;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String authType = headers.getFirst(proxyConfig.getHeaders().getAuthType());

        // Skip if not a proxy request
        if (!"oauth2-proxy".equals(authType)) {
            return chain.filter(exchange);
        }

        // Extract proxy context from headers
        ProxyContext ctx = extractProxyContext(headers);

        // Validate JWT + partner + persona
        return extractBearerToken(exchange)
            .flatMap(jwtDecoder::decode)
            .flatMap(jwt -> validateProxyRequest(jwt, ctx))
            .doOnSuccess(v -> {
                // Store context for downstream use
                exchange.getAttributes().put("proxyContext", ctx);
            })
            .then(chain.filter(exchange))
            .onErrorResume(e -> unauthorized(exchange, e.getMessage()));
    }

    private ProxyContext extractProxyContext(HttpHeaders headers) {
        return ProxyContext.builder()
            .partnerId(headers.getFirst(proxyConfig.getHeaders().getPartnerId()))
            .memberId(headers.getFirst(proxyConfig.getHeaders().getMemberId()))
            .persona(headers.getFirst(proxyConfig.getHeaders().getPersona()))
            .operatorId(headers.getFirst(proxyConfig.getHeaders().getOperatorId()))
            .operatorName(headers.getFirst(proxyConfig.getHeaders().getOperatorName()))
            .correlationId(headers.getFirst(proxyConfig.getHeaders().getCorrelationId()))
            .build();
    }

    private Mono<Void> validateProxyRequest(Jwt jwt, ProxyContext ctx) {
        // 1. Validate partner exists
        var partner = proxyConfig.getAllowedPartners().stream()
            .filter(p -> p.getId().equals(ctx.getPartnerId()))
            .findFirst()
            .orElseThrow(() -> new AccessDeniedException("Unknown partner"));

        // 2. Validate persona is allowed
        if (!partner.getAllowedPersonas().contains(ctx.getPersona())) {
            return Mono.error(new AccessDeniedException("Persona not allowed for partner"));
        }

        // 3. Validate persona is valid
        if (!personaConfig.getProxy().getAllowed().contains(ctx.getPersona())) {
            return Mono.error(new AccessDeniedException("Invalid persona"));
        }

        // 4. Validate JWT scopes
        Set<String> requiredScopes = new HashSet<>(partner.getScopes());
        Set<String> tokenScopes = jwt.getClaimAsStringList("scope").stream()
            .collect(Collectors.toSet());

        if (!tokenScopes.containsAll(requiredScopes)) {
            return Mono.error(new AccessDeniedException("Insufficient scopes"));
        }

        return Mono.empty();
    }
}
```

### 3.4 Deliverables Checklist

- [ ] Session binding filter (IP + UA)
- [ ] Sliding session expiration
- [ ] OAuth2 resource server configured
- [ ] Proxy auth filter
- [ ] Partner validation
- [ ] Persona validation
- [ ] ProxyContext extraction and propagation
- [ ] Integration tests for both auth paths

---

## Phase 4: MFE Integration

### Objective
Set up MFE as web component, shared state, React Query configuration.

### 4.1 Shared State Library

```typescript
// libs/shared-state/src/types/persona.ts
export type HsidPersona = 'individual' | 'parent';
export type ProxyPersona = 'agent' | 'config' | 'case_worker';
export type Persona = HsidPersona | ProxyPersona;

export interface User {
  sub: string;
  name: string;
  email: string;
}

export interface AuthContext {
  isAuthenticated: boolean;
  persona: Persona | null;
  user?: User;
  dependents?: string[];
  sessionExpiry?: number;
  memberId?: string;
  operatorId?: string;
  operatorName?: string;
}
```

```typescript
// libs/shared-state/src/stores/auth.store.ts
import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { AuthContext, Persona, User } from '../types/persona';

interface AuthActions {
  setHsidAuth: (user: User, persona: Persona, expiry: number, dependents?: string[]) => void;
  setProxyContext: (memberId: string, persona: Persona, operatorId: string, operatorName: string) => void;
  clearAuth: () => void;
  refreshSession: (expiry: number) => void;
}

type AuthStore = AuthContext & AuthActions;

const initialState: AuthContext = {
  isAuthenticated: false,
  persona: null,
};

export const useAuthStore = create<AuthStore>()(
  persist(
    (set) => ({
      ...initialState,

      setHsidAuth: (user, persona, expiry, dependents) =>
        set({
          isAuthenticated: true,
          user,
          persona,
          sessionExpiry: expiry,
          dependents,
        }),

      setProxyContext: (memberId, persona, operatorId, operatorName) =>
        set({
          isAuthenticated: true,
          memberId,
          persona,
          operatorId,
          operatorName,
        }),

      clearAuth: () => set(initialState),

      refreshSession: (expiry) => set({ sessionExpiry: expiry }),
    }),
    {
      name: 'auth-storage',
      storage: createJSONStorage(() => sessionStorage),
    }
  )
);
```

### 4.2 React Query Configuration

```typescript
// libs/shared-state/src/queries/queryClient.ts
import { QueryClient } from '@tanstack/react-query';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,      // 5 minutes
      gcTime: 10 * 60 * 1000,        // 10 minutes (formerly cacheTime)
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});
```

```typescript
// libs/shared-state/src/queries/user.queries.ts
import { useQuery, useMutation } from '@tanstack/react-query';
import { api } from '../api/client';

export const userKeys = {
  all: ['user'] as const,
  profile: () => [...userKeys.all, 'profile'] as const,
  summary: (userId: string) => [...userKeys.all, 'summary', userId] as const,
};

export const useUserProfile = () => {
  return useQuery({
    queryKey: userKeys.profile(),
    queryFn: () => api.get('/api/user/profile'),
  });
};

export const useSummary = (userId: string) => {
  return useQuery({
    queryKey: userKeys.summary(userId),
    queryFn: () => api.get(`/api/mfe/summary/${userId}`),
    enabled: !!userId,
  });
};
```

### 4.3 MFE Web Component Wrapper

```typescript
// apps/mfe-summary/src/web-component.tsx
import { createRoot, Root } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from '@mono/shared-state';
import { SummaryApp } from './app/SummaryApp';

class MfeSummary extends HTMLElement {
  private root: Root | null = null;

  static get observedAttributes() {
    return ['member-id', 'persona', 'operator-id', 'operator-name', 'api-base'];
  }

  connectedCallback() {
    const shadow = this.attachShadow({ mode: 'open' });
    const container = document.createElement('div');
    shadow.appendChild(container);

    this.root = createRoot(container);
    this.render();
  }

  disconnectedCallback() {
    this.root?.unmount();
  }

  attributeChangedCallback() {
    this.render();
  }

  private render() {
    const props = {
      memberId: this.getAttribute('member-id') || '',
      persona: this.getAttribute('persona') || '',
      operatorId: this.getAttribute('operator-id') || '',
      operatorName: this.getAttribute('operator-name') || '',
      apiBase: this.getAttribute('api-base') || '',
    };

    this.root?.render(
      <QueryClientProvider client={queryClient}>
        <SummaryApp {...props} />
      </QueryClientProvider>
    );
  }
}

customElements.define('mfe-summary', MfeSummary);
```

### 4.4 Deliverables Checklist

- [ ] shared-state library created
- [ ] Zustand auth store
- [ ] React Query client configured
- [ ] Query hooks for user/summary
- [ ] mfe-summary web component wrapper
- [ ] mfe-profile web component wrapper
- [ ] web-cl integration with MFEs
- [ ] API client with interceptors

---

## Phase 5: Zero Trust Security

### Objective
Implement continuous verification, device validation, rate limiting, audit logging.

### 5.1 Zero Trust Filter Chain

```java
// config/SecurityConfig.java - Updated
@Bean
public SecurityWebFilterChain securityFilterChain(
        ServerHttpSecurity http,
        RateLimitFilter rateLimitFilter,
        SessionBindingFilter sessionBindingFilter,
        DeviceValidationFilter deviceValidationFilter,
        AnomalyDetectionFilter anomalyDetectionFilter,
        AuditLoggingFilter auditLoggingFilter,
        ProxyAuthFilter proxyAuthFilter) {

    return http
        .addFilterAt(rateLimitFilter, SecurityWebFiltersOrder.FIRST)
        .addFilterBefore(auditLoggingFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterBefore(sessionBindingFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterBefore(deviceValidationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterBefore(anomalyDetectionFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .addFilterBefore(proxyAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        // ... rest of config
        .build();
}
```

### 5.2 Rate Limiting Filter

```java
// zerotrust/filter/RateLimitFilter.java
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rate-limiting.enabled", havingValue = "true")
public class RateLimitFilter implements WebFilter {

    private final RateLimitProperties config;
    private final ReactiveRedisOperations<String, String> redisOps;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String clientKey = resolveClientKey(exchange);
        RateLimitRule rule = findMatchingRule(exchange);

        return checkRateLimit(clientKey, rule)
            .flatMap(allowed -> {
                if (!allowed) {
                    return tooManyRequests(exchange);
                }
                return chain.filter(exchange);
            });
    }

    private Mono<Boolean> checkRateLimit(String key, RateLimitRule rule) {
        String redisKey = "rate:" + key;
        long windowSeconds = rule.getWindowSeconds();

        return redisOps.opsForValue()
            .increment(redisKey)
            .flatMap(count -> {
                if (count == 1) {
                    return redisOps.expire(redisKey, Duration.ofSeconds(windowSeconds))
                        .thenReturn(true);
                }
                return Mono.just(count <= rule.getMaxRequests());
            });
    }
}
```

### 5.3 Audit Logging Filter

```java
// zerotrust/filter/AuditLoggingFilter.java
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zero-trust.audit.enabled", havingValue = "true")
public class AuditLoggingFilter implements WebFilter {

    private final ZeroTrustProperties config;
    private final AuditLogService auditLogService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startTime = System.currentTimeMillis();

        return chain.filter(exchange)
            .doFinally(signalType -> {
                AuditEntry entry = AuditEntry.builder()
                    .timestamp(Instant.now())
                    .correlationId(exchange.getRequest().getHeaders()
                        .getFirst("X-Correlation-Id"))
                    .userId(extractUserId(exchange))
                    .persona(extractPersona(exchange))
                    .ipAddress(extractClientIp(exchange))
                    .userAgent(exchange.getRequest().getHeaders().getFirst("User-Agent"))
                    .method(exchange.getRequest().getMethod().name())
                    .path(exchange.getRequest().getPath().value())
                    .status(exchange.getResponse().getStatusCode().value())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();

                auditLogService.log(entry).subscribe();
            });
    }
}
```

### 5.4 Step-Up Authentication

```java
// zerotrust/filter/StepUpAuthFilter.java
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zero-trust.step-up-auth.enabled", havingValue = "true")
public class StepUpAuthFilter implements WebFilter {

    private final ZeroTrustProperties config;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        return config.getStepUpAuth().getTriggers().stream()
            .filter(trigger -> pathMatches(path, trigger.getPattern())
                && trigger.getMethods().contains(method))
            .findFirst()
            .map(trigger -> checkLastAuthTime(exchange, trigger))
            .orElse(chain.filter(exchange));
    }

    private Mono<Void> checkLastAuthTime(ServerWebExchange exchange, StepUpTrigger trigger) {
        return extractLastAuthTime(exchange)
            .flatMap(lastAuth -> {
                long maxAge = trigger.getMaxAgeSeconds() * 1000;
                if (System.currentTimeMillis() - lastAuth > maxAge) {
                    return requireReAuthentication(exchange);
                }
                return chain.filter(exchange);
            });
    }
}
```

### 5.5 Deliverables Checklist

- [ ] Rate limiting filter
- [ ] Audit logging filter
- [ ] Device validation filter
- [ ] Anomaly detection filter
- [ ] Step-up authentication
- [ ] Continuous verification (IP/UA mid-session)
- [ ] Configuration for all zero trust features
- [ ] Metrics and monitoring integration

---

## Execution Summary

| Phase | Focus | Key Deliverables |
|-------|-------|------------------|
| **1** | Foundation | BFF scaffold, dependencies, config structure |
| **2** | HSID Auth | OIDC PKCE, single session, persona mapping |
| **3** | Session & Proxy | Valkey session, IP/UA binding, OAuth2 CC |
| **4** | MFE Integration | Web components, Zustand, React Query |
| **5** | Zero Trust | Rate limit, audit, device validation |

---

## Infrastructure Requirements

```yaml
# docker-compose.yml (local development)
services:
  valkey:
    image: valkey/valkey:8.1.5
    ports:
      - "6379:6379"
    command: valkey-server --requirepass ${VALKEY_PASSWORD}

  # Mock HSID (for local testing)
  keycloak:
    image: quay.io/keycloak/keycloak:26.0
    ports:
      - "8180:8080"
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    command: start-dev
```

---

## Testing Strategy

| Phase | Test Type | Coverage |
|-------|-----------|----------|
| 1 | Unit | Config loading, properties binding |
| 2 | Integration | OIDC flow, session creation |
| 3 | Integration | Session binding, proxy auth |
| 4 | E2E | MFE loading, state management |
| 5 | Security | Penetration testing, rate limit verification |

```bash
# Run tests per phase
cd apps/bff && ./mvnw test -Dgroups=phase1
cd apps/bff && ./mvnw test -Dgroups=phase2
# ...
```
