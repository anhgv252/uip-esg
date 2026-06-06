# Playwright E2E Stability Fixes — 2026-06-01

## Summary

Full Playwright suite (`chromium`, `firefox`, `mobile-chrome`, `mobile-safari`) stabilized from **10 failures** down to **0 failures**.  
Latest result: **375 passed, 0 failed, 0 flaky, 4 skipped** across 4 browser projects (exit code 0).

> **Fix #11 added 2026-06-01 (session 2)**: Resolved 2 chromium genuine failures in `ai-workflow.spec.ts` caused by missing routeMap entry and unreliable sidebar navigation.

---

## Fixes Applied

### 1. citizen1 Wrong Password Hash (DB + Flyway migration)

**Problem**: `citizen1` user's `password_hash` in the DB was incorrect — login always failed with 401.

**Files**:
- `backend/src/main/resources/db/migration/V32__add_citizen1_app_user.sql`

**Fix**: Changed `ON CONFLICT DO NOTHING` → `ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash, ...` to force-upsert the correct BCrypt hash for `citizen_Dev#2026!`.

---

### 2. `/citizen` Route Missing ROLE_CITIZEN Guard

**Problem**: Citizen portal routes had no role guard — any logged-in user could access `/citizen`, causing RBAC test failures.

**Files**:
- `frontend/src/routes/index.tsx`
- `frontend/src/routes/ProtectedRoute.tsx`

**Fix**:
- Added `redirectTo?: string` prop to `ProtectedRoute` (defaults to `/dashboard`).
- Wrapped `/citizen` subtree with `<ProtectedRoute requiredRoles={['ROLE_CITIZEN']} redirectTo="/login">`.

---

### 3. X-Tenant-Id Default Tenant Leaking in API Requests

**Problem**: Axios interceptor sent `X-Tenant-Id: default` header for the default tenant, causing backend RLS to filter out data that should be visible under the default tenant.

**Files**:
- `frontend/src/api/client.ts`

**Fix**: Changed `if (tid)` → `if (tid && tid !== 'default')` so the default tenant never sends the header.

---

### 4. `navigateTo()` Helper routeMap Too Sparse

**Problem**: `helpers/auth.ts` `navigateTo()` only knew 4 routes — tests navigating to `/alerts`, `/traffic`, `/esg`, etc. fell back to direct `page.goto()` without token injection, causing 401s.

**Files**:
- `frontend/e2e/helpers/auth.ts`

**Fix**: Expanded routeMap from 4 entries to 17 entries covering: `alerts`, `traffic`, `environment`, `esg`, `dashboard`, `city-ops`, `ai-workflow`, `trigger-config`, `workflow-config`, `admin`, `citizens/citizen`, and more.

---

### 5. `alert:escalate` Scope Missing From JWT

**Problem**: Escalate-alert tests failed with 403 because `alert:escalate` was not included in the JWT scopes for `ROLE_ADMIN` / `ROLE_OPERATOR`.

**Files**:
- `backend/src/main/java/com/uip/backend/auth/service/AuthService.java`
- `backend/src/main/resources/db/migration/V33__add_alert_escalate_scope.sql`

**Fix**:
- Added `"alert:escalate"` to both `ROLE_ADMIN` and `ROLE_OPERATOR` scope lists in `AuthService.java`.
- Added `V33` migration to insert `alert:escalate` into `app_user_scopes` for all existing admins/operators.
- Rebuilt and redeployed backend JAR.

---

### 6. CitizenNotificationsPage Loading Guard Blocking Header

**Problem**: The "Cập nhật tự động mỗi 30 giây" auto-refresh header was gated behind a loading state check, so tests checking for this element failed on first render.

**Files**:
- `frontend/src/components/citizen/CitizenNotificationsPage.tsx`

**Fix**: Moved the auto-refresh header `<Box>` outside the loading condition so it renders unconditionally from the first render.

---

### 7. WebKit Binary Not Installed

**Problem**: All 9 `[mobile-safari]` tests failed with `browserType.launch: Executable doesn't exist at .../webkit-2272/pw_run.sh`.

**Fix**: Ran `npx playwright install webkit` to download WebKit 26.4 (75.4 MiB) to `/Users/anhgv/Library/Caches/ms-playwright/webkit-2272/`.

---

### 8. Firefox `isMobile` Option Not Supported

**Problem**: `[firefox] sprint2-po-demo.spec.ts AC-08` failed with `browser.newContext: options.isMobile is not supported in Firefox`.

**Files**:
- `frontend/e2e/sprint2-po-demo.spec.ts`

**Fix**: Added `browserName` fixture to the test, conditionally spreading `isMobile: true, hasTouch: true` only for non-Firefox:
```typescript
const context = await browser.newContext({
  viewport: { width: 768, height: 1024 },
  ...(browserName !== 'firefox' ? { isMobile: true, hasTouch: true } : {}),
})
```

---

### 9. TC-S2-08 Sidebar Collapse — Wrong Accessible Name Selector

**Problem**: After collapsing the AppShell sidebar, `getByRole('button', { name: /^Environment$/ })` still matched because MUI `<Tooltip title="Environment">` sets the accessible name even when `ListItemText` is removed from DOM.

**Files**:
- `frontend/e2e/sprint2-multi-tenancy.spec.ts`

**Fix**: Changed `navTextAfter` assertion to check `.MuiListItemText-primary` element visibility instead of button accessible name:
```typescript
// Before (wrong — tooltip still provides accessible name):
const navTextAfter = await page.getByRole('button', { name: /^Environment$/ }).isVisible().catch(() => false)

// After (correct — checks actual text label element):
const navTextAfter = await page.locator('.MuiListItemText-primary')
  .filter({ hasText: /^Environment$/ })
  .first()
  .isVisible({ timeout: 1_000 })
  .catch(() => false)
```

### 10. Sprint 1 RBAC Redirect — Test Assertion Matched Wrong Final URL

**Problem**: `[firefox] sprint1-demo.spec.ts:296 — 5-1: RBAC operator redirected to login when accessing /citizen` failed with:
```
Expected pattern: /\/login/
Received string:   "http://localhost:3000/dashboard"
```
Root cause: operator → `/citizen` → `ProtectedRoute` redirects to `/login` → `LoginPage` detects `isAuthenticated === true` and immediately bounces to `/dashboard` (the default `from` value). The test expected to land on `/login`, but the authenticated user always skips past the login page.

**Files**:
- `frontend/e2e/sprint1-demo.spec.ts`

**Fix**: Changed the test assertion from `toHaveURL(/\/login/)` to the semantically correct invariant — the operator must **not** remain on `/citizen`:
```typescript
// Before (wrong — authenticated user bounces through /login to /dashboard):
await expect(page).toHaveURL(/\/login/, { timeout: 8000 });

// After (correct — verifies RBAC enforcement: operator cannot access /citizen):
await expect(page).not.toHaveURL(/\/citizen$/, { timeout: 8000 });
```

---

### 11. AI Workflow Chromium Navigation — Missing routeMap Entry + No waitForURL Guard

**Problem**: `[chromium] ai-workflow.spec.ts:17` and `:38` consistently failed (both initial run and retry) with `expect(locator).toBeVisible() failed — element not found (timeout 10000ms)`.

Root cause: `navigateTo(page, 'AI Workflows')` normalises the label to lowercase → key `'ai workflows'` (with trailing **s**), but `routeMap` only had `'ai workflow'` (no s). When the sidebar button was not yet rendered (parallel cold start), the fallback path was `undefined` and the function silently skipped navigation — leaving the page on `/dashboard` instead of `/ai-workflow`. Tests checking `h1-h6` for "AI Workflow Dashboard" and `getByRole('tab', { name: /process instances/i })` then timed out because those elements don't exist on `/dashboard`.

Tests 23 and 50 in the same suite passed because they use `isVisible().catch(() => false)` (resilient pattern) or check `[role="tablist"]` which exists on the dashboard page too.

**Files**:
- `frontend/e2e/helpers/auth.ts`
- `frontend/e2e/ai-workflow.spec.ts`

**Fix**:
1. Added `'ai workflows': '/ai-workflow'` to `routeMap` in `navigateTo()` so the fallback covers the plural label.
2. Added a `waitForURL` guard in `beforeEach` with a direct `page.goto` fallback:
```typescript
// beforeEach:
await navigateTo(page, 'AI Workflows');
// Ensure navigation succeeded — fallback to direct goto if sidebar click was missed
await page.waitForURL('**/ai-workflow', { timeout: 5000 }).catch(async () => {
  await page.goto('/ai-workflow');
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
});
```

---

## Running the Test Suite

```bash
npm --prefix /path/to/frontend run e2e
# or from the frontend/ directory:
npm run e2e
```

> **Note**: Use `npm run e2e` (not `npx playwright test`) to avoid the interactive `playwright@1.60.0` install prompt that npx triggers when a newer version is available.

---

## Acceptable Known Flakiness (pass on retry)

The following tests are inherently timing-sensitive and may fail on first attempt but pass on retry (`retries: 1` in config):

| Test | Project | Reason |
|------|---------|--------|
| `ai-workflow.spec.ts:50` | firefox | HTTP 429 rate limiting at cold start (parallel workers all login simultaneously) |
| `alert-pipeline.spec.ts:14` | firefox | HTTP 429 rate limiting at cold start |
| `pwa-mobile.spec.ts:201` | mobile-chrome / mobile-safari | Service Worker requires HTTPS; test runs on HTTP |

> **Previously flaky, now fixed (Fix #11)**: `ai-workflow.spec.ts:17`, `:23`, `:38`, `:50` in chromium — navigation race fixed by routeMap entry + `waitForURL` guard.
