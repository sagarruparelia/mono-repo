# BFF Architecture

## Overview

The BFF (Backend-for-Frontend) is a Spring WebFlux reactive application that serves as the backend for the React MFE (Micro-Frontend). It handles authentication, authorization, session management, and proxies requests to external APIs.

### Key Technologies
- **Spring WebFlux** - Reactive, non-blocking web framework
- **Spring Security** - OAuth2/OIDC authentication
- **Redis** - Distributed session and cache storage (production)
- **MongoDB** - Health data caching
- **WebClient** - Reactive HTTP client for external APIs

---

## Authentication Architecture

The BFF supports dual authentication models to handle both browser-based and proxy-based requests.

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Authentication Flow                           │
├─────────────────────────────────────────────────────────────────────┤
│  Browser Request:                                                    │
│    Browser → OAuth2 (HSID) → HsidAuthSuccessHandler → Session       │
│                                                                      │
│  Proxy Request:                                                      │
│    Proxy → mTLS Headers (x-auth-*, x-member-*) → ExternalAuthFilter │
└─────────────────────────────────────────────────────────────────────┘
```

### Session-Based Authentication (Browser)
1. User initiates OAuth2 login at `/oauth2/authorization/hsid`
2. HSID provider authenticates and redirects back
3. `HsidAuthenticationSuccessHandler` creates session with:
   - User info from UserInfoService (REST API)
   - Eligibility from EligibilityService (GraphQL)
   - Managed members from ManagedMembersService (GraphQL)
4. Session cookie set with `HttpOnly`, `Secure`, `SameSite=Strict`

### Proxy-Based Authentication (External)
1. mTLS-authenticated proxy sends request with headers:
   - `x-auth-principal` - Authentication principal
   - `x-member-eid` - Member enterprise ID
   - `x-persona` - User persona type
2. `ExternalAuthFilter` validates headers and creates `AuthPrincipal`
3. No session created - stateless per-request authentication

### AuthPrincipal Model
Unified identity representation used throughout the application:
```java
AuthPrincipal {
    hsidUuid        // HSID unique identifier
    memberEid       // Enterprise ID
    sessionId       // Session identifier (null for proxy auth)
    authMethod      // SESSION or PROXY
    persona         // Current persona
    permissions     // PermissionSet for delegated access
}
```

---

## Filter Chain Architecture

Request processing flows through ordered security filters:

```
Order  Filter                       Purpose
─────  ──────────────────────────   ─────────────────────────────────
+5     RateLimitingFilter           Rate limit by client IP
+10    SessionBindingFilter         Validate session, IP, device
+20    DualAuthWebFilter            Create AuthPrincipal
+30    PersonaAuthorizationFilter   Validate @RequirePersona
```

### SessionBindingFilter (+10)
- Extracts session cookie
- Loads `SessionData` from cache
- Validates device fingerprint
- Validates IP binding (configurable strictness)
- Attaches session to exchange attributes

### DualAuthWebFilter (+20)
- For session auth: Creates `AuthPrincipal` from session
- For proxy auth: Delegates to `ExternalAuthFilter`
- Sets `AuthPrincipal` on exchange attributes

### PersonaAuthorizationFilter (+30)
- Intercepts methods annotated with `@RequirePersona`
- Validates current persona matches required personas
- For delegates: Validates temporal permissions (start/end dates)
- For managed member access: Validates enterprise ID permissions

---

## Authorization Model

### Persona Types
| Persona | Description | Use Case |
|---------|-------------|----------|
| `INDIVIDUAL_SELF` | User accessing own data | Default for logged-in users |
| `DELEGATE` | Authorized delegate (DAA, RPR, ROI) | Accessing managed member data |
| `CASE_WORKER` | Case worker access | Professional access |
| `AGENT` | Agent access | Customer service |
| `CONFIG_SPECIALIST` | Configuration specialist | Admin functions |

### Delegate Types
| Type | Name | Access Level |
|------|------|--------------|
| `DAA` | Durable Access Authorization | Full ongoing access |
| `RPR` | Authorized Representative | Full access with time limits |
| `ROI` | Release of Information | Read-only access |

### @RequirePersona Annotation
Method-level authorization:
```java
@RequirePersona(
    value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE},
    requiredDelegates = {DelegateType.DAA, DelegateType.RPR}
)
public Mono<ResponseEntity<...>> getHealthData(...) { }
```

### Temporal Permission Validation
- Permissions have `startDate` and `endDate`
- Filter validates current date is within permission window
- Expired permissions result in 403 Forbidden

---

## Caching Architecture

The BFF uses conditional bean loading for flexible cache implementations:

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Caching Strategy                             │
├─────────────────────────────────────────────────────────────────────┤
│  app.cache.type=redis         │  app.cache.type=memory              │
│  ──────────────────────────   │  ──────────────────────────         │
│  RedisSessionService          │  InMemorySessionService             │
│  RedisIdentityCacheService    │  InMemoryIdentityCacheService       │
│  Redis Pub/Sub invalidation   │  Local invalidation only            │
│  Multi-pod consistency        │  Single-pod deployment              │
└─────────────────────────────────────────────────────────────────────┘
```

### Session Cache
- **Key**: Session ID (UUID)
- **Value**: `SessionData` (user info, persona, permissions)
- **TTL**: Configurable via `app.session.timeout`
- **Operations**: `SessionOperations` interface

### Identity Cache
- **User Info**: Cached by `hsidUuid`
- **Eligibility**: Cached by `memberEid`
- **Permissions**: Cached by `memberEid`
- **TTL**: Configurable per cache type
- **Operations**: `IdentityCacheOperations` interface

### Health Data Cache (MongoDB)
- **Immunizations**: `ImmunizationEntity` by `memberEid`
- **Allergies**: `AllergyEntity` by `memberEid`
- **Conditions**: `ConditionEntity` by `memberEid`
- **TTL**: Configurable via `app.health-data.cache-ttl`

### Redis Pub/Sub Cache Invalidation
For multi-pod deployments, cache invalidation propagates via Redis pub/sub:
```
Pod A invalidates → IdentityCacheEventPublisher → Redis Channel
                                                       ↓
Pod B receives   ← IdentityCacheEventListener   ← Redis Channel
```

---

## External API Integration

```
┌─────────────────────────────────────────────────────────────────────┐
│                          External APIs                               │
├─────────────────────────────────────────────────────────────────────┤
│  User Service (REST)                                                 │
│    Endpoint: /api/identity/user/individual/v1/read                  │
│    Method:   GET with ?hsidUuid= query param                        │
│    Service:  UserInfoService                                         │
├─────────────────────────────────────────────────────────────────────┤
│  Eligibility (GraphQL)                                               │
│    Endpoint: /graph/1.0.0                                           │
│    Query:    CheckEligibility($memberEid)                           │
│    Service:  EligibilityService                                      │
├─────────────────────────────────────────────────────────────────────┤
│  Permissions (GraphQL)                                               │
│    Endpoint: /api/consumer/prefs/del-gr/1.0.0                       │
│    Query:    GetPermissions($memberEid)                             │
│    Service:  ManagedMembersService                                   │
├─────────────────────────────────────────────────────────────────────┤
│  ECDH Health Data (GraphQL)                                          │
│    Endpoint: Configured via EcdhApiProperties                       │
│    Queries:  Paginated immunizations, allergies, conditions         │
│    Service:  EcdhApiClientService                                    │
└─────────────────────────────────────────────────────────────────────┘
```

### WebClient Configuration
Two dedicated WebClient instances:
- `EXTERNAL_API_WEBCLIENT` - For identity APIs (User, Eligibility, Permissions)
- `ECDH_API_WEBCLIENT` - For health data API

### Retry Strategy
All external API calls use `RetryUtils`:
- Configurable max attempts
- Exponential backoff
- Retries on transient errors (5xx, timeouts)
- No retry on client errors (4xx)

---

## Observability

### Metrics (Micrometer)
- Session creation/invalidation counts
- Authentication success/failure rates
- External API call latencies
- Cache hit/miss ratios

### Tracing (OpenTelemetry)
- Correlation IDs propagated across services
- Span creation for external API calls
- Request tracing through filter chain

### Logging
- Structured logging with SLF4J
- PII sanitization via `StringSanitizer.forLog()`
- Configurable log levels per package

### Health Indicators
- Redis connectivity check
- MongoDB connectivity check
- External API availability

---

## Package Structure

```
com.example.bff/
├── auth/           # Authentication (OAuth2, handlers)
├── authz/          # Authorization (personas, permissions)
├── session/        # Session management
├── health/         # Health data domain
├── document/       # Document management
├── config/         # Configuration classes
├── common/         # Shared utilities
├── external/       # External authentication
└── observability/  # Metrics, logging
```
