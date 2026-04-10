# Sprint 3 — Bug Report

**Sprint:** Sprint 3 (25/04 → 09/05 trong kế hoạch; thực tế done 06/04/2026)
**Ngày viết:** 06/04/2026
**Người viết:** QA / Product Owner Review
**Nguồn:** Sprint 3 Manual Test Report + Code Review

---

## Tóm tắt

| Bug ID | Severity | Component | Trạng thái | Ảnh hưởng Sprint 4 |
|---|---|---|---|---|
| BUG-S3-CONFIG-01 | 🔴 CRITICAL | Backend config | **FIXED** | Camunda không hoạt động nếu không fix |
| BUG-S3-ESG-02 | 🔴 HIGH | Frontend / ESG | **FIXED** | Dashboard ESG Score = undefined |
| BUG-S3-03-01 | 🟡 MEDIUM | Frontend / BE | **FIXED** | Timestamp sai trên mọi màn hình |
| BUG-S3-MIGRATION-01~04 | 🔵 INFO | DB Migrations | Fixed trong testing | Schema đã ổn định |
| BUG-S3-JACKSON-01 | 🔵 INFO | Backend config | Fixed trong testing | — |
| BUG-S3-JSX-01 | 🔵 INFO | Frontend | Fixed trong testing | — |
| **BUG-S3-ESG-03** | 🔴 HIGH | Backend / ESG | **FIXED 07/04** | ESG report sinh hoàn toàn không được (PENDING forever) |
| **BUG-S3-TRAFFIC-01** | 🟡 MEDIUM | Backend / Traffic | **FIXED 07/04** | Traffic counts luôn rỗng do time-window quá hẹp |
| **BUG-S3-CITIZEN-01** | 🟡 MEDIUM | DB Migration | **FIXED 07/04** | citizen2/citizen3 không đăng nhập được |

---

## BUG-S3-CONFIG-01 — Malformed Camunda YAML

| Field | Value |
|---|---|
| **ID** | BUG-S3-CONFIG-01 |
| **Severity** | CRITICAL |
| **Component** | `backend/src/main/resources/application.yml` |
| **Phát hiện** | Code review trước Sprint 4 |
| **Trạng thái** | ✅ FIXED |

### Mô tả
Block `camunda.bpm` trong `application.yml` bị malformed YAML:
- `webapp:` theo sau bởi `      :` trên dòng mới — parse thành string literal kỳ lạ
- `springapplication-path: /camunda` ở root level với các dòng indented bên dưới (`database:`, `job-execution:`, `history-level:`) — **INVALID YAML**, Spring Boot / SnakeYAML có thể bỏ qua hoặc throw ScannerException

### Hậu quả
- `camunda.bpm.webapp.application-path` không được set → Camunda webapp UI có thể không mount đúng
- `camunda.bpm.database.schema-update: true` không được apply → Camunda không tự tạo schema khi khởi động
- **Sprint 4 hoàn toàn phụ thuộc Camunda (7 AI scenarios)** — nếu không fix, Sprint 4 không thể bắt đầu

### Before (sai)
```yaml
camunda:
  bpm:
    webapp:
      :
  task:
    execution:
      pool: ...

springapplication-path: /camunda
    database:
      schema-update: true
    job-execution:
      enabled: true
    history-level: audit
```

### After (đúng)
```yaml
camunda:
  bpm:
    webapp:
      application-path: /camunda
    database:
      schema-update: true
    job-execution:
      enabled: true
    history-level: audit
    task:
      execution:
        pool:
          core-size: 5
          max-size: 20
          queue-capacity: 100
```

### Verify Steps (cho QA/Tester)
1. Chạy backend: `./gradlew bootRun`
2. Truy cập `http://localhost:8080/camunda/app/cockpit` → phải redirect đến Camunda login
3. Không có lỗi `ScannerException` hay `BeanCreationException` liên quan đến Camunda trong logs
4. Logs phải có dòng: `Camunda BPM ... process engine ...`

---

## BUG-S3-ESG-02 — Field Name Mismatch: totalCarbonKg vs totalCarbonTco2e

| Field | Value |
|---|---|
| **ID** | BUG-S3-ESG-02 |
| **Severity** | HIGH |
| **Component** | Frontend `api/esg.ts`, `pages/DashboardPage.tsx`, `pages/EsgPage.tsx` |
| **Phát hiện** | Code review — API contract drift |
| **Trạng thái** | ✅ FIXED |

### Mô tả
Không nhất quán giữa backend DTO và frontend interface:

| | Backend `EsgSummaryDto.java` | Frontend `api/esg.ts` | Đúng/Sai |
|---|---|---|---|
| Carbon field | `totalCarbonTco2e` | `totalCarbonKg` | ❌ Sai tên |
| `generated/api-types.ts` | `totalCarbonTco2e` | — | ✅ Contract đúng |

### Hậu quả
- `DashboardPage.tsx`: ESG Score card hiển thị `NaN kg` hoặc `undefined` (không có giá trị)
- `EsgPage.tsx`: Carbon Footprint KPI card luôn trống
- Bug âm thầm — không có lỗi console, chỉ hiển thị sai

### Affected files
- `frontend/src/api/esg.ts` — `EsgSummary` interface
- `frontend/src/pages/DashboardPage.tsx` — line 22
- `frontend/src/pages/EsgPage.tsx` — line 73

### Verify Steps (cho QA/Tester)
1. Login admin → Dashboard: **ESG Score card phải hiện số** (ví dụ `19 tCO₂e`), không còn loading spinner vô hạn
2. Vào trang ESG → KPI card "Carbon Footprint": phải hiện số và unit `tCO₂e`
3. API call: `GET /api/v1/esg/summary` → response JSON có field `totalCarbonTco2e`, không có `totalCarbonKg`

---

## BUG-S3-03-01 — Timestamp Hiển thị "56 years ago"

| Field | Value |
|---|---|
| **ID** | BUG-S3-03-01 |
| **Severity** | MEDIUM (display only, data đúng) |
| **Component** | Backend `application.yml` + Frontend `api/esg.ts` |
| **Phát hiện** | Sprint 3 Manual Test — TC-003, TC-010, TC-011 |
| **Trạng thái** | ✅ FIXED |

### Root Cause (phân tích sâu)
Hai nguyên nhân độc lập:

**Nguyên nhân 1 — Backend**: Do `application.yml` bị malformed (BUG-S3-CONFIG-01), SnakeYAML có thể bỏ qua `spring.jackson.serialization.write-dates-as-timestamps: false`. Kết quả: `Instant` fields serialize thành epoch-seconds float (`1774371279.3`) thay vì ISO-8601.

**Nguyên nhân 2 — Frontend `esg.ts`**: Không có `normalizeInstant()` workaround (khác `environment.ts` đã có). Khi backend trả epoch float cho `/esg/energy` và `/esg/carbon`, `EsgBarChart` sẽ parse timestamp sai.

**Đã được workaround một phần**: `environment.ts` có `normalizeInstant()` (xử lý cả string ISO và number epoch×1000). Nhưng `esg.ts` và `alerts.ts` chưa có.

### Affected
| Endpoint | Field | Màn hình |
|---|---|---|
| `GET /api/v1/alerts` | `detectedAt`, `acknowledgedAt` | Alert Management |
| `GET /api/v1/environment/sensors` | `lastSeenAt`, `installedAt` | Environment / City Ops |
| `GET /api/v1/esg/energy`, `/carbon` | `timestamp` | ESG Dashboard chart |

### Fix
1. **Backend**: Sửa malformed YAML (BUG-S3-CONFIG-01 fix) → `write-dates-as-timestamps: false` có hiệu lực → backend trả ISO-8601
2. **Frontend `esg.ts`**: Thêm `normalizeInstant` mapping cho `/esg/energy` và `/esg/carbon`

### Verify Steps (cho QA/Tester)
1. Alert Management: cột "Detected" hiển thị thời gian tương đối đúng (ví dụ "2 hours ago", không phải "56 years ago")
2. Alert detail drawer: "Detected" hiển thị date format `dd/MM/yyyy HH:mm:ss` đúng năm 2026
3. ESG page → toggle Carbon chart: trục X hiển thị tháng 2026 (không phải 1970)
4. Environment page → sensor table cột "Last Seen": hiển thị "Offline" hoặc thời gian đúng, không phải "56 years ago"
5. Gọi API: `GET /api/v1/alerts` → field `detectedAt` trong response phải là ISO-8601 string (ví dụ `"2026-04-01T10:30:00Z"`), không phải số float

---

## Bugs Fixed Trong Quá Trình Testing (Pre-test Fixes)

Các bug này đã được fix trực tiếp trong session QA ngày 06/04/2026. Ghi lại để lịch sử.

### BUG-S3-MIGRATION-01 — V6: Wrong column name (traffic_counts)
- **Root cause**: `timestamp` column (POC legacy) thay vì `recorded_at`
- **Fix**: Drop + recreate table với đúng schema
- **Trạng thái**: ✅ Fixed in migration

### BUG-S3-MIGRATION-02 — V7: citizen_accounts thiếu username column
- **Root cause**: Schema cũ không có `username` column
- **Fix**: Drop cascade → recreate với đúng schema
- **Trạng thái**: ✅ Fixed in migration

### BUG-S3-MIGRATION-03 — V8: TimescaleDB partition key
- **Root cause**: TimescaleDB yêu cầu partition key (`recorded_at`) phải có trong unique index
- **Fix**: Composite PK `(id, recorded_at)` thay vì PK chỉ `id`
- **Trạng thái**: ✅ Fixed in migration

### BUG-S3-MIGRATION-04 — V8 Seed SQL: Wrong table alias
- **Root cause**: `n.n` thay vì `t.n` trong `CROSS JOIN ... AS t`
- **Fix**: Sửa alias
- **Trạng thái**: ✅ Fixed in seed SQL

### BUG-S3-JACKSON-01 — Jackson unknown properties
- **Root cause**: Jackson throws 500 khi response có extra JSON fields
- **Fix**: `spring.jackson.deserialization.fail-on-unknown-properties: false`
- **Trạng thái**: ✅ Fixed in application.yml (đã có)

### BUG-S3-JSX-01 — CitizenRegisterPage.tsx extra closing tag
- **Root cause**: Thừa `</Typography>` gây Vite compile error
- **Fix**: Remove thừa tag
- **Trạng thái**: ✅ Fixed in source

---

## Test Gaps (Không phải bug nhưng cần verify trước Sprint 4)

### GAP-S3-01 — City Ops Center Map chưa được test
- **Sprint 3 DoD**: "Map hiển thị 10+ sensors live"
- **Thực tế**: Không có test case nào test City Ops Center map (`/city-ops`)
- **Hành động cần**: QA thực hiện smoke test màn hình `/city-ops` xác nhận Leaflet map load, sensor markers hiển thị

### GAP-S3-02 — SSE Alert Latency chưa verify E2E
- **Sprint 3 DoD**: "Alert <30s verified"
- **Thực tế**: Chỉ test API, không test latency SSE end-to-end
- **Hành động cần**: Test thủ công: trigger alert → đo thời gian đến khi SSE notification xuất hiện trên UI

---

## Bugs Phát Hiện Trong Live Demo — 07/04/2026

### BUG-S3-ESG-03 — ESG Report Stuck PENDING Forever

| Field | Value |
|---|---|
| **ID** | BUG-S3-ESG-03 |
| **Severity** | HIGH |
| **Component** | `backend/src/main/java/com/uip/backend/esg/service/EsgService.java` |
| **Phát hiện** | Live demo 07/04/2026 — `POST /api/v1/esg/reports/generate` |
| **Trạng thái** | ✅ FIXED 07/04/2026 |

### Mô tả
Sau khi gọi `POST /esg/reports/generate`, report tạo ra nhưng status mãi là `PENDING` — không bao giờ chuyển sang `GENERATING` hay `DONE`.

### Root Cause
`EsgService` có class-level annotation `@Transactional(readOnly = true)`. Khi không có method-level override, Hibernate set `FlushMode.MANUAL` cho tất cả transactions → `reportRepository.save(report)` không commit row vào DB.

Đồng thời, ngay sau `save()`, `reportGenerator.generateAsync(id)` được gọi. Thread async cố `findById(id)` nhưng row chưa tồn tại → `IllegalArgumentException("Report not found")` throw trước khi biến `report` được set → `catch` block không thể set `status=FAILED` → exception bị nuốt bởi `SimpleAsyncUncaughtExceptionHandler` → report còn mãi ở `PENDING`.

Thứ tự lỗi:
1. `@Transactional(readOnly=true)` → Hibernate `FlushMode.MANUAL`
2. `save()` không flush → row không có trong DB
3. `@Async` thread chạy ngay, `findById` → `IllegalArgumentException`
4. Exception handler nuốt lỗi → PENDING forever

### Fix
```java
// EsgService.java — trước đây (sai)
// Không có @Transactional trên method → kế thừa class-level readOnly=true

// Sau khi fix
@Transactional  // override class-level readOnly — commit row ngay
public EsgReportDto triggerReportGeneration(String periodType, int year, int quarter) {
    EsgReport saved = reportRepository.save(report);
    // Dispatch async CHỈ SAU KHI transaction commit
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            reportGenerator.generateAsync(saved.getId());
        }
    });
    return toReportDto(saved);
}
```

`TransactionSynchronizationManager.afterCommit()` đảm bảo async thread chỉ chạy sau khi INSERT đã visible với mọi connection.

### Verify
```bash
curl -X POST '.../esg/reports/generate?periodType=QUARTERLY&year=2026&quarter=2' -H "Authorization: Bearer $TOKEN"
# → {"status":"PENDING", "id":"..."}
sleep 4
curl '.../esg/reports/{id}/status' -H "Authorization: Bearer $TOKEN"
# → {"status":"DONE", "downloadUrl":"/api/v1/esg/reports/{id}/download", ...}
```
**Kết quả thực tế:** `PENDING → DONE` trong ~1.2 giây ✅

---

### BUG-S3-TRAFFIC-01 — Traffic Counts Luôn Trả Về Mảng Rỗng

| Field | Value |
|---|---|
| **ID** | BUG-S3-TRAFFIC-01 |
| **Severity** | MEDIUM |
| **Component** | `backend/.../traffic/api/TrafficController.java` |
| **Phát hiện** | Live demo 07/04/2026 — `GET /api/v1/traffic/counts` |
| **Trạng thái** | ✅ FIXED 07/04/2026 |

### Mô tả
`GET /api/v1/traffic/counts` luôn trả về `[]` dù DB có dữ liệu seed.

### Root Cause
`TrafficController.getTrafficCounts()` có default time-window là **24 giờ** (`from = now - 24h`). Dữ liệu seed trong V6 migration được insert với `recorded_at` cố định (timestamp tại thời điểm chạy migration, ~ngày 06/04). Sau 24 giờ kể từ migration, tất cả records đều nằm ngoài window → query trả về rỗng.

### Fix
Mở rộng default window từ **24 giờ → 7 ngày** trong `TrafficController`:

```java
// Trước
Instant defaultFrom = Instant.now().minus(24, ChronoUnit.HOURS);

// Sau
Instant defaultFrom = Instant.now().minus(7, ChronoUnit.DAYS);
```

### Verify
```bash
curl '.../traffic/counts' -H "Authorization: Bearer $TOKEN"
# → [{intersectionId: "INT-001", vehicleCount: 450, vehicleType: "CAR", ...}, ...]
```
**Kết quả thực tế:** Trả về records đúng ✅

---

### BUG-S3-CITIZEN-01 — citizen2/citizen3 Không Đăng Nhập Được

| Field | Value |
|---|---|
| **ID** | BUG-S3-CITIZEN-01 |
| **Severity** | MEDIUM |
| **Component** | `backend/src/main/resources/db/migration/V7__create_citizen_entities.sql` |
| **Phát hiện** | Live demo 07/04/2026 — test citizen login |
| **Trạng thái** | ✅ FIXED 07/04/2026 (V9 migration) |

### Mô tả
`citizen2` và `citizen3` tồn tại trong `citizens.citizen_accounts` và có household + invoice data (từ V7/V8 migration), nhưng **không có record trong `public.app_users`** → `POST /auth/login` trả về `401 Invalid username or password`.

`citizen1` cũng bị ảnh hưởng (đã fix riêng bằng cách reset password hash về `citizen_Dev#2026!`).

### Root Cause
V7 migration (`V7__create_citizen_entities.sql`) chỉ insert vào `citizens.citizen_accounts` (bảng profile) mà không insert vào `public.app_users` (bảng auth). Backend dùng `app_users` để authenticate — thiếu record ở đây dẫn đến `UsernameNotFoundException`.

### Fix
Thêm V9 migration insert citizen1/2/3 vào `app_users` với bcrypt hash của `citizen_Dev#2026!`:

```sql
-- V9__fix_citizen_auth_accounts.sql
INSERT INTO public.app_users (username, email, password_hash, role)
VALUES
  ('citizen1', 'citizen1@example.com', '$2a$12$PNchUMuWmIKTRq/gi33.Zu744k2c/D.S1DvGXFin/qO/j1QuM6sg2', 'ROLE_CITIZEN'),
  ('citizen2', 'citizen2@example.com', '$2a$12$PNchUMuWmIKTRq/gi33.Zu744k2c/D.S1DvGXFin/qO/j1QuM6sg2', 'ROLE_CITIZEN'),
  ('citizen3', 'citizen3@example.com', '$2a$12$PNchUMuWmIKTRq/gi33.Zu744k2c/D.S1DvGXFin/qO/j1QuM6sg2', 'ROLE_CITIZEN')
ON CONFLICT (username) DO NOTHING;
```

**Password:** `citizen_Dev#2026!` (cùng pattern với `citizen` seed account)

### Verify
```bash
curl -X POST '.../auth/login' -d '{"username":"citizen2","password":"citizen_Dev#2026!"}' -H 'Content-Type: application/json'
# → {"accessToken":"eyJ...", "tokenType":"Bearer", ...}
curl '.../citizen/profile' -H "Authorization: Bearer $TOKEN"
# → {"username":"citizen2", "fullName":"Trần Thị B", "household":{...}, ...}
```
**Kết quả thực tế:** citizen2 và citizen3 login thành công, profile và invoices trả về đúng ✅

---

## Regression Checklist (QA verify trước khi approve Sprint 4)

- [ ] **BUG-S3-CONFIG-01 verify**: Camunda webapp accessible tại `/camunda/app/cockpit`
- [ ] **BUG-S3-ESG-02 verify**: Dashboard ESG Score card hiện số không phải spinner
- [ ] **BUG-S3-ESG-02 verify**: ESG page Carbon Footprint KPI hiện `tCO₂e`
- [ ] **BUG-S3-03-01 verify**: Alert timestamps hiển thị đúng năm (2026, không phải 1970)
- [ ] **BUG-S3-03-01 verify**: ESG carbon/energy chart trục X đúng tháng 2026
- [ ] **GAP-S3-01**: City Ops Center map load đúng với sensor markers
- [ ] **GAP-S3-02**: SSE alert notification latency <30s
- [ ] Regression: Login admin/citizen vẫn hoạt động sau fixes
- [ ] Regression: Alert acknowledge flow vẫn hoạt động
- [ ] Regression: Citizen registration 3-step wizard vẫn hoạt động
- [ ] BUG-S3-ESG-03 verify: `POST /esg/reports/generate?periodType=QUARTERLY&year=2026&quarter=2` → wait 4s → `GET /esg/reports/{id}/status` returns `{"status":"DONE","downloadUrl":"..."}` not null
- [ ] BUG-S3-TRAFFIC-01 verify: `GET /traffic/counts` (no params) returns non-empty list (>0 records)
- [ ] BUG-S3-CITIZEN-01 verify: `POST /auth/login` với username=citizen2 + password=`citizen_Dev#2026!` → HTTP 200 + JWT; repeat với citizen3

---

*Bug report này là tài liệu chính thức cho Sprint 3. Mọi bugs phải được verify bởi QA trước khi approve sang Sprint 4.*