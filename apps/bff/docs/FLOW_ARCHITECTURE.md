# Flow Architecture

## Browser Authentication Flow

### Login Initiation

```
┌─────────┐          ┌─────────┐          ┌─────────┐
│ Browser │          │   BFF   │          │  HSID   │
└────┬────┘          └────┬────┘          └────┬────┘
     │                    │                    │
     │  GET /login        │                    │
     │───────────────────>│                    │
     │                    │                    │
     │                    │  Generate PKCE     │
     │                    │  (code_verifier,   │
     │                    │   code_challenge)  │
     │                    │                    │
     │  302 Redirect      │                    │
     │<───────────────────│                    │
     │  Location: HSID    │                    │
     │  /oidc/authorize   │                    │
     │  ?code_challenge   │                    │
     │  &state            │                    │
     │                    │                    │
     │  Redirect to HSID login page            │
     │─────────────────────────────────────────>
     │                    │                    │
```

### OIDC Callback

```
┌─────────┐          ┌─────────┐          ┌─────────┐          ┌─────────────┐
│ Browser │          │   BFF   │          │  HSID   │          │ User Service│
└────┬────┘          └────┬────┘          └────┬────┘          └──────┬──────┘
     │                    │                    │                      │
     │  GET /?code=xxx    │                    │                      │
     │  &state=yyy        │                    │                      │
     │───────────────────>│                    │                      │
     │                    │                    │                      │
     │                    │  POST /oidc/token  │                      │
     │                    │  code + verifier   │                      │
     │                    │───────────────────>│                      │
     │                    │                    │                      │
     │                    │  {access_token,    │                      │
     │                    │   id_token,        │                      │
     │                    │   refresh_token}   │                      │
     │                    │<───────────────────│                      │
     │                    │                    │                      │
     │                    │  Extract claims    │                      │
     │                    │  (enterpriseId,    │                      │
     │                    │   memberId)        │                      │
     │                    │                    │                      │
     │                    │  GET /user/{id}    │                      │
     │                    │─────────────────────────────────────────>│
     │                    │                    │                      │
     │                    │  {preferences,     │                      │
     │                    │   profile}         │                      │
     │                    │<─────────────────────────────────────────│
     │                    │                    │                      │
     │                    │  Create BffSession │                      │
     │                    │  Store in SessionStore                    │
     │                    │                    │                      │
     │  302 Redirect      │                    │                      │
     │  Set-Cookie:       │                    │                      │
     │   BFF_SESSION=xxx  │                    │                      │
     │<───────────────────│                    │                      │
     │                    │                    │                      │
```

## Browser API Request Flow

```
┌─────────┐          ┌─────────────────────────────────────────────────────┐
│ Browser │          │                        BFF                          │
└────┬────┘          │  ┌────────┐ ┌────────┐ ┌────────┐ ┌─────────────┐  │
     │               │  │Origin  │ │Session │ │Delegate│ │ Persona     │  │
     │               │  │Filter  │ │Filter  │ │Filter  │ │ Auth Filter │  │
     │               │  └───┬────┘ └───┬────┘ └───┬────┘ └──────┬──────┘  │
     │               └──────┼──────────┼──────────┼─────────────┼─────────┘
     │                      │          │          │             │
     │  GET /api/v1/user    │          │          │             │
     │  Cookie: BFF_SESSION │          │          │             │
     │  Origin: https://abc.com        │          │             │
     │─────────────────────>│          │          │             │
     │                      │          │          │             │
     │                      │ Validate │          │             │
     │                      │ Origin   │          │             │
     │                      │──────────>          │             │
     │                      │          │          │             │
     │                      │          │ Load     │             │
     │                      │          │ Session  │             │
     │                      │          │──────────>             │
     │                      │          │          │             │
     │                      │          │          │ DELEGATE?   │
     │                      │          │          │ Validate    │
     │                      │          │          │ enterpriseId│
     │                      │          │          │─────────────>
     │                      │          │          │             │
     │                      │          │          │             │ Check
     │                      │          │          │             │ @RequiredPersona
     │                      │          │          │             │
     │                      │          │          │             │ Controller
     │                      │          │          │             │ invoked
     │                      │          │          │             │
     │  200 OK              │          │          │             │
     │  {user data}         │          │          │             │
     │<─────────────────────────────────────────────────────────│
     │                      │          │          │             │
```

## Partner/MFE API Request Flow

```
┌─────────┐          ┌──────────────────────────────────────────────────────────────┐
│ Partner │          │                            BFF                                │
└────┬────┘          │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────────┐ │
     │               │  │Rewrite │ │Partner │ │Delegate│ │Persona │ │MfeRoute    │ │
     │               │  │Filter  │ │Auth    │ │Filter  │ │Auth    │ │Validator   │ │
     │               │  └───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘ └─────┬──────┘ │
     │               └──────┼──────────┼──────────┼──────────┼────────────┼────────┘
     │                      │          │          │          │            │
     │  GET /mfe/api/v1/user           │          │          │            │
     │  X-Persona: AGENT   │           │          │          │            │
     │  X-Member-Id: 123   │           │          │          │            │
     │  X-Member-Id-Type: MSID         │          │          │            │
     │─────────────────────>│          │          │          │            │
     │                      │          │          │          │            │
     │                      │ Rewrite  │          │          │            │
     │                      │ path to  │          │          │            │
     │                      │ /api/v1/ │          │          │            │
     │                      │──────────>          │          │            │
     │                      │          │          │          │            │
     │                      │          │ Create   │          │            │
     │                      │          │ AuthContext         │            │
     │                      │          │ from headers        │            │
     │                      │          │──────────>          │            │
     │                      │          │          │          │            │
     │                      │          │          │ Validate │            │
     │                      │          │          │ Persona  │            │
     │                      │          │          │ + IDP    │            │
     │                      │          │          │──────────>            │
     │                      │          │          │          │            │
     │                      │          │          │          │ Check      │
     │                      │          │          │          │ @MfeEnabled│
     │                      │          │          │          │            │
     │                      │          │          │          │            │ Controller
     │                      │          │          │          │            │ invoked
     │                      │          │          │          │            │
     │  200 OK              │          │          │          │            │
     │  {user data}         │          │          │          │            │
     │<────────────────────────────────────────────────────────────────────│
     │                      │          │          │          │            │
```

## Delegate Flow (DELEGATE Persona)

```
┌─────────┐          ┌─────────┐          ┌───────────────┐          ┌─────────────────┐
│ Browser │          │   BFF   │          │ Delegate Graph│          │ Eligibility Graph│
└────┬────┘          └────┬────┘          └───────┬───────┘          └────────┬────────┘
     │                    │                       │                           │
     │  POST /api/v1/account                      │                           │
     │  Cookie: BFF_SESSION                       │                           │
     │  Body: {enterpriseId: "ENT123"}            │                           │
     │───────────────────>│                       │                           │
     │                    │                       │                           │
     │                    │  Load Session         │                           │
     │                    │  persona=DELEGATE     │                           │
     │                    │                       │                           │
     │                    │  Query activeDelegates                            │
     │                    │  from session         │                           │
     │                    │                       │                           │
     │                    │  Check: "ENT123"      │                           │
     │                    │  in delegate list?    │                           │
     │                    │                       │                           │
     │                    │  [If not cached]      │                           │
     │                    │  GraphQL query        │                           │
     │                    │───────────────────────>                           │
     │                    │                       │                           │
     │                    │  {delegates: [...]}   │                           │
     │                    │<───────────────────────                           │
     │                    │                       │                           │
     │                    │  Validate ENT123      │                           │
     │                    │  is in delegates      │                           │
     │                    │                       │                           │
     │                    │  GraphQL eligibility query                        │
     │                    │────────────────────────────────────────────────────>
     │                    │                       │                           │
     │                    │  {eligibility: [...]} │                           │
     │                    │<────────────────────────────────────────────────────
     │                    │                       │                           │
     │                    │  Update AuthContext   │                           │
     │                    │  with delegated       │                           │
     │                    │  enterpriseId         │                           │
     │                    │                       │                           │
     │  200 OK            │                       │                           │
     │  {account data}    │                       │                           │
     │<───────────────────│                       │                           │
     │                    │                       │                           │
```

## Session Creation Flow (OIDC Callback)

```
┌─────────┐     ┌─────────┐     ┌─────────┐     ┌───────────┐     ┌──────────┐     ┌──────────┐
│ Browser │     │   BFF   │     │  HSID   │     │User Service│     │Del. Graph│     │Elig.Graph│
└────┬────┘     └────┬────┘     └────┬────┘     └─────┬─────┘     └────┬─────┘     └────┬─────┘
     │               │               │                │                │                │
     │ /?code=xxx    │               │                │                │                │
     │──────────────>│               │                │                │                │
     │               │               │                │                │                │
     │               │ Token Request │                │                │                │
     │               │──────────────>│                │                │                │
     │               │               │                │                │                │
     │               │ Tokens        │                │                │                │
     │               │<──────────────│                │                │                │
     │               │               │                │                │                │
     │               │ Extract enterpriseId, memberId │                │                │
     │               │               │                │                │                │
     │               ├───────────────┼────────────────┼────────────────┼────────────────┤
     │               │               │  PARALLEL CALLS                 │                │
     │               │               │                │                │                │
     │               │ Get User ─────────────────────>│                │                │
     │               │               │                │                │                │
     │               │ Get Delegates ────────────────────────────────>│                │
     │               │               │                │                │                │
     │               │ Get Eligibility ──────────────────────────────────────────────>│
     │               │               │                │                │                │
     │               │<───────────────────────────────│                │                │
     │               │<────────────────────────────────────────────────│                │
     │               │<────────────────────────────────────────────────────────────────│
     │               │               │                │                │                │
     │               ├───────────────┼────────────────┼────────────────┼────────────────┤
     │               │               │                │                │                │
     │               │ Build BffSession:              │                │                │
     │               │ - sessionId (UUID)             │                │                │
     │               │ - accessToken                  │                │                │
     │               │ - refreshToken                 │                │                │
     │               │ - enterpriseId                 │                │                │
     │               │ - memberId + type              │                │                │
     │               │ - persona (SELF)               │                │                │
     │               │ - activeDelegates              │                │                │
     │               │               │                │                │                │
     │               │ Store in SessionStore          │                │                │
     │               │               │                │                │                │
     │ 302 + Cookie  │               │                │                │                │
     │<──────────────│               │                │                │                │
     │               │               │                │                │                │
```

## Error Flows

### Invalid Origin

```
┌─────────┐          ┌─────────────────┐
│ Browser │          │ OriginValidator │
└────┬────┘          └────────┬────────┘
     │                        │
     │  GET /api/v1/user      │
     │  Origin: https://evil.com
     │───────────────────────>│
     │                        │
     │                        │ Origin not in
     │                        │ allowedOrigins
     │                        │
     │  401 Unauthorized      │
     │  "Request origin not   │
     │   allowed"             │
     │<───────────────────────│
     │                        │
```

### Invalid Session

```
┌─────────┐          ┌───────────────────┐
│ Browser │          │ BrowserSessionFilter│
└────┬────┘          └─────────┬─────────┘
     │                         │
     │  GET /api/v1/user       │
     │  Cookie: BFF_SESSION=xxx│
     │────────────────────────>│
     │                         │
     │                         │ Session not found
     │                         │ or expired
     │                         │
     │  401 Unauthorized       │
     │  "Session expired"      │
     │  Set-Cookie: (clear)    │
     │<────────────────────────│
     │                         │
```

### Invalid Persona for Route

```
┌─────────┐          ┌──────────────────────┐
│ Partner │          │ PersonaAuthorizationFilter │
└────┬────┘          └───────────┬──────────┘
     │                           │
     │  GET /mfe/api/v1/admin    │
     │  X-Persona: AGENT         │
     │──────────────────────────>│
     │                           │
     │                           │ Route requires
     │                           │ @RequiredPersona(
     │                           │   CONFIG_SPECIALIST)
     │                           │
     │  403 Forbidden            │
     │  "Insufficient persona"   │
     │<──────────────────────────│
     │                           │
```

### Delegate Access Denied

```
┌─────────┐          ┌─────────────────────────┐
│ Browser │          │ DelegateEnterpriseIdFilter │
└────┬────┘          └────────────┬────────────┘
     │                            │
     │  POST /api/v1/account      │
     │  Cookie: BFF_SESSION       │
     │  Body: {enterpriseId: "X"} │
     │───────────────────────────>│
     │                            │
     │                            │ Session persona=DELEGATE
     │                            │ but "X" not in
     │                            │ activeDelegates list
     │                            │
     │  403 Forbidden             │
     │  "No delegation for       │
     │   enterprise X"            │
     │<───────────────────────────│
     │                            │
```
