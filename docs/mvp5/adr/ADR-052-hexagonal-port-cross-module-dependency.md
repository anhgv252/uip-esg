# ADR-052: Hexagonal Port cho Cross-Module Dependency — Modular Monolith Boundary Enforcement

**Date**: 2026-06-30
**Status**: Accepted
**Priority**: P1 (MVP5 — kiến trúc cốt lõi, chặn regression module boundary)
**Sprint**: post-M5-4 (fix BUG-M5-009)
**Author**: Solution Architect
**Related**: ADR-047 (CH RowPolicy tenant isolation), `feedback_doc_vs_code_gap`, `project_mvp5_archtest_deferred_coupling`, D1/D2/D3 deferred coupling (M5-1-T13)
**Artifact**: `common/spi/AirQualityPort.java`, `environment/adapter/AirQualityAdapter.java`, `ModuleBoundaryArchTest` (73 rules green)

---

## 1. Context

### 1.1 Problem — BUG-M5-009 (phát hiện re-test 2026-06-30)

UIP backend là **modular monolith** với 23 bounded context (`auth`, `tenant`, `environment`, `esg`, `alert`, `bms`, `billing`, `iso37120`, `lotus`, ...). Module boundary được enforce bằng `ModuleBoundaryArchTest` (73 ArchUnit rules) — mỗi context có rule "must not depend on X" để chặn cross-module leak.

Trong M5-4, 2 service mới trong package `esg` đã **inject trực tiếp repository của module khác**:

| Consumer (package `esg`) | Provider repository (`environment`) | Mục đích |
|---|---|---|
| `Iso37120IndicatorEngine` (ISO 37120 ENV-2 PM2.5) | `environment.repository.AirQualityReadingRepository.findAveragePm25ByPeriod` | Đọc PM2.5 trung bình tenant |
| `LotusVnScoringService` (LOTUS VN IEQ-2) | `environment.repository.AirQualityReadingRepository.findAveragePm25ByBuildingAndPeriod` | Đọc PM2.5 trung bình building |

→ Vi phạm 2 ArchTest rule:
- `esg must not depend on environment` (2 lần)
- `no classes outside 'environment..' access 'environment.repository..'` (2 lần)

### 1.2 Tại sao đây là vấn đề kiến trúc nghiêm trọng (không chỉ 1 bug)

1. **Silent coupling → extract microservice bị chặn.** Khi `esg` reference trực tiếp `environment.repository` (JPA entity `SensorReading`), module `esg` không thể tách ra microservice (MVP6+) mà không break. Đây là technical debt tích lũy âm thầm.
2. **Test false-positive.** Report M5-4 đánh giá sprint chỉ chạy test M5-4, **không re-run `ModuleBoundaryArchTest` toàn module** → ghi "73/73 PASS" sai. Bug tồn tại 5 ngày (2026-06-25 → 2026-06-30) trước khi re-test bắt được. Pattern lặp lại của `feedback_doc_vs_code_gap`.
3. **Trùng pattern D1/D2/D3.** M5-1-T13 đã documented 3 deferred coupling (`UserIdentityPort`, `TenantConfigPort`, `EnvironmentBroadcastPort`) nhưng **không có ADR quy chuẩn hóa pattern fix** → M5-4 lặp lại cùng lỗi với `AirQualityReadingRepository`.

### 1.3 Existing state

- `ModuleBoundaryArchTest` (73 rules) — enforce boundary, nhưng **chỉ bắt vi phạm, không hướng dẫn fix**.
- D1/D2/D3 deferred coupling documented trong `project_mvp5_archtest_deferred_coupling` — **chưa có Port/Adapter implementation**.
- Pattern Port-Adapter đã dùng locally: `EsgReportExportPort` (trong `esg.export`), `ForecastPort` (`forecast`), `AnalyticsPort` (`esg.config.analytics`) — nhưng **không nhất quán về placement package** (đôi khi Port ở consumer module, đôi khi adapter reference ngược → tiềm ẩn vi phạm chiều ngược).

---

## 2. Decision

### 2.1 Quy chuẩn: Cross-module dependency phải qua Hexagonal Port ở neutral package

**Mọi dependency từ module A sang data/logic của module B (khác bounded context) PHẢI đi qua Port interface, KHÔNG được reference trực tiếp repository/service/domain của B.**

Cấu trúc bắt buộc:

```
com.uip.backend.common.spi.<Port>.java          ← Port interface (neutral, consumer-agnostic)
com.uip.backend.<moduleB>.adapter.<Adapter>.java ← @Component implement Port, delegate sang repository nội bộ
com.uip.backend.<moduleA>.service.<Service>.java ← @RequiredArgsConstructor inject Port (không biết module B)
```

**Quy tắc placement (QUAN TRỌNG — tránh vi phạm chiều ngược):**

| Layer | Package | Lý do |
|---|---|---|
| **Port interface** | `common.spi.*` | Neutral — không thuộc module nào. Cả consumer (`esg`) và provider (`environment`) cùng access mà không vi phạm rule `environment must not depend on esg` hay `esg must not depend on environment`. |
| **Adapter implementation** | `<provider-module>.adapter.*` (vd `environment.adapter`) | Provider sở hữu data → implement Port. Reference `common.spi` (OK) + repository nội bộ (cùng module, OK). |
| **Consumer** | `<consumer-module>.service.*` | Inject `common.spi.Port`. KHÔNG bao giờ import `<provider>.repository` hay `<provider>.domain`. |

> **Tại sao Port ở `common.spi`, KHÔNG ở consumer module?** Nếu Port ở `esg.port`, thì adapter trong `environment` phải reference `esg.port` → vi phạm rule `environment must not depend on esg`. Neutral package giải quyết cả 2 chiều.

### 2.2 Khi nào áp dụng Port (vs. cho phép trực tiếp)

| Tình huống | Giải pháp |
|---|---|
| Module A cần **đọc aggregate data** của module B (vd: PM2.5, energy, sensor count) | **BẮT BUỘC Port** ở `common.spi` |
| Module A cần **gửi event** cho module B | Dùng **Kafka EventBus** (ADR-020), KHÔNG inject service của B |
| Module A và B **cùng bounded context** (sub-package) | Trực tiếp OK (không phải cross-module) |
| **Shared identity/config** (AppUser, TenantConfig) | Port (D1 `UserIdentityPort`, D2 `TenantConfigPort` — deferred, fix theo ADR này) |
| **Cross-cutting infrastructure** (Cache, Audit) | `common.*` trực tiếp (đã là neutral) |

### 2.3 Workflow bắt buộc khi thêm dependency mới

**Trước khi PR merge, MỖI lần thêm `import com.uip.backend.<moduleX>...` vào file ở module Y (X ≠ Y):**

1. **Check ArchTest sẽ vi phạm không?** Chạy local: `./gradlew test --tests "com.uip.backend.arch.ModuleBoundaryArchTest"`.
2. **Nếu vi phạm → KHÔNG patch ArchTest để ignore.** Phải extract Port theo §2.1.
3. **Port naming convention:** `<Entity>Port` (vd `AirQualityPort`, `EnergyReadingPort`). Method đặt theo **business intent** của consumer (`findAveragePm25ByBuildingAndPeriod`), KHÔNG expose JPA semantic (`findByXyz`).
4. **Adapter naming:** `<Entity>Adapter`, `@Component`, `@RequiredArgsConstructor`, delegate 1-1 sang repository.
5. **Test:** consumer test `@Mock Port`, KHÔNG mock repository của provider.

---

## 3. Enforcement (CI + ArchTest)

### 3.1 ArchTest — không thêm exception mới

`ModuleBoundaryArchTest` giữ nguyên 73 rules (KHÔNG thêm `@ArchIgnore` exception cho `esg→environment`). Fix BUG-M5-009 bằng Port, không phải bằng cách nới lỏng rule.

> **Nguyên tắc:** ArchTest rule là **contract kiến trúc**. Nếu rule vi phạm, fix code, không relax rule. Relax rule = chấp nhận debt vĩnh viễn.

### 3.2 CI gate — re-run ArchTest trên mọi PR chạm cross-module

**MỚI (đề xuất DevOps):** CI workflow phải chạy `ModuleBoundaryArchTest` trên **mọi PR** (không chỉ PR chạm `arch/` package), vì dependency import có thể nằm ở bất kỳ file nào. Hiện tại nếu ArchTest chỉ chạy trong `./gradlew test` full → đã OK, nhưng cần đảm bảo **không skip** qua `exclude` hay `-x test`.

### 3.3 Pre-Deploy SA Review checklist — thêm item

CLAUDE.md "SA Review Checklist — Backend" thêm:
- **Item 11:** Cross-module dependency: mọi `import com.uip.backend.<moduleX>` ở module Y (X≠Y) phải qua `common.spi.Port`. Verify `ModuleBoundaryArchTest` PASS local trước khi DONE.

---

## 4. Consequences

### 4.1 Tích cực

| Lợi ích | Mô tả |
|---|---|
| **Microservice-extractable** | Module A chỉ phụ thuộc Port interface → tách A ra microservice chỉ cần thay Adapter sang HTTP client (giống `AnalyticsPort` Tier-1/Tier-2 pattern ADR-011) |
| **Test isolation** | Consumer test mock Port, không cần setup provider repository/data → test nhanh, không Testcontainers chain |
| **Business intent rõ** | Port method tên theo business (`findAveragePm25ByBuildingAndPeriod`) → adapter có thể swap implementation (TimescaleDB → ClickHouse → REST) mà consumer không đổi |
| **ArchTest catch được** | Vi phạm bị chặn CI ngay, không silent 5 ngày như BUG-M5-009 |

### 4.2 Trade-off

| Chi phí | Chấp nhận? | Lý do |
|---|---|---|
| **+1 interface + 1 class mỗi cross-module dependency** | ✅ Yes | Boilerplate nhỏ (~30 dòng), đổi lại extractability |
| **Indirection 1 lớp** | ✅ Yes | Negligible runtime cost (Spring proxy) |
| **Đồng bộ Port ↔ Adapter khi đổi method** | ✅ Yes | Compiler bắt mismatch (interface contract) |

### 4.3 Migration plan (backlog)

Áp dụng ADR này cho 3 deferred coupling D1/D2/D3 (M5-1-T13) + audit toàn codebase:

| Priority | Coupling | Port đề xuất | SP |
|---|---|---|---|
| P2 | D1: `auth.repository.AppUserRepository` shared bởi admin/citizen/notification/tenant | `common.spi.UserIdentityPort` | 5-8 |
| P2 | D2: `tenant.repository` shared bởi auth/common/esg | `common.spi.TenantConfigPort` | 3-5 |
| P3 | D3: `scheduler.EnvironmentBroadcastScheduler → environment.service` | `common.spi.EnvironmentBroadcastPort` | 1-2 |
| P3 | Audit: grep toàn bộ `import com.uip.backend.<X>.repository` cross-module | SA spike | 2 |

**MVP5:** Port D1/D2/D3 defer sang MVP6 (tech-debt register). MVP5 chỉ fix BUG-M5-009 (AirQualityPort) vì nó vi phạm ArchTest active.

> **Audit toàn hệ thống hoàn tất 2026-06-30:** xem companion doc **`docs/mvp5/reports/mvp5-cross-module-migration-plan.md`** — 28 cross-module coupling runtime được phân loại (B documented / D deferred / C coupling-ngầm-no-rule / A vi phạm active). Phát hiện **ArchTest coverage gap**: rule chỉ protect 4 module data-owner, 6 Port mới (Nhóm C) + rule bổ sung cần thêm để close gap. Tổng effort ~20-27 SP (MVP5 P1 ~8 SP + MVP6 P2 ~12-17 SP).

---

## 5. Verification (fix BUG-M5-009)

| Check | Kết quả |
|---|---|
| `ModuleBoundaryArchTest` | ✅ 73/73 PASS (trước 71/73) |
| `Iso37120IndicatorEngineTest` | ✅ 6/6 PASS (Port wire đúng, PM2.5 delegate hoạt động) |
| `LotusVnScoringServiceTest` | 5/8 (3 fail = BUG-M5-004 business-logic, KHÔNG liên quan Port wiring — errors=0) |
| Bean wiring (Spring context) | ✅ `AirQualityAdapter` `@Component` resolve được khi inject `AirQualityPort` |

**Files:**
- `common/spi/AirQualityPort.java` (NEW — Port)
- `environment/adapter/AirQualityAdapter.java` (NEW — Adapter)
- `esg/iso37120/service/Iso37120IndicatorEngine.java` (inject Port)
- `esg/lotus/service/LotusVnScoringService.java` (inject Port)
- 2 test file (`@Mock AirQualityPort`)

---

## 6. Anti-patterns cấm (lessons learned)

| ❌ Anti-pattern | ✅ Đúng |
|---|---|
| `esg` inject `environment.repository.XyzRepository` trực tiếp | `esg` inject `common.spi.XyzPort`, `environment.adapter.XyzAdapter` implement |
| Đặt Port ở consumer module (`esg.port.X`) rồi provider reference ngược → vi phạm chiều ngược | Port ở `common.spi.*` (neutral) |
| Relax ArchTest rule (`@ArchIgnore`) để "fix nhanh" | Fix code bằng Port extraction |
| Sprint review chỉ chạy test của sprint đó, skip ArchTest toàn module | Re-run `ModuleBoundaryArchTest` trên mọi cross-module PR |
| Đánh giá DONE dựa "file tên đúng" (`feedback_doc_vs_code_gap`) | Đánh giá DONE dựa executable test PASS bao gồm ArchTest |
| Method Port expose JPA semantic (`findBySensorReadingId`) | Method Port theo business intent (`findAveragePm25ByBuildingAndPeriod`) |
| Mock provider repository trong consumer test | Mock Port interface trong consumer test |

---

## 7. References

- `feedback_doc_vs_code_gap` — DONE phải dựa executable artifact
- `project_mvp5_archtest_deferred_coupling` — D1/D2/D3 (M5-1-T13)
- `feedback_mvp5_retest_catches_regression` — re-test pattern bắt regression cross-sprint
- ADR-011 (AnalyticsPort Tier-1/Tier-2 — cùng pattern, precedent)
- ADR-020 (Kafka EventBus — alternative cho cross-module communication via event)
- ADR-047 (tenant isolation — boundary rule sibling)

---

*Authored by Solution Architect, 2026-06-30. Source of truth cho pattern cross-module dependency trong UIP modular monolith. Mọi PR thêm cross-module import phải tuân §2.3 workflow. MVP6 re-verify trước khi extract microservice.*
