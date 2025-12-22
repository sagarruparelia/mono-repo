# BFF API Reference

## Overview

This document provides a complete reference for all BFF API endpoints, including authentication requirements, request/response formats, and error handling.

---

## Base URL

All API endpoints are prefixed with `/api/v1/`.

---

## Authentication

All endpoints (except OAuth2 initiation) require a valid session cookie:
```
Cookie: BFF_SESSION=<session-id>
```

For CSRF-protected endpoints, include the CSRF token:
```
X-CSRF-TOKEN: <csrf-token>
```

---

## Authentication Endpoints

### OAuth2 Login Initiation

Initiates the OAuth2 login flow with HSID provider.

```
GET /oauth2/authorization/hsid
```

**Response:** 302 Redirect to HSID authorization endpoint

---

### Get Session Info

Returns current session information.

```
POST /api/v1/auth/session
```

**Request:** No body required (session from cookie)

**Response (valid session):**
```json
{
  "valid": true,
  "hsidUuid": "abc-123-def",
  "name": "John Doe",
  "email": "john.doe@example.com",
  "persona": "INDIVIDUAL_SELF",
  "isParent": true,
  "managedMembers": ["eid-001", "eid-002"],
  "expiresAt": "2024-01-15T10:30:00Z",
  "lastAccessedAt": "2024-01-15T10:00:00Z"
}
```

**Response (invalid session):**
```json
{
  "valid": false,
  "reason": "session_not_found"
}
```

---

### Check Session Status

Quick check if session is valid with expiration info.

```
POST /api/v1/auth/session/status
```

**Response (valid):**
```json
{
  "valid": true,
  "expiresIn": 1800,
  "persona": "INDIVIDUAL_SELF"
}
```

**Response (invalid):**
```json
{
  "valid": false
}
```

---

### Extend Session

Extends the session TTL.

```
POST /api/v1/auth/session/extend
```

**Response (success):**
```json
{
  "extended": true
}
```

**Response (failure):**
```json
{
  "extended": false,
  "reason": "no_session"
}
```

---

### Logout

Logs out the user and invalidates the session.

```
POST /api/v1/auth/logout
```

**Response:**
```json
{
  "loggedOut": true,
  "redirectUrl": "https://hsid.example.com/logout?id_token_hint=xxx&post_logout_redirect_uri=yyy"
}
```

---

## Health Data Endpoints

All health data endpoints require:
- **Personas:** `INDIVIDUAL_SELF`, `DELEGATE`, `CASE_WORKER`, `AGENT`
- **Delegate Types (if DELEGATE):** `DAA`, `RPR`

### Get Immunizations

```
POST /api/v1/health/immunizations
```

**Request Body (for delegate access):**
```json
{
  "enterpriseId": "eid-001"
}
```

**Response:**
```json
{
  "data": [
    {
      "id": "imm-001",
      "vaccineCode": "CVX-141",
      "vaccineName": "Influenza, seasonal",
      "administrationDate": "2024-01-10",
      "provider": "ABC Clinic",
      "lotNumber": "LOT123",
      "site": "Left Arm",
      "status": "completed"
    }
  ],
  "count": 1,
  "type": "immunizations"
}
```

---

### Get Allergies

```
POST /api/v1/health/allergies
```

**Response:**
```json
{
  "data": [
    {
      "id": "alg-001",
      "allergenCode": "227493005",
      "allergenName": "Penicillin",
      "category": "medication",
      "severity": "severe",
      "reaction": "Anaphylaxis",
      "onsetDate": "2020-05-15",
      "status": "active"
    }
  ],
  "count": 1,
  "type": "allergies"
}
```

---

### Get Conditions

```
POST /api/v1/health/conditions
```

**Response:**
```json
{
  "data": [
    {
      "id": "cnd-001",
      "conditionCode": "44054006",
      "conditionName": "Type 2 diabetes mellitus",
      "category": "encounter-diagnosis",
      "onsetDate": "2019-03-20",
      "abatementDate": null,
      "clinicalStatus": "active",
      "verificationStatus": "confirmed"
    }
  ],
  "count": 1,
  "type": "conditions"
}
```

---

### Invalidate Health Cache

Forces a cache refresh for health data.

```
POST /api/v1/health/cache/invalidate
```

**Response:** `200 OK` (no body)

---

## Member Endpoints

### Get Managed Members

Returns list of members the user can manage (delegates only).

```
POST /api/v1/members/managed
```

**Required Persona:** `DELEGATE`

**Response:**
```json
[
  {
    "memberId": "eid-001",
    "firstName": "Jane",
    "lastName": "Doe",
    "birthDate": "1990-05-15",
    "permissions": ["VIEW_HEALTH", "VIEW_DOCUMENTS"]
  },
  {
    "memberId": "eid-002",
    "firstName": "John",
    "lastName": "Doe Jr",
    "birthDate": "2015-08-20",
    "permissions": ["VIEW_HEALTH", "VIEW_DOCUMENTS", "MANAGE_DOCUMENTS"]
  }
]
```

---

## Document Endpoints

### List Documents

```
POST /api/v1/documents/list
```

**Required Personas:** `INDIVIDUAL_SELF`, `DELEGATE` (DAA/RPR), `CASE_WORKER`, `AGENT`

**Response:**
```json
[
  {
    "id": "doc-001",
    "fileName": "lab_results.pdf",
    "contentType": "application/pdf",
    "size": 102400,
    "description": "Blood test results",
    "documentType": "LAB_RESULT",
    "createdAt": "2024-01-10T10:00:00Z",
    "createdBy": "eid-user"
  }
]
```

---

### Get Document Metadata

```
POST /api/v1/documents/get
```

**Request:**
```json
{
  "documentId": "doc-001"
}
```

**Response:**
```json
{
  "id": "doc-001",
  "fileName": "lab_results.pdf",
  "contentType": "application/pdf",
  "size": 102400,
  "description": "Blood test results",
  "documentType": "LAB_RESULT",
  "createdAt": "2024-01-10T10:00:00Z",
  "createdBy": "eid-user"
}
```

---

### Download Document

```
POST /api/v1/documents/download
```

**Required Personas:** All health personas + `CONFIG_SPECIALIST`
**Required Delegates:** `DAA`, `RPR`, `ROI`

**Request:**
```json
{
  "documentId": "doc-001"
}
```

**Response:** Binary file with headers:
```
Content-Type: application/pdf
Content-Disposition: attachment; filename="lab_results.pdf"
Content-Length: 102400
```

---

### Upload Document

```
POST /api/v1/documents/upload
Content-Type: multipart/form-data
```

**Form Fields:**
- `file` (required): The file to upload
- `description` (optional): Document description
- `documentType` (optional): Document type enum

**Response (201 Created):**
```json
{
  "id": "doc-002",
  "fileName": "prescription.pdf",
  "contentType": "application/pdf",
  "size": 51200,
  "description": "Monthly prescription",
  "documentType": "PRESCRIPTION",
  "createdAt": "2024-01-15T14:30:00Z",
  "createdBy": "eid-user"
}
```

---

### Delete Document

```
POST /api/v1/documents/delete
```

**Required Personas:** `INDIVIDUAL_SELF`, `DELEGATE` (DAA/RPR), `CASE_WORKER`

**Request:**
```json
{
  "documentId": "doc-001"
}
```

**Response:** `204 No Content`

---

## Error Responses

### Error Format

All error responses follow this format:

```json
{
  "error": "ERROR_TYPE",
  "code": "SPECIFIC_CODE",
  "message": "Human-readable message"
}
```

### HTTP Status Codes

| Status | Error Type | Description |
|--------|------------|-------------|
| 400 | `BAD_REQUEST` | Invalid request format |
| 401 | `UNAUTHORIZED` | Authentication required |
| 403 | `FORBIDDEN` | Access denied |
| 404 | `NOT_FOUND` | Resource not found |
| 429 | `TOO_MANY_REQUESTS` | Rate limit exceeded |
| 500 | `INTERNAL_ERROR` | Server error |

### Common Error Codes

**Authentication Errors (401):**
| Code | Description |
|------|-------------|
| `NO_SESSION` | Session cookie missing |
| `INVALID_SESSION` | Session not found or expired |
| `SESSION_EXPIRED` | Session has timed out |
| `DEVICE_MISMATCH` | Device fingerprint changed |
| `IP_MISMATCH` | Client IP changed |

**Authorization Errors (403):**
| Code | Description |
|------|-------------|
| `INVALID_PERSONA` | Persona not authorized |
| `INVALID_DELEGATE_TYPE` | Delegate type not allowed |
| `PERMISSION_EXPIRED` | Temporal permission expired |
| `ACCESS_DENIED` | General access denial |

**Rate Limiting (429):**
```json
{
  "error": "TOO_MANY_REQUESTS",
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Rate limit exceeded. Try again later.",
  "retryAfter": 60
}
```

---

## Request Headers

### Required Headers

| Header | Description |
|--------|-------------|
| `Cookie` | Session cookie (`BFF_SESSION=xxx`) |
| `X-CSRF-TOKEN` | CSRF token (for mutating requests) |
| `Content-Type` | Request content type |

### Optional Headers

| Header | Description |
|--------|-------------|
| `X-Request-ID` | Correlation ID for tracing |
| `X-Device-Fingerprint` | Client device fingerprint |

---

## Response Headers

| Header | Description |
|--------|-------------|
| `X-Request-ID` | Correlation ID (echoed or generated) |
| `X-RateLimit-Remaining` | Remaining requests in window |
| `X-RateLimit-Reset` | Window reset timestamp |

---

## Content Types

| Endpoint Type | Request | Response |
|--------------|---------|----------|
| JSON APIs | `application/json` | `application/json` |
| File Upload | `multipart/form-data` | `application/json` |
| File Download | `application/json` | `application/octet-stream` or specific type |

---

## Pagination

For endpoints returning lists, pagination is supported:

**Request:**
```json
{
  "page": 0,
  "size": 20
}
```

**Response includes:**
```json
{
  "data": [...],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8
}
```

---

## Document Types

| Type | Description |
|------|-------------|
| `LAB_RESULT` | Laboratory test results |
| `PRESCRIPTION` | Prescriptions |
| `IMAGING` | X-rays, MRIs, etc. |
| `CLINICAL_NOTE` | Doctor's notes |
| `DISCHARGE_SUMMARY` | Hospital discharge |
| `OTHER` | Other documents |
