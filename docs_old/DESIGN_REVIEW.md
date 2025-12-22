# Design Review & Security Audit

**Review Date:** December 2025
**Scope:** Architecture, Dependencies, Zero Trust Compliance, Config-over-Code

---

## Executive Summary

| Category | Status | Critical Issues |
|----------|--------|-----------------|
| Frontend Dependencies | ⚠️ Warning | 1 moderate vulnerability |
| Backend Dependencies | ✅ OK | Requires Redis/Valkey 8.2.2+ |
| Zero Trust Compliance | ❌ Gaps | 6 missing controls |
| Config-over-Code | ⚠️ Partial | 4 improvements needed |

---

## 1. Vulnerability Assessment

### 1.1 Frontend Vulnerabilities

#### Current Issues Found
```
npm audit report:

@nxrocks/nx-spring-boot  >=9.0.0
  Severity: moderate
  Via: @nxrocks/common-jvm → xmlbuilder2 → js-yaml
  CVE: GHSA-mh29-5h37-fv8m (Prototype Pollution)
  Fix: Downgrade to @nxrocks/nx-spring-boot@8.1.0
```

#### Recommended Versions (Verified Stable)

| Package | Current | Recommended | Notes |
|---------|---------|-------------|-------|
| `react` | 19.2.3 | 19.2.3 ✅ | Latest stable |
| `react-dom` | 19.2.3 | 19.2.3 ✅ | Latest stable |
| `vite` | 7.2.7 | 7.2.7 ✅ | Latest stable |
| `zustand` | - | 5.0.9 ✅ | Add - latest stable |
| `@tanstack/react-query` | - | 5.90.12 ✅ | Add - explicit React 19 support |
| `react-router-dom` | 6.29.0 | 6.29.0 ✅ | Keep v6 (v7 optional, no security benefit) |
| `@nxrocks/nx-spring-boot` | 11.0.1 | 8.1.0 ⚠️ | Downgrade to fix vulnerability |

**Action Required:**
```bash
# Fix vulnerability
npm install @nxrocks/nx-spring-boot@8.1.0 --save-dev

# Add new dependencies
npm install zustand@5.0.9 @tanstack/react-query@5.90.12
```

### 1.2 Backend Vulnerabilities

#### Critical: Redis/Valkey Server (CVE-2025-49844)

| CVE | Severity | CVSS | Description |
|-----|----------|------|-------------|
| CVE-2025-49844 | **CRITICAL** | 10.0 | RCE via Lua scripts (RediShell) |
| CVE-2025-21605 | HIGH | 7.5 | DoS via output buffer overflow |
| CVE-2025-32023 | HIGH | 8.1 | HyperLogLog RCE |

**Required Action:**
```yaml
# ElastiCache/Valkey must be version 8.2.2+
# AWS ElastiCache: Ensure engine version is patched
infrastructure:
  valkey:
    min-version: "8.2.2"  # Fixes CVE-2025-49844
```

#### Spring Boot 4.0 Dependencies (Verified)

| Dependency | Version | Status |
|------------|---------|--------|
| Spring Boot | 4.0.0 | ✅ Latest (Nov 2025) |
| Spring Security | 7.0.0 | ✅ Via Boot 4.0 |
| Spring Session | 4.0.0 | ✅ Via Boot 4.0 |
| Lettuce | 6.5.x | ✅ No known CVEs |
| Spring Framework | 7.0.1 | ✅ Via Boot 4.0 |

**Note:** Spring Boot 4.0.0 includes fixes for CVE-2025-41248, CVE-2025-41249.

---

## 2. Zero Trust Compliance Gaps

### Current Design vs Zero Trust Principles

| Zero Trust Principle | Current Design | Gap | Severity |
|---------------------|----------------|-----|----------|
| Never trust, always verify | ✅ Session validation | - | - |
| Continuous verification | ❌ Only at request start | Missing mid-session checks | HIGH |
| Least privilege | ⚠️ Persona-based | Missing fine-grained ABAC | MEDIUM |
| Assume breach | ❌ No anomaly detection | Missing behavioral analysis | HIGH |
| Device validation | ❌ Not implemented | No device posture check | HIGH |
| Micro-segmentation | ⚠️ Partial | API-level only | MEDIUM |
| Re-authentication | ❌ Not implemented | No step-up auth | HIGH |
| Audit everything | ❌ Not designed | Missing audit trail | HIGH |

### Required Zero Trust Enhancements

#### 2.1 Continuous Verification (CRITICAL)
```yaml
# Add to application.yml
zero-trust:
  continuous-verification:
    enabled: true
    checks:
      # Re-validate every N requests or time interval
      interval-requests: 10
      interval-seconds: 300

      # Risk signals to monitor
      signals:
        ip-change: block              # Block if IP changes mid-session
        ua-change: re-authenticate    # Require re-auth if UA changes
        geo-velocity: alert           # Alert if impossible travel
        time-anomaly: challenge       # Challenge if unusual access time
```

#### 2.2 Device Validation
```yaml
zero-trust:
  device:
    enabled: true
    validation:
      # Collect device fingerprint
      fingerprint-headers:
        - Sec-CH-UA
        - Sec-CH-UA-Platform
        - Sec-CH-UA-Mobile
      # Bind session to device
      bind-to-session: true
      # Trust score threshold
      min-trust-score: 0.7
```

#### 2.3 Step-Up Authentication
```yaml
zero-trust:
  step-up-auth:
    enabled: true
    triggers:
      # Sensitive operations require re-auth
      - pattern: "/api/user/profile"
        methods: [PUT, DELETE]
        max-age-seconds: 300          # Re-auth if last auth > 5 min
      - pattern: "/api/mfe/profile/**"
        methods: [PUT, DELETE]
        max-age-seconds: 300
```

#### 2.4 Behavioral Anomaly Detection
```yaml
zero-trust:
  anomaly-detection:
    enabled: true
    rules:
      # Unusual data access volume
      - name: data-exfiltration
        condition: "request_count > 100 per minute"
        action: block

      # Unusual time access
      - name: off-hours-access
        condition: "hour < 6 or hour > 22"
        action: challenge
        personas: [individual, parent]  # Not for agents
```

#### 2.5 Comprehensive Audit Logging
```yaml
zero-trust:
  audit:
    enabled: true
    log-all-requests: true
    sensitive-fields-mask: [ssn, dob, account_number]
    retention-days: 90
    destinations:
      - type: elasticsearch
        index: "audit-logs"
      - type: cloudwatch
        log-group: "/bff/audit"
```

---

## 3. Config-over-Code Improvements

### Current State Analysis

| Component | Current | Improvement |
|-----------|---------|-------------|
| Security rules | Hardcoded paths | Externalize to YAML |
| Rate limiting | Not implemented | Add config-driven limits |
| CORS | Hardcoded | Externalize origins |
| Error messages | Hardcoded | Externalize to messages.yml |
| Feature flags | None | Add toggle config |

### 3.1 Security Path Configuration (Externalize)

**Before (Hardcoded):**
```java
.pathMatchers("/", "/api/auth/**", "/actuator/health").permitAll()
.pathMatchers("/api/mfe/**").access(proxyAuthFilter.authManager())
.anyExchange().authenticated()
```

**After (Config-driven):**
```yaml
# security-rules.yml
security:
  paths:
    public:
      - pattern: "/"
      - pattern: "/api/auth/**"
      - pattern: "/actuator/health"
      - pattern: "/actuator/info"

    proxy-auth:  # OAuth2 CC
      - pattern: "/api/mfe/**"
        required-scopes: ["mfe:read"]

    session-auth:  # HSID session
      - pattern: "/api/user/**"
      - pattern: "/api/dashboard/**"

    admin:
      - pattern: "/api/admin/**"
        required-roles: [ADMIN]
```

```java
// SecurityConfig.java - reads from config
@Bean
public SecurityWebFilterChain securityFilterChain(
        ServerHttpSecurity http,
        SecurityPathsProperties pathsConfig) {

    var auth = http.authorizeExchange();

    pathsConfig.getPublic().forEach(p ->
        auth.pathMatchers(p.getPattern()).permitAll());

    pathsConfig.getProxyAuth().forEach(p ->
        auth.pathMatchers(p.getPattern()).access(proxyAuthManager(p)));

    pathsConfig.getSessionAuth().forEach(p ->
        auth.pathMatchers(p.getPattern()).authenticated());

    // ... rest via config
}
```

### 3.2 Rate Limiting Configuration

```yaml
rate-limiting:
  enabled: true
  default:
    requests-per-second: 10
    burst-capacity: 20

  rules:
    # By path
    - pattern: "/api/auth/login"
      requests-per-minute: 5
      by: ip

    # By persona
    - personas: [individual, parent]
      requests-per-second: 5

    - personas: [agent, case_worker]
      requests-per-second: 50

    # By partner
    - partner-ids: [partner-001]
      requests-per-second: 100
```

### 3.3 CORS Configuration (Externalize)

```yaml
cors:
  enabled: true
  mappings:
    # Internal apps (with credentials)
    - patterns: ["/api/**"]
      origins:
        - "${WEB_CL_ORIGIN:https://web-cl.example.com}"
        - "${WEB_HS_ORIGIN:https://web-hs.example.com}"
      allow-credentials: true
      allowed-methods: [GET, POST, PUT, DELETE]
      max-age: 3600

    # MFE endpoints (no credentials - token auth)
    - patterns: ["/api/mfe/**"]
      origins: ${MFE_ALLOWED_ORIGINS:}  # From domain registry
      allow-credentials: false
      allowed-methods: [GET, POST]
```

### 3.4 Feature Flags

```yaml
features:
  # Zero trust features (gradual rollout)
  continuous-verification: ${FEATURE_CONTINUOUS_VERIFY:false}
  device-validation: ${FEATURE_DEVICE_VALIDATION:false}
  step-up-auth: ${FEATURE_STEP_UP_AUTH:false}
  anomaly-detection: ${FEATURE_ANOMALY_DETECTION:false}

  # MFE features
  mfe-summary-v2: ${FEATURE_MFE_SUMMARY_V2:false}
  mfe-profile-enabled: ${FEATURE_MFE_PROFILE:true}
```

---

## 4. Updated Dependency Matrix

### Frontend (package.json)

```json
{
  "dependencies": {
    "react": "^19.2.3",
    "react-dom": "^19.2.3",
    "react-router-dom": "^6.29.0",
    "zustand": "^5.0.9",
    "@tanstack/react-query": "^5.90.12"
  },
  "devDependencies": {
    "@nxrocks/nx-spring-boot": "^8.1.0",
    "@tanstack/react-query-devtools": "^5.90.12"
  }
}
```

### Backend (pom.xml)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.8</version>
</parent>

<dependencies>
    <!-- Core -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>

    <!-- Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>

    <!-- Session -->
    <dependency>
        <groupId>org.springframework.session</groupId>
        <artifactId>spring-session-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
    </dependency>

    <!-- Observability -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-otel</artifactId>
    </dependency>

    <!-- Rate Limiting -->
    <dependency>
        <groupId>com.bucket4j</groupId>
        <artifactId>bucket4j-core</artifactId>
        <version>8.14.0</version>
    </dependency>
</dependencies>
```

### Infrastructure Requirements

```yaml
infrastructure:
  valkey:
    version: ">=8.2.2"              # CRITICAL: CVE-2025-49844 fix
    cluster-mode: true
    encryption-at-rest: true
    encryption-in-transit: true

  elasticache:
    engine: valkey
    engine-version: "8.2"           # Minimum
    node-type: cache.r7g.large
    auth-token: true                # Require AUTH
    acl-enabled: true               # Use ACLs
```

---

## 5. Revised Architecture (Zero Trust)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              ZERO TRUST LAYER                                    │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                         WAF / API Gateway                                   │ │
│  │  • Rate limiting (config-driven)                                           │ │
│  │  • DDoS protection                                                         │ │
│  │  • Request validation                                                      │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                 BFF LAYER                                        │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                    CONTINUOUS VERIFICATION CHAIN                            │ │
│  │                                                                             │ │
│  │  Request → [Rate Limit] → [Auth] → [Device Check] → [Anomaly] → [Audit]   │ │
│  │              │              │           │              │           │       │ │
│  │              │              │           │              │           ▼       │ │
│  │              │              │           │              │      Audit Log    │ │
│  │              │              │           │              │                   │ │
│  │              │              │           │         [Risk Score]             │ │
│  │              │              │           │              │                   │ │
│  │              │              │           │     ┌────────┴────────┐          │ │
│  │              │              │           │     │                 │          │ │
│  │              │              │           │  Low Risk         High Risk      │ │
│  │              │              │           │     │                 │          │ │
│  │              │              │           │  Continue      Step-Up Auth      │ │
│  │              │              │           │                                  │ │
│  └──────────────┴──────────────┴───────────┴──────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Action Items

### Immediate (P0)
- [ ] Downgrade `@nxrocks/nx-spring-boot` to 8.1.0
- [ ] Ensure Valkey/ElastiCache is version 8.2.2+
- [ ] Add comprehensive audit logging

### Short-term (P1)
- [ ] Implement rate limiting (config-driven)
- [ ] Externalize security paths to YAML
- [ ] Add device fingerprinting

### Medium-term (P2)
- [ ] Implement continuous verification
- [ ] Add step-up authentication
- [ ] Implement behavioral anomaly detection

### Long-term (P3)
- [ ] Add ML-based anomaly detection
- [ ] Implement ABAC (Attribute-Based Access Control)
- [ ] Add real-time threat intelligence feeds

---

## 7. References

### Security Advisories
- [Spring Security Advisories](https://spring.io/security/)
- [Redis CVE-2025-49844 (RediShell)](https://redis.io/blog/security-advisory-cve-2025-49844/)
- [NIST SP 800-207 Zero Trust Architecture](https://nvlpubs.nist.gov/nistpubs/specialpublications/NIST.SP.800-207.pdf)

### Version Sources
- [Spring Boot 4.0.0 Release](https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now/)
- [React Router v6 → v7 Migration](https://reactrouter.com/upgrading/v6)
- [Zustand GitHub](https://github.com/pmndrs/zustand)
- [TanStack Query](https://tanstack.com/query/latest)
