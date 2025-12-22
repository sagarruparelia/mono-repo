# BFF Security

## Overview

This document describes the security measures implemented in the BFF application, covering authentication, authorization, session management, API security, and data protection.

---

## Authentication Security

### OAuth2/OIDC with HSID
- **Provider**: HSID (Health Services ID)
- **Flow**: Authorization Code with PKCE
- **Tokens**: Access token, refresh token, ID token
- **Token Storage**: Server-side session only (never exposed to browser)

### mTLS for Proxy Authentication
- Proxy requests authenticated via mutual TLS
- Headers validated by `ExternalAuthFilter`:
  - `x-auth-principal` - Authentication identity
  - `x-member-eid` - Member enterprise ID
  - `x-persona` - User persona type
- Header format validation prevents injection attacks

### Session Token Security
Session cookies are configured with maximum security:
```
HttpOnly:     true    # Prevents XSS access
Secure:       true    # HTTPS only
SameSite:     Strict  # Prevents CSRF
Path:         /       # Application-wide
MaxAge:       <TTL>   # Session timeout
```

---

## Authorization Security

### Persona-Based Access Control
- Every endpoint protected by `@RequirePersona` annotation
- Persona validated against current user context
- Delegate types validated for managed member access

### Temporal Permission Validation
Managed member permissions include time bounds:
```java
// Permission validated by PersonaAuthorizationFilter
if (now.isBefore(permission.startDate()) || now.isAfter(permission.endDate())) {
    return Mono.error(new ForbiddenException("Permission expired"));
}
```

### Enterprise ID Resolution
For delegated access:
1. Persona filter validates user has permission for target `enterpriseId`
2. `VALIDATED_ENTERPRISE_ID` attribute set on exchange
3. Controllers use validated ID, not user-provided input

---

## Session Security

### Zero-Trust Session Model
Sessions are validated on every request, not just created:
1. **Cookie Validation**: Session cookie present and valid
2. **Session Exists**: Session data found in cache
3. **Device Fingerprint**: Browser fingerprint matches
4. **IP Binding**: Client IP matches (configurable strictness)

### Device Fingerprint Validation
```
Fingerprint Components:
- User-Agent header
- Accept-Language header
- Custom fingerprint header (if provided)
```
Mismatch triggers session invalidation and re-authentication.

### IP Binding
Configurable via `app.security.ip-binding-mode`:
| Mode | Behavior |
|------|----------|
| `STRICT` | Exact IP match required |
| `SUBNET` | Same /24 subnet required |
| `DISABLED` | No IP validation |

### Session Rotation
Session ID rotated on security events:
- After successful authentication
- On privilege escalation
- Periodically (configurable interval)

### Concurrent Session Limits
Configurable maximum concurrent sessions per user:
- New login can invalidate oldest session
- Or reject new login if limit reached

---

## API Security

### CSRF Protection
Cookie-to-Header pattern:
1. Server sets CSRF token in cookie
2. Client reads cookie and sends in `X-CSRF-TOKEN` header
3. Server validates header matches cookie

Configured in `SecurityWebFilterChain`:
```java
.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
```

### Content Security Policy (CSP)
Headers configured via `SecurityConfigProperties`:
```
Content-Security-Policy:
  default-src 'self';
  script-src 'self';
  style-src 'self' 'unsafe-inline';
  img-src 'self' data:;
  connect-src 'self' https://api.*;
  frame-ancestors 'none';
```

### Rate Limiting
Per-client-IP rate limiting via `RateLimitingFilter`:
- Configurable requests per window
- Sliding window algorithm
- 429 Too Many Requests on limit exceeded
- IP extraction via `ClientIpExtractor` (handles X-Forwarded-For)

### Input Validation
- Jakarta Bean Validation on DTOs (`@Valid`, `@NotBlank`, etc.)
- Path parameter validation
- Request body size limits
- Content-Type validation

---

## Data Security

### PII Logging Sanitization
All PII sanitized before logging via `StringSanitizer`:
```java
log.debug("Fetching user info for hsidUuid: {}",
    StringSanitizer.forLog(hsidUuid));
// Output: "Fetching user info for hsidUuid: abc1...ef23"
```

Methods:
- `forLog(String)` - Truncates/masks for logging
- `sanitizeEmail(String)` - Masks email addresses
- `sanitizeName(String)` - Partial masking of names

### Secure Cache Key Generation
`CacheKeyUtils` generates consistent, secure cache keys:
```java
// Prevents key collision and injection
String key = CacheKeyUtils.forSession(sessionId);
String key = CacheKeyUtils.forUserInfo(hsidUuid);
```

### JSON Injection Prevention
`FilterResponseUtils` uses Jackson `ObjectMapper` for all JSON responses:
```java
// Safe - uses ObjectMapper
FilterResponseUtils.unauthorized(exchange, "INVALID_TOKEN", message, mapper);

// Unsafe - vulnerable to injection (NOT USED)
String.format("{\"error\": \"%s\"}", userInput);
```

### Error Response Security
Error responses:
- Never expose stack traces
- Use generic error codes
- Log details server-side only
- Consistent format via `FilterResponseUtils`

---

## Network Security

### HTTPS Only
- All traffic over TLS 1.2+
- HTTP redirects to HTTPS
- HSTS headers enabled

### Trusted Proxy Configuration
For accurate client IP detection:
```yaml
app:
  security:
    trusted-proxies:
      - "10.0.0.0/8"
      - "172.16.0.0/12"
```

### External API Security
- mTLS for external API connections
- Client certificates managed via KeyStore
- Connection timeouts prevent resource exhaustion
- Retry with backoff prevents cascade failures

---

## Security Headers

Standard security headers applied:
```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: geolocation=(), camera=(), microphone=()
```

---

## Security Utilities

### FilterResponseUtils
Centralized error response handling:
- `unauthorized()` - 401 responses
- `forbidden()` - 403 responses
- `error()` - Generic error responses

### ClientIpExtractor
Secure client IP extraction:
- Validates X-Forwarded-For against trusted proxies
- Prevents IP spoofing
- Falls back to remote address

### SessionCookieUtils
Secure cookie creation:
- Enforces security attributes
- Consistent cookie naming
- Proper expiration handling

---

## Security Monitoring

### Audit Logging
Security events logged for audit:
- Authentication success/failure
- Authorization denials
- Session creation/invalidation
- Rate limit violations

### Alerting
Configurable thresholds for:
- Failed authentication attempts
- Rate limit violations
- Session anomalies
- External API failures
