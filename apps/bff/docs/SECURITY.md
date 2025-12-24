# Security Documentation

## Overview

The BFF implements a defense-in-depth security model with multiple layers of protection for both browser and partner access patterns.

## Security Layers

```
┌─────────────────────────────────────────────────────────────────┐
│  Layer 1: Transport Security (TLS 1.3 / mTLS)                   │
├─────────────────────────────────────────────────────────────────┤
│  Layer 2: Origin Validation (Browser paths only)                │
├─────────────────────────────────────────────────────────────────┤
│  Layer 3: Authentication (Session / Header-based)               │
├─────────────────────────────────────────────────────────────────┤
│  Layer 4: Authorization (Persona / Delegate validation)         │
├─────────────────────────────────────────────────────────────────┤
│  Layer 5: Application Security (Input validation, CSRF)         │
└─────────────────────────────────────────────────────────────────┘
```

## Cookie Security

### Cookie Attributes

| Cookie | HttpOnly | Secure | SameSite | Purpose |
|--------|----------|--------|----------|---------|
| `BFF_SESSION` | true | true | Strict | Session identifier |
| `XSRF-TOKEN` | **false** | true | Strict | CSRF token (JS readable) |

### Session Cookie

| Attribute | Value | Security Benefit |
|-----------|-------|------------------|
| `Name` | `BFF_SESSION` | Unique identifier |
| `Domain` | Configurable (abc.com) | Restricts cookie scope to trusted domain |
| `Path` | `/` | Available for all BFF paths |
| `Secure` | `true` | Only transmitted over HTTPS |
| `HttpOnly` | `true` | Inaccessible to JavaScript (prevents XSS theft) |
| `SameSite` | `Strict` | Never sent cross-site (prevents CSRF) |
| `MaxAge` | `30 minutes` | Automatic expiration |

### CSRF Token Cookie

| Attribute | Value | Security Benefit |
|-----------|-------|------------------|
| `Name` | `XSRF-TOKEN` | CSRF token identifier |
| `Domain` | Same as session | Consistent domain scope |
| `Path` | `/` | Available for all BFF paths |
| `Secure` | `true` | Only transmitted over HTTPS |
| `HttpOnly` | `false` | **Must be readable by JavaScript** |
| `SameSite` | `Strict` | Defense in depth |

### Cookie Creation Example

```java
ResponseCookie.from("BFF_SESSION", sessionId)
    .domain(cookieDomain)      // e.g., "abc.com"
    .path("/")
    .httpOnly(true)            // No JavaScript access
    .secure(true)              // HTTPS only
    .sameSite("Strict")        // No cross-site requests
    .maxAge(Duration.ofMinutes(30))
    .build();
```

### Domain Restriction

The cookie domain configuration ensures cookies are only sent to trusted domains:

```yaml
bff:
  session:
    cookie-domain: abc.com     # Base domain
    allowed-origins:           # Full origin URLs
      - https://abc.com
      - https://www.abc.com
```

**Subdomain Support:**
- Cookie domain `abc.com` allows access from `www.abc.com`, `app.abc.com`, etc.
- Use leading dot (`.abc.com`) for explicit subdomain inclusion

## Origin Validation

### Purpose

Prevents unauthorized domains from making API requests using stolen cookies or CSRF attacks.

### Validation Logic

```java
@Override
public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getPath().value();

    // Partner paths: mTLS validated by ALB, skip origin check
    if (path.startsWith("/mfe/")) {
        return chain.filter(exchange);
    }

    // Public paths: Allow for OIDC callbacks
    if (isPublicPath(path)) {
        return chain.filter(exchange);
    }

    // Browser paths: Validate origin
    return validateOrigin(exchange)
        .flatMap(valid -> chain.filter(exchange));
}
```

### Origin Matching Rules

1. **Exact Match**: Origin header matches allowed origin exactly
2. **Domain Match**: Origin host matches cookie domain or is a subdomain
3. **Referer Fallback**: For same-origin requests without Origin header

```java
private boolean isAllowedOrigin(String origin) {
    // Exact match against allowed origins list
    if (allowedOrigins.contains(origin)) {
        return true;
    }

    // Domain match (for subdomains)
    URI uri = URI.create(origin);
    String host = uri.getHost();
    return host.equals(cookieDomain) ||
           host.endsWith("." + cookieDomain);
}
```

### Security Rejections

| Condition | Response |
|-----------|----------|
| Origin not in allowed list | 401 Unauthorized |
| Origin is malformed | 401 Unauthorized |
| Missing Origin AND Referer | 401 Unauthorized |

## CSRF Protection

### Overview

CSRF (Cross-Site Request Forgery) protection is enabled for browser paths using the double-submit cookie pattern.

| Path | CSRF Enabled | Reason |
|------|--------------|--------|
| `/api/v1/**` (Browser) | **Yes** | Session cookie auth needs protection |
| `/mfe/api/v1/**` (Partner) | No | Header-based + mTLS is CSRF-immune |
| Public paths | No | No state-changing operations |

### How It Works

1. **Token Generation**: On first GET request, Spring creates a CSRF token
2. **Cookie Setting**: Token stored in `XSRF-TOKEN` cookie (HttpOnly=false)
3. **Frontend Reads**: JavaScript reads token from cookie
4. **Request Header**: Frontend sends `X-CSRF-TOKEN` header with POST/PUT/DELETE/PATCH
5. **Validation**: Spring validates header value matches cookie value

### Frontend Integration

```javascript
// Read CSRF token from cookie
const getCsrfToken = () => {
    const value = `; ${document.cookie}`;
    const parts = value.split('; XSRF-TOKEN=');
    if (parts.length === 2) return parts.pop().split(';').shift();
    return null;
};

// Include in all state-changing requests
fetch('/api/v1/resource', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': getCsrfToken()
    },
    body: JSON.stringify(data)
});
```

### Configuration

```yaml
bff:
  session:
    csrf-cookie-name: ${BFF_CSRF_COOKIE:XSRF-TOKEN}
    csrf-header-name: ${BFF_CSRF_HEADER:X-CSRF-TOKEN}
```

### Why Both SameSite=Strict AND CSRF Tokens?

SameSite=Strict on the session cookie is excellent protection, but CSRF tokens add defense-in-depth:

1. **Browser Compatibility**: Older browsers don't support SameSite
2. **Double Validation**: Token must match in both cookie and header
3. **Explicit Intent**: Proves request was initiated by our frontend
4. **Industry Standard**: Required by many security compliance frameworks

## Authentication

### Browser Authentication (OIDC)

**Flow: Authorization Code + PKCE**

1. User visits `/login`
2. BFF generates PKCE code verifier and challenge
3. Redirect to HSID with code challenge
4. User authenticates at HSID
5. Callback to `/?code=xxx&state=yyy`
6. BFF exchanges code + verifier for tokens
7. Tokens stored server-side in session
8. Session cookie returned to browser

**Token Storage:**
- Access token, refresh token, ID token stored in BffSession
- Never exposed to browser JavaScript
- Session ID is the only value in cookie

### Partner Authentication (Header-based)

**Required Headers:**

| Header | Description | Validation |
|--------|-------------|------------|
| `X-Persona` | User persona type | Must be valid Persona enum |
| `X-Member-Id` | Member identifier | Required, non-empty |
| `X-Member-Id-Type` | Identifier type | Must be valid MemberIdType |

**mTLS Security:**
- Partners connect via mTLS
- Certificate validation performed by ALB
- BFF trusts authenticated partner requests

### Persona-IDP Validation

Each persona is validated against its expected identity provider:

| Persona | Required IDP | Validation |
|---------|-------------|------------|
| SELF | HSID | Member ID type must be HSID |
| DELEGATE | HSID | Member ID type must be HSID |
| AGENT | MSID | Member ID type must be MSID |
| CONFIG_SPECIALIST | MSID | Member ID type must be MSID |
| CASE_WORKER | OHID | Member ID type must be OHID |

**Invalid combinations are rejected with 401 Unauthorized.**

## Authorization

### Persona-Based Access Control

Controllers can require specific personas using the `@RequiredPersona` annotation:

```java
@GetMapping("/admin/config")
@RequiredPersona(Persona.CONFIG_SPECIALIST)
public Mono<Config> getConfig() {
    // Only CONFIG_SPECIALIST can access
}

@GetMapping("/user/profile")
@RequiredPersona({Persona.SELF, Persona.DELEGATE, Persona.AGENT})
public Mono<Profile> getProfile() {
    // Multiple personas allowed
}
```

### MFE Access Control

Partner routes must be explicitly enabled with `@MfeEnabled`:

```java
@GetMapping("/api/v1/user")
@MfeEnabled  // Accessible via /mfe/api/v1/user
public Mono<User> getUser() {
    // Available to both browser and partner clients
}

@GetMapping("/api/v1/settings")
// No @MfeEnabled - browser only
public Mono<Settings> getSettings() {
    // Only accessible via /api/v1/settings (browser)
}
```

### Delegate Authorization

When operating as DELEGATE, additional validation ensures the user is authorized:

1. **Delegate Relationship Check**: User must have active delegation
2. **Enterprise ID Validation**: Request enterprise ID must match delegated member
3. **Delegate Type Verification**: RPR, DAA, or ROI with appropriate permissions

```java
// DelegateEnterpriseIdFilter
if (persona == Persona.DELEGATE) {
    String requestedEnterpriseId = extractFromBody(exchange);
    List<DelegateInfo> delegates = session.getActiveDelegates();

    boolean authorized = delegates.stream()
        .anyMatch(d -> d.getEnterpriseId().equals(requestedEnterpriseId));

    if (!authorized) {
        return Mono.error(new AuthorizationException(
            "No delegation for enterprise: " + requestedEnterpriseId));
    }
}
```

## Session Security

### Session ID Generation

```java
String sessionId = UUID.randomUUID().toString();
```

- Cryptographically random UUID
- 128 bits of entropy
- Not predictable or enumerable

### Session Storage

**In-Memory:**
```java
private final ConcurrentHashMap<String, BffSession> sessions;
```
- Not suitable for production (no persistence, no scaling)
- Use for development/testing only

**Redis:**
```java
private final ReactiveRedisTemplate<String, BffSession> redisTemplate;
```
- Production-ready
- Automatic expiration via Redis TTL
- Supports horizontal scaling

### Session Expiration

- Default timeout: 30 minutes of inactivity
- Sliding expiration: `lastAccessedAt` updated on each request
- Absolute expiration: Tokens expire based on IDP configuration

### Session Binding

Sessions are bound to the client's browser fingerprint and IP address to prevent session hijacking. If an attacker steals a session cookie, they cannot use it without matching the original device fingerprint and IP.

**Captured at Session Creation:**
- `browserFingerprint`: Hash from `X-Fingerprint` header (using FingerprintJS)
- `clientIp`: From `X-Forwarded-For` header (first IP) or direct connection

**Validation Logic:**

```
Session Validation:
├─ If fingerprint matches stored → Allow (even if IP changed)
├─ If fingerprint missing/mismatches AND IP matches → Allow (fallback)
├─ If both mismatch AND strict mode → Reject with 401
└─ If both mismatch AND permissive mode → Log warning, allow
```

**Configuration:**

| Property | Default | Description |
|----------|---------|-------------|
| `bff.session.session-binding-enabled` | `true` | Enable binding validation |
| `bff.session.strict-session-binding` | `true` | Reject on mismatch (false = warn only) |

**Frontend Integration:**

```javascript
import FingerprintJS from '@fingerprintjs/fingerprintjs';

// Initialize once on page load
const fp = await FingerprintJS.load();
const result = await fp.get();
const fingerprint = result.visitorId;

// Include in every API request
fetch('/api/v1/resource', {
    headers: {
        'X-Fingerprint': fingerprint,
        'X-CSRF-TOKEN': csrfToken
    }
});
```

**Security Benefits:**

| Attack | Protection |
|--------|------------|
| Session cookie theft | Attacker needs same IP/fingerprint |
| Session fixation | Session bound to original device |
| Cookie replay | IP/fingerprint mismatch detected |

## Attack Mitigations

### Cross-Site Request Forgery (CSRF)

| Protection | Implementation |
|------------|----------------|
| SameSite=Strict | Cookie never sent cross-site |
| Origin validation | Requests must come from allowed origins |
| State parameter | OIDC callback validates state |

### Cross-Site Scripting (XSS)

| Protection | Implementation |
|------------|----------------|
| HttpOnly cookies | Tokens inaccessible to JavaScript |
| Server-side tokens | Access tokens never in browser |
| Input validation | All inputs sanitized |

### Session Hijacking

| Protection | Implementation |
|------------|----------------|
| Secure cookie | HTTPS-only transmission |
| Random session ID | UUID with 128-bit entropy |
| Session binding | Session bound to browser fingerprint + client IP |
| Fingerprint validation | `X-Fingerprint` header validated on each request |
| IP validation | Client IP validated as fallback |

### Token Theft

| Protection | Implementation |
|------------|----------------|
| Server-side storage | Tokens never leave BFF |
| HttpOnly cookie | Session ID only in cookie |
| Short expiration | 30-minute session timeout |

## Security Logging

### Events Logged

| Event | Level | Details |
|-------|-------|---------|
| Invalid origin | WARN | Origin value, path |
| Session expired | INFO | Session ID (masked) |
| Authentication failure | WARN | Reason, client IP |
| Authorization denied | WARN | Persona, required persona |
| Delegate access denied | WARN | Enterprise ID, user ID |
| Session binding violation | WARN | Masked session ID, stored/current IP, fingerprint presence |

### Log Format

```
SECURITY: Invalid origin rejected: https://evil.com
SECURITY: Missing origin/referer for path: /api/v1/user
SECURITY: Persona mismatch - required: CONFIG_SPECIALIST, actual: AGENT
SECURITY: Delegate not authorized for enterprise: ENT123
SESSION_BINDING_VIOLATION: sessionId=abc12345***, storedIp=1.2.3.4, currentIp=5.6.7.8, storedFingerprint=present, currentFingerprint=null
```

## Defense in Depth

### Catch-All Security Chain

The application includes a catch-all security chain that denies all requests to unregistered paths:

```java
@Bean
@Order(Integer.MAX_VALUE)
public SecurityWebFilterChain catchAllSecurityFilterChain(ServerHttpSecurity http) {
    return http
            .securityMatcher(ServerWebExchangeMatchers.anyExchange())
            .authorizeExchange(exchanges -> exchanges.anyExchange().denyAll())
            .build();
}
```

This ensures that any path not explicitly configured is rejected with 403 Forbidden.

### Error Message Sanitization

Security exceptions are logged with full details server-side but return generic messages to clients:

| Exception | Logged Message | Client Response |
|-----------|----------------|-----------------|
| AuthenticationException | Full message | "Authentication required" |
| AuthorizationException | Full message | "Access denied" |
| SecurityIncidentException | Full incident details | "Access denied due to security policy violation" |

### Session ID Protection

Session IDs are masked in logs to prevent exposure:
- Only first 8 characters shown: `abc12345***`
- Full session ID never logged

## Security Checklist

### Deployment

- [ ] TLS 1.3 enabled at load balancer
- [ ] mTLS configured for partner paths
- [ ] Redis TLS enabled for session storage
- [ ] Environment variables properly secured
- [ ] Secrets not in configuration files

### Configuration

- [ ] `cookie-secure: true` for production
- [ ] `cookie-domain` matches production domain
- [ ] `allowed-origins` contains only trusted domains
- [ ] Session timeout appropriate for use case
- [ ] Logging level appropriate (not DEBUG in prod)

### Monitoring

- [ ] Security events logged and monitored
- [ ] Failed authentication alerts configured
- [ ] Session store connectivity monitored
- [ ] Origin validation rejections tracked
- [ ] Catch-all chain rejections monitored (indicates misconfiguration)
