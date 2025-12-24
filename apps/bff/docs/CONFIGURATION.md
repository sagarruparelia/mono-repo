# Configuration Reference

## Overview

The BFF application uses Spring Boot's externalized configuration. Properties are organized under the `bff` prefix and can be overridden via environment variables.

## Configuration Structure

```yaml
bff:
  session:       # Session and cookie configuration
  cache:         # API response caching
  client:        # External service endpoints
  eligibility:   # Eligibility filtering rules

spring:
  security:
    oauth2:      # OIDC/OAuth2 provider configuration
  data:
    redis:       # Redis connection settings
```

## Session Configuration

| Property | Default | Environment Variable | Description |
|----------|---------|---------------------|-------------|
| `bff.session.store` | `in-memory` | `BFF_SESSION_STORE` | Storage backend: `in-memory` or `redis` |
| `bff.session.timeout-minutes` | `30` | `BFF_SESSION_TIMEOUT` | Session expiration time |
| `bff.session.cookie-name` | `BFF_SESSION` | - | Session cookie name |
| `bff.session.cookie-domain` | `abc.com` | `BFF_COOKIE_DOMAIN` | Cookie domain restriction |
| `bff.session.cookie-secure` | `true` | - | HTTPS-only cookie |
| `bff.session.cookie-http-only` | `true` | - | Prevent JavaScript access |
| `bff.session.cookie-same-site` | `Strict` | - | SameSite attribute |
| `bff.session.allowed-origins` | `https://abc.com,https://www.abc.com` | `BFF_ALLOWED_ORIGINS` | Allowed request origins (comma-separated) |
| `bff.session.csrf-cookie-name` | `XSRF-TOKEN` | `BFF_CSRF_COOKIE` | CSRF token cookie name |
| `bff.session.csrf-header-name` | `X-CSRF-TOKEN` | `BFF_CSRF_HEADER` | CSRF token header name |
| `bff.session.session-binding-enabled` | `true` | `BFF_SESSION_BINDING` | Enable session binding validation |
| `bff.session.strict-session-binding` | `true` | `BFF_STRICT_BINDING` | Strict mode rejects on mismatch |

### Session Storage Options

**In-Memory (Development)**
```yaml
bff:
  session:
    store: in-memory
```
- Sessions stored in ConcurrentHashMap
- Lost on application restart
- Suitable for single-instance development

**Redis (Production)**
```yaml
bff:
  session:
    store: redis

spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
```
- Sessions stored in Redis
- Supports horizontal scaling
- Sessions persist across restarts

## Cache Configuration

| Property | Default | Environment Variable | Description |
|----------|---------|---------------------|-------------|
| `bff.cache.store` | `in-memory` | `BFF_CACHE_STORE` | Cache backend: `in-memory` or `redis` |
| `bff.cache.ttl-minutes` | `30` | `BFF_CACHE_TTL` | Cache entry TTL |

### Cache Storage Options

**In-Memory**
```yaml
bff:
  cache:
    store: in-memory
    ttl-minutes: 30
```

**Redis**
```yaml
bff:
  cache:
    store: redis
    ttl-minutes: 30
```

## External Client Configuration

| Property | Default | Environment Variable | Description |
|----------|---------|---------------------|-------------|
| `bff.client.user-service.base-url` | - | `USER_SERVICE_URL` | User service REST API base URL |
| `bff.client.delegate-graph.base-url` | - | `DELEGATE_GRAPH_URL` | Delegate GraphQL API base URL |
| `bff.client.eligibility-graph.base-url` | - | `ELIGIBILITY_GRAPH_URL` | Eligibility GraphQL API base URL |

### Example Configuration

```yaml
bff:
  client:
    user-service:
      base-url: ${USER_SERVICE_URL:https://api.hcp.com/user-service}
    delegate-graph:
      base-url: ${DELEGATE_GRAPH_URL:https://api.hcp.com/delegate/graphql}
    eligibility-graph:
      base-url: ${ELIGIBILITY_GRAPH_URL:https://api.hcp.com/eligibility/graphql}
```

## Eligibility Configuration

| Property | Default | Environment Variable | Description |
|----------|---------|---------------------|-------------|
| `bff.eligibility.eligible-plan-codes` | `[]` | `ELIGIBLE_PLAN_CODES` | Plan codes for eligibility filtering |

### Example

```yaml
bff:
  eligibility:
    eligible-plan-codes: ${ELIGIBLE_PLAN_CODES:PLAN001,PLAN002,PLAN003}
```

## OAuth2 Configuration

### HSID (Browser Authentication)

| Property | Environment Variable | Description |
|----------|---------------------|-------------|
| `spring.security.oauth2.client.registration.hsid.client-id` | `HSID_CLIENT_ID` | OIDC client ID |
| `spring.security.oauth2.client.registration.hsid.client-secret` | `HSID_CLIENT_SECRET` | OIDC client secret |

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          hsid:
            provider: hsid
            client-id: ${HSID_CLIENT_ID}
            client-secret: ${HSID_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/"
            scope: openid,profile,email
        provider:
          hsid:
            issuer-uri: https://nonprod.identity.healthsafe-id.com
            authorization-uri: https://nonprod.identity.healthsafe-id.com/oidc/authorize
            token-uri: https://nonprod.identity.healthsafe-id.com/oidc/token
            jwk-set-uri: https://nonprod.identity.healthsafe-id.com/oidc/jwks
            user-info-uri: https://nonprod.identity.healthsafe-id.com/oidc/userinfo
```

### HCP (Service-to-Service Authentication)

| Property | Environment Variable | Description |
|----------|---------------------|-------------|
| `spring.security.oauth2.client.registration.hcp.client-id` | `HCP_CLIENT_ID` | Client credentials ID |
| `spring.security.oauth2.client.registration.hcp.client-secret` | `HCP_CLIENT_SECRET` | Client credentials secret |

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          hcp:
            provider: hcp
            client-id: ${HCP_CLIENT_ID}
            client-secret: ${HCP_CLIENT_SECRET}
            authorization-grant-type: client_credentials
        provider:
          hcp:
            token-uri: ${HCP_TOKEN_URI:https://hcp.com/oauth2/token}
```

## Actuator Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when-authorized
```

| Endpoint | Path | Description |
|----------|------|-------------|
| Health | `/actuator/health` | Application health status |
| Info | `/actuator/info` | Application information |

## Logging Configuration

| Property | Default | Environment Variable | Description |
|----------|---------|---------------------|-------------|
| `logging.level.com.example.bff` | `INFO` | `LOG_LEVEL` | Application log level |
| `logging.level.org.springframework.security` | `INFO` | `SECURITY_LOG_LEVEL` | Security framework log level |

```yaml
logging:
  level:
    com.example.bff: ${LOG_LEVEL:INFO}
    org.springframework.security: ${SECURITY_LOG_LEVEL:INFO}
```

## Environment Variable Summary

### Required Variables

| Variable | Description |
|----------|-------------|
| `HSID_CLIENT_ID` | HSID OIDC client ID |
| `HSID_CLIENT_SECRET` | HSID OIDC client secret |
| `HCP_CLIENT_ID` | HCP service client ID |
| `HCP_CLIENT_SECRET` | HCP service client secret |
| `USER_SERVICE_URL` | User service API URL |
| `DELEGATE_GRAPH_URL` | Delegate GraphQL URL |
| `ELIGIBILITY_GRAPH_URL` | Eligibility GraphQL URL |

### Optional Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BFF_SESSION_STORE` | `in-memory` | Session storage type |
| `BFF_SESSION_TIMEOUT` | `30` | Session timeout (minutes) |
| `BFF_COOKIE_DOMAIN` | `abc.com` | Cookie domain |
| `BFF_ALLOWED_ORIGINS` | `https://abc.com,https://www.abc.com` | Allowed origins |
| `BFF_CACHE_STORE` | `in-memory` | Cache storage type |
| `BFF_CACHE_TTL` | `30` | Cache TTL (minutes) |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `LOG_LEVEL` | `INFO` | Application log level |
| `SECURITY_LOG_LEVEL` | `INFO` | Security log level |
| `BFF_SESSION_BINDING` | `true` | Enable session binding |
| `BFF_STRICT_BINDING` | `true` | Strict binding mode |

## Profile-Specific Configuration

### Development Profile

```yaml
# application-dev.yml
bff:
  session:
    store: in-memory
    cookie-domain: localhost
    cookie-secure: false  # Allow HTTP for local dev
    allowed-origins: http://localhost:3000,http://localhost:8080
    strict-session-binding: false  # Permissive in dev
  cache:
    store: in-memory

logging:
  level:
    com.example.bff: DEBUG
    org.springframework.security: DEBUG
```

### Production Profile

```yaml
# application-prod.yml
bff:
  session:
    store: redis
    cookie-domain: ${BFF_COOKIE_DOMAIN}
    cookie-secure: true
    allowed-origins: ${BFF_ALLOWED_ORIGINS}
    session-binding-enabled: true
    strict-session-binding: true  # Strict in prod
  cache:
    store: redis

logging:
  level:
    com.example.bff: INFO
    org.springframework.security: WARN
```
