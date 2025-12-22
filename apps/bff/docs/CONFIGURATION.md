# BFF Configuration

## Overview

This document describes all configuration properties available in the BFF application, organized by functional area.

---

## Property Classes

| Class | Prefix | Purpose |
|-------|--------|---------|
| `SessionProperties` | `app.session` | Session management |
| `SecurityConfigProperties` | `app.security` | Security settings |
| `ExternalApiProperties` | `app.external-api` | External API endpoints |
| `EcdhApiProperties` | `app.ecdh-api` | Health data API |
| `CacheProperties` | `app.cache` | Cache configuration |
| `RateLimitProperties` | `app.rate-limit` | Rate limiting |
| `HealthDataProperties` | `app.health-data` | Health data caching |
| `HsidAuthProperties` | `app.auth.hsid` | HSID OAuth2 settings |

---

## Session Configuration

**Prefix:** `app.session`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `timeout` | Duration | `30m` | Session inactivity timeout |
| `cookie.name` | String | `BFF_SESSION` | Session cookie name |
| `cookie.domain` | String | - | Cookie domain (optional) |
| `cookie.secure` | Boolean | `true` | Require HTTPS |
| `cookie.http-only` | Boolean | `true` | Prevent JS access |
| `cookie.same-site` | String | `Strict` | SameSite policy |
| `max-concurrent` | Integer | `5` | Max sessions per user |

Example:
```yaml
app:
  session:
    timeout: 30m
    cookie:
      name: BFF_SESSION
      domain: .example.com
      secure: true
      http-only: true
      same-site: Strict
    max-concurrent: 5
```

---

## Security Configuration

**Prefix:** `app.security`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ip-binding-mode` | Enum | `SUBNET` | IP validation strictness |
| `csrf.enabled` | Boolean | `true` | Enable CSRF protection |
| `csrf.cookie-name` | String | `XSRF-TOKEN` | CSRF cookie name |
| `csrf.header-name` | String | `X-CSRF-TOKEN` | CSRF header name |
| `csp.enabled` | Boolean | `true` | Enable CSP headers |
| `csp.policy` | String | - | CSP policy string |
| `trusted-proxies` | List | `[]` | Trusted proxy CIDRs |

IP Binding Modes:
- `STRICT` - Exact IP match required
- `SUBNET` - Same /24 subnet required
- `DISABLED` - No IP validation

Example:
```yaml
app:
  security:
    ip-binding-mode: SUBNET
    csrf:
      enabled: true
      cookie-name: XSRF-TOKEN
      header-name: X-CSRF-TOKEN
    csp:
      enabled: true
      policy: "default-src 'self'; script-src 'self'"
    trusted-proxies:
      - "10.0.0.0/8"
      - "172.16.0.0/12"
```

---

## External API Configuration

**Prefix:** `app.external-api`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `base-url` | String | - | Base URL (HTTPS required) |
| `user-service.path` | String | `/api/identity/user/individual/v1/read` | User service endpoint |
| `user-service.timeout` | Duration | `5s` | Request timeout |
| `eligibility.path` | String | `/graph/1.0.0` | Eligibility endpoint |
| `eligibility.timeout` | Duration | `5s` | Request timeout |
| `permissions.path` | String | `/api/consumer/prefs/del-gr/1.0.0` | Permissions endpoint |
| `permissions.timeout` | Duration | `5s` | Request timeout |
| `retry.max-attempts` | Integer | `3` | Max retry attempts |
| `retry.initial-backoff` | Duration | `100ms` | Initial backoff |
| `retry.max-backoff` | Duration | `1s` | Maximum backoff |

Example:
```yaml
app:
  external-api:
    base-url: https://api.example.com
    user-service:
      path: /api/identity/user/individual/v1/read
      timeout: 5s
    eligibility:
      path: /graph/1.0.0
      timeout: 5s
    permissions:
      path: /api/consumer/prefs/del-gr/1.0.0
      timeout: 5s
    retry:
      max-attempts: 3
      initial-backoff: 100ms
      max-backoff: 1s
```

---

## ECDH API Configuration

**Prefix:** `app.ecdh-api`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `base-url` | String | - | ECDH API base URL |
| `graph-path` | String | `/graph/1.0.0` | GraphQL endpoint path |
| `timeout` | Duration | `10s` | Request timeout |
| `retry.max-attempts` | Integer | `3` | Max retry attempts |
| `retry.initial-backoff` | Duration | `100ms` | Initial backoff |
| `retry.max-backoff` | Duration | `2s` | Maximum backoff |

Example:
```yaml
app:
  ecdh-api:
    base-url: https://api.abc.com
    graph-path: /graph/1.0.0
    timeout: 10s
    retry:
      max-attempts: 3
      initial-backoff: 100ms
      max-backoff: 2s
```

---

## Cache Configuration

**Prefix:** `app.cache`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `type` | Enum | `memory` | Cache implementation |
| `user-info-ttl` | Duration | `15m` | User info cache TTL |
| `eligibility-ttl` | Duration | `15m` | Eligibility cache TTL |
| `permissions-ttl` | Duration | `15m` | Permissions cache TTL |

Cache Types:
- `redis` - Distributed Redis cache (production)
- `memory` - Local Caffeine cache (development)

Example:
```yaml
app:
  cache:
    type: redis
    user-info-ttl: 15m
    eligibility-ttl: 15m
    permissions-ttl: 15m
```

---

## Rate Limiting Configuration

**Prefix:** `app.rate-limit`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | Boolean | `true` | Enable rate limiting |
| `requests-per-second` | Integer | `100` | Max requests per second |
| `burst-capacity` | Integer | `200` | Burst capacity |
| `key-resolver` | String | `ip` | Rate limit key (ip, user) |

Example:
```yaml
app:
  rate-limit:
    enabled: true
    requests-per-second: 100
    burst-capacity: 200
    key-resolver: ip
```

---

## Health Data Configuration

**Prefix:** `app.health-data`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `cache-ttl` | Duration | `1h` | MongoDB cache TTL |
| `max-page-size` | Integer | `100` | Max items per page |

Example:
```yaml
app:
  health-data:
    cache-ttl: 1h
    max-page-size: 100
```

---

## HSID Authentication Configuration

**Prefix:** `app.auth.hsid`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `logout-uri` | String | - | HSID logout endpoint |
| `post-logout-redirect-uri` | String | - | Redirect after logout |

Example:
```yaml
app:
  auth:
    hsid:
      logout-uri: https://hsid.example.com/logout
      post-logout-redirect-uri: https://app.example.com/
```

---

## Conditional Bean Loading

The BFF uses `@ConditionalOnProperty` for flexible deployment configurations:

### Redis Mode (Production)
```yaml
app:
  cache:
    type: redis

spring:
  data:
    redis:
      host: redis.example.com
      port: 6379
```

Beans activated:
- `RedisSessionService`
- `RedisIdentityCacheService`
- `IdentityCacheEventPublisher`
- `IdentityCacheEventListener`

### Memory Mode (Development)
```yaml
app:
  cache:
    type: memory
```

Beans activated:
- `InMemorySessionService`
- `InMemoryIdentityCacheService`

---

## Environment Profiles

### Base Configuration
`application.yml` - Shared settings for all environments

### Local Development
`application-local.yml`
```yaml
app:
  cache:
    type: memory
  security:
    ip-binding-mode: DISABLED
  rate-limit:
    enabled: false

logging:
  level:
    com.example.bff: DEBUG
```

### Docker Compose
`application-docker.yml`
```yaml
app:
  cache:
    type: redis

spring:
  data:
    redis:
      host: redis
      port: 6379
    mongodb:
      uri: mongodb://mongodb:27017/bff
```

### Test Configuration
`application-test.yml`
```yaml
app:
  cache:
    type: memory
  external-api:
    base-url: http://wiremock:8080
```

---

## Spring Configuration Reference

### Spring Security OAuth2
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          hsid:
            client-id: ${HSID_CLIENT_ID}
            client-secret: ${HSID_CLIENT_SECRET}
            scope: openid,profile,email
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
        provider:
          hsid:
            authorization-uri: https://hsid.example.com/authorize
            token-uri: https://hsid.example.com/token
            user-info-uri: https://hsid.example.com/userinfo
            jwk-set-uri: https://hsid.example.com/.well-known/jwks.json
```

### Redis Configuration
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}
      timeout: 2s
      lettuce:
        pool:
          max-active: 10
          max-idle: 5
```

### MongoDB Configuration
```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/bff
      auto-index-creation: true
```

---

## Environment Variables

Sensitive configuration should use environment variables:

| Variable | Property | Description |
|----------|----------|-------------|
| `HSID_CLIENT_ID` | OAuth2 client ID | HSID client identifier |
| `HSID_CLIENT_SECRET` | OAuth2 client secret | HSID client secret |
| `REDIS_PASSWORD` | Redis password | Redis authentication |
| `MONGODB_URI` | MongoDB connection | Full connection string |
| `EXTERNAL_API_BASE_URL` | External API URL | Identity services URL |
| `ECDH_API_BASE_URL` | ECDH API URL | Health data API URL |
