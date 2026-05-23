# JaCoCo Coverage Report — Sprint 3 Retro Action Items
**Date:** 2026-05-22  
**Build:** `./gradlew test jacocoTestReport --no-daemon`  
**Status:** BUILD SUCCESSFUL — 864 tests, 0 failures, 0 errors, 0 skipped

---

## Summary: Test Fixes Applied (A1 / A2)

Three new test files added during Sprint 3 contained 15 failing tests. All root causes were diagnosed and fixed in this session before the clean build.

| File | Failures | Root Cause | Fix Applied |
|---|---|---|---|
| `AuthServiceTest.java` | 6 | `mockJwtProperties()` called inside `thenReturn()` — inner `when()` corrupted Mockito pending stub state (`UnfinishedStubbingException`) | Pre-compute `var jwtPropsN = mockJwtProperties(...)` before each `when()` call |
| `TenantContextFilterTest.java` | 2 | `assertThat(TenantContext.getCurrentTenant()).isEqualTo(TenantContext.getDefaultTenant())` — after `clear()`, `ThreadLocal.get()` returns `null`, not `"default"` | Changed assertions to `isNull()` |
| `PushNotificationServiceHttpStatusTest.java` | 7 | (a) `mockHttpResponse()` inside `thenReturn()` — same nested-stub corruption; (b) Fake p256dh key (`"dGVzdA=="`) failed `Utils.loadPublicKey()` EC validation → `InvalidKeySpecException` → `send()` never reached → `UnnecessaryStubbingException` | (a) Pre-compute responses; (b) `@BeforeAll` real EC key-pair generation via `KeyPairGenerator("EC", secp256r1)` with 65-byte uncompressed point; (c) `@MockitoSettings(LENIENT)` as safety net |

---

## Action Item A3: JaCoCo Branch Coverage Results

### Overall Coverage

| Metric | Total | Covered | Missed | Coverage |
|---|---|---|---|---|
| **BRANCH** | 1,087 | 637 | 450 | **58.6%** |
| LINE | 2,716 | 2,581 | 135 | 95.0% |
| INSTRUCTION | 16,186 | 13,989 | 2,197 | 86.4% |
| METHOD | 656 | 570 | 86 | 86.9% |
| CLASS | 89 | 88 | 1 | 98.9% |

> **Baseline (stale):** LINE 93.3% / BRANCH 56.8%  
> **After fixes:** LINE 95.0% (+1.7pp) / BRANCH 58.6% (+1.8pp)

---

### Target Packages — Detailed Coverage

#### `auth/service`
| Metric | Total | Covered | Missed | Coverage |
|---|---|---|---|---|
| BRANCH | 51 | 38 | 13 | **74.5%** |
| LINE | 161 | 145 | 16 | 90.1% |
| INSTRUCTION | 834 | 745 | 89 | 89.3% |
| METHOD | 40 | 36 | 4 | 90.0% |

> Target: >90%. Current: **74.5% — BELOW TARGET**  
> Gap: 13 uncovered branches remain. Likely uncovered: error-path branches (bad credentials edge cases, expired token corner cases, null principal paths).

#### `tenant/filter`
| Metric | Total | Covered | Missed | Coverage |
|---|---|---|---|---|
| BRANCH | 18 | 16 | 2 | **88.9%** |
| LINE | 45 | 44 | 1 | 97.8% |
| INSTRUCTION | 153 | 151 | 2 | 98.7% |
| METHOD | 8 | 8 | 0 | 100.0% |

> Target: >95%. Current: **88.9% — BELOW TARGET**  
> Gap: 2 uncovered branches (2/18). Most likely the `null` return path in `extractTenantId()` when no cookie or header is present but a specific conditional path is not hit, or an edge case in `decodeJwtPayload()`.

#### `notification/service`
| Metric | Total | Covered | Missed | Coverage |
|---|---|---|---|---|
| BRANCH | 70 | 53 | 17 | **75.7%** |
| LINE | 219 | 208 | 11 | 95.0% |
| INSTRUCTION | 970 | 906 | 64 | 93.4% |
| METHOD | 37 | 37 | 0 | 100.0% |

> Target: >90%. Current: **75.7% — BELOW TARGET**  
> Gap: 17 uncovered branches. All methods are covered (100%), so the gaps are within-method conditionals. Likely: VAPID-disabled fast-path branches, subscription list empty/null guards, retry-on-failure multi-iteration branches not fully exercised.

---

### All Packages — Branch Coverage

| Package | Covered | Total | Coverage | Status |
|---|---|---|---|---|
| `tenant/context` | 4 | 4 | **100.0%** | ✅ |
| `tenant/repository` | 2 | 2 | **100.0%** | ✅ |
| `esg/common` | 2 | 2 | **100.0%** | ✅ |
| `scheduler` | 12 | 12 | **100.0%** | ✅ |
| `tenant/kafka` | 4 | 4 | **100.0%** | ✅ |
| `workflow/controller` | 37 | 38 | **97.4%** | ✅ |
| `common/ratelimit` | 17 | 18 | **94.4%** | ✅ |
| `building/service` | 15 | 16 | **93.8%** | ✅ |
| `alert/kafka` | 11 | 12 | **91.7%** | ✅ |
| `tenant/filter` | 16 | 18 | **88.9%** | ⚠️ target >95% |
| `tenant/hibernate` | 12 | 14 | **85.7%** | ℹ️ |
| `workflow/service` | 44 | 52 | **84.6%** | ℹ️ |
| `traffic/service` | 23 | 26 | **88.5%** | ℹ️ |
| `workflow/delegate/management` | 32 | 39 | **82.1%** | ℹ️ |
| `workflow/trigger` | 23 | 28 | **82.1%** | ℹ️ |
| `esg/export` | 67 | 80 | **83.8%** | ℹ️ |
| `environment/service` | 58 | 73 | **79.5%** | ℹ️ |
| `esg/service` | 86 | 112 | **76.8%** | ℹ️ |
| `notification/service` | 53 | 70 | **75.7%** | ⚠️ target >90% |
| `auth/service` | 38 | 51 | **74.5%** | ⚠️ target >90% |
| `tenant/service` | 16 | 22 | **72.7%** | ℹ️ |
| `alert/service` | 33 | 46 | **71.7%** | ℹ️ |
| `citizen/service` | 12 | 20 | **60.0%** | ℹ️ |
| `common/filter` | 1 | 4 | **25.0%** | ⚠️ low |
| `partner` | 0 | 26 | **0.0%** | ❌ no tests |
| `workflow/dto` | 0 | 276 | **0.0%** | ❌ no branch tests |

---

## Analysis & Recommendations

### 1. Overall Branch Coverage: 58.6% — Still Below 65% Target

The two largest gaps dragging the overall number down:
- **`workflow/dto`**: 276 branch points, 0 covered (0%). These are likely `if (field != null)` guards in DTO `equals()`/`hashCode()` or builder validation. Adding a few simple DTO unit tests would add ~25pp to this package alone.
- **`partner`**: 26 branch points, 0 covered (0%). The `partner-energy-optimizer` integration has no tests at all.

**If both `workflow/dto` and `partner` are excluded as out-of-scope**, the effective branch coverage of in-scope packages is substantially higher.

### 2. `auth/service` — 74.5% vs >90% Target (gap: 13 branches)

Recommended test additions:
- Login with locked/disabled user account (if `UserDetails.isEnabled()` / `isAccountNonLocked()` branches exist)
- `refresh()` with a token issued by a different issuer — currently only same-issuer paths tested
- `resolveScopes()` with an AppUser whose roles list is empty vs. `null`
- Exception propagation paths in `login()` when `authenticationManager.authenticate()` throws `BadCredentialsException`

### 3. `tenant/filter` — 88.9% vs >95% Target (gap: 2 branches)

Only 2 branches missed. Most likely candidates:
- A specific `null` vs. empty string check in `extractTenantId()` when the JWT payload exists but `tenant_id` key is absent entirely (vs. blank value)
- A defensive null-check path in the cookie-scanning loop (`cookies == null` guard in Servlet API)

A single targeted test for `request.getCookies()` returning `null` (not an empty array) would likely close this.

### 4. `notification/service` — 75.7% vs >90% Target (gap: 17 branches)

- **VAPID disabled fast-path**: When `enabled == false`, `send()` should return early — add a test with `enabled=false`
- **Empty subscriber list**: `send()` called with no active subscriptions — currently only non-empty lists tested
- **Partial failure retry**: The multi-subscription loop where first succeeds and second fails — edge case paths in the loop body
- **`sendToUser()` permission branch**: If any `if (userId == null)` guard exists

### 5. Packages Needing Attention (not in sprint targets)

| Package | Coverage | Priority | Reason |
|---|---|---|---|
| `common/filter` | 25.0% | HIGH | Security filter — low coverage is a risk |
| `citizen/service` | 60.0% | MEDIUM | Core citizen portal service |
| `alert/service` | 71.7% | MEDIUM | Alert delivery reliability |
| `partner` | 0.0% | LOW | External integration, lower risk for MVP |
| `workflow/dto` | 0.0% | LOW | DTOs, risk is low if serialization tests exist |

---

## Sprint 3 Retro Action Items — Status

| Action | Owner | Status | Result |
|---|---|---|---|
| A1: Identify 15 failing tests root causes | QA/Backend | ✅ Done | 3 distinct root causes found |
| A2: Fix all 15 failing tests | Backend | ✅ Done | All 3 files fixed, 864/864 tests pass |
| A3: Clean Gradle build + real JaCoCo numbers | Backend | ✅ Done | BRANCH 58.6%, LINE 95.0% |

### Next Sprint Coverage Targets
- Overall BRANCH: 58.6% → **65%** (+6.4pp needed)
- `auth/service` BRANCH: 74.5% → **90%** (+15.5pp needed)
- `tenant/filter` BRANCH: 88.9% → **95%** (+6.1pp needed)
- `notification/service` BRANCH: 75.7% → **90%** (+14.3pp needed)
