# UI Browser Demo Validation (2026-05-07)

## Scope
- Frontend UI validation for PO demo in browser (not API-only).
- Target suites:
  - `frontend/e2e/pwa-mobile.spec.ts`
  - `frontend/e2e/tenant-admin-crud.spec.ts`

## Fixes Applied
- PWA/dev setup:
  - Enabled PWA dev options in `frontend/vite.config.ts` (`enabled: true`, `type: 'module'`).
- Mobile routing/layout:
  - Wired `/citizen/*` through `MobileLayout` in `frontend/src/routes/index.tsx`.
  - Added bottom-nav test id in `frontend/src/components/mobile/MobileNav.tsx`.
  - Added offline banner + app-shell test id in `frontend/src/components/mobile/MobileLayout.tsx`.
- Tenant admin selectors:
  - Added KPI `data-testid` values in `frontend/src/pages/tenant-admin/TenantOverviewPage.tsx`.
  - Added `data-testid="usage-chart"` in `frontend/src/pages/tenant-admin/UsageReportPage.tsx`.
- Citizen mobile selectors/pages:
  - Added `data-testid="bill-card"` in `frontend/src/pages/citizen/MobileBillsPage.tsx`.
  - Added `data-testid` for AQI in `frontend/src/components/mobile/AqiGauge.tsx`.
  - Added mobile bill detail page `frontend/src/pages/citizen/MobileBillDetailPage.tsx` and route `/citizen/bills/:billId`.
- E2E auth stability:
  - Switched Playwright auth helper to API-based login (`frontend/e2e/helpers/auth.ts`).
  - Added token persistence/hydration:
    - `frontend/src/api/client.ts`
    - `frontend/src/contexts/AuthContext.tsx`

## Key Root Cause Found During Validation
- Playwright with `BASE_URL=http://127.0.0.1:3000` causes backend response `403 Invalid CORS request` for login through proxy path.
- Using `BASE_URL=http://localhost:3000` avoids this and allows login.

## Current Test Status (latest)
### `pwa-mobile.spec.ts` (mobile-chrome, localhost, workers=1)
- Failing flows remain:
  - Bills flow: no bill card visible after navigation to bills.
  - AQI flow: horizontal overflow check fails (`scrollWidth` 448 > `clientWidth` 390).
  - Bottom nav flow: test still waits for `networkidle` after tab tap and times out.
  - Service worker registration and offline app-shell checks still fail in dev-mode behavior.

### `tenant-admin-crud.spec.ts` (chromium, localhost, workers=1)
- Authentication and navigation now proceed.
- Focused failing assertion observed:
  - Invite flow did not show toast text `invite sent successfully` within timeout.

## Demo Readiness Assessment
- API regression remains healthy (previously confirmed 86/86 pass).
- UI browser demo is partially improved but **not fully green** for the full E2E suites yet.
- Recommended demo path for PO:
  - Use localhost origin.
  - Demo manually on validated pages/components while avoiding flaky/non-seeded assertions (invite toast, offline SW behavior in dev).
