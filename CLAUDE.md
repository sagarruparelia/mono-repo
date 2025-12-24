# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

### Nx Workspace (Root)
```bash
# Install dependencies
npm install

# Build all projects
npx nx run-many --target=build --all

# Run all tests
npx nx run-many --target=test --all

# Lint all projects
npx nx run-many --target=lint --all
```

### Frontend Apps (React/Vite)
```bash
# Development server
npx nx serve web-cl              # Main client shell (port 4200)
npx nx serve mfe-summary         # Summary micro-frontend
npx nx serve mfe-profile         # Profile micro-frontend

# Build
npx nx build web-cl
npx nx build mfe-summary
npx nx build-wc mfe-summary      # Build as web component

# Test (Vitest)
npx nx test web-cl
npx nx test mfe-summary
npx nx test mfe-summary --watch  # Watch mode
```

### BFF (Spring Boot/Java 25)
```bash
# Build
npx nx build bff                 # or: ./apps/bff/mvnw package
./apps/bff/mvnw clean compile    # Compile only

# Run
npx nx serve bff                 # or: ./apps/bff/mvnw spring-boot:run

# Test
npx nx test bff                  # or: ./apps/bff/mvnw test
./apps/bff/mvnw test -Dtest=ClassName  # Single test class
```

### E2E Tests (Playwright)
```bash
npx nx e2e web-cl-e2e
BASE_URL=http://localhost:3000 npx nx e2e web-cl-e2e  # Against specific URL
```

### Docker E2E Stack
```bash
docker-compose -f docker-compose.e2e.yml up --build
docker-compose -f docker-compose.e2e.yml down -v
```

## Architecture Overview

This is an Nx monorepo with a React frontend (micro-frontends) and Spring Boot BFF.

### Project Structure
```
apps/
├── bff/                 # Spring Boot Backend-for-Frontend (Java 25)
├── web-cl/              # Main React shell app (TanStack Router)
├── web-cl-e2e/          # Playwright E2E tests for web-cl
├── web-hs/              # Health summary React app
├── mfe-summary/         # Summary micro-frontend (Web Component)
├── mfe-profile/         # Profile micro-frontend (Web Component)
libs/
├── shared-state/        # Zustand store, React Query hooks, API client
├── shared-ui/           # Shared UI components
```

### Key TypeScript Imports
```typescript
import { useAuthStore, api, queryClient } from '@mono-repo/shared-state';
import { useImmunizations, useAllergies } from '@mono-repo/shared-state';
```

### BFF Security Architecture

The BFF implements dual authentication flows:

1. **Browser Flow** (`/api/v1/**`): Cookie-based sessions via HSID OAuth2/OIDC
   - Session stored in Redis (or in-memory for dev)
   - CSRF protection via `XSRF-TOKEN` cookie
   - Origin validation for CORS

2. **Partner MFE Flow** (`/mfe/api/v1/**`): mTLS header-based auth
   - ALB terminates mTLS and injects headers
   - No cookies, stateless per-request auth

### BFF Filter Chain Order
1. `OriginValidationFilter` - Validates Origin header (browser paths only)
2. `BrowserSessionFilter` / `PartnerAuthenticationFilter` - Auth based on path
3. `DelegateEnterpriseIdFilter` - Validates delegate access
4. `PersonaAuthorizationFilter` - Enforces `@RequiredPersona` annotations

### Persona Types
- **SELF**: Logged-in user accessing own data
- **DELEGATE**: User acting on behalf of a dependent (e.g., parent for child)
- **AGENT**: Partner system operator
- **CASE_WORKER**, **CONFIG_SPECIALIST**: External partner personas

### BFF Configuration
Key properties in `apps/bff/src/main/resources/application.yml`:
- `bff.session.*` - Cookie domain, TTL, store type (memory/redis)
- `bff.client.*` - External API client configuration
- `bff.eligibility.*` - Eligibility graph client settings

### Micro-Frontend Pattern
MFEs are built as Web Components for embedding in partner sites:
```bash
npx nx build-wc mfe-summary  # Outputs to dist/mfe/summary/
```

The BFF rewrites `/mfe/api/v1/**` to `/api/v1/**` after authentication.

## Key Documentation
Detailed architecture docs are in `apps/bff/docs/`:
- `REFERENCE_ARCHITECTURE.md` - System diagrams and package structure
- `FLOW_ARCHITECTURE.md` - Authentication and request flow diagrams
- `SECURITY.md` - Cookie settings, CSRF, origin validation
- `CONFIGURATION.md` - Environment variables and properties
