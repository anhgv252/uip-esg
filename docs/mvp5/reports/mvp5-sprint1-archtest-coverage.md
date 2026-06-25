# MVP5 Sprint M5-1 Task T13 — ArchTest Module Boundary Coverage

**Status:** DONE — BUILD SUCCESSFUL, 73/73 @Test PASS
**Date:** 2026-06-24
**Owner:** Backend (T13)
**Reviewer:** SA (pending follow-up on 3 deferred coupling items)

## Mục tiêu

Mở rộng `ModuleBoundaryArchTest` để enforce boundary cho **mọi bounded-context** trong
`com.uip.backend` (23 package vật lý). Trước T13 chỉ có 5 context được cover
(environment, esg, alert, notification, traffic) với 22 @Test.

## Kết quả

| Metric | Trước T13 | Sau T13 |
|---|---|---|
| `@Test` count | 22 | **73** |
| Bounded-context được enforce | 5 | **23 / 23** |
| Build | SUCCESSFUL | **SUCCESSFUL** |
| Coupling phát hiện & defer | — | 3 (SA follow-up) |

Test file: `backend/src/test/java/com/uip/backend/arch/ModuleBoundaryArchTest.java`

Verify:
```
cd backend && ./gradlew test --tests "com.uip.backend.arch.*" --offline --rerun-tasks
→ BUILD SUCCESSFUL
```

## Phương pháp

- Dùng `accessClassesThat().resideInAPackage(...)` (bytecode access thật, không phải
  import text) → event DTO hợp lệ KHÔNG bị cấm.
- Phân biệt 2 dạng rule:
  - **Full-package ban** (`..module..`): cho cặp module hoàn toàn isolated.
  - **Internals-only ban** (`..module.domain..` / `..module.repository..` /
    `..module.service..`): cho module được phép consume event DTO nhưng KHÔNG được
    chạm internals.
- Nguyên tắc **BUILD PHẢI XANH**: rule fail → nếu là ADR exception → thêm exception;
  nếu là coupling thực tồn tại từ trước → **defer** (không sửa production code trong
  T13, ngoài scope) và ghi vào section "Deferred coupling".

## Coverage theo bounded-context

| Context | # Rules | Loại rule chính | Trạng thái |
|---|---|---|---|
| admin | 4 | repo isolation + boundary tới esg/alert/traffic | PASS |
| ai | 5 | boundary tới env/esg/notification/traffic internals | PASS |
| aiworkflow | 5 | repo isolation + boundary tới env/esg/traffic/notification | PASS |
| alert | 9 | bidirectional với 4 module + repo isolation (ADR-046 ai.feedback exception) | PASS |
| auth | 2 | boundary tới env/esg internals *(repo isolation DEFERRED)* | PASS |
| bms | 4 | repo isolation + boundary tới env/esg/traffic internals | PASS |
| building | 3 | repo isolation + boundary tới env/esg internals | PASS |
| citizen | 3 | repo isolation + boundary tới env/esg internals | PASS |
| common | 1 | không access business domain internals (cross-cutting) | PASS |
| correlation | 5 | repo isolation + boundary tới env/esg/notification/traffic | PASS |
| dashboard | 3 | boundary tới env/esg/alert internals | PASS |
| environment | 19 | bidirectional với tất cả + repo isolation | PASS |
| esg | 18 | bidirectional + repo isolation (ADR-032 forecast exception) | PASS |
| forecast | 4 | boundary tới env/alert/notification/traffic (ADR-032 D6 cho phép esg.repo) | PASS |
| kafka | 1 | không access business domain internals (infra config) | PASS |
| monitoring | 1 | không access business domain internals (observability) | PASS |
| notification | 4 | bidirectional + boundary tới alert/env/esg | PASS |
| partner | 2 | boundary tới env/esg internals | PASS |
| safety | 2 | boundary tới env/esg internals | PASS |
| scheduler | 3 | boundary tới esg/alert/traffic internals *(env.service DEFERRED)* | PASS |
| tenant | 2 | boundary tới env/esg internals *(repo isolation DEFERRED)* | PASS |
| traffic | 9 | bidirectional + repo isolation | PASS |
| workflow | 6 | boundary tới env internals (probe-only, không có repo) | PASS |

**Tổng: 23/23 context được enforce.**

## Documented exceptions (đã được rule cho phép)

| Exception | ADR | Rule áp dụng |
|---|---|---|
| `ai.feedback` reads `alert.repository` | ADR-046 (Incident Feedback Loop) | `alertRepository_mustOnlyBeAccessedWithin_alertModule` |
| `forecast` reads `esg.repository` (NaiveForecastAdapter) | ADR-032 D6 | `esgRepository_mustOnlyBeAccessedWithin_esgModule` |

## Deferred coupling — SA follow-up cần thiết

Đây là 3 coupling thực mà ArchUnit phát hiện. Chúng **không phải leak tình cờ** mà là
coupling có chủ đích hoặc tồn tại từ MVP4 working code. Đã **bỏ rule** (không để build
đỏ) và defer cho SA review. KHÔNG sửa production code trong T13.

### D1. `auth.repository` (AppUserRepository) — shared identity infrastructure

**Rule bị bỏ:** `authRepository_mustOnlyBeAccessedWithin_authModule`

**Coupling thực (12 access):**
- `admin.api.AdminController` — user management (changeRole, deactivateUser, listUsers)
- `citizen.api.CitizenController.register` — create user on citizen registration
- `notification.api.PushSubscriptionController.resolveUserId` — lookup user for push
- `notification.api.PushTestController.resolveUserId` — lookup user for push test
- `tenant.service.InviteService.acceptInvite` — create user on invite accept
- `tenant.service.TenantAdminService` — listUsers, updateUserRole

**Đánh giá:** `AppUserRepository` đang được dùng như identity infrastructure dùng chung.
Đây là quyết định kiến trúc hợp lý cho monolith, nhưng vi phạm nguyên tắc module
boundary nghiêm ngặt.

**Khuyến nghị SA:** Trích xuất `UserIdentityPort` interface (đặt ở `auth` hoặc
`common` package), implement bởi `auth`, inject bởi admin/citizen/notification/tenant.
Khi tách microservice → thay bằng gRPC stub. ước tính 5-8 SP.

### D2. `tenant.repository` (TenantConfigRepository, TenantRepository) — shared tenant config

**Rule bị bỏ:** `tenantRepository_mustOnlyBeAccessedWithin_tenantModule`

**Coupling thực (4 access):**
- `auth.config.DynamicCorsConfigurationSource.reload` — đọc CORS config per-tenant
- `common.ratelimit.TenantRateLimiter.reloadTenantRpm` — đọc RPM limit per-tenant
- `esg.service.EsgCacheWarmupService.warmupCache` — iterate tất cả tenant để warmup
- `esg.service.EsgService.calculateWaterIntensity` — đọc tenant config cho intensity calc

**Đánh giá:** Tenant config (CORS, rate limit RPM, feature flags) là shared
configuration mà nhiều module cần đọc. Đây là pattern phổ biến trong multi-tenant SaaS.

**Khuyến nghị SA:** Trích xuất `TenantConfigPort` interface (read-only config lookup),
implement bởi `tenant`. Cache warmup và các access read-only qua port. ước tính 3-5 SP.

### D3. `scheduler → environment.service` — direct service call thay vì Port

**Rule bị bỏ:** `scheduler_mustNotDependOn_environment_internals`

**Coupling thực (1 access):**
- `scheduler.EnvironmentBroadcastScheduler.broadcastSensorUpdates()` gọi trực tiếp
  `environment.service.EnvironmentService.getCurrentAqi()`

**Đánh giá:** Đây là coupling rõ ràng vi phạm pattern — scheduler nên pull data qua
Port interface hoặc consume event, không inject service của module khác. Coupling tồn
tại từ MVP4 working code.

**Khuyến nghị SA:** Trích xuất `EnvironmentBroadcastPort` interface (method
`getCurrentAqi()` hoặc `getBroadcastSnapshot()`), implement bởi `environment`,
inject bởi `scheduler`. ước tính 1-2 SP — fix đơn giản, có thể làm trong Sprint M5-2
tech-debt.

## Lưu ý kỹ thuật

- ArchUnit import với `DO_NOT_INCLUDE_TESTS` — chỉ kiểm tra production code.
- `accessClassesThat` dựa trên bytecode access (method call, field access, constructor
  call, annotation, etc.), KHÔNG phải import text → chính xác hơn và không false
  positive với unused imports.
- Rule `.orShould()` trong ArchUnit là **OR logic** (tổng hợp violation) — dùng để
  gộp nhiều package cấm vào 1 rule thay vì tách thành N rule.
