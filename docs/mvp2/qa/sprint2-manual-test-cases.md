# Sprint 2 (MVP2-1) — Manual Test Cases

**Sprint:** MVP2-Sprint 1 (Apr 28 – May 9, 2026)  
**Tester:** QA / Manual Tester  
**Prepared:** 2026-05-03  
**Environment:** Local (frontend: `http://localhost:3000`, backend: `http://localhost:8080`)

---

## Scope

| Feature Area | Stories | Priority |
|---|---|---|
| FE-01 — JWT extended claims in AuthContext | MVP2-FE-001 | P0 |
| FE-02 — TenantConfigContext (feature flags) | MVP2-FE-002 | P0 |
| FE-03 — AppShell nav filtering by featureFlag & role | MVP2-FE-003 | P0 |
| FE-04 — ProtectedRoute multi-role (requiredRoles[]) | MVP2-FE-004 | P0 |
| FE-05 — ProtectedRoute scope check (requiredScope) | MVP2-FE-005 | P0 |
| FE-06 — X-Tenant-Id header in API client | MVP2-FE-006 | P1 |
| BE-01 — EntityNotFoundException → 404 | MVP2-BE-001 | P0 |
| BE-02 — GET /api/v1/tenant/config | MVP2-BE-002 | P1 |
| BE-03 — Tenant data isolation (RLS) | MVP2-BE-003 | P1 |

---

## Test Cases

### TC-S2-001: JWT tenant claims parsed in AuthContext

**Feature:** FE-01  
**Priority:** P0  
**Type:** Manual + Automated (unit: `authTenantClaims.test.ts`)

**Preconditions:**
- Backend running at `http://localhost:8080`
- Test user `admin` with JWT containing `tenant_id: "default"`, `scopes: []`

**Steps:**
1. Open browser DevTools → Application → Local Storage
2. Navigate to `http://localhost:3000/login`
3. Login as `admin` / `admin_Dev#2026!`
4. After redirect to `/dashboard`, open DevTools → Application → Local Storage → find `accessToken`
5. Decode the JWT payload (use `https://jwt.io`)
6. Verify claims in the JWT payload

**Expected Results:**
- JWT contains `tenant_id` field (string, e.g. `"default"`)
- JWT contains `tenant_path` field (e.g. `"city.default"`)
- JWT contains `scopes` array (empty `[]` for default admin)
- JWT contains `allowed_buildings` array
- The decoded user object in React DevTools (`AuthContext`) shows `tenantId: "default"`

**Pass/Fail:** ___  
**Notes:** ___

---

### TC-S2-002: TenantConfigContext fetches and caches feature flags

**Feature:** FE-02  
**Priority:** P0  
**Type:** Manual

**Preconditions:**
- Backend running
- `GET /api/v1/tenant/config` returns valid `TenantConfigResponse`

**Steps:**
1. Open browser DevTools → Network tab → filter by `tenant/config`
2. Login as admin
3. Observe network request to `GET /api/v1/tenant/config`
4. Verify response has correct shape
5. Navigate to another page and back
6. Check if `tenant/config` is re-fetched or served from cache

**Expected Results:**
- `GET /api/v1/tenant/config` is called once after login
- Response shape:
  ```json
  {
    "tenantId": "...",
    "features": { "<flag>": { "enabled": true|false } },
    "branding": { "partnerName": "...", "primaryColor": "...", "logoUrl": null|"..." }
  }
  ```
- Subsequent navigations reuse cached data (no duplicate network requests within cache TTL)
- No console errors related to tenant config

**Pass/Fail:** ___  
**Notes:** ___

---

### TC-S2-003: AppShell nav filtering — ROLE_TENANT_ADMIN + enabled flag

**Feature:** FE-03  
**Priority:** P0  
**Type:** Manual

**Preconditions:**
- Backend has a user with `ROLE_TENANT_ADMIN` role
- Tenant config for that user has `tenant_management: { enabled: true }`

**Steps:**
1. Login as a `ROLE_TENANT_ADMIN` user
2. Observe sidebar navigation items

**Expected Results:**
- "Tenant Admin" nav item IS visible in sidebar
- Clicking "Tenant Admin" navigates to `/tenant-admin`

**Known Issue:** `/tenant-admin` route is NOT defined (BUG-001). Page will be blank after navigation.

**Pass/Fail:** ___  
**Notes:** Blocked by BUG-001 — route missing in `frontend/src/routes/index.tsx`

---

### TC-S2-004: AppShell nav filtering — flag disabled hides item

**Feature:** FE-03  
**Priority:** P0  
**Type:** Manual

**Preconditions:**
- Any user logged in
- Tenant config has `tenant_management: { enabled: false }` (or flag is absent)

**Steps:**
1. Login as admin
2. Check sidebar navigation

**Expected Results:**
- "Tenant Admin" nav item is NOT visible regardless of user role

**Pass/Fail:** ___  
**Notes:** ___

---

### TC-S2-005: ProtectedRoute — requiredRoles[] multi-role enforcement

**Feature:** FE-04  
**Priority:** P0  
**Type:** Manual

**Preconditions:**
- Users available: `admin` (ROLE_ADMIN), `operator` (ROLE_OPERATOR), `citizen1` (ROLE_CITIZEN)
- `/ai-workflow` route uses `requiredRoles: ['ROLE_ADMIN', 'ROLE_OPERATOR']`

**Steps (ROLE_CITIZEN blocked):**
1. Login as `citizen1`
2. Navigate directly to `http://localhost:3000/ai-workflow`

**Expected Results (Step 2):**
- User is redirected to `/dashboard`
- No flash of AI Workflow content before redirect

**Steps (ROLE_OPERATOR allowed):**
3. Logout; login as `operator`
4. Navigate to `http://localhost:3000/ai-workflow`

**Expected Results (Step 4):**
- AI Workflow page loads successfully
- No redirect occurs

**Pass/Fail:** ___  
**Notes:** ___

---

### TC-S2-006: ProtectedRoute — requiredScope enforcement

**Feature:** FE-05  
**Priority:** P0  
**Type:** Manual

**Preconditions:**
- A user whose JWT includes a specific scope (e.g. `environment:write`)
- A user whose JWT does NOT include that scope

**Steps:**
1. Login as a user without `environment:write` scope
2. Navigate to any route that requires `requiredScope: "environment:write"`
3. Verify redirect occurs
4. Login as a user WITH `environment:write` scope
5. Navigate to the same route

**Expected Results:**
- Step 2–3: User without scope is redirected to `/dashboard`
- Step 4–5: User with scope can access the route

**Pass/Fail:** ___  
**Notes:** ___

---

### TC-S2-007: X-Tenant-Id header in API requests

**Feature:** FE-06  
**Priority:** P1  
**Type:** Manual

**Preconditions:**
- Backend running
- Any user logged in

**Steps:**
1. Open DevTools → Network tab
2. Login as admin
3. Navigate to `/environment`
4. Observe any API request (e.g. `GET /api/v1/environment/sensors`)
5. Check request headers

**Expected Results:**
- For default tenant (no super-admin override): `X-Tenant-Id` header is absent or empty in the request
- No console errors related to the tenant header
- (If super-admin override is set via `tenantStore.set(id)`, header should be present with the correct value)

**Pass/Fail:** ___  
**Notes:** ___

---

### TC-S2-008: EntityNotFoundException → HTTP 404 response

**Feature:** BE-01  
**Priority:** P0  
**Type:** Manual (API)

**Preconditions:**
- Backend running at `http://localhost:8080`
- Valid admin JWT obtained

**Steps:**
1. Get an access token:
   ```bash
   TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"admin_Dev#2026!"}' \
     | jq -r .accessToken)
   ```
2. Request a non-existent sensor:
   ```bash
   curl -s -o /dev/null -w "%{http_code}" \
     -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/v1/sensors/SENSOR-NONEXISTENT-99999
   ```
3. Request a non-existent workflow:
   ```bash
   curl -s -w "\n%{http_code}\n" \
     -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/v1/workflows/WORKFLOW-NONEXISTENT
   ```

**Expected Results:**
- Step 2: HTTP status code `404`
- Step 3: HTTP status `404` and body matches `application/problem+json`:
  ```json
  {
    "type": "/errors/not-found",
    "title": "Not Found",
    "status": 404,
    "detail": "...",
    "traceId": "..."
  }
  ```
- No `500 Internal Server Error` returned for missing entities

**Pass/Fail:** ___  
**Notes:** Requires backend to be running

---

### TC-S2-009: GET /api/v1/tenant/config — API contract

**Feature:** BE-02  
**Priority:** P1  
**Type:** Manual (API)

**Preconditions:**
- Backend running
- Valid JWT available

**Steps:**
1. Obtain access token (see TC-S2-008 step 1)
2. Call:
   ```bash
   curl -s -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/v1/tenant/config | jq .
   ```

**Expected Results:**
- HTTP 200 OK
- Response body contains:
  - `tenantId` (string)
  - `features` (object with keys as feature flag names, values as `{ "enabled": bool }`)
  - `branding` (object with `partnerName`, `primaryColor`, `logoUrl`)
- HTTP 401 if called without a valid JWT

**Pass/Fail:** ___  
**Notes:** ___

---

### TC-S2-010: Tenant data isolation — ROLE_ADMIN cannot access other tenant's data

**Feature:** BE-03  
**Priority:** P1  
**Type:** Manual (API)

**Preconditions:**
- Two tenants exist: `default` and `hcm`
- Each tenant has sensor data
- Backend running with RLS migration (V15) applied

**Steps:**
1. Login as `default` tenant admin, call `/api/v1/environment/sensors`
2. Verify all returned sensors belong to `default` tenant
3. Attempt to forge request with `X-Tenant-Id: hcm` header while authenticated as `default` tenant admin
4. Verify that cross-tenant data is NOT returned

**Expected Results:**
- Step 2: All sensor records have `tenant_id = 'default'`
- Step 4: Access denied or empty results for `hcm` tenant data (RLS prevents leakage)
- No `hcm` tenant records appear in the response

**Pass/Fail:** ___  
**Notes:** Requires multi-tenant seed data in the database

---

## Test Execution Summary

| Test Case | Status | Tester | Date | Notes |
|---|---|---|---|---|
| TC-S2-001 JWT tenant claims | — | | | Automated (unit) |
| TC-S2-002 TenantConfigContext fetch | — | | | |
| TC-S2-003 Nav filtering TENANT_ADMIN | — | | | Blocked BUG-001 |
| TC-S2-004 Nav filtering flag disabled | — | | | |
| TC-S2-005 ProtectedRoute multi-role | — | | | Automated (E2E) |
| TC-S2-006 ProtectedRoute scope | — | | | Automated (E2E) |
| TC-S2-007 X-Tenant-Id header | — | | | |
| TC-S2-008 EntityNotFoundException→404 | — | | | Backend offline |
| TC-S2-009 GET /tenant/config contract | — | | | Backend offline |
| TC-S2-010 Tenant data isolation | — | | | Backend offline |

---

## Known Issues / Blockers

| ID | Severity | Summary | File | Status |
|---|---|---|---|---|
| BUG-001 | P2 | `/tenant-admin` route missing in routes/index.tsx — page blank on nav | `frontend/src/routes/index.tsx` | Open |
| ENV-001 | N/A | Backend services offline — BE test cases cannot be executed | All backend modules | Open |

---

## Automation Coverage

| Test Case | Unit Test | E2E (Playwright) |
|---|---|---|
| TC-S2-001 | `authTenantClaims.test.ts` (14 tests) | `sprint2-multi-tenancy.spec.ts` TC-S2-01 |
| TC-S2-002 | `TenantConfigContext.test.tsx` | TC-S2-05 |
| TC-S2-003/004 | `navFiltering.test.ts` (10 tests) | TC-S2-02 |
| TC-S2-005 | `ProtectedRoute.test.tsx` | TC-S2-03 |
| TC-S2-006 | `ProtectedRoute.test.tsx` | TC-S2-03 |
| TC-S2-007 | `tenantStore.test.ts` | TC-S2-04 |
| TC-S2-008 | `GlobalExceptionHandlerTest.java` | TC-S2-06 (mocked) |
| TC-S2-009 | `TenantConfigControllerTest.java` | TC-S2-05 (mocked) |
| TC-S2-010 | DB-level unit tests | TC-S2-07 (mocked) |
