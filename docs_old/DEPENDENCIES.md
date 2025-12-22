# Dependency Matrix & Compatibility

## Frontend Dependencies

### Core Stack
| Package | Version | React 19 | Notes |
|---------|---------|----------|-------|
| `react` | 19.2.3 | - | Currently installed |
| `react-dom` | 19.2.3 | - | Currently installed |
| `vite` | 7.2.7 | Compatible | Currently installed via NX |

### State Management

#### Zustand (Recommended)
| Package | Version | React 19 | MFE Compatible |
|---------|---------|----------|----------------|
| `zustand` | 5.0.9 | `>=18.0.0` | Yes |

**Why Zustand for MFEs:**
- No Provider wrapper required (unlike Redux/Context)
- Each MFE can have isolated stores
- Stores can be shared across MFEs when needed via import
- Minimal bundle size (~1.1kb gzipped)
- Works outside React components
- Built-in middleware: `persist`, `devtools`, `immer`

```typescript
// Isolated store per MFE (recommended)
// mfe-summary/src/stores/summaryStore.ts
import { create } from 'zustand';

interface SummaryState {
  data: Summary | null;
  loading: boolean;
  fetch: (userId: string) => Promise<void>;
}

export const useSummaryStore = create<SummaryState>((set) => ({
  data: null,
  loading: false,
  fetch: async (userId) => {
    set({ loading: true });
    const data = await api.getSummary(userId);
    set({ data, loading: false });
  },
}));
```

#### Peer Dependency Note
Zustand 5.x peer dependency is `react: >=18.0.0`. Works with React 19, but npm may show warnings. Safe to use `--legacy-peer-deps` if needed.

### Server State Management

#### TanStack React Query (Recommended)
| Package | Version | React 19 | Notes |
|---------|---------|----------|-------|
| `@tanstack/react-query` | 5.90.12 | `^18 \|\| ^19` | Explicit React 19 support |
| `@tanstack/react-query-devtools` | 5.90.12 | `^18 \|\| ^19` | DevTools (dev only) |

**Why React Query:**
- Handles caching, revalidation, background refetch
- Deduplicates requests
- Stale-while-revalidate pattern
- Works perfectly with Zustand (different concerns)
- Built-in retry, pagination, infinite scroll

```typescript
// Zustand for client state, React Query for server state
// This is the recommended pattern in 2025

// hooks/useUserData.ts
import { useQuery } from '@tanstack/react-query';

export const useUserData = (userId: string) => {
  return useQuery({
    queryKey: ['user', userId],
    queryFn: () => api.getUser(userId),
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

// stores/uiStore.ts (Zustand for UI state)
export const useUIStore = create<UIState>((set) => ({
  sidebarOpen: false,
  theme: 'light',
  toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen })),
}));
```

### MFE Distribution

| Package | Version | Purpose |
|---------|---------|---------|
| `@module-federation/vite` | 1.x | Module federation for Vite |
| `@nx/module-federation` | 22.2.3 | Already installed via NX |

**Already Available:** NX already includes `@module-federation/enhanced@0.21.6` - no additional setup needed for module federation.

### Routing
| Package | Version | React 19 | Notes |
|---------|---------|----------|-------|
| `react-router-dom` | 6.29.0 | Compatible | Currently installed |

---

## Backend Dependencies (Spring Boot 4.0)

### Current Dependencies (pom.xml)
```xml
<!-- Already present -->
spring-boot-starter-actuator
spring-boot-starter-data-mongodb-reactive
spring-boot-starter-elasticsearch
spring-boot-starter-webflux
```

### Required Additions

#### Authentication & Session
| Dependency | Purpose | Spring Boot 4 Compatible |
|------------|---------|--------------------------|
| `spring-boot-starter-oauth2-client` | OIDC PKCE flow | Yes (Spring Security 7.0) |
| `spring-session-data-redis` | Session storage in Valkey | Yes (Spring Session 4.0) |
| `spring-boot-starter-data-redis-reactive` | Reactive Redis/Valkey client | Yes |

#### Security
| Dependency | Purpose | Notes |
|------------|---------|-------|
| `spring-security-oauth2-jose` | JWT/JOSE validation | Included in oauth2-client |
| `spring-security-oauth2-resource-server` | For validating client credentials tokens | For MFE proxy auth |

### Updated pom.xml Dependencies

```xml
<dependencies>
    <!-- Existing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb-reactive</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>

    <!-- NEW: OAuth2 Client for OIDC PKCE -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>

    <!-- NEW: OAuth2 Resource Server (for MFE proxy validation) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>

    <!-- NEW: Session with Redis/Valkey -->
    <dependency>
        <groupId>org.springframework.session</groupId>
        <artifactId>spring-session-data-redis</artifactId>
    </dependency>

    <!-- NEW: Reactive Redis for Valkey -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
    </dependency>

    <!-- Optional: For JSON serialization of session -->
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>
</dependencies>
```

### Valkey/ElastiCache Compatibility

| Component | Version | Valkey 8.x Compatible |
|-----------|---------|----------------------|
| Lettuce | 6.5.x (via Spring) | Yes - drop-in replacement |
| Spring Data Redis | 3.4.x | Yes - best effort support |
| Spring Session Redis | 4.0.x | Yes |

**AWS ElastiCache Note:** Lettuce 6.2.2+ is recommended by AWS for ElastiCache. Spring Boot 4.0 includes Lettuce 6.5.x.

---

## Dependency Installation Commands

### Frontend (package.json additions)
```bash
# State management
npm install zustand@^5.0.9

# Server state
npm install @tanstack/react-query@^5.90.12

# Dev tools (optional)
npm install -D @tanstack/react-query-devtools@^5.90.12
```

### Backend (Maven)

**Spring Boot Version: 3.5.8** (Stable, released Nov 2025)

Includes:
- Spring Framework 6.2.12
- Spring Security 6.5.6
- Spring Session 3.5.3
- Spring Data 2025.0.5

Dependencies added to pom.xml above. Run:
```bash
cd apps/bff && ./mvnw dependency:resolve
```

---

## Compatibility Matrix Summary

| Layer | Package | Version | Status |
|-------|---------|---------|--------|
| Frontend | React | 19.2.3 | Installed |
| Frontend | Vite | 7.2.7 | Installed |
| Frontend | Zustand | 5.0.9 | To Install |
| Frontend | React Query | 5.90.12 | To Install |
| Frontend | Module Federation | 0.21.6 | Installed (via NX) |
| Backend | Spring Boot | **3.5.8** | To Update |
| Backend | Spring Security | 6.5.6 | Via Boot 3.5.8 |
| Backend | Spring Session | 3.5.3 | Via Boot 3.5.8 |
| Backend | Spring Framework | 6.2.12 | Via Boot 3.5.8 |
| Backend | Lettuce | 6.4.x | Via Boot 3.5.8 |
| Infra | Valkey | **8.1.5** | Required (CVE fix) |

---

## MFE State Sharing Strategy

### Option A: Isolated Stores (Recommended for External MFEs)
```
┌─────────────────┐     ┌─────────────────┐
│   mfe-summary   │     │   mfe-profile   │
│  ┌───────────┐  │     │  ┌───────────┐  │
│  │ Zustand   │  │     │  │ Zustand   │  │
│  │ Store     │  │     │  │ Store     │  │
│  └───────────┘  │     │  └───────────┘  │
└─────────────────┘     └─────────────────┘
        │                       │
        └───────────┬───────────┘
                    │
           React Query Cache
                    │
                    ▼
                  BFF
```

### Option B: Shared Store (For web-cl internal MFEs)
```
┌──────────────────────────────────────────┐
│                web-cl                     │
│  ┌────────────────────────────────────┐  │
│  │         Shared Zustand Store        │  │
│  │  (auth state, user preferences)     │  │
│  └────────────────────────────────────┘  │
│       │               │                  │
│  ┌────┴────┐    ┌────┴────┐             │
│  │mfe-sum  │    │mfe-prof │             │
│  │(import) │    │(import) │             │
│  └─────────┘    └─────────┘             │
└──────────────────────────────────────────┘
```

### Implementation

```typescript
// libs/shared-state/src/types/persona.ts
export type HsidPersona = 'individual' | 'parent';
export type ProxyPersona = 'agent' | 'config' | 'case_worker';
export type Persona = HsidPersona | ProxyPersona;

// libs/shared-state/src/authStore.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AuthState {
  isAuthenticated: boolean;
  persona: Persona | null;

  // HSID context (web-cl direct login)
  user?: { sub: string; name: string };
  dependents?: string[];        // For parent persona
  sessionExpiry?: number;

  // Proxy context (MFE in external portal)
  memberId?: string;            // Target member
  operatorId?: string;          // Portal user
  operatorName?: string;
}

export const useAuthStore = create<AuthState>()(
  persist(
    () => ({
      isAuthenticated: false,
      persona: null,
    }),
    { name: 'auth-storage' }
  )
);

// When MFE used in web-cl: imports store directly
// import { useAuthStore } from '@mono/shared-state';

// When MFE used in external portal: receives context via props
// <mfe-summary member-id="123" persona="agent" operator-id="456" />
```

### Persona Access Patterns

| Persona | Access Pattern |
|---------|----------------|
| `individual` | Own data only |
| `parent` | Own + dependents data |
| `agent` | Any member (via X-Member-Id header) |
| `config` | System configuration |
| `case_worker` | Any member + case management |

---

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| Zustand peer dep warning with React 19 | Use `--legacy-peer-deps` or wait for Zustand 5.1 |
| Spring Boot 4.0 is very new (Nov 2025) | Monitor Spring blog for patches; fallback to 3.5.x if needed |
| Valkey vs Redis API drift | Stick to common commands; Spring Data Redis supports both |
| Module Federation version conflicts | NX manages this; avoid manual version overrides |
| **CVE-2025-49844 (RediShell)** | **CRITICAL: Require Valkey 8.2.2+** |
| @nxrocks/nx-spring-boot vulnerability | Downgrade to 8.1.0 (js-yaml prototype pollution) |

---

## Security Requirements

### Infrastructure

```yaml
# CRITICAL: Valkey/ElastiCache minimum versions
infrastructure:
  valkey:
    version: "8.1.5"              # Latest stable (Dec 2025)
    min-version: "8.1.1"          # Fixes CVE-2025-49844 (CVSS 10.0)
    encryption-at-rest: true
    encryption-in-transit: true
    auth-token-required: true
    acl-enabled: true             # Use ACLs, disable Lua if not needed
```

### Known Vulnerabilities (Addressed)

| CVE | Component | Severity | Fixed Version | Status |
|-----|-----------|----------|---------------|--------|
| CVE-2025-49844 | Redis/Valkey | CRITICAL (10.0) | 8.2.2+ | Requires upgrade |
| CVE-2025-21605 | Redis/Valkey | HIGH (7.5) | 8.2.2+ | Requires upgrade |
| GHSA-mh29-5h37-fv8m | js-yaml | MODERATE | N/A | Downgrade @nxrocks |
