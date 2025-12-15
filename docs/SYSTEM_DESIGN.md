# System Design Document

## Project Overview

| Component | Type | Purpose |
|-----------|------|---------|
| `bff` | Spring Boot 4.0 (WebFlux) | Backend for Frontend - handles auth, session, API aggregation |
| `web-cl` | React 19 + Vite | Primary web portal (client) |
| `mfe-summary` | React 19 MFE | Micro frontend - embeddable component |
| `mfe-profile` | React 19 MFE | Micro frontend - user profile |
| `web-hs` | React 19 | Secondary web application |
| `shared-ui` | React Library | Shared UI components |

### Design Principles
- **Config over Code**: Maximum functionality through configuration, minimum boilerplate
- **DRY (Don't Repeat Yourself)**: Shared utilities, reusable components, centralized config
- **Convention over Configuration**: Sensible defaults, override only when needed

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           EXTERNAL PARTNER DOMAINS                               │
│                    (OAuth2 Client Credentials via Internal Gateway)              │
└───────────────────────────────────┬─────────────────────────────────────────────┘
                                    │
                                    │ MFE embeds via <script>
                                    │ API calls via same-origin proxy
                                    ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│                         PARTNER BACKEND (Same-Origin Proxy)                        │
│                    Calls BFF with OAuth2 Client Credentials                       │
│                    Header: X-Auth-Type: oauth2-proxy                              │
└───────────────────────────────────┬───────────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┴───────────────────────────┐
        │                                                       │
        ▼                                                       ▼
┌───────────────────────────────────┐     ┌────────────────────────────────────────┐
│        DIRECT ACCESS (HSID)        │     │      PROXY ACCESS (OAuth2 CC)          │
│  ┌─────────────┐  ┌─────────────┐  │     │  ┌─────────────┐  ┌─────────────┐     │
│  │   web-cl    │  │   web-hs    │  │     │  │ mfe-summary │  │ mfe-profile │     │
│  │  (React)    │  │  (React)    │  │     │  │ (embedded)  │  │ (embedded)  │     │
│  └──────┬──────┘  └─────────────┘  │     │  └──────┬──────┘  └──────┬──────┘     │
│         │                          │     │         │                │            │
└─────────┼──────────────────────────┘     └─────────┼────────────────┼────────────┘
          │                                          │                │
          │ Session Cookie                           │ Bearer Token   │
          │                                          │ (OAuth2 CC)    │
          ▼                                          ▼                ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                 BFF LAYER                                        │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                      Spring Boot 4.0 + WebFlux                              │ │
│  │                                                                             │ │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐ │ │
│  │  │ SecurityConfig  │  │  SessionFilter  │  │    ProxyAuthFilter          │ │ │
│  │  │ (Auto-config)   │  │ (IP+UA binding) │  │  (X-Auth-Type detection)    │ │ │
│  │  └────────┬────────┘  └────────┬────────┘  └──────────────┬──────────────┘ │ │
│  │           │                    │                          │                │ │
│  │           ▼                    ▼                          ▼                │ │
│  │  ┌─────────────────────────────────────────────────────────────────────┐  │ │
│  │  │               Unified Auth Service (Strategy Pattern)                │  │ │
│  │  │   ┌─────────────────────┐  ┌──────────────────────────────────────┐ │  │ │
│  │  │   │ HSID Strategy       │  │ OAuth2 Client Credentials Strategy   │ │  │ │
│  │  │   │ (Session-based)     │  │ (Token-based, stateless)             │ │  │ │
│  │  │   └─────────────────────┘  └──────────────────────────────────────┘ │  │ │
│  │  └─────────────────────────────────────────────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────┬────────────────────────────────────┘
                                             │
                    ┌────────────────────────┼────────────────────────┐
                    │                        │                        │
                    ▼                        ▼                        ▼
        ┌───────────────────┐    ┌───────────────────┐    ┌───────────────────┐
        │ Valkey/ElastiCache│    │       HSID        │    │  OAuth2 Server    │
        │  (Session Store)  │    │  (Identity Prov)  │    │ (Client Creds)    │
        │  30min TTL        │    │  OIDC PKCE        │    │ Token Validation  │
        └───────────────────┘    └───────────────────┘    └───────────────────┘
```

---

## Authentication Strategies

### Strategy 1: HSID (Direct Users via web-cl)
- **Flow**: OIDC PKCE
- **Session**: 30 minutes, single session per user
- **Binding**: IP + User-Agent
- **Storage**: Valkey hybrid session
- **Personas**: `individual`, `parent`

### Strategy 2: OAuth2 Client Credentials (MFE via Partner Proxy)
- **Flow**: Partner backend obtains token, passes to BFF
- **Header**: `X-Auth-Type: oauth2-proxy`
- **Validation**: JWT signature + claims verification
- **No session**: Stateless, token per request
- **Personas**: `agent`, `config`, `case_worker`
- **Member Context**: Portal passes logged-in member info via headers

---

## Persona Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            PERSONA TYPES                                     │
├─────────────────────────────────┬───────────────────────────────────────────┤
│     HSID (Direct Login)         │     External Portal (Proxy)               │
├─────────────────────────────────┼───────────────────────────────────────────┤
│                                 │                                           │
│  ┌───────────────────────────┐  │  ┌───────────────────────────────────┐   │
│  │      INDIVIDUAL           │  │  │           AGENT                   │   │
│  │  - Self-service user      │  │  │  - Customer service rep           │   │
│  │  - Own data access        │  │  │  - Multi-member access            │   │
│  └───────────────────────────┘  │  │  - Read/write on behalf of        │   │
│                                 │  └───────────────────────────────────┘   │
│  ┌───────────────────────────┐  │                                           │
│  │        PARENT             │  │  ┌───────────────────────────────────┐   │
│  │  - Guardian/parent        │  │  │           CONFIG                  │   │
│  │  - Dependent data access  │  │  │  - System configuration           │   │
│  │  - Family management      │  │  │  - Admin functions                │   │
│  └───────────────────────────┘  │  └───────────────────────────────────┘   │
│                                 │                                           │
│                                 │  ┌───────────────────────────────────┐   │
│                                 │  │        CASE_WORKER                │   │
│                                 │  │  - Case management                │   │
│                                 │  │  - Member assistance              │   │
│                                 │  └───────────────────────────────────┘   │
│                                 │                                           │
│  Auth: HSID Session Cookie      │  Auth: OAuth2 CC + Member Headers        │
│  Context: From HSID claims      │  Context: From Portal proxy headers      │
│                                 │                                           │
└─────────────────────────────────┴───────────────────────────────────────────┘
```

### Proxy Header Contract (External Portal → BFF)

```yaml
# Required headers from external portal proxy
X-Auth-Type: oauth2-proxy              # Identifies proxy request
X-Partner-Id: partner-001              # Partner identification
X-Member-Id: member-123                # Target member being acted upon
X-Persona: agent                       # agent | config | case_worker
X-Operator-Id: operator-456            # Logged-in portal user
X-Operator-Name: "Jane Smith"          # Display name
X-Correlation-Id: uuid                 # Request tracing
Authorization: Bearer <jwt>            # OAuth2 CC token
```

### Persona Permissions Matrix

| Persona | Own Data | Dependent Data | Other Member | Config | Case Mgmt |
|---------|----------|----------------|--------------|--------|-----------|
| `individual` | RW | - | - | - | - |
| `parent` | RW | RW | - | - | - |
| `agent` | - | - | RW | - | - |
| `config` | - | - | R | RW | - |
| `case_worker` | - | - | RW | - | RW |

---

## HSID Authentication Flow (OIDC PKCE)

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│  web-cl  │     │   BFF    │     │ Valkey   │     │   HSID   │
│ (browser)│     │          │     │          │     │   (IDP)  │
└────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘
     │                │                │                │
     │ 1. GET / (unauth landing)       │                │
     │───────────────►│                │                │
     │◄───────────────│ 2. HTML        │                │
     │                │                │                │
     │ 3. Click Login │                │                │
     │───────────────►│                │                │
     │                │ 4. Generate PKCE params         │
     │                │───────────────►│ 5. Store temp  │
     │                │                │                │
     │◄───────────────│ 6. 302 → HSID /authorize       │
     │                │    + code_challenge (S256)     │
     │                │                │                │
     │───────────────────────────────────────────────►│
     │                │ 7. User authenticates          │
     │◄──────────────────────────────────────────────│
     │                │ 8. 302 → /callback?code=xxx    │
     │                │                │                │
     │───────────────►│ 9. Callback    │                │
     │                │───────────────────────────────►│
     │                │ 10. POST /token (code_verifier)│
     │                │◄──────────────────────────────│
     │                │ 11. id_token, access_token     │
     │                │                │                │
     │                │ 12. Validate + Extract claims  │
     │                │                │                │
     │                │ 13. Check existing session     │
     │                │───────────────►│                │
     │                │◄───────────────│ 14. If exists, │
     │                │                │     invalidate │
     │                │                │                │
     │                │───────────────►│ 15. Create new │
     │                │                │ session (30min)│
     │                │                │ + IP + UA      │
     │                │                │                │
     │◄───────────────│ 16. Set-Cookie + 302 → /app   │
```

---

## OAuth2 Client Credentials Flow (MFE Proxy)

```
┌───────────────┐   ┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│ External Site │   │Partner Backend│   │      BFF      │   │ OAuth2 Server │
│  (MFE embed)  │   │ (Same-origin) │   │               │   │               │
│               │   │ Portal has    │   │               │   │               │
│               │   │ logged-in user│   │               │   │               │
└───────┬───────┘   └───────┬───────┘   └───────┬───────┘   └───────┬───────┘
        │                   │                   │                   │
        │ 1. MFE loads     │                   │                   │
        │ 2. API request   │                   │                   │
        │    (member-123)  │                   │                   │
        │──────────────────►                   │                   │
        │                   │                   │                   │
        │                   │ 3. Get/cache OAuth2 CC token         │
        │                   │──────────────────────────────────────►
        │                   │◄──────────────────────────────────────
        │                   │ 4. access_token (cached)              │
        │                   │                   │                   │
        │                   │ 5. Forward to BFF with context       │
        │                   │  ┌─────────────────────────────────┐ │
        │                   │  │ Authorization: Bearer <jwt>     │ │
        │                   │  │ X-Auth-Type: oauth2-proxy       │ │
        │                   │  │ X-Partner-Id: partner-001       │ │
        │                   │  │ X-Member-Id: member-123         │ │
        │                   │  │ X-Persona: agent                │ │
        │                   │  │ X-Operator-Id: operator-456     │ │
        │                   │  │ X-Operator-Name: Jane Smith     │ │
        │                   │  │ X-Correlation-Id: uuid-xxx      │ │
        │                   │  └─────────────────────────────────┘ │
        │                   │──────────────────►                   │
        │                   │                   │                   │
        │                   │                   │ 6. Validate JWT  │
        │                   │                   │ 7. Validate:     │
        │                   │                   │    - aud (BFF)   │
        │                   │                   │    - scope       │
        │                   │                   │    - partner_id  │
        │                   │                   │ 8. Extract:      │
        │                   │                   │    - memberId    │
        │                   │                   │    - persona     │
        │                   │                   │    - operator    │
        │                   │                   │ 9. Check persona │
        │                   │                   │    permissions   │
        │                   │                   │                   │
        │                   │◄──────────────────│ 10. Response     │
        │◄──────────────────│ 11. Response     │                   │
```

---

## Session Design

### HSID Sessions (Hybrid)

```yaml
# Valkey Key Structure
session:{sid}:
  # User identity
  userId: "user-123"
  email: "john@example.com"
  name: "John Doe"

  # Persona (from HSID claims)
  persona: "individual"             # individual | parent
  dependents: ["dep-001", "dep-002"] # Only for parent persona

  # Tokens (encrypted)
  accessToken: "encrypted:..."      # AES-256-GCM
  refreshToken: "encrypted:..."
  idToken: "encrypted:..."
  tokenExpiry: 1702503600

  # Session metadata
  createdAt: 1702500000
  lastAccessedAt: 1702501800
  ipAddress: "192.168.1.1"          # Bound
  userAgentHash: "sha256:..."       # Bound (hash)

# Single session enforcement
user_session:{userId}: "{sid}"      # Only one session ID per user

# TTL: 30 minutes (sliding on activity)
```

### Proxy Request Context (No Session - Stateless)

```yaml
# Extracted from headers per request (not stored)
ProxyContext:
  # OAuth2 token claims
  partnerId: "partner-001"
  scopes: ["mfe:summary:read"]

  # Member context (from portal headers)
  memberId: "member-123"            # Target member
  persona: "agent"                  # agent | config | case_worker

  # Operator info (portal user)
  operatorId: "operator-456"
  operatorName: "Jane Smith"

  # Tracing
  correlationId: "uuid-xxx"
```

### Session Cookie
```yaml
name: HSID_SESSION
httpOnly: true
secure: true
sameSite: Strict            # Strict for HSID (same domain)
maxAge: 1800                # 30 minutes
path: /
```

### Session Validation (Every Request)
```java
// Pseudo-code - actual implementation via Spring Security filter
validate(session, request):
  1. Check session exists in Valkey
  2. Check TTL not expired
  3. Verify IP matches session.ipAddress
  4. Verify hash(User-Agent) matches session.userAgentHash
  5. If token near expiry → background refresh
  6. Update lastAccessedAt + reset TTL
```

---

## State Management (Frontend)

### Architecture: Zustand + React Query

```
┌─────────────────────────────────────────────────────────────────┐
│                        web-cl / MFEs                             │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                    React Query                             │  │
│  │              (Server State - API Cache)                    │  │
│  │   ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐     │  │
│  │   │ users   │  │ summary │  │ profile │  │ config  │     │  │
│  │   │ query   │  │ query   │  │ query   │  │ query   │     │  │
│  │   └─────────┘  └─────────┘  └─────────┘  └─────────┘     │  │
│  └───────────────────────────────────────────────────────────┘  │
│                              │                                   │
│  ┌───────────────────────────┼───────────────────────────────┐  │
│  │                    Zustand                                 │  │
│  │              (Client State - UI/Auth)                      │  │
│  │   ┌─────────────────┐  ┌─────────────────────────────┐   │  │
│  │   │   authStore     │  │        uiStore              │   │  │
│  │   │ - isAuth        │  │ - sidebarOpen              │   │  │
│  │   │ - user (basic)  │  │ - theme                    │   │  │
│  │   │ - sessionExp    │  │ - notifications            │   │  │
│  │   └─────────────────┘  └─────────────────────────────┘   │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Store Definitions (Config-driven)

```typescript
// libs/shared-state/src/types/persona.ts
export type HsidPersona = 'individual' | 'parent';
export type ProxyPersona = 'agent' | 'config' | 'case_worker';
export type Persona = HsidPersona | ProxyPersona;

export interface AuthContext {
  // Common
  isAuthenticated: boolean;
  persona: Persona | null;

  // HSID context
  user?: { sub: string; name: string; email: string };
  dependents?: string[];  // For parent persona
  sessionExpiry?: number;

  // Proxy context (MFE embedded in external portal)
  memberId?: string;      // Target member
  operatorId?: string;    // Portal user
  operatorName?: string;
}
```

```typescript
// libs/shared-state/src/stores/auth.store.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { AuthContext, Persona } from '../types/persona';

interface AuthState extends AuthContext {
  setHsidAuth: (user: AuthContext['user'], persona: Persona, expiry: number, dependents?: string[]) => void;
  setProxyContext: (memberId: string, persona: Persona, operatorId: string, operatorName: string) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      isAuthenticated: false,
      persona: null,

      setHsidAuth: (user, persona, expiry, dependents) => set({
        isAuthenticated: true,
        user,
        persona,
        sessionExpiry: expiry,
        dependents,
      }),

      setProxyContext: (memberId, persona, operatorId, operatorName) => set({
        isAuthenticated: true,
        memberId,
        persona,
        operatorId,
        operatorName,
      }),

      clearAuth: () => set({
        isAuthenticated: false,
        persona: null,
        user: undefined,
        dependents: undefined,
        sessionExpiry: undefined,
        memberId: undefined,
        operatorId: undefined,
        operatorName: undefined,
      }),
    }),
    { name: 'auth-storage' }
  )
);
```

```typescript
// libs/shared-state/src/queries/index.ts
// Config-driven query definitions

export const queryConfig = {
  user: {
    queryKey: ['user'],
    queryFn: () => api.get('/api/user/profile'),
    staleTime: 5 * 60 * 1000,
  },
  summary: {
    queryKey: (userId: string) => ['summary', userId],
    queryFn: (userId: string) => api.get(`/api/summary/${userId}`),
    staleTime: 1 * 60 * 1000,
  },
} as const;

// Usage: useQuery(queryConfig.user)
```

---

## BFF Configuration (application.yml)

```yaml
spring:
  application:
    name: bff

  # HSID OAuth2 Client (OIDC PKCE)
  security:
    oauth2:
      client:
        registration:
          hsid:
            client-id: ${HSID_CLIENT_ID}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/api/auth/callback"
            scope: openid,profile,email
            client-authentication-method: none  # PKCE, no secret
        provider:
          hsid:
            issuer-uri: ${HSID_ISSUER_URI}
            authorization-uri: ${HSID_AUTH_URI}
            token-uri: ${HSID_TOKEN_URI}
            jwk-set-uri: ${HSID_JWKS_URI}
            user-info-uri: ${HSID_USERINFO_URI}

      # Resource Server for OAuth2 Client Credentials validation
      resourceserver:
        jwt:
          issuer-uri: ${OAUTH2_ISSUER_URI}
          audiences: ${OAUTH2_AUDIENCE:bff-api}

  # Valkey (ElastiCache)
  data:
    redis:
      host: ${VALKEY_HOST:localhost}
      port: ${VALKEY_PORT:6379}
      password: ${VALKEY_PASSWORD:}
      ssl:
        enabled: ${VALKEY_SSL_ENABLED:false}
      lettuce:
        pool:
          max-active: 10
          max-idle: 5
          min-idle: 2

  # Session
  session:
    store-type: redis
    timeout: 30m
    redis:
      namespace: hsid:session

# Custom session config
session:
  cookie:
    name: HSID_SESSION
    http-only: true
    secure: true
    same-site: strict
  binding:
    ip-address: true
    user-agent: true
  single-session: true          # Enforce one session per user

# Persona configuration
persona:
  hsid:
    claim-name: persona_type          # HSID claim containing persona
    allowed: [individual, parent]
    parent:
      dependents-claim: dependents    # Claim containing dependent IDs
  proxy:
    allowed: [agent, config, case_worker]

# MFE Proxy Auth (OAuth2 Client Credentials)
mfe:
  proxy:
    enabled: true
    headers:
      auth-type: X-Auth-Type          # Value must be "oauth2-proxy"
      partner-id: X-Partner-Id
      member-id: X-Member-Id          # Target member
      persona: X-Persona              # agent | config | case_worker
      operator-id: X-Operator-Id      # Portal user ID
      operator-name: X-Operator-Name  # Portal user name
      correlation-id: X-Correlation-Id
    allowed-partners:
      - id: partner-001
        name: "External Portal"
        scopes: ["mfe:summary:read", "mfe:profile:read"]
        allowed-personas: [agent, config, case_worker]

# External domain registry
domain-registry:
  allowed-origins:
    - origin: "https://portal.example.com"
      partner-id: partner-001
      allowed-mfes: [mfe-summary, mfe-profile]
```

---

## BFF Security Config (Config-driven)

```java
// Minimal code - most config via application.yml

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityFilterChain(
            ServerHttpSecurity http,
            SessionBindingFilter sessionBindingFilter,
            ProxyAuthFilter proxyAuthFilter) {

        return http
            .authorizeExchange(auth -> auth
                .pathMatchers("/", "/api/auth/**", "/actuator/health").permitAll()
                .pathMatchers("/api/mfe/**").access(proxyAuthFilter.authManager())
                .anyExchange().authenticated()
            )
            .oauth2Login(Customizer.withDefaults())      // HSID via config
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults())) // Proxy tokens
            .addFilterBefore(sessionBindingFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .addFilterBefore(proxyAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .csrf(csrf -> csrf.csrfTokenRepository(
                CookieServerCsrfTokenRepository.withHttpOnlyFalse()))
            .build();
    }
}
```

---

## Session Binding Filter

```java
@Component
@RequiredArgsConstructor
public class SessionBindingFilter implements WebFilter {

    private final SessionProperties sessionProperties;
    private final ReactiveRedisOperations<String, Object> redisOps;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!sessionProperties.getBinding().isEnabled()) {
            return chain.filter(exchange);
        }

        return exchange.getSession()
            .flatMap(session -> validateBinding(session, exchange))
            .then(chain.filter(exchange));
    }

    private Mono<Void> validateBinding(WebSession session, ServerWebExchange exchange) {
        String sessionIp = session.getAttribute("bound_ip");
        String sessionUaHash = session.getAttribute("bound_ua_hash");

        String requestIp = extractClientIp(exchange);
        String requestUaHash = hashUserAgent(exchange);

        if (sessionProperties.getBinding().isIpAddress()
                && sessionIp != null && !sessionIp.equals(requestIp)) {
            return invalidateAndError(session, "IP_MISMATCH");
        }

        if (sessionProperties.getBinding().isUserAgent()
                && sessionUaHash != null && !sessionUaHash.equals(requestUaHash)) {
            return invalidateAndError(session, "UA_MISMATCH");
        }

        return Mono.empty();
    }
}
```

---

## MFE Proxy Auth Filter

```java
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mfe.proxy.enabled", havingValue = "true")
public class ProxyAuthFilter implements WebFilter {

    private final MfeProxyProperties proxyProps;
    private final PersonaProperties personaProps;
    private final ReactiveJwtDecoder jwtDecoder;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String authType = headers.getFirst(proxyProps.getHeaders().getAuthType());

        if (!"oauth2-proxy".equals(authType)) {
            return chain.filter(exchange);  // Not a proxy request
        }

        // Extract all context headers
        ProxyContext ctx = ProxyContext.builder()
            .partnerId(headers.getFirst(proxyProps.getHeaders().getPartnerId()))
            .memberId(headers.getFirst(proxyProps.getHeaders().getMemberId()))
            .persona(headers.getFirst(proxyProps.getHeaders().getPersona()))
            .operatorId(headers.getFirst(proxyProps.getHeaders().getOperatorId()))
            .operatorName(headers.getFirst(proxyProps.getHeaders().getOperatorName()))
            .correlationId(headers.getFirst(proxyProps.getHeaders().getCorrelationId()))
            .build();

        return extractBearerToken(exchange)
            .flatMap(jwtDecoder::decode)
            .flatMap(jwt -> validatePartnerAndPersona(jwt, ctx))
            .doOnSuccess(v -> exchange.getAttributes().put("proxyContext", ctx))
            .then(chain.filter(exchange))
            .onErrorResume(e -> unauthorized(exchange, e.getMessage()));
    }

    private Mono<Void> validatePartnerAndPersona(Jwt jwt, ProxyContext ctx) {
        // 1. Validate partner exists and has required scopes
        var partner = proxyProps.getAllowedPartners().stream()
            .filter(p -> p.getId().equals(ctx.getPartnerId()))
            .findFirst()
            .orElseThrow(() -> new AccessDeniedException("Unknown partner"));

        // 2. Validate persona is allowed for this partner
        if (!partner.getAllowedPersonas().contains(ctx.getPersona())) {
            return Mono.error(new AccessDeniedException("Persona not allowed"));
        }

        // 3. Validate persona is in allowed list
        if (!personaProps.getProxy().getAllowed().contains(ctx.getPersona())) {
            return Mono.error(new AccessDeniedException("Invalid persona"));
        }

        // 4. Validate JWT scopes
        return validateScopes(jwt, partner);
    }
}

// ProxyContext available in controllers via exchange attribute
@RestController
public class MfeController {
    @GetMapping("/api/mfe/summary/{memberId}")
    public Mono<Summary> getSummary(@PathVariable String memberId, ServerWebExchange exchange) {
        ProxyContext ctx = exchange.getAttribute("proxyContext");
        // ctx.getMemberId(), ctx.getPersona(), ctx.getOperatorId() available
        return summaryService.getForMember(memberId, ctx);
    }
}
```

---

## web-cl Route Structure

```
web-cl/src/
├── main.tsx                          # Providers setup
├── app/
│   ├── App.tsx
│   ├── providers/
│   │   ├── QueryProvider.tsx         # React Query setup
│   │   └── AuthProvider.tsx          # Auth context
│   ├── routes/
│   │   ├── routes.config.ts          # Route definitions (config)
│   │   ├── public/
│   │   │   └── LandingPage.tsx       # Single unauth page
│   │   └── protected/
│   │       ├── Dashboard.tsx
│   │       ├── Summary.tsx           # Uses mfe-summary
│   │       └── Profile.tsx           # Uses mfe-profile
│   ├── guards/
│   │   └── AuthGuard.tsx             # Route protection
│   └── api/
│       └── client.ts                 # BFF API client
```

### Route Config (Config-driven)

```typescript
// routes/routes.config.ts
import { lazy } from 'react';

const LandingPage = lazy(() => import('./public/LandingPage'));
const Dashboard = lazy(() => import('./protected/Dashboard'));
const Summary = lazy(() => import('./protected/Summary'));
const Profile = lazy(() => import('./protected/Profile'));

export const routesConfig = {
  public: [
    { path: '/', element: LandingPage, exact: true },
  ],
  protected: [
    { path: '/app', element: Dashboard },
    { path: '/app/summary', element: Summary },
    { path: '/app/profile', element: Profile },
  ],
} as const;
```

---

## API Endpoints Summary

### Auth (HSID)
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/` | Public | Landing page |
| GET | `/api/auth/login` | Public | Initiates HSID PKCE |
| GET | `/api/auth/callback` | Public | HSID callback |
| POST | `/api/auth/logout` | Session | Destroys session |
| GET | `/api/auth/session` | Session | Current session info |

### Protected (Session)
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/user/profile` | Session | User profile |
| GET | `/api/dashboard/*` | Session | Dashboard APIs |

### MFE Proxy (OAuth2 CC)
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/mfe/summary/*` | OAuth2 CC | Summary MFE APIs |
| GET | `/api/mfe/profile/*` | OAuth2 CC | Profile MFE APIs |

---

## Implementation Phases

### Phase 1: Foundation (Config Setup)
- [ ] Add dependencies to pom.xml (see DEPENDENCIES.md)
- [ ] Configure application.yml for HSID + Valkey
- [ ] Install Zustand + React Query in frontend

### Phase 2: HSID Auth
- [ ] SecurityConfig with oauth2Login
- [ ] Session binding filter (IP + UA)
- [ ] Single session enforcement

### Phase 3: Session Management
- [ ] Hybrid session in Valkey
- [ ] 30-minute sliding expiration
- [ ] Token encryption/refresh

### Phase 4: OAuth2 Proxy Auth
- [ ] Resource server config for JWT validation
- [ ] Proxy auth filter with partner validation
- [ ] CORS for allowed origins

### Phase 5: MFE Integration
- [ ] Web component wrapper for MFEs
- [ ] Shared state setup (Zustand)
- [ ] React Query configuration

---

## Answered Questions

| Question | Answer |
|----------|--------|
| IDP | HSID |
| Session timeout | 30 minutes (sliding) |
| Concurrent sessions | Single session per user |
| MFE authentication | OAuth2 Client Credentials via partner proxy |
| Session binding | IP + User-Agent (both required) |
| State management | Zustand (client) + React Query (server) |
| HSID personas | `individual`, `parent` |
| Portal personas | `agent`, `config`, `case_worker` |
| Member context | Portal passes via `X-Member-Id`, `X-Operator-*` headers |
