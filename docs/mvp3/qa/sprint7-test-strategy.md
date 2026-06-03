# Sprint 7 QA Test Strategy

**Sprint:** 2026-06-16 → 2026-06-27
**Tác giả:** QA Engineer
**Phiên bản:** 1.0 (2026-06-02)
**Tham chiếu:** [Sprint 7 Task Assignments](../project/sprint7-task-assignments.md) | [ADR-034](../architecture/ADR-034-structural-monitoring.md)

---

## 1. Phạm vi Test

### In-scope (DEV DONE — sẵn sàng test)

| Task | Mô tả | Loại test ưu tiên |
|------|--------|-------------------|
| B1-1 | ESG Permission Bypass fix | WebMvc / API |
| B1-2 | Apicurio Schema Registry | Docker health / config |
| B2-2 | VibrationAnomalyJob (Flink CEP) | Unit ✅ + Integration |
| B2-3 | BuildingSafetyService + V34 migration | Unit ✅ + IT |
| B2-4 | REST API Building Safety | API / WebMvc ✅ |
| B2-5 | StructuralAlertConsumer | Unit ✅ + E2E |
| OPS-2 | Apicurio Docker + Kafka env | Smoke |
| FE-1 | SafetyScoreGauge | Manual UI |
| FE-2 | SafetyTrendChart | Manual UI (blocked — xem §7) |
| FE-3 | Building Detail Page / Safety Tab | Manual E2E |
| FE-4 | Safety Alert Integration | Manual UI + API |

### Out-of-scope sprint 7

- B1-3..B1-7 (Avro dual-publish, ESG PDF, BMS ACK) — chưa DEV DONE
- B2-6 (Avro migration consumer) — chưa DEV DONE
- FE-5..FE-9 (ESG PDF UI, BMS SSE, Mobile) — chưa DEV DONE
- OPS-3..OPS-5 — chưa DEV DONE
- Native iOS/Android device test (QA-4) — yêu cầu phần cứng riêng

---

## 2. Chiến lược Test theo Task

### B1-1: ESG Permission Bypass

**Focus:** `POST /api/v1/esg/reports/generate` chỉ chấp nhận `SCOPE_esg:write`

| # | Test case | Expected | Type |
|---|-----------|----------|------|
| 1 | OPERATOR có `esg:write` → generate report | 202 Accepted | API |
| 2 | OPERATOR không có `esg:write` → từ chối | 403 Forbidden | API |
| 3 | OPERATOR có `esg:read` only → từ chối | 403 Forbidden | API |
| 4 | CITIZEN → từ chối | 403 Forbidden | API |
| 5 | Unauthenticated → từ chối | 401 Unauthorized | API |

**Risk:** Test với JWT token thực từ Keycloak (không chỉ mock user).

---

### B1-2: Apicurio Schema Registry

**Focus:** Container healthy, Spring Boot config load đúng

| # | Test case | Expected | Type |
|---|-----------|----------|------|
| 1 | `curl http://localhost:8087/apis/registry/v2/health` | `{"status":"UP"}` | Smoke |
| 2 | `docker compose up` không fail vì Apicurio | health check passes | Docker |
| 3 | Backend start với `APICURIO_REGISTRY_URL` env | No connection error in logs | Smoke |
| 4 | AvroConfig bean loaded | RegistryClient bean not null | Unit ✅ |

**Risk:** `apicurio-registry-mem` là in-memory — restart mất schema. Cần test behavior khi registry restart.

---

### B2-2: VibrationAnomalyJob

**Focus:** Welford cold start, CEP pattern, BR-010

| # | Test case | Expected | Type |
|---|-----------|----------|------|
| 1 | n < 1000 readings → không emit | Không có alert | Unit ✅ |
| 2 | n = 1000, spike > 4σ + > floor → emit | Alert emitted | Unit ✅ |
| 3 | 3 spikes liên tiếp trong 10s → alert | StructuralAlertEvent to Kafka | Unit ✅ |
| 4 | 2 spikes trong 10s → không alert | Không có alert | Unit ✅ |
| 5 | 3 spikes trong 15s → không alert (window=10s) | Không có alert | Unit |
| 6 | Vibration boundary: 9.9 mm/s → không anomaly | Pass | Unit ✅ |
| 7 | Vibration boundary: 10.0 mm/s → anomaly | Pass | Unit ✅ |
| 8 | Tilt boundary: 2.9 mrad → không anomaly | Pass | Unit ✅ |
| 9 | Crack boundary: 0.29 mm → không anomaly | Pass | Unit ✅ |
| 10 | Sensor A và Sensor B state độc lập | Pass | Unit ✅ |
| 11 | **BR-010**: `requiresOperatorReview = true` | LUÔN true | Unit ✅ |

**Risk cao:** Welford state cần ≥ 1000 readings để hoạt động — integration test cần seed data thực tế (xem §6).

---

### B2-3: BuildingSafetyService

**Focus:** Score algorithm, Redis cache, RLS isolation

| # | Test case | Expected | Type |
|---|-----------|----------|------|
| 1 | Không có alert → score=100, SAFE | Pass | Unit ✅ |
| 2 | 1 WARNING → score=90 | Pass | Unit ✅ |
| 3 | 1 CRITICAL → score=70 | Pass | Unit ✅ |
| 4 | 3 CRITICAL + 1 WARNING → score=0 (floor) | Pass | Unit ✅ |
| 5 | Score cache miss → DB query | DB hit | IT |
| 6 | Score cache hit → không query DB | Cache serve | IT |
| 7 | evictSafetyScore → next call hits DB | Pass | IT |
| **ISO-009** | Tenant A không thấy score của tenant B | 403 / empty | IT |
| **ISO-009b** | Cache key `tenantA:BLDG-001` ≠ `tenantB:BLDG-001` | Khác nhau | IT |

**Risk:** `TenantContext` phải được set trước khi gọi `@Cacheable` — nếu không thì cache key = `null:buildingId`.

---

### B2-4: REST API Building Safety

**Focus:** Endpoint contract, auth, response shape

| # | Test case | Expected | Type |
|---|-----------|----------|------|
| 1 | `GET /buildings/BLDG-001/safety` authenticated | 200 `{score, status, lastUpdated, activeAlerts}` | API ✅ |
| 2 | `GET /buildings/BLDG-001/safety` unauthenticated | 401 | API ✅ |
| 3 | `GET /buildings/BLDG-001/vibration/readings` | 200 `[]` (TODO B2-5) | API ✅ |
| 4 | `GET /buildings/BLDG-001/vibration/readings?sensorType=STRUCTURAL_TILT` | 200 | API ✅ |
| 5 | `GET /buildings/BLDG-001/vibration/readings?range=7d` | 200 | API |
| 6 | `GET /buildings/X-TENANT-B/safety` từ tenant A | 403 / empty | IT |

---

### B2-5: StructuralAlertConsumer

**Focus:** Pipeline đầy đủ, BR-010, dedup, DLQ

| # | Test case | Expected | Type |
|---|-----------|----------|------|
| 1 | CRITICAL alert → persist module=STRUCTURAL | DB record đúng module | Unit ✅ |
| 2 | CRITICAL alert → evict safety score cache | `evictSafetyScore` gọi | Unit ✅ |
| 3 | CRITICAL alert → dispatch notification | `notificationRouter.route` gọi | Unit ✅ |
| 4 | **BR-010**: notification message chứa "operator" | Pass | Unit ✅ |
| 5 | **BR-010**: notification KHÔNG chứa "auto-evacuate" | Pass | Unit ✅ |
| 6 | Tenant không hợp lệ → DLQ | `UIP.structural.alert.dlq.v1` | Unit ✅ |
| 7 | Duplicate trong 1 phút → suppressed | Không persist | Unit ✅ |
| 8 | WARNING severity → mapped thành HIGH | Pass | Unit ✅ |
| **ISO-008** | Tenant A không thấy structural alert của tenant B | Pass | IT |
| 9 | E2E: Flink emit → consumer persist → API trả score | < 15s | E2E |

---

### FE-1: SafetyScoreGauge

| # | Test case | Expected | Type |
|---|-----------|----------|------|
| 1 | score=0-40 → màu đỏ `#EF4444` | Visual | Manual |
| 2 | score=41-70 → màu vàng `#F59E0B` | Visual | Manual |
| 3 | score=71-100 → màu xanh `#22C55E` | Visual | Manual |
| 4 | offline=true → màu xám `#9CA3AF`, label "Ngoại tuyến" | Visual | Manual |
| 5 | Loading → skeleton hiển thị | Skeleton | Manual |
| 6 | Gauge kim chỉ đúng vị trí | score=100 → kim ở cực phải | Manual |
| 7 | Responsive: 768px mobile | Layout không vỡ | Manual |

---

### FE-2: SafetyTrendChart (BLOCKED)

> ⚠ **Blocked:** `GET /buildings/{id}/vibration/readings` trả `[]` cho đến khi B2-5 + data pipeline hoạt động. Thực hiện manual test sau khi structural sensor readings có data thực.

| # | Test case | Expected | Type |
|---|-----------|----------|------|
| 1 | Tab "Rung động" → hiển thị LineChart | Chart visible | Manual (khi có data) |
| 2 | Tab "Nghiêng" → switch chart | Chart update | Manual |
| 3 | Reference line WARNING = 10 mm/s | Đường ngang vàng | Manual |
| 4 | Reference line CRITICAL = 50 mm/s | Đường ngang đỏ | Manual |
| 5 | Brush zoom click-drag | Zoom in range | Manual |
| 6 | Empty state khi không có data | "Chưa có readings" text | Manual ✓ |

---

### FE-3: Building Detail Page

| # | Test case | Expected | Type |
|---|-----------|----------|------|
| 1 | `/buildings/BLDG-001?tab=safety` → load trang | Page renders | Manual |
| 2 | Tab "An toàn kết cấu" hiển thị gauge | Gauge visible | Manual |
| 3 | P0 CRITICAL alert banner → non-dismissible | Không có nút close | Manual |
| 4 | P1 WARNING alert banner → có nút close | Close button visible | Manual |
| 5 | URL tab state persist sau refresh | `?tab=safety` trong URL | Manual |
| 6 | Loading skeleton → data | Skeleton → gauge | Manual |
| 7 | Error state khi API fail | Error message | Manual |
| 8 | Building name từ API hiển thị đúng | Tên tòa nhà | Manual |

---

### FE-4: Safety Alert Integration

| # | Test case | Expected | Type |
|---|-----------|----------|------|
| 1 | Module filter "STRUCTURAL" → chỉ hiện STRUCTURAL | Filtered | Manual |
| 2 | STRUCTURAL alert → badge màu tím `#7B1FA2` | Visual | Manual |
| 3 | FLOOD alert → badge màu xanh `#0288D1` | Visual | Manual |
| 4 | `GET /api/v1/alerts?module=STRUCTURAL` | Chỉ trả STRUCTURAL alerts | API |
| 5 | BuildingSafetyMapOverlay: CRITICAL building → marker đỏ | Visual | Manual |
| 6 | Click marker → navigate `/buildings/:id?tab=safety` | Navigation | Manual |

---

## 3. Isolation Tests (BẮT BUỘC — QA-2)

### ISO-008: Structural Alert Tenant Isolation

```
Setup:
  - Tenant A: seed 1 STRUCTURAL alert cho BLDG-A01
  - Tenant B: seed 1 STRUCTURAL alert cho BLDG-B01

Kiểm tra:
  - GET /alerts?module=STRUCTURAL từ token tenant A → KHÔNG thấy alert của tenant B
  - GET /buildings/BLDG-B01/safety từ token tenant A → 403 hoặc 404
  - StructuralAlertConsumer: event với tenantId="unknown" → DLQ, không persist

Expected: RLS đảm bảo hoàn toàn isolation
```

### ISO-009: Safety Score Tenant Isolation

```
Setup:
  - Tenant A: BLDG-A01 có 1 CRITICAL alert → score=70
  - Tenant B: BLDG-A01 (cùng buildingId) không có alert → score=100

Kiểm tra:
  - GET /buildings/BLDG-A01/safety từ token tenant A → score=70
  - GET /buildings/BLDG-A01/safety từ token tenant B → score=100
  - Cache key tenant A: "hcm:BLDG-A01"
  - Cache key tenant B: "danang:BLDG-A01"
  - Không bị cross-tenant cache pollution

Risk: TenantContext chưa set → cache key "null:BLDG-A01" → dữ liệu lẫn nhau
```

---

## 4. Regression Plan

Features từ Sprint 1-6 có risk regression do Sprint 7 changes:

| Feature | Risk | Test |
|---------|------|------|
| ESG generate report | B1-1 thay đổi `@PreAuthorize` | Verify admin vẫn generate được |
| Flood alert flow | B2-5 thêm `module` filter vào AlertController | Verify flood alerts vẫn hiện trong `/alerts` |
| Alert SSE stream | StructuralAlertConsumer publish Redis channel | Verify flood/environment alerts không bị ảnh hưởng |
| V34 migration | ALTER TABLE alert_events ADD COLUMN building_id | Verify không break existing alert queries |
| Redis cache | Safety score cache mới `safety:score:*` | Verify không conflict với `sensors:*`, `alerts:*` caches |

---

## 5. SLA Verification Plan (QA-3)

### k6 Scenarios

```javascript
// Scenario 1: Safety score endpoint
export const safetyScoreOptions = {
  vus: 50,
  duration: '2m',
  thresholds: {
    http_req_duration: ['p95<2000'],  // <2s p95
    http_req_failed: ['rate<0.0001'], // <0.01% errors
  }
}
// GET /api/v1/buildings/{id}/safety × 50 VUs

// Scenario 2: Alert list with STRUCTURAL filter
export const alertFilterOptions = {
  vus: 100,
  thresholds: {
    http_req_duration: ['p99<100'],  // Kong p99 <100ms
  }
}
// GET /api/v1/alerts?module=STRUCTURAL × 100 VUs
```

### P0 Alert Latency (<15s E2E)

```
Measure: sensor event publish → Flink CEP detect → Kafka UIP.structural.alert.critical.v1
         → StructuralAlertConsumer persist → NotificationRouter dispatch
Target: < 15s end-to-end
```

---

## 6. Test Data Requirements

### Structural sensor seed

```sql
-- Cần seed structural sensors với building_id để test B2-4 + FE-3
INSERT INTO environment.sensors (sensor_id, sensor_name, sensor_type, building_id, is_active)
VALUES
  ('SENSOR-VIBR-001', 'Vibration Sensor F1', 'STRUCTURAL_VIBRATION', 'BLDG-001', true),
  ('SENSOR-TILT-001', 'Tilt Sensor F1',      'STRUCTURAL_TILT',      'BLDG-001', true),
  ('SENSOR-CRACK-001','Crack Sensor F1',      'STRUCTURAL_CRACK',     'BLDG-001', true);
```

### Welford warm-up data

```
Để test VibrationAnomalyJob:
  - Cần seed ≥ 1000 sensor readings với giá trị bình thường (e.g. vibration ~5.0 mm/s)
  - Sau đó inject 3 spike readings > 10 mm/s trong 10s → expect alert
  - Script: flink-jobs/src/test/resources/seed-structural-readings.sh (cần tạo)
```

---

## 7. Entry/Exit Criteria

### Entry Criteria (bắt đầu test)

- [ ] Docker compose up thành công: Apicurio healthy (port 8087)
- [ ] Backend compile + 68 unit tests PASS (toàn bộ safety module)
- [ ] DB migration V34 applied thành công
- [ ] Frontend TypeScript 0 errors, dev server start OK

### Exit Criteria (sprint DONE)

- [ ] Tất cả TC Priority 1 PASS (không có open P0/P1 bugs)
- [ ] ISO-008 và ISO-009 PASS
- [ ] BR-010 verified: không có auto-evacuate path
- [ ] SLA gate: P0 alert latency < 15s verified
- [ ] OWASP ZAP 0 Critical findings (QA-6)
- [ ] 100+ regression test cases documented (QA-2)

---

## 8. Test Schedule

| Sprint Day | Activity | Owner |
|-----------|----------|-------|
| Day 1-4 | QA-1: Fix 4 flaky E2E tests | QA Engineer |
| Day 5-6 | B1-1, B1-2, B2-3, B2-4 API tests | Tester |
| Day 6-7 | B2-5 consumer test (ISO-008, ISO-009) | Tester |
| Day 7-8 | FE-1, FE-3, FE-4 manual UI tests | Tester |
| Day 8-9 | QA-2: Regression 100+ test cases | QA + Tester |
| Day 9 | QA-3: SLA gate k6 + B2-2 E2E latency | QA Engineer |
| Day 9 | QA-6: OWASP ZAP scan | QA Engineer |
| Day 10 | Defect triage + retest | QA + Dev |

---

## 9. Known Limitations & Notes

1. **FE-2 blocked**: `GET /buildings/{id}/vibration/readings` trả `[]` — manual test defer đến B2-5 data pipeline done
2. **Apicurio in-memory**: `apicurio-registry-mem` image không persistent — schemas mất khi restart. Pilot production cần dùng image khác.
3. **Welford cold start**: Cần seed ≥ 1000 readings trước khi integration test anomaly detection
4. **building_id trong Sensor entity**: Chưa mapped trong `Sensor.java` (column tồn tại trong DB từ V2). `GET /buildings/{id}/vibration/readings` sẽ cần mapping này để filter đúng sensor readings.
5. **Cache key null risk**: `TenantContext.getCurrentTenant()` trong SpEL — nếu không set → cache key `"null:buildingId"` — cần verify trong IT rằng TenantContext luôn được set.

---

*QA Test Strategy Sprint 7 — Version 1.0*
*Reference: ADR-034-structural-monitoring.md, sprint7-task-assignments.md*
