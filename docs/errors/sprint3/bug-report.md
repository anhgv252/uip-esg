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

---

*Bug report này là tài liệu chính thức cho Sprint 3. Mọi bugs phải được verify bởi QA trước khi approve sang Sprint 4.*