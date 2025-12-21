# Authorization Refactoring Plan

## Problem Statement

Current `AuthPrincipal` structure has issues with `activeDelegateTypes` and `permissions`. The permission model needs to support per-dependent permissions, not global delegate types.

## Current State

### What exists today:
1. `@RequirePersona` + `PersonaAuthorizationFilter` - checks persona and delegate types globally
2. ABAC system - config-driven policies with per-resource permission checks
3. Inline `authorizeAndExecute` in controllers - duplicates authorization logic

### Problems:
1. `AuthPrincipal.hasAllDelegates()` checks global delegate types, not per-dependent
2. Permission structure doesn't match delegate-graph API response
3. ABAC and @RequirePersona overlap, causing confusion
4. No validation of permissions for specific enterpriseId from request header

---

## Target State

### delegate-graph API Response Structure
```json
[
  {
    "eid": "dependent-enterprise-id-1",
    "startDate": "2024-01-15",
    "stopDate": "2025-12-31",
    "delegateType": "DAA",
    "active": true
  },
  {
    "eid": "dependent-enterprise-id-1",
    "startDate": "2024-01-15",
    "stopDate": "2025-12-31",
    "delegateType": "RPR",
    "active": true
  },
  {
    "eid": "dependent-enterprise-id-2",
    "startDate": "2024-03-01",
    "stopDate": null,
    "delegateType": "DAA",
    "active": false
  }
]
```

### New AuthPrincipal Permission Structure
```java
public record AuthPrincipal(
    // ... existing fields ...

    // NEW: Per-dependent permissions
    // Key: enterpriseId (eid) of dependent
    // Value: Map of delegateType -> DelegatePermission
    Map<String, Map<DelegateType, DelegatePermission>> dependentPermissions
) {

    public record DelegatePermission(
        LocalDate startDate,
        LocalDate stopDate,      // nullable - no end date
        boolean active
    ) {
        public boolean isCurrentlyValid() {
            if (!active) return false;
            LocalDate today = LocalDate.now(ZoneId.of("America/Chicago")); // CST
            boolean afterStart = !today.isBefore(startDate);
            boolean beforeStop = stopDate == null || !today.isAfter(stopDate);
            return afterStart && beforeStop;
        }
    }

    /**
     * Check if user has valid permissions for a specific dependent.
     * @param dependentEid The dependent's enterprise ID
     * @param requiredTypes The delegate types required (e.g., DAA, RPR, ROI)
     * @return true if user has ALL required permissions that are currently valid
     */
    public boolean hasPermissionsFor(String dependentEid, Set<DelegateType> requiredTypes) {
        Map<DelegateType, DelegatePermission> perms = dependentPermissions.get(dependentEid);
        if (perms == null) return false;

        return requiredTypes.stream().allMatch(type -> {
            DelegatePermission perm = perms.get(type);
            return perm != null && perm.isCurrentlyValid();
        });
    }

    /**
     * Check if user qualifies as DELEGATE persona.
     * Requires at least one dependent with active DAA + RPR permissions.
     */
    public boolean isValidDelegate() {
        if (persona != Persona.DELEGATE) return false;

        return dependentPermissions.values().stream().anyMatch(perms -> {
            DelegatePermission daa = perms.get(DelegateType.DAA);
            DelegatePermission rpr = perms.get(DelegateType.RPR);
            return daa != null && daa.isCurrentlyValid()
                && rpr != null && rpr.isCurrentlyValid();
        });
    }
}
```

---

## Authorization Approach Decision

### Option A: Keep @RequirePersona + Enhance with Per-Dependent Check
- Keep `@RequirePersona` for coarse-grained persona checks
- Add per-dependent permission validation in filter or controller
- Remove ABAC entirely

### Option B: Use ABAC for Everything
- Move all authorization to ABAC
- Remove @RequirePersona
- ABAC handles both persona and per-dependent permissions

### Option C: Hybrid (Recommended for MVP)
- `@RequirePersona` for persona check (is user a DELEGATE, INDIVIDUAL_SELF, etc.)
- Add `@RequirePermissions` annotation for per-dependent permission check
- New filter validates permissions for enterpriseId from request header
- Remove ABAC (not needed for MVP with category-level ROI)

---

## Recommended Implementation (Option C)

### Phase 1: Fix AuthPrincipal Structure

1. **Create DelegatePermission record**
   - File: `auth/model/DelegatePermission.java`
   - Fields: startDate, stopDate, active
   - Method: `isCurrentlyValid()` with CST timezone

2. **Update AuthPrincipal**
   - Replace `activeDelegateTypes` with `Map<String, Map<DelegateType, DelegatePermission>> dependentPermissions`
   - Add `hasPermissionsFor(String dependentEid, Set<DelegateType> requiredTypes)`
   - Add `isValidDelegate()` method

3. **Update DualAuthWebFilter / Authentication**
   - Parse delegate-graph API response into new structure
   - Populate `dependentPermissions` map

### Phase 2: Create Per-Dependent Permission Validation

1. **Create @RequirePermissions annotation**
   ```java
   @Target(ElementType.METHOD)
   @Retention(RetentionPolicy.RUNTIME)
   public @interface RequirePermissions {
       DelegateType[] value();  // Required permissions (DAA, RPR, ROI)
   }
   ```

2. **Create PermissionValidationFilter**
   - Runs after PersonaAuthorizationFilter
   - For DELEGATE persona:
     - Extract enterpriseId from request header
     - Validate `principal.hasPermissionsFor(enterpriseId, requiredTypes)`
     - Return 403 if permissions not valid for this dependent
   - For INDIVIDUAL_SELF: enterpriseId must match principal's own ID
   - For PROXY: Skip (authorization delegated to consumer)

3. **Update Controllers**
   ```java
   @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, ...})
   @RequirePermissions({DelegateType.DAA, DelegateType.RPR})  // For this dependent
   @GetMapping("/immunization")
   public Mono<ResponseEntity<...>> getImmunizations(...) {
       // No inline authorization - handled by filters
   }
   ```

### Phase 3: Remove ABAC

Delete the following:
```
apps/bff/src/main/java/com/example/bff/authz/abac/  (entire folder)
apps/bff/src/main/java/com/example/bff/authz/filter/AuthorizationFilter.java
apps/bff/src/main/resources/config/abac-policies.yml
```

Remove from controllers:
- `authorizeAndExecute` methods
- `AbacAuthorizationService` injection and usage

### Phase 4: Update Tests

- Update AuthPrincipal tests for new structure
- Update PersonaAuthorizationFilter tests
- Add PermissionValidationFilter tests
- Remove ABAC-related tests

---

## Validation Flow (After Implementation)

```
Request with enterpriseId header
         │
         ▼
┌─────────────────────────────────┐
│   DualAuthWebFilter             │
│   - Authenticate user           │
│   - Load permissions from       │
│     delegate-graph API          │
│   - Build AuthPrincipal with    │
│     dependentPermissions map    │
└─────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│   PersonaAuthorizationFilter    │
│   - Check @RequirePersona       │
│   - Validate persona is allowed │
│   - For DELEGATE: check         │
│     isValidDelegate() (has any  │
│     dependent with DAA+RPR)     │
└─────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│   PermissionValidationFilter    │  ← NEW
│   - Check @RequirePermissions   │
│   - Extract enterpriseId header │
│   - For DELEGATE: validate      │
│     hasPermissionsFor(eid,      │
│     requiredTypes)              │
│   - For INDIVIDUAL_SELF: ensure │
│     eid matches principal's eid │
└─────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│   Controller                    │
│   - No authorization logic      │
│   - Just business logic         │
└─────────────────────────────────┘
```

---

## Files to Create/Modify

### Create:
- `auth/model/DelegatePermission.java`
- `authz/annotation/RequirePermissions.java`
- `authz/filter/PermissionValidationFilter.java`

### Modify:
- `auth/model/AuthPrincipal.java` - new permission structure
- `auth/filter/DualAuthWebFilter.java` - populate new structure
- `health/controller/HealthDataController.java` - remove authorizeAndExecute
- `document/controller/DocumentController.java` - remove authorizeAndExecute

### Delete:
- `authz/abac/` (entire folder - 10 files)
- `authz/filter/AuthorizationFilter.java`
- `src/main/resources/config/abac-policies.yml`

---

## MVP Scope

For MVP with category-level ROI:
- All health data requires same permissions (DAA + RPR + ROI or DAA + RPR)
- No per-field or per-subcategory permission differences
- PROXY auth skips validation (delegated to consumer)

This approach:
- Clean annotation-based authorization
- Per-dependent permission validation
- No ABAC complexity
- Easy to extend post-MVP for fine-grained permissions
