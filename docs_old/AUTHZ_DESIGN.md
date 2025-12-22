# Authorization (AuthZ) Layer Design

## Overview

| Layer | Responsibility | Technology |
|-------|---------------|------------|
| **AuthN** | Identity verification | HSID (OIDC PKCE) / OAuth2 Proxy |
| **AuthZ Data** | Attribute source | Backend API (HSID) / Headers (Proxy) |
| **AuthZ Cache** | Session-scoped attributes | Valkey/Redis |
| **AuthZ Engine** | Policy evaluation | BFF (ABAC) |

---

## 1. Authorization Model: ABAC (Attribute-Based Access Control)

### Why ABAC?

ABAC provides a unified, flexible authorization model that handles both authentication flows:

1. **Unified model** - Single policy engine for HSID and Proxy users
2. **Attribute-driven** - Decisions based on subject, resource, action, environment
3. **Extensible** - Easy to add new policies without code changes
4. **Auditable** - Clear policy decisions with reasons

### ABAC Components

```
┌─────────────────────────────────────────────────────────────────┐
│                     ABAC Policy Engine                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Subject    │  │   Resource   │  │    Action    │          │
│  │  Attributes  │  │  Attributes  │  │              │          │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
│         │                 │                 │                   │
│         └────────────┬────┴────────────────┘                   │
│                      ▼                                          │
│              ┌──────────────┐                                   │
│              │   Policies   │                                   │
│              │  (Rules)     │                                   │
│              └──────┬───────┘                                   │
│                     ▼                                           │
│              ┌──────────────┐                                   │
│              │   Decision   │                                   │
│              │ ALLOW/DENY   │                                   │
│              └──────────────┘                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Attribute Models

### 2.1 Subject Attributes

```java
record SubjectAttributes(
    // Common
    AuthType authType,      // HSID or PROXY
    String userId,
    String persona,         // parent, individual, agent, case_worker, config

    // HSID-specific
    PermissionSet permissions,  // DAA, RPR, ROI per dependent

    // Proxy-specific
    String partnerId,
    String memberId,
    String operatorId,
    String operatorName
)
```

| Auth Type | Attribute Source |
|-----------|-----------------|
| HSID | Session (Redis) + Permissions API |
| Proxy | Request Headers |

### 2.2 Resource Attributes

```java
record ResourceAttributes(
    ResourceType type,      // DEPENDENT, MEMBER, PROFILE, MEDICAL_RECORD
    String id,
    Sensitivity sensitivity, // NORMAL, SENSITIVE
    String ownerId,
    String partnerId
)
```

### 2.3 Actions

```java
enum Action {
    VIEW,            // Basic access
    VIEW_SENSITIVE,  // Sensitive data (medical, financial)
    EDIT,
    DELETE,
    LIST
}
```

---

## 3. Policy Rules

### 3.1 HSID Policies

#### Policy: HSID_VIEW_DEPENDENT
```
ALLOW VIEW dependent WHERE
  subject.authType = HSID AND
  subject.permissions[resource.id] CONTAINS DAA AND
  subject.permissions[resource.id] CONTAINS RPR AND
  resource.sensitivity = NORMAL
```

#### Policy: HSID_VIEW_SENSITIVE
```
ALLOW VIEW_SENSITIVE dependent WHERE
  subject.authType = HSID AND
  subject.permissions[resource.id] CONTAINS DAA AND
  subject.permissions[resource.id] CONTAINS RPR AND
  subject.permissions[resource.id] CONTAINS ROI
```

### 3.2 Proxy Policies

#### Policy: PROXY_VIEW_MEMBER
```
ALLOW VIEW member WHERE
  subject.authType = PROXY AND
  (subject.persona = "config" OR subject.memberId = resource.id) AND
  resource.sensitivity = NORMAL
```

#### Policy: PROXY_VIEW_SENSITIVE
```
ALLOW VIEW_SENSITIVE member WHERE
  subject.authType = PROXY AND
  subject.persona = "config"
```

### 3.3 Policy Summary Table

| Policy ID | Auth Type | Action | Required Attributes |
|-----------|-----------|--------|---------------------|
| HSID_VIEW_DEPENDENT | HSID | VIEW | DAA + RPR |
| HSID_VIEW_SENSITIVE | HSID | VIEW_SENSITIVE | DAA + RPR + ROI |
| PROXY_VIEW_MEMBER | PROXY | VIEW | config OR assigned member |
| PROXY_VIEW_SENSITIVE | PROXY | VIEW_SENSITIVE | config persona only |

---

## 4. Permission Types (HSID)

| Permission | Code | Description | Required For |
|------------|------|-------------|--------------|
| Delegate Access Authority | `DAA` | Legal authority to act on behalf | VIEW |
| Relying Party Representative | `RPR` | Registered as representative | VIEW |
| Release of Information | `ROI` | Consent for sensitive data | VIEW_SENSITIVE |

### Permission Scenarios

| Scenario | Permissions | Can View? | Can View Sensitive? |
|----------|-------------|-----------|---------------------|
| child1 | DAA, RPR | ✅ Yes | ❌ No |
| child2 | RPR only | ❌ No | ❌ No |
| child3 | DAA, RPR, ROI | ✅ Yes | ✅ Yes |
| child4 | DAA only | ❌ No | ❌ No |

---

## 5. Proxy Personas

| Persona | Access Level | Sensitive Data | Use Case |
|---------|-------------|----------------|----------|
| `agent` | Assigned member only | ❌ No | Call center agents |
| `case_worker` | Assigned member only | ❌ No | Case management |
| `config` | All members | ✅ Yes | Admin/configuration |

### Proxy Headers

```
X-Auth-Type: proxy
X-Persona: agent | case_worker | config
X-Partner-Id: partner123
X-Member-Id: member456
X-Operator-Id: op789
X-Operator-Name: John Doe
X-Correlation-Id: uuid
```

---

## 6. Implementation Architecture

### 6.1 Package Structure

```
com.example.bff/
├── authz/
│   ├── abac/
│   │   ├── model/
│   │   │   ├── SubjectAttributes.java
│   │   │   ├── ResourceAttributes.java
│   │   │   ├── Action.java
│   │   │   └── PolicyDecision.java
│   │   ├── policy/
│   │   │   ├── Policy.java              # Interface
│   │   │   ├── HsidViewDependentPolicy.java
│   │   │   ├── HsidViewSensitivePolicy.java
│   │   │   ├── ProxyViewMemberPolicy.java
│   │   │   └── ProxyViewSensitivePolicy.java
│   │   ├── engine/
│   │   │   └── AbacPolicyEngine.java    # Evaluates policies
│   │   └── service/
│   │       └── AbacAuthorizationService.java
│   ├── model/
│   │   ├── AuthType.java                # HSID | PROXY
│   │   ├── Permission.java              # DAA, RPR, ROI
│   │   ├── DependentAccess.java
│   │   └── PermissionSet.java
│   ├── filter/
│   │   └── AuthorizationFilter.java     # WebFilter using ABAC
│   ├── annotation/
│   │   └── RequiresPermission.java
│   ├── aspect/
│   │   └── AuthorizationAspect.java     # @RequiresPermission handler
│   ├── dto/
│   │   └── PermissionsApiResponse.java
│   └── service/
│       └── PermissionsFetchService.java # Backend API client
```

### 6.2 Authorization Flow

```
┌─────────┐     ┌─────────┐     ┌─────────┐     ┌─────────┐
│ Request │     │ AuthZ   │     │ ABAC    │     │ Policy  │
│         │────▶│ Filter  │────▶│ Service │────▶│ Engine  │
└─────────┘     └────┬────┘     └────┬────┘     └────┬────┘
                     │               │               │
                     │ 1. Extract    │               │
                     │    auth type  │               │
                     │               │               │
                     │ 2. Build      │               │
                     │    subject    │               │
                     │    attributes │               │
                     │               │               │
                     │ 3. Build      │               │
                     │    resource   │               │
                     │    attributes │               │
                     │               │               │
                     │               │ 4. Evaluate   │
                     │               │    policies   │
                     │               │               │
                     │               │◀──────────────│
                     │               │   Decision    │
                     │◀──────────────│               │
                     │   ALLOW/DENY  │               │
                     │               │               │
```

---

## 7. Configuration

```yaml
app:
  authz:
    enabled: true
    permissions-api:
      url: ${PERMISSIONS_API_URL}
      timeout: 5s
    cache:
      enabled: true
      ttl: 5m
```

---

## 8. Error Responses

### 8.1 Access Denied (HSID)

```json
{
  "error": "access_denied",
  "code": "HSID_VIEW_DEPENDENT",
  "policy": "HSID_VIEW_DEPENDENT",
  "message": "Missing permissions [DAA] for dependent child2",
  "missing": ["DAA"]
}
```

### 8.2 Access Denied (Proxy)

```json
{
  "error": "access_denied",
  "code": "PROXY_VIEW_MEMBER",
  "policy": "PROXY_VIEW_MEMBER",
  "message": "Persona 'agent' is not assigned to member member456",
  "missing": ["memberId"]
}
```

---

## 9. Adding New Policies

To add a new policy:

1. Create a class implementing `Policy` interface
2. Implement `getPolicyId()`, `getDescription()`, `appliesTo()`, `evaluate()`
3. Register in `AbacPolicyEngine` constructor

Example:
```java
public class TimeBasedAccessPolicy implements Policy {
    @Override
    public String getPolicyId() { return "TIME_BASED_ACCESS"; }

    @Override
    public PolicyDecision evaluate(SubjectAttributes subject,
                                    ResourceAttributes resource,
                                    Action action) {
        LocalTime now = LocalTime.now();
        if (now.isBefore(LocalTime.of(9, 0)) || now.isAfter(LocalTime.of(17, 0))) {
            return PolicyDecision.deny(getPolicyId(), "Access only allowed 9am-5pm");
        }
        return PolicyDecision.notApplicable(getPolicyId());
    }
}
```

---

## 10. Summary

| Aspect | Implementation |
|--------|----------------|
| **Model** | ABAC (Attribute-Based Access Control) |
| **Engine** | AbacPolicyEngine with pluggable policies |
| **Attributes** | Subject, Resource, Action |
| **HSID Permissions** | DAA, RPR (required), ROI (sensitive) |
| **Proxy Personas** | agent, case_worker, config |
| **Enforcement** | Filter + Annotation + Programmatic |
| **Storage** | Redis session (HSID), Headers (Proxy) |
