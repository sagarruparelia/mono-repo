# Dual Authentication Implementation Plan

> **Status**: Approved for Implementation
> **Last Updated**: 2024-12-19
> **Author**: Claude Code

## Overview

Implement unified authentication that supports both:
1. **Browser → BFF**: HSID session-based auth (BFF_SESSION cookie)
2. **Partner → mTLS ALB → BFF**: Header-based auth (X-Persona, X-Member-Id)

Both auth types resolve to a unified `AuthContext` with `effectiveMemberId` for consistent data access.

---

## Architecture Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Partner auth method | Header-based (mTLS ALB) | ALB validates partner JWT, forwards headers. BFF trusts mTLS. |
| JWT issuer | Separate OAuth2 server | Partners use different OAuth2 server than HSID |
| Member access | Unified effectiveMemberId | Both auth types resolve to same member context |
| Parent flow | Require memberId param | Explicit member selection on each request, matches proxy pattern |
| CSRF handling | Session only | CSRF required for HSID (cookie-based), skipped for PROXY (stateless) |

---

## AWS Infrastructure

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│   Browser                              Partner System                        │
│      │                                       │                               │
│      ▼                                       ▼                               │
│  ┌───────────┐                        ┌─────────────┐                        │
│  │  ALB #1   │                        │   ALB #2    │                        │
│  │ (Public)  │                        │   (mTLS)    │                        │
│  │           │                        │ Validates   │                        │
│  │ Passes    │                        │ Partner JWT │                        │
│  │ Cookie    │                        │ Forwards    │                        │
│  └─────┬─────┘                        │ Headers     │                        │
│        │                              └──────┬──────┘                        │
│        │ BFF_SESSION                         │ X-Persona                     │
│        │ cookie                              │ X-Member-Id                   │
│        │                                     │ X-Partner-Id                  │
│        └──────────────┬──────────────────────┘                               │
│                       ▼                                                      │
│              ┌─────────────────┐                                             │
│              │       BFF       │                                             │
│              │                 │                                             │
│              │ DualAuthFilter  │                                             │
│              │ → AuthContext   │                                             │
│              │ → ABAC Layer    │                                             │
│              └─────────────────┘                                             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Filter Chain Order

| Order | Filter | Purpose |
|-------|--------|---------|
| HIGHEST_PRECEDENCE + 5 | ExternalAuthFilter | Maps mTLS ALB headers to proxy format |
| HIGHEST_PRECEDENCE + 10 | SessionBindingFilter | Validates session binding for HSID |
| **HIGHEST_PRECEDENCE + 20** | **DualAuthWebFilter** | **NEW: Builds unified AuthContext** |
| Spring Security | SecurityFilterChain | OAuth2 login, CSRF, authorization |
| LOWEST_PRECEDENCE - 100 | AuthorizationFilter | ABAC policy enforcement |

---

## Components

### 1. AuthContext Record

**File**: `src/main/java/com/example/bff/auth/model/AuthContext.java`

```java
public record AuthContext(
    AuthType authType,              // HSID or PROXY
    String userId,                  // Authenticated user's ID
    String effectiveMemberId,       // Member context for data access
    String persona,                 // individual/parent or agent/config/case_worker
    String sessionId,               // For HSID (null for PROXY)
    String partnerId,               // For PROXY (null for HSID)
    String operatorId,              // For PROXY (null for HSID)
    SubjectAttributes subject       // Cached for ABAC
) {
    public static final String EXCHANGE_ATTRIBUTE = "AUTH_CONTEXT";

    public static AuthContext forHsid(...) { ... }
    public static AuthContext forProxy(...) { ... }
}
```

**Key Design:**
- `effectiveMemberId`: HSID = userId (or selected child), PROXY = X-Member-Id header
- `SubjectAttributes` cached at creation to avoid repeated Redis lookups
- Stored in `ServerWebExchange.getAttributes()` for downstream access

### 2. DualAuthWebFilter

**File**: `src/main/java/com/example/bff/auth/filter/DualAuthWebFilter.java`

**Logic:**
```
1. Skip public paths
2. Determine path category (PUBLIC, SESSION_ONLY, PROXY_ONLY, DUAL_AUTH)
3. Try resolve auth:
   a. Check BFF_SESSION cookie → build HSID context
   b. Else check proxy headers → build PROXY context
4. Validate auth type allowed for path category
5. Store AuthContext in exchange attributes
6. If no auth and required → 401 Unauthorized
```

**Order**: `HIGHEST_PRECEDENCE + 20` (after ExternalAuthFilter and SessionBindingFilter)

### 3. AuthContextResolver Utility

**File**: `src/main/java/com/example/bff/auth/util/AuthContextResolver.java`

```java
public static Optional<AuthContext> resolve(ServerWebExchange exchange);
public static AuthContext require(ServerWebExchange exchange);
public static String requireMemberId(ServerWebExchange exchange);
```

### 4. Path Configuration

**File**: `src/main/resources/config/security-paths.yml`

```yaml
security:
  paths:
    # Public (no auth)
    public:
      - pattern: "/api/auth/**"
      - pattern: "/actuator/health"

    # Session only (browser)
    session-auth:
      - pattern: "/api/user/**"
      - pattern: "/api/dashboard/**"

    # Proxy only (partners)
    proxy-auth:
      - pattern: "/api/mfe/**"

    # NEW: Dual auth (session OR proxy)
    dual-auth:
      - pattern: "/api/summary/**"
      - pattern: "/api/member/{id}/profile"
      - pattern: "/api/member/{id}/documents/**"
```

---

## API Path Security Matrix

| Path Pattern | Session | Proxy | Notes |
|--------------|---------|-------|-------|
| `/api/auth/**` | Public | Public | Login, logout, token refresh |
| `/api/user/**` | ✅ | ❌ | User info (browser only) |
| `/api/dashboard/**` | ✅ | ❌ | Dashboard data (browser only) |
| `/api/summary/**` | ✅ | ✅ | **Health data (dual auth)** |
| `/api/profile/**` | ✅ | ✅ | **Profile data (dual auth)** |
| `/api/documents/**` | ✅ | ✅ | **Documents (dual auth)** |

**Note**: `/api/mfe/**` is **deprecated** - consolidated into `/api/summary/**` with dual auth.

## Member ID Pattern

All dual-auth endpoints use **POST with request body** (not URL path):

```java
// Request body for all data endpoints
public record MemberRequest(
    @NotBlank String memberId
) {}

// Controller pattern
@PostMapping("/health")
public Mono<HealthSummary> getHealthSummary(
    @RequestBody @Valid MemberRequest request,
    ServerWebExchange exchange) {

    AuthContext auth = AuthContextResolver.require(exchange);
    // Validate: auth can access request.memberId()
    return healthService.getSummary(request.memberId());
}
```

**Why POST for reads?**
- Unified pattern for session (browser) and proxy (partners)
- memberId in body, not URL - cleaner, more secure
- Works consistently across all auth types

---

## ABAC Integration

The AuthContext feeds into the existing ABAC layer unchanged:

```java
// Controller usage
AuthContext auth = AuthContextResolver.require(exchange);

ResourceAttributes resource = ResourceAttributes.builder()
    .type("health_summary")
    .ownerId(auth.effectiveMemberId())
    .build();

abacService.authorize(auth.subject(), resource, Action.VIEW)
    .flatMap(decision -> {
        if (!decision.isAllowed()) {
            return Mono.error(new ForbiddenException(decision.reason()));
        }
        return healthService.getSummary(auth.effectiveMemberId());
    });
```

**Policy evaluation** uses `auth.subject().authType()` to select HSID or PROXY policies.

---

## Implementation Phases

### Phase 1: Core Models
- [ ] Create `AuthContext` record
- [ ] Create `AuthContextResolver` utility
- [ ] Unit tests for models

### Phase 2: Configuration
- [ ] Update `SecurityPathsProperties` with `dualAuth` field
- [ ] Update `security-paths.yml` with dual-auth section

### Phase 3: DualAuthWebFilter
- [ ] Implement filter with path matching
- [ ] HSID context resolution (session cookie → Redis)
- [ ] PROXY context resolution (headers → SubjectAttributes)
- [ ] Error handling and logging
- [ ] Unit tests for filter

### Phase 4: ABAC Integration
- [ ] Update `AuthorizationFilter` to use `AuthContext` when available
- [ ] Backward compatibility with existing subject building
- [ ] Integration tests

### Phase 5: Controller Migration
- [ ] Update controllers to use `AuthContextResolver`
- [ ] Remove redundant auth code
- [ ] E2E tests

---

## Files to Create

| File | Purpose |
|------|---------|
| `auth/model/AuthContext.java` | Unified auth context record |
| `auth/filter/DualAuthWebFilter.java` | Auth context builder filter |
| `auth/util/AuthContextResolver.java` | Utility for extracting context |

## Files to Modify

| File | Changes |
|------|---------|
| `config/properties/SecurityPathsProperties.java` | Add `dualAuth` field |
| `resources/config/security-paths.yml` | Add dual-auth section |
| `authz/filter/AuthorizationFilter.java` | Use AuthContext when available |
| `document/controller/DocumentController.java` | Use AuthContextResolver |

---

## Security Considerations

| Aspect | HSID (Session) | PROXY (Header) |
|--------|----------------|----------------|
| Auth validation | Redis session lookup | mTLS ALB validates JWT |
| Session binding | IP + User-Agent hash | N/A (stateless) |
| CSRF | Required for mutations | Not required (no cookies) |
| effectiveMemberId source | Session (selectedChild or userId) | X-Member-Id header |
| Audit trail | userId from session | operatorId from header |

---

## ABAC Policies for Dual Auth

### Permission Model

| Permission | Description | Required For |
|------------|-------------|--------------|
| **DAA** | Delegate Access Authority | Basic dependent access |
| **RPR** | Relying Party Representative | Registered representative |
| **ROI** | Release of Information | Sensitive data access |

**Access Combinations:**
- View dependent: `DAA + RPR`
- View sensitive data: `DAA + RPR + ROI`

### Dual Auth Policy Matrix

| Resource | Auth Type | Persona | Action | Required |
|----------|-----------|---------|--------|----------|
| health_summary | HSID | individual | VIEW | Owner check (userId = memberId) |
| health_summary | HSID | parent | VIEW | DAA + RPR for dependent |
| health_summary | PROXY | agent/case_worker | VIEW | Assignment check |
| health_summary | PROXY | config | VIEW | Full access |
| health_sensitive | HSID | parent | VIEW | DAA + RPR + ROI |
| health_sensitive | PROXY | config | VIEW | Full access |
| health_sensitive | PROXY | agent | VIEW | **DENIED** |
| profile | HSID | individual | VIEW/EDIT | Owner check |
| profile | HSID | parent | VIEW | DAA + RPR |
| profile | PROXY | all | VIEW | Assignment or config |
| document | HSID | individual | ALL | Owner check |
| document | HSID | parent | VIEW/UPLOAD | DAA + RPR + ROI |
| document | PROXY | agent | VIEW | Assignment check |
| document | PROXY | config | ALL | Full access |

### New ABAC Policies to Add

```yaml
# abac-policies.yml additions

# Health Summary - Dual Auth
- id: HSID_INDIVIDUAL_HEALTH
  description: "Member can view own health summary"
  priority: 150
  conditions:
    auth-type: HSID
    persona: individual
    resource-type: health_summary
    action: VIEW
  owner-check: true

- id: HSID_PARENT_HEALTH
  description: "Parent can view dependent's health summary"
  priority: 100
  conditions:
    auth-type: HSID
    persona: parent
    resource-type: health_summary
    action: VIEW
    sensitive: false
  required-permissions: [DAA, RPR]

- id: HSID_PARENT_HEALTH_SENSITIVE
  description: "Parent can view dependent's sensitive health data"
  priority: 100
  conditions:
    auth-type: HSID
    persona: parent
    resource-type: health_summary
    action: VIEW
    sensitive: true
  required-permissions: [DAA, RPR, ROI]

- id: PROXY_HEALTH_SUMMARY
  description: "Proxy users can view assigned member's health summary"
  priority: 100
  conditions:
    auth-type: PROXY
    resource-type: health_summary
    action: VIEW
    sensitive: false
  proxy-rules:
    config-full-access: true
    require-assignment: true

- id: PROXY_HEALTH_SENSITIVE
  description: "Only config can view sensitive health data via proxy"
  priority: 100
  conditions:
    auth-type: PROXY
    resource-type: health_summary
    action: VIEW
    sensitive: true
  proxy-rules:
    config-only: true

# Profile - Dual Auth
- id: HSID_INDIVIDUAL_PROFILE
  description: "Member can manage own profile"
  priority: 150
  conditions:
    auth-type: HSID
    persona: individual
    resource-type: profile
  owner-check: true

- id: HSID_PARENT_PROFILE
  description: "Parent can view dependent's profile"
  priority: 100
  conditions:
    auth-type: HSID
    persona: parent
    resource-type: profile
    action: VIEW
  required-permissions: [DAA, RPR]

- id: PROXY_PROFILE
  description: "Proxy users can view assigned member's profile"
  priority: 100
  conditions:
    auth-type: PROXY
    resource-type: profile
    action: VIEW
  proxy-rules:
    config-full-access: true
    require-assignment: true
```

### Authorization Flow in Controller

```java
@PostMapping("/health")
public Mono<HealthSummary> getHealthSummary(
        @RequestBody @Valid MemberRequest request,
        ServerWebExchange exchange) {

    AuthContext auth = AuthContextResolver.require(exchange);

    // Build resource attributes
    ResourceAttributes resource = ResourceAttributes.builder()
        .type("health_summary")
        .ownerId(request.memberId())
        .sensitive(false)  // or true for sensitive endpoints
        .build();

    // ABAC authorization - uses auth.subject() which has authType
    return abacService.authorize(auth.subject(), resource, Action.VIEW)
        .flatMap(decision -> {
            if (!decision.isAllowed()) {
                return Mono.error(new ForbiddenException(decision.reason()));
            }
            return healthService.getSummary(request.memberId());
        });
}
```

### Owner Check Logic

For `owner-check: true` policies, the ABAC engine validates:
```java
// SubjectAttributes
boolean isOwner = subject.userId().equals(resource.ownerId());
```

### Assignment Check Logic

For `require-assignment: true` proxy policies:
```java
// SubjectAttributes
boolean isAssigned = subject.isAssignedTo(resource.ownerId());
// OR
boolean isConfig = subject.isConfig();  // config-full-access: true
```

---

## Implementation Checklist

### Phase 1: Core Models
- [ ] Create `AuthContext` record
- [ ] Create `AuthContextResolver` utility
- [ ] Create `MemberRequest` DTO

### Phase 2: Configuration
- [ ] Update `SecurityPathsProperties` with `dualAuth`
- [ ] Update `security-paths.yml`
- [ ] Add new ABAC policies to `abac-policies.yml`

### Phase 3: DualAuthWebFilter
- [ ] Implement filter
- [ ] HSID context resolution
- [ ] PROXY context resolution
- [ ] CSRF auth-type check

### Phase 4: Controller Migration
- [ ] Create health summary endpoints (POST)
- [ ] Create profile endpoints (POST)
- [ ] Update document endpoints
- [ ] Deprecate /api/mfe/**

### Phase 5: Testing
- [ ] Unit tests for AuthContext
- [ ] Integration tests for DualAuthWebFilter
- [ ] ABAC policy tests for all scenarios
- [ ] E2E tests with both auth types

---

## Notes

_Updated after planning session. Ready for implementation._
