# Reference Architecture

## System Context

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              EXTERNAL CLIENTS                                │
├─────────────────────────────────┬───────────────────────────────────────────┤
│         Browser Users           │           Partner MFE Systems             │
│     (SELF / DELEGATE)           │  (AGENT / CASE_WORKER / CONFIG_SPECIALIST)│
│         /api/v1/**              │            /mfe/api/v1/**                 │
└───────────────┬─────────────────┴─────────────────────┬─────────────────────┘
                │                                       │
                │ HTTPS + Cookie                        │ HTTPS + mTLS + Headers
                ▼                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           APPLICATION LOAD BALANCER                          │
│                         (mTLS Termination for /mfe/**)                       │
└───────────────────────────────────────┬─────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                                    BFF                                       │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                         SECURITY LAYER                                   ││
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐  ││
│  │  │ Origin Validator │  │ Session Manager  │  │ Persona Authorizer   │  ││
│  │  └──────────────────┘  └──────────────────┘  └──────────────────────┘  ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                         BUSINESS LAYER                                   ││
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐  ││
│  │  │  User Controller │  │Delegate Controller│  │Eligibility Controller│  ││
│  │  └──────────────────┘  └──────────────────┘  └──────────────────────┘  ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                         DATA LAYER                                       ││
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐  ││
│  │  │  Session Store   │  │   Cache Store    │  │  API Clients         │  ││
│  │  │ (Memory / Redis) │  │ (Memory / Redis) │  │  (WebClient)         │  ││
│  │  └──────────────────┘  └──────────────────┘  └──────────────────────┘  ││
│  └─────────────────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
           ┌───────────────────────────┼───────────────────────────┐
           │                           │                           │
           ▼                           ▼                           ▼
┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────────┐
│    User Service     │  │   Delegate Graph    │  │   Eligibility Graph     │
│    (REST API)       │  │    (GraphQL)        │  │      (GraphQL)          │
└─────────────────────┘  └─────────────────────┘  └─────────────────────────┘
```

## Component Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              BFF APPLICATION                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  CONFIG                          SECURITY                                    │
│  ┌──────────────────────┐       ┌──────────────────────────────────────┐   │
│  │ BffProperties        │       │ SecurityConfig                       │   │
│  │ - session.*          │       │ - mfeSecurityFilterChain (Order 1)   │   │
│  │ - cache.*            │       │ - browserSecurityFilterChain (Order 2)│   │
│  │ - client.*           │       │ - publicSecurityFilterChain (Order 3) │   │
│  │ - eligibility.*      │       └──────────────────────────────────────┘   │
│  └──────────────────────┘                                                   │
│                                                                              │
│  FILTERS                                                                     │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌────────────────────┐│
│  │OriginValidationFilter│  │PartnerAuthFilter     │  │BrowserSessionFilter││
│  │(Order -150, Global)  │  │(MFE Chain)           │  │(Browser Chain)     ││
│  └──────────────────────┘  └──────────────────────┘  └────────────────────┘│
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌────────────────────┐│
│  │DelegateEnterpriseId  │  │PersonaAuthorization  │  │MfeRouteValidator   ││
│  │Filter (Both Chains)  │  │Filter (Both Chains)  │  │(MFE Chain Only)    ││
│  └──────────────────────┘  └──────────────────────┘  └────────────────────┘│
│  ┌──────────────────────┐                                                   │
│  │MfePathRewriteFilter  │                                                   │
│  │(MFE Chain Only)      │                                                   │
│  └──────────────────────┘                                                   │
│                                                                              │
│  CONTEXT                                                                     │
│  ┌──────────────────────┐  ┌──────────────────────┐                        │
│  │ AuthContext          │  │ AuthContextHolder    │                        │
│  │ - enterpriseId       │  │ - Reactor Context    │                        │
│  │ - loggedInMemberId   │  │ - ThreadLocal-like   │                        │
│  │ - memberIdType       │  │   for reactive       │                        │
│  │ - persona            │  └──────────────────────┘                        │
│  └──────────────────────┘                                                   │
│                                                                              │
│  SESSION                                                                     │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌────────────────────┐│
│  │ BffSession           │  │ SessionStore         │  │SessionCookieManager││
│  │ (Data Record)        │  │ (Interface)          │  │(Cookie Operations) ││
│  └──────────────────────┘  └──────────────────────┘  └────────────────────┘│
│           │                         │                                       │
│           │                         ├──────────────────────┐               │
│           ▼                         ▼                      ▼               │
│  ┌──────────────────────┐  ┌──────────────────┐  ┌────────────────────┐   │
│  │ InMemorySessionStore │  │ RedisSessionStore│  │ (Toggle via config)│   │
│  └──────────────────────┘  └──────────────────┘  └────────────────────┘   │
│                                                                              │
│  CLIENTS                                                                     │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌────────────────────┐│
│  │ UserServiceClient    │  │ DelegateGraphClient  │  │EligibilityGraphClient│
│  └──────────────────────┘  └──────────────────────┘  └────────────────────┘│
│                                                                              │
│  CACHE                                                                       │
│  ┌──────────────────────┐  ┌──────────────────────┐                        │
│  │ ClientCache          │  │ InMemory / Redis     │                        │
│  │ (Interface)          │  │ (Implementations)    │                        │
│  └──────────────────────┘  └──────────────────────┘                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Package Structure

```
com.example.bff/
├── config/
│   ├── BffProperties.java          # Application configuration
│   ├── SecurityConfig.java         # Security filter chains
│   ├── WebClientConfig.java        # HTTP client configuration
│   └── CacheConfig.java            # Cache configuration
│
├── security/
│   ├── context/
│   │   ├── AuthContext.java        # Authentication context record
│   │   ├── AuthContextHolder.java  # Reactor context accessor
│   │   ├── Persona.java            # Persona enum
│   │   ├── MemberIdType.java       # Member ID type enum
│   │   └── DelegateType.java       # Delegate type enum
│   │
│   ├── filter/
│   │   ├── OriginValidationFilter.java     # Origin header validation
│   │   ├── BrowserSessionFilter.java       # Cookie session auth
│   │   ├── PartnerAuthenticationFilter.java # Header-based auth
│   │   ├── DelegateEnterpriseIdFilter.java # Delegate validation
│   │   ├── PersonaAuthorizationFilter.java # Persona access control
│   │   ├── MfePathRewriteFilter.java       # Path rewriting
│   │   └── MfeRouteValidator.java          # @MfeEnabled validation
│   │
│   ├── session/
│   │   ├── BffSession.java            # Session data record
│   │   ├── SessionStore.java          # Session storage interface
│   │   ├── InMemorySessionStore.java  # In-memory implementation
│   │   ├── RedisSessionStore.java     # Redis implementation
│   │   └── SessionCookieManager.java  # Cookie operations
│   │
│   ├── annotation/
│   │   ├── RequiredPersona.java    # Persona requirement annotation
│   │   └── MfeEnabled.java         # MFE access marker annotation
│   │
│   └── exception/
│       ├── AuthenticationException.java   # Auth failures
│       └── AuthorizationException.java    # Authz failures
│
├── client/
│   ├── UserServiceClient.java      # User service API client
│   ├── DelegateGraphClient.java    # Delegate GraphQL client
│   └── EligibilityGraphClient.java # Eligibility GraphQL client
│
├── cache/
│   ├── ClientCache.java            # Cache interface
│   ├── InMemoryClientCache.java    # In-memory cache
│   └── RedisClientCache.java       # Redis cache
│
├── controller/
│   ├── LoginController.java        # OIDC login/callback
│   ├── UserController.java         # User endpoints
│   ├── DelegateController.java     # Delegate endpoints
│   └── EligibilityController.java  # Eligibility endpoints
│
└── BffApplication.java             # Application entry point
```

## Deployment Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                    AWS                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                         VPC                                              ││
│  │                                                                          ││
│  │  ┌──────────────────────────────────────────────────────────────────┐  ││
│  │  │                      Public Subnet                                │  ││
│  │  │  ┌────────────────────────────────────────────────────────────┐  │  ││
│  │  │  │              Application Load Balancer                      │  │  ││
│  │  │  │  - HTTPS Termination                                        │  │  ││
│  │  │  │  - mTLS for /mfe/** paths                                   │  │  ││
│  │  │  │  - WAF Integration                                          │  │  ││
│  │  │  └────────────────────────────────────────────────────────────┘  │  ││
│  │  └──────────────────────────────────────────────────────────────────┘  ││
│  │                                    │                                    ││
│  │  ┌──────────────────────────────────────────────────────────────────┐  ││
│  │  │                     Private Subnet                                │  ││
│  │  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │  ││
│  │  │  │   BFF Pod 1     │  │   BFF Pod 2     │  │   BFF Pod N     │  │  ││
│  │  │  │  (EKS/ECS)      │  │  (EKS/ECS)      │  │  (EKS/ECS)      │  │  ││
│  │  │  └─────────────────┘  └─────────────────┘  └─────────────────┘  │  ││
│  │  │                                                                   │  ││
│  │  │  ┌─────────────────────────────────────────────────────────────┐│  ││
│  │  │  │                    ElastiCache (Redis)                      ││  ││
│  │  │  │  - Session Storage (Production)                             ││  ││
│  │  │  │  - API Response Cache                                       ││  ││
│  │  │  └─────────────────────────────────────────────────────────────┘│  ││
│  │  └──────────────────────────────────────────────────────────────────┘  ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Security Boundaries

```
                    INTERNET
                        │
                        ▼
    ┌───────────────────────────────────────┐
    │           SECURITY ZONE 1             │
    │         (Public Internet)             │
    │                                       │
    │  - TLS 1.3 encryption                 │
    │  - DDoS protection (CloudFront/WAF)   │
    │  - Rate limiting                      │
    └───────────────────┬───────────────────┘
                        │
                        ▼
    ┌───────────────────────────────────────┐
    │           SECURITY ZONE 2             │
    │         (ALB / Edge Layer)            │
    │                                       │
    │  - mTLS termination for partners      │
    │  - Certificate validation             │
    │  - Request inspection                 │
    └───────────────────┬───────────────────┘
                        │
                        ▼
    ┌───────────────────────────────────────┐
    │           SECURITY ZONE 3             │
    │         (BFF Application)             │
    │                                       │
    │  - Origin validation                  │
    │  - Session validation                 │
    │  - Persona authorization              │
    │  - Cookie security (Strict)           │
    └───────────────────┬───────────────────┘
                        │
                        ▼
    ┌───────────────────────────────────────┐
    │           SECURITY ZONE 4             │
    │         (Backend Services)            │
    │                                       │
    │  - Bearer token authentication        │
    │  - Service mesh (optional)            │
    │  - Network policies                   │
    └───────────────────────────────────────┘
```
