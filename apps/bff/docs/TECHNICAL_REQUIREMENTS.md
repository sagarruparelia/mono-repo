# Technical Requirements

## Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Runtime | Java | 25 |
| Framework | Spring Boot | 3.5.9 |
| Web Layer | Spring WebFlux | Reactive/Non-blocking |
| Security | Spring Security WebFlux | 6.x |
| Build Tool | Maven | 3.x |
| Session Store | In-Memory / Redis | Configurable |
| Cache Store | In-Memory / Redis | Configurable |

## Authentication Requirements

### Browser Authentication (OIDC)

| Requirement | Specification |
|-------------|---------------|
| Protocol | OAuth 2.0 + OpenID Connect |
| Flow | Authorization Code with PKCE |
| IDP | HSID (HealthSafe-ID) |
| Token Storage | Server-side session only |
| Cookie Name | `BFF_SESSION` |
| Cookie Attributes | Secure, HttpOnly, SameSite=Strict |

### Partner Authentication (Header-based)

| Header | Purpose | Required |
|--------|---------|----------|
| `X-Persona` | User persona type | Yes |
| `X-Member-Id` | Member identifier value | Yes |
| `X-Member-Id-Type` | Identifier type (HSID/MSID/OHID) | Yes |

### Persona-IDP Validation Matrix

| Persona | HSID | MSID | OHID |
|---------|------|------|------|
| SELF | Valid | Invalid | Invalid |
| DELEGATE | Valid | Invalid | Invalid |
| AGENT | Invalid | Valid | Invalid |
| CONFIG_SPECIALIST | Invalid | Valid | Invalid |
| CASE_WORKER | Invalid | Invalid | Valid |

## Session Requirements

### Session Data Structure

```java
record BffSession(
    String sessionId,
    Instant createdAt,
    Instant lastAccessedAt,
    String accessToken,
    String refreshToken,
    Instant tokenExpiry,
    String enterpriseId,
    String loggedInMemberIdValue,
    MemberIdType loggedInMemberIdType,
    Persona persona,
    List<DelegateInfo> activeDelegates
)
```

### Session Storage Options

| Option | Use Case | Configuration |
|--------|----------|---------------|
| In-Memory | Development/Testing | `bff.session.store=in-memory` |
| Redis | Production | `bff.session.store=redis` |

## Security Requirements

### Cookie Security

| Attribute | Value | Purpose |
|-----------|-------|---------|
| Domain | Configurable | Restrict to trusted domain |
| Path | `/` | Available for all paths |
| Secure | `true` | HTTPS transmission only |
| HttpOnly | `true` | No JavaScript access |
| SameSite | `Strict` | No cross-site transmission |
| MaxAge | 30 minutes | Session timeout |

### Origin Validation

- All browser requests (`/api/v1/**`) must include valid Origin header
- Origin must match configured allowed origins list
- Fallback to Referer header if Origin missing (same-origin GET requests)
- Partner paths (`/mfe/**`) skip origin validation (mTLS provides security)

## External API Requirements

### API Client Configuration

| Service | Auth Method | Cache TTL |
|---------|-------------|-----------|
| User Service | Bearer Token (HCP) | 30 minutes |
| Delegate Graph | Bearer Token (HCP) | 30 minutes |
| Eligibility Graph | Bearer Token (HCP) | 30 minutes |

### GraphQL Integration

```graphql
# Delegate Graph Query
query GetDelegates($memberId: String!) {
    delegates(memberId: $memberId) {
        delegateType
        targetMemberId
        permissions
    }
}

# Eligibility Graph Query
query GetEligibility($enterpriseId: String!) {
    eligibility(enterpriseId: $enterpriseId) {
        planCode
        effectiveDate
        terminationDate
    }
}
```

## Filter Chain Order

### Browser Path (`/api/v1/**`)

| Order | Filter | Purpose |
|-------|--------|---------|
| -150 | OriginValidationFilter | Validate request origin |
| AUTHENTICATION | BrowserSessionFilter | Cookie-based session auth |
| AUTHENTICATION+1 | DelegateEnterpriseIdFilter | Validate delegate requests |
| AUTHORIZATION+1 | PersonaAuthorizationFilter | Persona-based access control |

### Partner Path (`/mfe/api/v1/**`)

| Order | Filter | Purpose |
|-------|--------|---------|
| FIRST | MfePathRewriteFilter | Rewrite /mfe/api/v1 to /api/v1 |
| AUTHENTICATION | PartnerAuthenticationFilter | Header-based auth |
| AUTHENTICATION+1 | DelegateEnterpriseIdFilter | Validate delegate requests |
| AUTHORIZATION+1 | PersonaAuthorizationFilter | Persona-based access control |
| AUTHORIZATION+2 | MfeRouteValidator | Validate @MfeEnabled annotation |

## Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| Response Time | < 200ms p95 |
| Session Store Latency | < 10ms |
| Concurrent Sessions | 10,000+ |
| Availability | 99.9% |
