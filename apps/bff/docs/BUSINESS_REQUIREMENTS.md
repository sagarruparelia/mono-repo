# Business Requirements

## Overview

The BFF (Backend for Frontend) provides a unified API gateway that supports two distinct client access patterns while maintaining strict security boundaries.

## User Personas

| Persona | Description | IDP Source |
|---------|-------------|------------|
| SELF | End user managing their own data | HSID |
| DELEGATE | Authorized representative acting on behalf of another member | HSID |
| CASE_WORKER | Healthcare case worker with administrative access | OHID |
| AGENT | Customer service agent | MSID |
| CONFIG_SPECIALIST | System configuration specialist | MSID |

## Access Patterns

### 1. Browser Access (End Users)

**Path:** `/api/v1/**`

- Users authenticate via HSID (HealthSafe-ID) OIDC
- Session-based authentication using secure cookies
- Supports SELF and DELEGATE personas only
- Origin validation ensures requests come from trusted domains

**Use Cases:**
- Member views their own health information (SELF)
- Authorized representative manages another member's account (DELEGATE)

### 2. Partner/MFE Access (Micro-Frontends)

**Path:** `/mfe/api/v1/**`

- Partners authenticate via mTLS (validated by upstream ALB)
- Header-based authentication without cookies
- Supports all persona types
- Enables embedded micro-frontends in partner applications

**Use Cases:**
- Partner health portal embeds member dashboard
- Case worker accesses member information
- Agent assists customer with account issues

## Delegate Authorization Rules

When a user operates as a DELEGATE, additional authorization is required:

| Delegate Type | Description | Authorization |
|---------------|-------------|---------------|
| RPR (Representative Payee) | Legally authorized representative | Full access to member data |
| DAA (Designated Authorized Agent) | Member-designated agent | Access per member authorization |
| ROI (Release of Information) | Limited information access | Read-only access to specific data |

### Delegate Validation

1. User must have active delegate relationship with target member
2. Enterprise ID in request must match delegated member
3. Delegate privileges must cover requested operation
4. Eligibility must be verified for delegated member

## Session Requirements

| Requirement | Value |
|-------------|-------|
| Session Duration | 30 minutes (configurable) |
| Cookie Security | HTTPS only, HttpOnly, SameSite=Strict |
| Domain Restriction | Configurable allowed domain |
| Origin Validation | Required for browser access |

## External Service Dependencies

| Service | Purpose |
|---------|---------|
| User Service | Member profile and preferences |
| Delegate Graph | Delegate relationship validation |
| Eligibility Graph | Member eligibility verification |
| HSID | Browser user authentication |
| HCP Token Service | External API authorization |

## Compliance Requirements

- All authentication tokens stored server-side only
- Cookies cannot be accessed by JavaScript (HttpOnly)
- Cross-site request forgery prevention (SameSite=Strict)
- Origin header validation for all browser requests
- Audit logging for security-relevant events
