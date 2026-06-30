# Cross-Module Coupling Migration Plan — Audit toàn hệ thống & lộ trình Port extraction

| Field | Value |
|---|---|
| **Date** | 2026-06-30 |
| **Author** | Solution Architect |
| **Trigger** | Sau fix BUG-M5-009 (ADR-052) — audit ArchTest coverage gap + coupling ngầm toàn hệ thống |
| **Related** | [ADR-052](../adr/ADR-052-hexagonal-port-cross-module-dependency.md), `project_mvp5_archtest_deferred_coupling`, `feedback_doc_vs_code_gap` |
| **Status**: | Audit hoàn tất. Migration backlog cho MVP6 (microservice extraction gate) |

---

## 1. Bối cảnh & mục đích

Sau khi fix BUG-M5-009 (extract `AirQualityPort`), SA review toàn hệ thống để trả lời 2 câu hỏi:

1. **ArchTest có đang catch mọi vi phạm không?** → KHÔNG. `ModuleBoundaryArchTest` (73 rule) PASS nhưng **chỉ cover coupling vào 4 "data-owner" module** (`environment`, `esg`, `alert`, `traffic`). Nhiều cặp module khác KHÔNG có rule → **false-green**.
2. **Có coupling ngầm nào khác cần migrate sang Port (ADR-052) không?** → CÓ. Tìm thấy 28 cross-module coupling runtime (repository/service injection), phân loại 4 nhóm bên dưới.

**Mục đích document:** Lập **migration plan** để từng module chuyển sang Port pattern theo lộ trình ưu tiên, đảm bảo kiến trúc modular monolith sẵn sàng extract microservice (MVP6 gate K2).

> **Quy trình tái diễn audit:** `grep -rn` cross-module import (xem Appendix §6) + cross-reference với `ModuleBoundaryArchTest.java` + ADR documented. SA nên re-audit mỗi lần thêm module mới.

---

## 2. Kết quả audit — 28 cross-module coupling runtime

Phương pháp: scan `import com.uip.backend.<X>.(repository|service).<Class>` trong main code, loại trừ `common.*` và cùng-module. Cross-reference với ArchTest rules + ADR documented.

### 2.1 Nhóm B — Documented exceptions / coupling có chủ đích (KHÔNG cần migrate)

| Coupling | File | Justification |
|---|---|---|
| `ai → alert.repository` | IncidentFeedbackAggregator, TriggerSuggestionGenerator | **ADR-046** Incident Feedback Loop — rule exception `ai.feedback..` (line 243-251) |
| `forecast → esg.repository` | NaiveForecastAdapter | **ADR-032 D6** — rule exception `forecast..` (line 268) |
| `workflow → esg.service` | EsgAnomalyStrategy, EsgUtilityAnomalyStrategy | Strategy pattern qua `ScheduledQueryStrategy` interface (bean ref `esgService.detectEsgAnomalies`) — KHÔNG inject trực tiếp, coupling qua interface |
| `workflow → notification` (channel const) | FloodEvacuationDelegate, AqiCitizenAlertDelegate | `implements JavaDelegate` (BPMN); chỉ reference `NotificationService.ALERT_CHANNEL` (hằng số static) để publish Redis pub/sub — KHÔNG inject service runtime |
| `bms → notification` (Kafka) | BmsCommandAckConsumer | `@KafkaListener` + inject `SseEmitterRegistry` — phần Kafka hợp lệ; phần inject registry ở Nhóm C |

**Hành động:** Không migrate. Nhưng nên:
- Move `NotificationService.ALERT_CHANNEL` (Redis channel constant) sang `common` để `workflow` không phải import `notification.service` chỉ lấy hằng số.
- Mở ArchTest rule cho `ai.feedback`/`forecast` đã có — giữ exception comment rõ ràng.

### 2.2 Nhóm D — Deferred coupling đã documented (M5-1-T13 D1/D2/D3)

> Đây là 3 coupling có chủ đích SA đã ghi nhận từ M5-1, defer để follow-up. **Migrate theo ADR-052 pattern.**

| ID | Coupling | Port đề xuất | Provider | SP |
|---|---|---|---|---|
| **D1** | `tenant`/`admin`/`citizen`/`notification` → `auth.repository.AppUserRepository` + `auth.domain` (5 entry points) | `common.spi.UserIdentityPort` | `auth.adapter.UserIdentityAdapter` | **5-8** |
| **D2** | `esg`/`auth`/`common` → `tenant.repository` (TenantRepository, TenantConfigRepository, InviteTokenRepository) | `common.spi.TenantConfigPort` | `tenant.adapter.TenantConfigAdapter` | **3-5** |
| **D3** | `scheduler.EnvironmentBroadcastScheduler` → `environment.service` (1 access) | `common.spi.EnvironmentBroadcastPort` | `environment.adapter.EnvironmentBroadcastAdapter` | **1-2** |

### 2.3 Nhóm C — Coupling ngầm KHÔNG có rule ArchTest cover (lỗ hổng false-green) ⚠️

> **Đây là phần quan trọng nhất.** Các coupling này tồn tại nhưng ArchTest PASS vì **không có rule nào cấm cặp module đó**. Cần (1) migrate Port + (2) thêm rule ArchTest.

| # | Coupling | File | Vấn đề | Port đề xuất | SP |
|---|---|---|---|---|---|
| C1 | `safety → alert.service` | BuildingSafetyService, StructuralAlertConsumer | Inject trực tiếp `AlertService` (service→service chéo) | `common.spi.AlertEmissionPort` (safety raise alert qua Port, không gọi AlertService) | 3 |
| C2 | `safety → notification.service` | StructuralAlertConsumer | Inject `NotificationRouter` | Gộp vào C1 hoặc `common.spi.NotificationPort` | 2 |
| C3 | `bms → notification.service` | BmsCommandAckConsumer | Inject `SseEmitterRegistry` (phần Kafka OK) | `common.spi.SseBroadcastPort` | 2 |
| C4 | `billing → audit.service` | InvoiceGenerationService | Inject `AuditLogService` trực tiếp (đã có Kafka event `BillingInvoiceGeneratedEvent` — nên consume event thay inject) | Bỏ inject, dùng `@EventListener` hoặc Kafka (đã partial) | 2 |
| C5 | `billing → tenant.service` | BillingAggregationJob | Inject tenant service | `common.spi.TenantLookupPort` (trùng D2) | 1 |
| C6 | `admin → auth.repository` | AdminController | Admin read users qua auth repo | Trùng D1 `UserIdentityPort` | 0 (covered D1) |
| C7 | `admin → environment.service` | AdminController | Admin gọi environment service | `common.spi.EnvironmentAdminPort` | 2 |
| C8 | `citizen → auth.repository/service` | CitizenController | Citizen mapping user | Trùng D1 | 0 (covered D1) |
| C9 | `common → tenant.repository` | TenantRateLimiter | Rate limiter cần tenant config | `common.spi.TenantConfigPort` (trùng D2) | 0 (covered D2) |
| C10 | `auth → tenant.repository` | DynamicCorsConfigurationSource | CORS config đọc tenant | Trùng D2 | 0 (covered D2) |

**Tổng SP Nhóm C (sau khi gộp trùng D1/D2):** ~12 SP cho 6 Port mới (C1, C3, C4, C7) + D1/D2 giải quyết C5/C6/C8/C9/C10.

### 2.4 Nhóm A — Vi phạm rule đang active nhưng ArchTest vẫn PASS?

**KHÔNG phát hiện.** Sau khi fix BUG-M5-009, toàn bộ 73 rule PASS thực sự (đã verify `./gradlew test --tests ModuleBoundaryArchTest` BUILD SUCCESSFUL). Các coupling còn lại đều rơi vào nhóm B/D/C — không có rule active nào bị vi phạm.

> Lỗ hổng là **rule chưa tồn tại** (Nhóm C), không phải rule bị bypass.

---

## 3. Migration plan theo ưu tiên

### 3.0 ✅ P1 HOÀN THÀNH 2026-06-30 (post-audit fix)

Toàn bộ P1 đã migrate sang Hexagonal Port + thêm ArchTest rule khóa lỗ hổng:

| Task | Port tạo | Adapter | Consumer migrate | ArchTest rule mới |
|---|---|---|---|---|
| **D3** | `EnvironmentBroadcastPort` (+ `AqiSnapshot` record) + `SseBroadcastPort` | `environment.adapter.EnvironmentBroadcastAdapter`, `notification.adapter.SseBroadcastAdapter` | `scheduler.EnvironmentBroadcastScheduler` | `scheduler_mustNotDependOn_environmentOrNotification_service` |
| **C3** | (dùng `SseBroadcastPort` của D3) | (dùng `SseBroadcastAdapter` của D3) | `bms.kafka.BmsCommandAckConsumer` | `bms_mustNotDependOn_notification_service` |
| **C4** | `BillingInvoiceGeneratedEvent` (Spring ApplicationEvent) | `@TransactionalEventListener` trong `AuditLogService` | `billing.InvoiceGenerationService` (bỏ inject `AuditLogService`, publish event) | `billing_mustNotDependOn_audit_service` |
| **C1+C2** | `AlertPort` (+ `StructuralAlertSnapshot`/`StructuralAlertInput`/`SavedAlertSnapshot` records) + `NotificationPort` | `alert.adapter.AlertPortAdapter`, `notification.adapter.NotificationPortAdapter` | `safety.service.BuildingSafetyService`, `safety.consumer.StructuralAlertConsumer` | `safety_mustNotDependOn_alertOrNotification_service` |

**Verify:**
- `ModuleBoundaryArchTest`: **77/77 PASS** (trước 73 — **+4 rule mới**, tất cả green)
- Toàn bộ test bị migrate PASS: BmsCommandAckConsumer 16/16, StructuralAlertConsumer 6/6, EnvironmentBroadcastScheduler 6/6, BuildingSafetyService (params), InvoiceGenerationService 6/6, AuditLogService 3/3
- **Không regression**: 138 failures còn lại trong full test đều là **pre-existing** (ApplicationContext load — root cause `ConflictingBeanDefinitionException 'invoiceController'` + `NoSuchBeanDefinition JwtTokenProvider`, không liên quan Port bean). Xác nhận: không có Port nào tôi thêm gây UnsatisfiedDependency.

### 3.1 P1 — Migrate trong MVP5 (chặn pilot risk)

| Task | Lý do P1 | SP |
|---|---|---|
| **D3** `EnvironmentBroadcastPort` | Nhỏ nhất (1-2 SP), đóng closing M5-1-T13 deferred | 1-2 |
| **C4** Bỏ `billing → audit.service` inject, dùng `@EventListener` | Đã có `BillingInvoiceGeneratedEvent` — inject `AuditLogService` là thừa coupling, refactor nhẹ | 2 |
| **C1+C2** `AlertEmissionPort` + `NotificationPort` cho safety | Safety module là building-safety critical (BR-010) — coupling alert/notification trực tiếp là risk cho pilot | 3-5 |

**Subtotal P1: ~8 SP** (đề xuất gộp vào M5-5 hardening hoặc M5-5-T15 ArchTest final).

### 3.2 P2 — Migrate MVP6 (microservice extraction prep)

| Task | Lý do P2 | SP |
|---|---|---|
| **D1** `UserIdentityPort` | 5 entry points — lớn nhất, cần trước khi tách `auth` | 5-8 |
| **D2** `TenantConfigPort` | Tenant config là cross-cutting, tách trước khi tách `tenant`/`esg` | 3-5 |
| **C7** `EnvironmentAdminPort` | Admin coupling | 2 |
| **C3** `SseBroadcastPort` | bms→notification | 2 |

**Subtotal P2: ~12-17 SP** (MVP6 backlog, gate K2 "re-verify trước commercial 10+ bldg").

### 3.3 P3 — Tech-debt register (không chặn, theo dõi)

| Task | Note |
|---|---|
| Move `NotificationService.ALERT_CHANNEL` → `common` | Nhóm B, cosmetic, 0.5 SP |
| Move `EsgAnomalyStrategy` bean ref qua Port nếu extract `esg` | Chỉ khi tách microservice |

---

## 4. ArchTest coverage gap — Rule cần THÊM

> **Đây là fix kiến trúc quan trọng nhất để không lặp lại BUG-M5-009.** Hiện tại ArchTest chỉ cover coupling vào 4 module data-owner. Coupling giữa các module khác (safety↔alert, billing↔audit, admin↔auth...) không có rule → false-green.

### 4.1 Rule mới đề xuất thêm vào `ModuleBoundaryArchTest`

```java
// Nhóm C coverage — chặn service→service coupling ngầm
@DisplayName("safety must not depend on alert/notification service internals")
@DisplayName("billing must not depend on audit/notification service internals")
@DisplayName("admin must not depend on auth/environment service internals")
@DisplayName("bms must not depend on notification service internals (Kafka consumer OK)")
@DisplayName("notification must not depend on auth/repository internals")
```

> Lưu ý: rule phải **distinguish Kafka consumer (`@KafkaListener`) hợp lệ** vs inject service trực tiếp. Dùng `accessClassesThat().resideInAPackage("..X.service..")` cho service inject; Kafka topic coupling (String constant) không bị bắt và không cần bắt.

### 4.2 Workflow thực thi

1. **Trước khi thêm rule mới**, migrate coupling tương ứng sang Port (không thêm rule rồi để fail).
2. Thêm rule → chạy PASS → commit cả Port extraction + rule cùng lúc.
3. **Không** dùng `@ArchIgnore` để bỏ qua — Nếu coupling thực sự cần, document ADR + thêm exception package có chủ đích (như ADR-046/032).

---

## 5. Definition of Done cho migration

Mỗi Port extraction (D1/D2/D3/C1-C7) phải:
- [ ] Port interface ở `common.spi.<Port>` (neutral — ADR-052 §2.1)
- [ ] Adapter `@Component` ở `<provider>.adapter.<Adapter>`
- [ ] Consumer inject Port, KHÔNG còn `import <provider>.repository|.service`
- [ ] Test consumer `@Mock Port` (không mock provider repository)
- [ ] **Thêm ArchTest rule** cho cặp module đó (chặn regression)
- [ ] `ModuleBoundaryArchTest` PASS (số rule tăng)
- [ ] Build + test full PASS

---

## 6. Appendix — Lệnh audit tái diễn

```bash
cd backend
# Scan cross-module repository/service coupling (runtime deps, bỏ qua domain/DTO)
grep -rln "import com.uip.backend" src/main/java/com/uip/backend/ | while read f; do
  consumer=$(echo "$f" | sed -E 's|src/main/java/com/uip/backend/([a-z]+)/.*|\1|')
  grep -oE "import com\.uip\.backend\.[a-z]+\.(repository|service)\.[A-Za-z]+" "$f" | while read imp; do
    provider=$(echo "$imp" | sed -E 's|import com\.uip\.backend\.([a-z]+)\..*|\1|')
    [ "$provider" != "$consumer" ] && [ "$provider" != "common" ] && echo "$consumer → $provider :: $(basename $f)"
  done
done | sort -u
```

So sánh output với rule list trong `ModuleBoundaryArchTest.java` — mọi coupling KHÔNG có rule = lỗ hổng (Nhóm C).

---

## 7. Tóm tắt

| Nhóm | Số coupling | Hành động | Sprint |
|---|---|---|---|
| **B** — Documented/strategic | 5 | Giữ, move constants (cosmetic) | — |
| **D3** — Deferred | 1 Port | ✅ **MIGRATED 2026-06-30** | done |
| **D1/D2** — Deferred | 2 Port | Migrate theo ADR-052 | MVP6 |
| **C1/C3/C4** — Coupling ngầm | 3 Port | ✅ **MIGRATED 2026-06-30** (+ 4 ArchTest rule) | done |
| **C2/C5-C7** — Coupling ngầm | còn lại | Migrate + ArchTest rule | MVP6 |
| **A** — Active rule violation | 0 | (đã fix BUG-M5-009) | ✅ |

**Verdict:** P1 migration hoàn tất — `ModuleBoundaryArchTest` 73→**77 rule**, toàn bộ coupling P1 (D3 + C1/C3/C4) đã chuyển sang Hexagonal Port, 4 lỗ hổng coverage đã khóa bằng rule mới. Còn lại P2 (~12-17 SP, D1/D2 + C2/C5-C7) cho MVP6 microservice-extraction prep.

---

*Authored by Solution Architect, 2026-06-30. Companion của ADR-052. Re-audit khi thêm module mới hoặc trước MVP6 microservice extraction (gate K2).*
