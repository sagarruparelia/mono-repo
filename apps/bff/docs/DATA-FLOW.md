# BFF Data Flow

## Overview

This document describes the request processing flows, data flows, and lifecycle patterns in the BFF application.

---

## Request Processing Flow

### Complete Request Flow
```
Browser/Proxy Request
        │
        ▼
┌───────────────────┐
│  Rate Limiting    │ → 429 Too Many Requests if exceeded
│  (+5)             │
└─────────┬─────────┘
          │
          ▼
┌───────────────────┐
│  Session Binding  │ → Validates session, IP, device fingerprint
│  (+10)            │ → Attaches SessionData to exchange
└─────────┬─────────┘
          │
          ▼
┌───────────────────┐
│  Dual Auth        │ → Creates AuthPrincipal from session or headers
│  (+20)            │ → Attaches AuthPrincipal to exchange
└─────────┬─────────┘
          │
          ▼
┌───────────────────┐
│  Persona Auth     │ → Validates @RequirePersona annotation
│  (+30)            │ → Validates delegate permissions if applicable
└─────────┬─────────┘
          │
          ▼
┌───────────────────┐
│  Controller       │ → Handles business logic
│                   │ → Calls service layer
└─────────┬─────────┘
          │
          ▼
┌───────────────────┐
│  Service Layer    │ → Cache lookup
│                   │ → External API calls if cache miss
└─────────┬─────────┘
          │
          ▼
┌───────────────────┐
│  Response         │ → JSON serialization
│                   │ → Security headers
└───────────────────┘
```

---

## Authentication Flows

### OAuth2 Login Flow
```
Browser                    BFF                         HSID Provider
   │                        │                               │
   │  GET /oauth2/authorization/hsid                        │
   │───────────────────────>│                               │
   │                        │                               │
   │  302 Redirect to HSID  │                               │
   │<───────────────────────│                               │
   │                        │                               │
   │  GET /authorize        │                               │
   │────────────────────────────────────────────────────────>
   │                        │                               │
   │  User authenticates    │                               │
   │                        │                               │
   │  302 Redirect with code│                               │
   │<────────────────────────────────────────────────────────
   │                        │                               │
   │  GET /login/oauth2/code/hsid?code=xxx                  │
   │───────────────────────>│                               │
   │                        │                               │
   │                        │  POST /token (exchange code)  │
   │                        │──────────────────────────────>│
   │                        │                               │
   │                        │  Tokens (access, refresh, id) │
   │                        │<──────────────────────────────│
   │                        │                               │
   │                        │  ┌─────────────────────────┐  │
   │                        │  │ HsidAuthSuccessHandler  │  │
   │                        │  │ - Fetch UserInfo        │  │
   │                        │  │ - Fetch Eligibility     │  │
   │                        │  │ - Fetch Permissions     │  │
   │                        │  │ - Create SessionData    │  │
   │                        │  │ - Store in cache        │  │
   │                        │  └─────────────────────────┘  │
   │                        │                               │
   │  302 Redirect + Cookie │                               │
   │<───────────────────────│                               │
   │                        │                               │
```

### Session Validation Flow
```
Request with Cookie
        │
        ▼
┌─────────────────────────┐
│ Extract session cookie  │
│ BFF_SESSION=<sessionId> │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│ Load SessionData        │
│ from SessionOperations  │
└───────────┬─────────────┘
            │
      ┌─────┴─────┐
      │ Session   │
      │ exists?   │
      └─────┬─────┘
        No  │  Yes
            ▼
   ┌────────────────────┐
   │ Validate device    │
   │ fingerprint        │
   └────────┬───────────┘
            │
      ┌─────┴─────┐
      │ Matches?  │
      └─────┬─────┘
        No  │  Yes
   ┌────────┴────────┐
   │                 │
   ▼                 ▼
┌─────────┐    ┌───────────────┐
│ 401     │    │ Validate IP   │
│ Unauth  │    │ binding       │
└─────────┘    └───────┬───────┘
                       │
                 ┌─────┴─────┐
                 │ Matches?  │
                 └─────┬─────┘
                   No  │  Yes
              ┌────────┴────────┐
              │                 │
              ▼                 ▼
         ┌─────────┐    ┌───────────────┐
         │ 401     │    │ Attach to     │
         │ Unauth  │    │ exchange      │
         └─────────┘    │ Continue...   │
                        └───────────────┘
```

### Logout Flow
```
POST /api/v1/auth/logout
        │
        ▼
┌─────────────────────────┐
│ Extract session cookie  │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│ Get ID token from       │
│ TokenOperations         │
│ (for HSID logout)       │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│ Revoke refresh token    │
│ at HSID provider        │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│ Invalidate session      │
│ in SessionOperations    │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│ Clear session cookie    │
│ (MaxAge=0)              │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────┐
│ Return logout URL       │
│ for client redirect     │
└─────────────────────────┘
```

---

## Health Data Flow

### Get Immunizations Flow
```
POST /api/v1/health/immunizations
        │
        ▼
┌──────────────────────────┐
│ HealthDataController     │
│ - Extract AuthPrincipal  │
│ - Get target enterpriseId│
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│ HealthDataOrchestrator   │
│ getImmunizations()       │
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│ ImmunizationService      │
│ Check MongoDB cache      │
└────────────┬─────────────┘
             │
       ┌─────┴─────┐
       │ Cache     │
       │ hit?      │
       └─────┬─────┘
         No  │  Yes
    ┌────────┴────────┐
    │                 │
    ▼                 ▼
┌───────────┐    ┌───────────┐
│ Call ECDH │    │ Return    │
│ API       │    │ cached    │
└─────┬─────┘    │ data      │
      │          └───────────┘
      ▼
┌──────────────────────────┐
│ EcdhApiClientService     │
│ - Paginated GraphQL      │
│ - Collect all pages      │
│ - Map to entities        │
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│ Store in MongoDB cache   │
│ with TTL                 │
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│ Return response          │
└──────────────────────────┘
```

### ECDH Pagination Flow
```
EcdhApiClientService.fetchAllPages()
        │
        ▼
┌──────────────────────────┐
│ First request            │
│ (no continuationToken)   │
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│ ECDH GraphQL API         │
│ Returns page + token     │
└────────────┬─────────────┘
             │
       ┌─────┴─────┐
       │ Has more  │
       │ pages?    │
       └─────┬─────┘
         No  │  Yes
    ┌────────┴────────┐
    │                 │
    ▼                 ▼
┌───────────┐    ┌────────────────┐
│ Collect   │    │ Fetch next     │
│ all       │    │ page with      │
│ results   │    │ continuationTk │
└───────────┘    └───────┬────────┘
                         │
                         └─────► (repeat until no more pages)
```

---

## Caching Flow

### Identity Cache Flow (Redis/Memory)
```
Service requests data (e.g., UserInfo)
        │
        ▼
┌──────────────────────────┐
│ IdentityCacheOperations  │
│ getOrLoadUserInfo()      │
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│ Check cache              │
│ (Redis or Caffeine)      │
└────────────┬─────────────┘
             │
       ┌─────┴─────┐
       │ Cache     │
       │ hit?      │
       └─────┬─────┘
         No  │  Yes
    ┌────────┴────────┐
    │                 │
    ▼                 ▼
┌───────────┐    ┌───────────┐
│ Execute   │    │ Return    │
│ loader    │    │ cached    │
│ Mono      │    │ value     │
└─────┬─────┘    └───────────┘
      │
      ▼
┌──────────────────────────┐
│ Store result in cache    │
│ with TTL                 │
└────────────┬─────────────┘
             │
             ▼
         Return value
```

### Cache Invalidation Flow (Redis Pub/Sub)
```
Pod A: Invalidate cache entry
        │
        ▼
┌──────────────────────────┐
│ IdentityCacheOperations  │
│ evictUserInfo()          │
└────────────┬─────────────┘
             │
             ├──────────────────────────────┐
             │                              │
             ▼                              ▼
┌──────────────────────────┐    ┌──────────────────────────┐
│ Local cache eviction     │    │ IdentityCacheEventPub    │
└──────────────────────────┘    │ publishEviction()        │
                                └────────────┬─────────────┘
                                             │
                                             ▼
                                ┌──────────────────────────┐
                                │ Redis Pub/Sub Channel    │
                                │ bff:cache:identity:events│
                                └────────────┬─────────────┘
                                             │
                      ┌──────────────────────┴──────────────────────┐
                      │                                             │
                      ▼                                             ▼
         ┌──────────────────────────┐              ┌──────────────────────────┐
         │ Pod B: EventListener     │              │ Pod C: EventListener     │
         │ Evict local cache        │              │ Evict local cache        │
         └──────────────────────────┘              └──────────────────────────┘
```

---

## Managed Member Access Flow

### Delegate Data Access
```
Delegate requests managed member data
        │
        ▼
┌──────────────────────────┐
│ PersonaAuthorizationFilter│
│ Check @RequirePersona    │
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│ Extract enterpriseId     │
│ from request             │
└────────────┬─────────────┘
             │
             ▼
┌──────────────────────────┐
│ Check PermissionSet      │
│ for enterpriseId access  │
└────────────┬─────────────┘
             │
       ┌─────┴─────┐
       │ Has       │
       │ access?   │
       └─────┬─────┘
         No  │  Yes
    ┌────────┴────────┐
    │                 │
    ▼                 ▼
┌───────────┐    ┌────────────────┐
│ 403       │    │ Validate       │
│ Forbidden │    │ delegate type  │
└───────────┘    │ (DAA/RPR/ROI)  │
                 └───────┬────────┘
                         │
                   ┌─────┴─────┐
                   │ Valid     │
                   │ type?     │
                   └─────┬─────┘
                     No  │  Yes
                ┌────────┴────────┐
                │                 │
                ▼                 ▼
           ┌───────────┐    ┌───────────────┐
           │ 403       │    │ Validate      │
           │ Forbidden │    │ time bounds   │
           └───────────┘    │ (start/end)   │
                            └───────┬───────┘
                                    │
                              ┌─────┴─────┐
                              │ Within    │
                              │ window?   │
                              └─────┬─────┘
                                No  │  Yes
                           ┌────────┴────────┐
                           │                 │
                           ▼                 ▼
                      ┌───────────┐    ┌───────────────┐
                      │ 403       │    │ Set validated │
                      │ Forbidden │    │ enterpriseId  │
                      └───────────┘    │ on exchange   │
                                       │ Continue...   │
                                       └───────────────┘
```

---

## External API Error Handling

### Retry and Fallback Flow
```
External API call
        │
        ▼
┌──────────────────────────┐
│ WebClient request        │
│ with timeout             │
└────────────┬─────────────┘
             │
       ┌─────┴─────┐
       │ Success?  │
       └─────┬─────┘
         No  │  Yes
    ┌────────┴────────┐
    │                 │
    ▼                 ▼
┌───────────┐    ┌───────────┐
│ Check if  │    │ Return    │
│ retryable │    │ response  │
└─────┬─────┘    └───────────┘
      │
┌─────┴─────┐
│ Retryable?│
│ (5xx,     │
│ timeout)  │
└─────┬─────┘
  No  │  Yes
 ┌────┴────┐
 │         │
 ▼         ▼
┌─────┐    ┌───────────────┐
│Error│    │ Wait backoff  │
│     │    │ Retry (max 3) │
└─────┘    └───────┬───────┘
                   │
                   └─────► (repeat until success or max retries)
                           │
                           ▼
                   ┌───────────────┐
                   │ onErrorResume │
                   │ Return empty  │
                   │ or default    │
                   └───────────────┘
```
