# API Documentation

## Overview

The BFF exposes REST APIs through two access patterns:

| Path Pattern | Access Method | Authentication |
|--------------|---------------|----------------|
| `/api/v1/**` | Browser | BFF_SESSION cookie |
| `/mfe/api/v1/**` | Partner/MFE | X-Persona, X-Member-Id, X-Member-Id-Type headers |

## Public Endpoints

### GET /login

Initiates OIDC authentication flow with HSID.

**Request:**
```http
GET /login HTTP/1.1
Host: bff.abc.com
```

**Response:**
```http
HTTP/1.1 302 Found
Location: https://nonprod.identity.healthsafe-id.com/oidc/authorize
  ?response_type=code
  &client_id=xxx
  &redirect_uri=https://bff.abc.com/
  &scope=openid+profile+email
  &state=xxx
  &code_challenge=xxx
  &code_challenge_method=S256
```

### GET /?code={code}&state={state}

OIDC callback endpoint. Creates session and sets cookie.

**Request:**
```http
GET /?code=abc123&state=xyz789 HTTP/1.1
Host: bff.abc.com
```

**Response:**
```http
HTTP/1.1 302 Found
Location: /
Set-Cookie: BFF_SESSION=session-id; Domain=abc.com; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=1800
```

### GET /actuator/health

Health check endpoint.

**Request:**
```http
GET /actuator/health HTTP/1.1
Host: bff.abc.com
```

**Response:**
```json
{
  "status": "UP"
}
```

### GET /actuator/info

Application information endpoint.

**Request:**
```http
GET /actuator/info HTTP/1.1
Host: bff.abc.com
```

**Response:**
```json
{
  "app": {
    "name": "bff",
    "version": "1.0.0"
  }
}
```

## Browser Endpoints

All browser endpoints require:
- Valid `BFF_SESSION` cookie
- `Origin` header matching allowed origins

### Common Headers

**Request Headers:**
```http
Cookie: BFF_SESSION=session-id
Origin: https://abc.com
Content-Type: application/json
```

**Response Headers:**
```http
Content-Type: application/json
```

### GET /api/v1/user

Get current user profile.

**Required Personas:** SELF, DELEGATE, AGENT, CASE_WORKER

**Request:**
```http
GET /api/v1/user HTTP/1.1
Host: bff.abc.com
Cookie: BFF_SESSION=xxx
Origin: https://abc.com
```

**Response:**
```json
{
  "enterpriseId": "ENT123456",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "preferences": {
    "language": "en",
    "notifications": true
  }
}
```

### GET /api/v1/delegates

Get delegate relationships for current user.

**Required Personas:** SELF, DELEGATE

**Request:**
```http
GET /api/v1/delegates HTTP/1.1
Host: bff.abc.com
Cookie: BFF_SESSION=xxx
Origin: https://abc.com
```

**Response:**
```json
{
  "delegates": [
    {
      "delegateType": "RPR",
      "targetEnterpriseId": "ENT789",
      "targetName": "Jane Doe",
      "permissions": ["VIEW_CLAIMS", "VIEW_ELIGIBILITY"],
      "effectiveDate": "2024-01-01",
      "terminationDate": null
    }
  ]
}
```

### POST /api/v1/account

Access account information. For DELEGATE persona, validates enterpriseId.

**Required Personas:** SELF, DELEGATE

**Request:**
```http
POST /api/v1/account HTTP/1.1
Host: bff.abc.com
Cookie: BFF_SESSION=xxx
Origin: https://abc.com
Content-Type: application/json

{
  "enterpriseId": "ENT789"
}
```

**Response:**
```json
{
  "enterpriseId": "ENT789",
  "accountStatus": "ACTIVE",
  "balance": 1234.56
}
```

**Error (Delegate not authorized):**
```json
{
  "error": "FORBIDDEN",
  "message": "No delegation for enterprise: ENT789"
}
```

### GET /api/v1/eligibility

Get eligibility information.

**Required Personas:** SELF, DELEGATE, CASE_WORKER

**Request:**
```http
GET /api/v1/eligibility HTTP/1.1
Host: bff.abc.com
Cookie: BFF_SESSION=xxx
Origin: https://abc.com
```

**Response:**
```json
{
  "eligibilities": [
    {
      "planCode": "PLAN001",
      "planName": "Premium Health Plan",
      "effectiveDate": "2024-01-01",
      "terminationDate": "2024-12-31",
      "status": "ACTIVE"
    }
  ]
}
```

## Partner/MFE Endpoints

Partner endpoints are accessed via `/mfe/api/v1/**` path prefix.

### Required Headers

| Header | Type | Required | Description |
|--------|------|----------|-------------|
| `X-Persona` | String | Yes | Persona type (SELF, DELEGATE, AGENT, etc.) |
| `X-Member-Id` | String | Yes | Member identifier value |
| `X-Member-Id-Type` | String | Yes | Identifier type (HSID, MSID, OHID) |

**Example:**
```http
GET /mfe/api/v1/user HTTP/1.1
Host: bff.abc.com
X-Persona: AGENT
X-Member-Id: MSID123456
X-Member-Id-Type: MSID
```

### MFE-Enabled Endpoints

Only endpoints marked with `@MfeEnabled` are accessible via partner paths.

| Endpoint | Browser Path | Partner Path | MFE Enabled |
|----------|-------------|--------------|-------------|
| User Profile | `/api/v1/user` | `/mfe/api/v1/user` | Yes |
| Eligibility | `/api/v1/eligibility` | `/mfe/api/v1/eligibility` | Yes |
| Delegates | `/api/v1/delegates` | N/A | No |
| Settings | `/api/v1/settings` | N/A | No |

### GET /mfe/api/v1/user

Get user profile via partner access.

**Request:**
```http
GET /mfe/api/v1/user HTTP/1.1
Host: bff.abc.com
X-Persona: AGENT
X-Member-Id: MSID123456
X-Member-Id-Type: MSID
```

**Response:**
```json
{
  "enterpriseId": "ENT123456",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com"
}
```

### GET /mfe/api/v1/eligibility

Get eligibility via partner access.

**Request:**
```http
GET /mfe/api/v1/eligibility HTTP/1.1
Host: bff.abc.com
X-Persona: CASE_WORKER
X-Member-Id: OHID789
X-Member-Id-Type: OHID
```

**Response:**
```json
{
  "eligibilities": [
    {
      "planCode": "PLAN001",
      "status": "ACTIVE"
    }
  ]
}
```

## Error Responses

### 401 Unauthorized

**Missing/Invalid Session:**
```json
{
  "error": "UNAUTHORIZED",
  "message": "Session expired or invalid",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Invalid Origin:**
```json
{
  "error": "UNAUTHORIZED",
  "message": "Request origin not allowed: https://evil.com",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Missing Headers (Partner):**
```json
{
  "error": "UNAUTHORIZED",
  "message": "Missing required header: X-Persona",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Invalid Persona-IDP Combination:**
```json
{
  "error": "UNAUTHORIZED",
  "message": "Persona AGENT not valid for member ID type HSID",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 403 Forbidden

**Insufficient Persona:**
```json
{
  "error": "FORBIDDEN",
  "message": "Persona SELF not authorized for this resource. Required: CONFIG_SPECIALIST",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Delegate Not Authorized:**
```json
{
  "error": "FORBIDDEN",
  "message": "No delegation for enterprise: ENT789",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**MFE Not Enabled:**
```json
{
  "error": "FORBIDDEN",
  "message": "Endpoint not available for partner access",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 400 Bad Request

**Invalid Request Body:**
```json
{
  "error": "BAD_REQUEST",
  "message": "Invalid request body",
  "details": [
    "enterpriseId: must not be blank"
  ],
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### 500 Internal Server Error

**Service Unavailable:**
```json
{
  "error": "INTERNAL_SERVER_ERROR",
  "message": "Service temporarily unavailable",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## Persona Requirements by Endpoint

| Endpoint | SELF | DELEGATE | AGENT | CASE_WORKER | CONFIG_SPECIALIST |
|----------|------|----------|-------|-------------|-------------------|
| GET /api/v1/user | Yes | Yes | Yes | Yes | No |
| GET /api/v1/delegates | Yes | Yes | No | No | No |
| POST /api/v1/account | Yes | Yes | No | No | No |
| GET /api/v1/eligibility | Yes | Yes | No | Yes | No |
| GET /api/v1/settings | Yes | No | No | No | No |
| GET /api/v1/admin/config | No | No | No | No | Yes |

## Rate Limiting

Rate limits are applied at the ALB level:

| Limit Type | Value |
|------------|-------|
| Requests per second (per IP) | 100 |
| Requests per minute (per session) | 1000 |
| Login attempts per minute (per IP) | 10 |

## Caching

API responses are cached based on configuration:

| Endpoint | Cache TTL | Cache Key |
|----------|-----------|-----------|
| GET /api/v1/user | 30 min | enterpriseId |
| GET /api/v1/eligibility | 30 min | enterpriseId |
| GET /api/v1/delegates | 30 min | memberId |

Cache can be invalidated by session logout or manual refresh.
