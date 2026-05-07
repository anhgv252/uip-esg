# Sprint 5 Test Session Report (MVP2-12 + MVP2-13)

- Date: 2026-05-06
- Scope: Mobile PWA (MVP2-12), Tenant Admin Dashboard (MVP2-13)
- Workspace: `/Users/anhgv/working/my-project/smartcity/uip-esg-poc`

## 1. Changes Completed In This Session

### Regression and E2E Updates
- Updated API regression suite with Sprint 5 groups in `scripts/api_regression_test.py`:
  - Group 15: `pwa_citizen`
  - Group 16: `tenant_admin_dashboard`
- Updated Playwright config in `frontend/playwright.config.ts`:
  - Projects: `chromium`, `firefox`, `mobile-chrome`, `mobile-safari`
  - Adjusted timeout/retry/reporter settings for CI and local runs
- Updated auth helpers in `frontend/e2e/helpers/auth.ts`:
  - Added `loginAsTenantAdmin(page)`
  - Added `loginAsCitizen(page)`
- Reworked Sprint 5 E2E specs:
  - `frontend/e2e/pwa-mobile.spec.ts`
  - `frontend/e2e/tenant-admin-crud.spec.ts`
- Updated regression runner in `scripts/regression_test.sh`:
  - Added `--e2e-project=` argument support
  - Added Playwright fallback path if Python E2E runner is absent

### Backend Test Fixes Applied
- Fixed strict Mockito stubbing in:
  - `backend/src/test/java/com/uip/backend/citizen/workflow/CitizenServiceRequestDelegateTest.java`
- Fixed `@WebMvcTest` context beans and rate-limit filter isolation in:
  - `backend/src/test/java/com/uip/backend/workflow/controller/WorkflowConfigControllerWebMvcTest.java`
  - `backend/src/test/java/com/uip/backend/alert/api/AlertControllerWebMvcTest.java`
  - `backend/src/test/java/com/uip/backend/esg/api/EsgControllerWebMvcTest.java`
- Updated notification unit test wiring for new router-based flow in:
  - `backend/src/test/java/com/uip/backend/notification/service/NotificationServiceTest.java`

## 2. Test Execution Results

### Backend (Gradle)
- Command: `./gradlew test --parallel`
- Snapshot before fixes in this session: `565 tests completed, 116 failed`
- Snapshot after applied fixes in this session: `565 tests completed, 87 failed`
- Net improvement: **29 failures reduced**

#### Backend failures still open (major groups)
- Integration/API environment dependent:
  - `AuthControllerIntegrationTest`
  - `Sprint2ApiRegressionIntegrationTest`
  - `Sprint5ApiRegressionIntegrationTest`
  - `SSE Notification Security`
  - `TenantIsolationIT`
  - `PushSubscription IT`
  - `Workflow Startup — 7 BPMN processes deployed`
  - `S4-03`, `S4-04`, `S4-10` scenario integration suites
- Repository/cache related:
  - `EsgMetricRepository — JPQL tenant isolation`
  - `MVP2-03b TriggerConfigCacheService — Spring Cache IT`
- Push notification unit tests still failing:
  - `PushNotificationServiceTest` (6 failures)

### Frontend E2E (Playwright)
- Environment check before E2E:
  - `http://localhost:3000` => `000`
  - `http://localhost:8080/actuator/health` => `000`
- Result: application services were not reachable locally, causing navigation timeouts.

#### Executed Playwright commands
1. `npx playwright test e2e/pwa-mobile.spec.ts --project=chromium`
   - Result: **1 passed, 8 failed**
   - Common failures: `TimeoutError: page.waitForURL` and offline/service-worker assertions
2. `npx playwright test e2e/pwa-mobile.spec.ts --project=mobile-chrome`
   - Result: **1 passed, 8 failed**
   - Common failures: same as chromium (URL timeout + offline assertions)
3. `npx playwright test e2e/tenant-admin-crud.spec.ts --project=chromium`
   - Result: **10 failed**
   - Common failures: `TimeoutError: page.waitForURL`

## 3. Root Cause Analysis (Frontend E2E)

Primary root cause of Playwright failures in this session:
- Local frontend and backend services were not available (`HTTP 000` on both required endpoints).
- Most test failures are environmental (service not up) rather than assertion regressions in Sprint 5 logic.

## 4. Sprint 5 Acceptance Signal

- Regression coverage for MVP2-12 and MVP2-13 has been added to API and E2E suites.
- Multi-browser/mobile Playwright projects have been configured and runnable.
- Backend test health improved significantly (116 -> 87 fails), with multiple test-infra and WebMvc issues fixed.
- Full Sprint 5 sign-off requires:
  - Stable local/CI runtime infrastructure for backend integration tests
  - Running frontend app + backend API before Playwright execution
  - Closing remaining PushNotificationService unit failures

## 5. Recommended Next Actions

1. Bring up required local stack (DB/Redis/Kafka/backend/frontend) before re-running E2E and integration tests.
2. Fix remaining `PushNotificationServiceTest` failures (6 tests) and rerun targeted suite.
3. Re-run `./gradlew test --parallel` and capture updated fail count.
4. Re-run Playwright on `chromium`, `firefox`, `mobile-chrome`, `mobile-safari` and attach consolidated report.
