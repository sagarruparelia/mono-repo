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
| Partner auth method | Header-based (mTLS ALB) | ALB validates partner OAuth2 server. BFF trusts mTLS. |
| JWT issuer | Separate OAuth2 server | Partners use different OAuth2 server than HSID |
| Member access | Unified effectiveMemberId | Both auth types resolve to same member context |
| Member ID source | Request body (POST) | Partner sends memberEid in request body, not header |
| Parent flow | Require memberEid param | Explicit member selection on each request |
| CSRF handling | Session only | CSRF required for HSID, skipped for PROXY (stateless) |
| IDP validation | Strict | BFF validates persona matches allowed personas for IDP type |
| ABAC granularity | Configurable | Default policy per resource, overrides per subcategory |

---

## Proxy Authentication Headers

### Headers from Partner (via mTLS ALB)

| Header | Description | Example |
|--------|-------------|---------|
| `X-User-Id` | Logged in agent/case_worker user ID | `agent-123` |
| `X-IDP-Type` | Identity Provider used to sign in | `msid` or `ohid` |
| `X-Persona` | Caller's persona | `agent`, `case_worker`, `config_specialist` |
| `X-Partner-Id` | Partner organization ID | `partner-abc` |

### IDP → Persona Validation

| IDP | Allowed Personas | Notes |
|-----|------------------|-------|
| `ohid` | `case_worker` | Ohio Health ID - case workers only |
| `msid` | `agent`, `config_specialist` | Member Services ID - agents and config |

**Validation Rule**: BFF returns `403 Forbidden` if persona doesn't match IDP's allowed list.

```java
// Example validation in DualAuthWebFilter
Map<String, Set<String>> idpPersonaMapping = Map.of(
    "ohid", Set.of("case_worker"),
    "msid", Set.of("agent", "config_specialist")
);

if (!idpPersonaMapping.get(idpType).contains(persona)) {
    return Mono.error(new ForbiddenException(
        "Persona '" + persona + "' not allowed for IDP '" + idpType + "'"));
}

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

**Note**: `/api/mfe/**` is **deprecated** - consolidated into `/api/health/**` with dual auth.

## API Endpoint Structure

### Health Endpoints (Dual Auth)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/health/summary` | POST | Full health summary (all subcategories) |
| `/api/health/immunizations` | POST | Immunization records |
| `/api/health/allergies` | POST | Allergy records |
| `/api/health/conditions` | POST | Medical conditions (SENSITIVE) |
| `/api/health/medications` | POST | Medication records |
| `/api/health/lab-reports` | POST | Lab test results (SENSITIVE) |

### Profile Endpoints (Dual Auth)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/profile/view` | POST | View member profile |
| `/api/profile/update` | POST | Update member profile (HSID only) |

### Document Endpoints (Dual Auth)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/documents/list` | POST | List member's documents |
| `/api/documents/view` | POST | View specific document |
| `/api/documents/upload` | POST | Upload document (multipart) |

### Request Body Pattern

All dual-auth endpoints use POST with `MemberRequest`:

```json
POST /api/health/immunizations
Content-Type: application/json

{
  "memberEid": "member-123"
}
```

For subcategory-specific requests:
```json
POST /api/health/summary
Content-Type: application/json

{
  "memberEid": "member-123",
  "subcategories": ["immunizations", "allergies", "medications"]
}
```

## Member EID Pattern

All dual-auth endpoints use **POST with request body** (not URL path):

```java
// Request body for all data endpoints
public record MemberRequest(
    @NotBlank String memberEid
) {}

// Controller pattern
@PostMapping("/health")
public Mono<HealthSummary> getHealthSummary(
    @RequestBody @Valid MemberRequest request,
    ServerWebExchange exchange) {

    AuthContext auth = AuthContextResolver.require(exchange);
    // Validate: auth can access request.memberEid()
    return healthService.getSummary(request.memberEid());
}
```

**Why POST for reads?**
- Unified pattern for session (browser) and proxy (partners)
- memberEid in body, not URL - cleaner, more secure
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

### Persona Terminology

| HSID Persona | UI Term | Description |
|--------------|---------|-------------|
| `individual` | **Member** | Youth/individual viewing own data |
| `parent` | **ResponsibleParty** | Parent/guardian viewing dependent's data |

### Permission Model

| Permission | Full Name | Description |
|------------|-----------|-------------|
| **RPR** | Responsible Party Relationship | Establishes legal relationship with member |
| **DAA** | Digital Authorization for Access | Legal consent for digital access |
| **ROI** | Release of Information | Consent to access sensitive data |

**Why both RPR + DAA for base access?**
- **RPR** establishes the relationship (you are the responsible party)
- **DAA** provides legal consent for digital access to records
- Both are required because relationship alone doesn't grant digital access rights

**Access Combinations:**
- View member data: `RPR + DAA` (relationship + digital consent)
- View sensitive data: `RPR + DAA + ROI` (+ release consent)

### Sensitivity Rules

#### Documents (SENSITIVE by default)

| Phase | Rule |
|-------|------|
| **MVP** | All uploaded documents are **SENSITIVE** by default |
| **Post-MVP** | Per-document `sensitive` flag (uploader can mark as non-sensitive) |

**ResponsibleParty** needs `RPR + DAA + ROI` to view any document in MVP.

#### Health Resources (Configurable)

| Default | Rule |
|---------|------|
| **Base** | Health resources are **NON-SENSITIVE** by default |
| **Override** | ABAC policy can mark subcategories as sensitive |

**Examples of business-driven sensitivity:**
- "All lab reports are sensitive" → add policy with `sensitive: true`
- "All medications are sensitive" → add policy with `sensitive: true`
- "All health records sensitive" → set default sensitivity to SENSITIVE

### Health Summary Subcategories

| Subcategory | Default | Can Override |
|-------------|---------|--------------|
| `immunization` | NORMAL | Yes - via ABAC policy |
| `allergy` | NORMAL | Yes - via ABAC policy |
| `condition` | NORMAL | Yes - via ABAC policy |
| `lab_reports` | NORMAL | Yes - via ABAC policy |
| `medication` | NORMAL | Yes - via ABAC policy |

### Sensitivity Access by Persona

| Persona | Non-Sensitive | Sensitive |
|---------|---------------|-----------|
| **Member** (individual) | Owner check | Owner check |
| **ResponsibleParty** (parent) | RPR + DAA | RPR + DAA + ROI |
| **agent** | ABAC policy decides | ABAC policy decides |
| **config_specialist** | ABAC policy decides | ABAC policy decides |
| **case_worker** | ABAC policy decides | ABAC policy decides |

**Proxy personas**: Sensitive access is controlled entirely by ABAC policies, not hardcoded.

### Configuration Pattern (abac-policies.yml)

```yaml
abac:
  resource-defaults:
    # Documents always sensitive in MVP
    document:
      default-sensitivity: SENSITIVE

    # Health resources non-sensitive by default
    health_summary:
      default-sensitivity: NORMAL
      # Business can override per subcategory:
      # subcategory-overrides:
      #   lab_reports: SENSITIVE
      #   medication: SENSITIVE

  policies:
    # ResponsibleParty viewing member's health data
    - id: RESPONSIBLE_PARTY_HEALTH
      conditions:
        auth-type: HSID
        persona: parent
        resource-type: health_summary
        sensitive: false
      required-permissions: [RPR, DAA]

    # ResponsibleParty viewing sensitive health data
    - id: RESPONSIBLE_PARTY_HEALTH_SENSITIVE
      conditions:
        auth-type: HSID
        persona: parent
        resource-type: health_summary
        sensitive: true
      required-permissions: [RPR, DAA, ROI]

    # ResponsibleParty viewing documents (always sensitive in MVP)
    - id: RESPONSIBLE_PARTY_DOCUMENT
      conditions:
        auth-type: HSID
        persona: parent
        resource-type: document
      required-permissions: [RPR, DAA, ROI]

    # Agent viewing health data - policy decides sensitivity access
    - id: PROXY_AGENT_HEALTH
      conditions:
        auth-type: PROXY
        persona: agent
        resource-type: health_summary
        sensitive: false
      proxy-rules:
        require-assignment: true

    # Agent cannot view sensitive health data (example policy)
    - id: PROXY_AGENT_HEALTH_SENSITIVE_DENIED
      conditions:
        auth-type: PROXY
        persona: agent
        resource-type: health_summary
        sensitive: true
      decision: DENY
      reason: "Agents cannot access sensitive health data"

    # Config specialist has full access (example)
    - id: PROXY_CONFIG_FULL_ACCESS
      conditions:
        auth-type: PROXY
        persona: config_specialist
        resource-type: [health_summary, document, profile]
      proxy-rules:
        config-full-access: true
```

### Dual Auth Policy Matrix

| Resource | Auth Type | Persona | Action | Required |
|----------|-----------|---------|--------|----------|
| health_summary | HSID | individual | VIEW | Owner check (userId = memberEid) |
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
        .ownerId(request.memberEid())
        .sensitive(false)  // or true for sensitive endpoints
        .build();

    // ABAC authorization - uses auth.subject() which has authType
    return abacService.authorize(auth.subject(), resource, Action.VIEW)
        .flatMap(decision -> {
            if (!decision.isAllowed()) {
                return Mono.error(new ForbiddenException(decision.reason()));
            }
            return healthService.getSummary(request.memberEid());
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

### Phase 1: Core Models ✅
- [x] Create `AuthContext` record (`auth/model/AuthContext.java`)
- [x] Create `AuthContextResolver` utility (`auth/util/AuthContextResolver.java`)
- [x] Create `MemberRequest` DTO (`common/dto/MemberRequest.java`)
- [x] Create `ErrorResponse` DTO (`common/dto/ErrorResponse.java`)

### Phase 2: Configuration ✅
- [x] Update `SecurityPathsProperties` with `dualAuth` field
- [x] Update `security-paths.yml` with dual-auth paths
- [x] Create `IdpProperties` for IDP-persona mapping
- [x] Add `partnerId` header to `ExternalIntegrationProperties`
- [x] Add IDP persona-mapping config to `application.yml`
- [x] Add new ABAC policies to `abac-policies.yml`

### Phase 3: DualAuthWebFilter ✅
- [x] Implement `DualAuthWebFilter` (`auth/filter/DualAuthWebFilter.java`)
- [x] HSID context resolution (session cookie → Redis)
- [x] PROXY context resolution (headers → IDP validation)
- [x] Path category matching (PUBLIC, SESSION_ONLY, PROXY_ONLY, DUAL_AUTH)
- [x] Standardized error responses with correlation ID

### Phase 4: Controller Migration
- [ ] Create health summary endpoints (POST pattern)
- [ ] Create profile endpoints (POST pattern)
- [ ] Update document endpoints
- [ ] Deprecate /api/mfe/**

### Phase 5: Testing
- [ ] Unit tests for AuthContext
- [ ] Integration tests for DualAuthWebFilter
- [ ] ABAC policy tests for all scenarios
- [ ] E2E tests with both auth types

---

## Error Handling Specification

### Standardized Error Response Format

All API errors return a consistent JSON structure:

```json
{
  "error": "error_code",
  "code": "DETAILED_ERROR_CODE",
  "message": "Human-readable message for UI display",
  "correlationId": "uuid-from-X-Correlation-Id-header",
  "timestamp": "2024-12-19T10:30:00.000Z",
  "path": "/api/summary/health",
  "details": {
    "field": "memberEid",
    "reason": "Required field missing"
  }
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `error` | Yes | Stable error category (for client error handling) |
| `code` | Yes | Specific error code (for debugging/logging) |
| `message` | Yes | Human-readable message (for UI display) |
| `correlationId` | Yes | Request correlation ID for tracing |
| `timestamp` | Yes | ISO-8601 timestamp |
| `path` | Yes | Request path |
| `details` | No | Additional context (validation errors, missing attrs) |

### New Error Codes for Dual Auth

| Code | HTTP | Category | Description |
|------|------|----------|-------------|
| `IDP_PERSONA_MISMATCH` | 403 | Auth | Persona not allowed for IDP type |
| `INVALID_IDP_TYPE` | 403 | Auth | Unrecognized IDP type |
| `MISSING_IDP_TYPE` | 401 | Auth | X-IDP-Type header required |
| `MEMBER_ACCESS_DENIED` | 403 | Authz | No access to requested member |
| `MEMBER_NOT_FOUND` | 404 | Resource | Member ID not found |
| `SUBCATEGORY_ACCESS_DENIED` | 403 | Authz | No access to health subcategory |
| `SENSITIVE_DATA_REQUIRES_ROI` | 403 | Authz | ROI permission required |
| `SESSION_REQUIRED` | 401 | Auth | Endpoint requires session auth |
| `PROXY_REQUIRED` | 401 | Auth | Endpoint requires proxy auth |

### Error Response Examples

**IDP-Persona Mismatch (403)**
```json
{
  "error": "access_denied",
  "code": "IDP_PERSONA_MISMATCH",
  "message": "Persona 'agent' is not allowed for IDP 'ohid'",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2024-12-19T10:30:00.000Z",
  "path": "/api/summary/health",
  "details": {
    "idpType": "ohid",
    "persona": "agent",
    "allowedPersonas": ["case_worker"]
  }
}
```

**Member Access Denied (403)**
```json
{
  "error": "access_denied",
  "code": "MEMBER_ACCESS_DENIED",
  "message": "You do not have access to this member's data",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2024-12-19T10:30:00.000Z",
  "path": "/api/summary/health",
  "details": {
    "memberEid": "member-123",
    "requiredPermissions": ["DAA", "RPR"],
    "missingPermissions": ["RPR"]
  }
}
```

**Sensitive Data Requires ROI (403)**
```json
{
  "error": "access_denied",
  "code": "SENSITIVE_DATA_REQUIRES_ROI",
  "message": "Release of Information consent required to access this data",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2024-12-19T10:30:00.000Z",
  "path": "/api/summary/health",
  "details": {
    "subcategory": "lab_reports",
    "requiredPermissions": ["DAA", "RPR", "ROI"]
  }
}
```

**Validation Error (400)**
```json
{
  "error": "validation_error",
  "code": "INVALID_REQUEST",
  "message": "Request validation failed",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2024-12-19T10:30:00.000Z",
  "path": "/api/summary/health",
  "details": {
    "fields": [
      {"field": "memberEid", "message": "must not be blank"}
    ]
  }
}
```

### Implementation

**Create `ErrorResponse` record:**
```java
public record ErrorResponse(
    String error,
    String code,
    String message,
    String correlationId,
    Instant timestamp,
    String path,
    Map<String, Object> details
) {
    public static ErrorResponse of(String error, String code, String message,
                                   String correlationId, String path) {
        return new ErrorResponse(error, code, message, correlationId,
                                 Instant.now(), path, null);
    }

    public ErrorResponse withDetails(Map<String, Object> details) {
        return new ErrorResponse(error, code, message, correlationId,
                                 timestamp, path, details);
    }
}
```

**Update GlobalExceptionHandler to use ErrorResponse**

**Update filters to include correlationId in error responses**

---

## Notes

_Updated after planning session. Ready for implementation._
