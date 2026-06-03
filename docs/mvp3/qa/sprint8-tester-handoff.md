# Sprint 8 — Tester Handoff Document

**Từ:** Dev + QA Team  
**Đến:** Tester  
**Ngày handoff:** 2026-06-04  
**Gate Review:** 2026-06-17 15:00 SGT  
**Sprint:** MVP3-8 — Pilot Prep + Mobile + Infrastructure HA

---

## Tổng quan

Tất cả dev tasks đã hoàn thành. SA Code Review APPROVED (2 CRITICAL + 3 MAJOR đã fix).
QA automation (285 TCs) đã PASS. **Tester cần thực hiện manual verification** để xác nhận
từ góc nhìn người dùng cuối trên staging environment.

**Staging URL:** `http://staging.uip.smartcity.vn` (hoặc theo môi trường local)  
**Flink UI:** `http://localhost:8081`  
**Keycloak Admin:** `http://localhost:8082`

---

## Checklist trước khi bắt đầu (S8-TEST-PREP)

```
□ Staging environment running: docker compose -f docker-compose.yml -f docker-compose.ha.yml up -d
□ Tất cả services healthy: docker compose ps (không có service nào "Exit" hoặc "Restarting")
□ Flink jobs running: http://localhost:8081 → VibrationAnomalyJob + EsgAggregationJob RUNNING
□ Mobile device / simulator: Expo Go hoặc React Native emulator ready
□ Postman / curl available cho API testing
□ kafka-console-consumer available (hoặc dùng docker exec vào uip-kafka)
□ Test accounts: admin/admin_Dev#2026! đăng nhập được
□ Pilot accounts: pilot-admin/pilot-operator/pilot-viewer (Keycloak realm: uip-pilot)
```

---

## Task 1 — S8-TEST-MOBILE: Mobile App (21 TCs) 🔴 PRIORITY

**Story:** S8-M01 + S8-M02  
**File TC:** [sprint8-manual-test-cases.md](sprint8-manual-test-cases.md) — section 5

### Dashboard (TC-S8-030 → TC-S8-037)

| TC | Scenario | Expected | Status |
|----|----------|----------|--------|
| TC-S8-030 | Mở app → Dashboard hiển thị 4 KPI cards | 4 cards có data thật, không "—" | ⬜ |
| TC-S8-031 | Energy KPI card | Hiển thị kWh + trend (up/down/stable) | ⬜ |
| TC-S8-032 | Safety Score card | Score 0-100 + màu đúng (green/amber/red) | ⬜ |
| TC-S8-033 | AQI card | Số AQI + label tiếng Việt (Tốt/Trung bình/Kém/...) | ⬜ |
| TC-S8-034 | Active Alerts card | Số lượng alerts đang OPEN | ⬜ |
| TC-S8-035 | Bottom tabs | 4 tabs navigate đúng screen | ⬜ |
| TC-S8-036 | Pull-to-refresh | Kéo xuống → data refresh | ⬜ |
| TC-S8-037 | Auto-refresh 30s | Để 30s → data tự cập nhật (không cần kéo) | ⬜ |

### Alerts + Safety (TC-S8-038 → TC-S8-045)

| TC | Scenario | Expected | Status |
|----|----------|----------|--------|
| TC-S8-038 | Alerts screen | List hiển thị, sorted P0 first | ⬜ |
| TC-S8-039 | Severity filter P0 | Tap "P0" → chỉ hiện P0 alerts | ⬜ |
| TC-S8-040 | Severity filter P1 | Tap "P1" → chỉ hiện P1 alerts | ⬜ |
| TC-S8-041 | Module filter STRUCTURAL | Tap "Kết cấu" → chỉ hiện structural | ⬜ |
| TC-S8-042 | Safety score gauge | Bar/gauge 0-100 hiển thị đúng màu | ⬜ |
| TC-S8-043 | **Safety status "Tốt"** | Khi không có critical alert → hiển thị "Tốt" (không phải "Offline") | ⬜ |
| TC-S8-044 | Pull-to-refresh alerts | Kéo xuống → list refresh | ⬜ |
| TC-S8-045 | Empty state | Khi filter không có kết quả → "Không có cảnh báo" ✅ icon | ⬜ |

> **Lưu ý TC-S8-043:** Đây là SA finding C-1 đã fix. Nếu thấy "Offline" khi building OK → P0 bug.

---

## Task 2 — S8-TEST-INFRA: Infrastructure Manual Tests (20 TCs)

**File TC:** [sprint8-manual-test-cases.md](sprint8-manual-test-cases.md) — section 2-3

### ClickHouse HA (TC-S8-010 → TC-S8-016)

| TC | Scenario | Expected | Status |
|----|----------|----------|--------|
| TC-S8-010 | CH node failover | Stop clickhouse-01 → analytics API vẫn trả data từ node-02 | ⬜ |
| TC-S8-011 | CH replication lag | Insert 1000 rows node-01 → xuất hiện ở node-02 trong 5s | ⬜ |
| TC-S8-012 | CH node rejoin | Start lại node-01 → tự đồng bộ data | ⬜ |
| TC-S8-013 | CH BACKWARD compat | `CLICKHOUSE_CLUSTER_ENABLED=false` → 82 analytics tests vẫn PASS | ⬜ |
| TC-S8-014 | CH both nodes down | Cả 2 node down → API trả 200 với data rỗng (không crash) | ⬜ |
| TC-S8-015 | **Keeper <server_id>** | Keeper container healthy (`docker ps` → healthy) | ⬜ |
| TC-S8-016 | CH data migration | Row count trước/sau migration bằng nhau | ⬜ |

> **Lưu ý TC-S8-015:** SA finding C-1 đã fix (`<server_id>1</server_id>`). Nếu keeper unhealthy → P0 bug.

### Kafka 3-broker (TC-S8-020 → TC-S8-026)

| TC | Scenario | Expected | Status |
|----|----------|----------|--------|
| TC-S8-020 | Kafka broker failover | Stop kafka-2 → producers/consumers tiếp tục | ⬜ |
| TC-S8-021 | Kafka rolling restart | Restart từng broker → không mất message | ⬜ |
| TC-S8-022 | Kafka RF=3 verified | `kafka-topics --describe` → ReplicationFactor=3 | ⬜ |
| TC-S8-023 | min.insync.replicas=2 | Producer với 1 broker → NotEnoughReplicasException | ⬜ |
| TC-S8-024 | VibrationAnomalyJob alert | Inject 3 spikes >50mm/s → alert trong 15s | ⬜ |

### PG Streaming Replication (TC-S8-027 → TC-S8-029)

| TC | Scenario | Expected | Status |
|----|----------|----------|--------|
| TC-S8-027 | PG replication lag | Insert 1000 rows primary → standby sync trong 1s | ⬜ |
| TC-S8-028 | Standby read-only | Write vào port 5433 → ERROR: read-only transaction | ⬜ |
| TC-S8-029 | **Replicator role** | `pg_stat_replication.usename = 'replicator'` (không phải 'uip') | ⬜ |

> **Lưu ý TC-S8-029:** SA finding M-3 đã fix. Xác nhận replication dùng dedicated role.

---

## Task 3 — S8-TEST-FLINK: Flink CI/CD (6 TCs)

**File TC:** [sprint8-manual-test-cases.md](sprint8-manual-test-cases.md) — section 4

| TC | Command | Expected | Status |
|----|---------|----------|--------|
| TC-S8-050 | `make flink-build` | JAR compiled thành công | ⬜ |
| TC-S8-051 | `make flink-submit` | Job submitted, status RUNNING trên UI | ⬜ |
| TC-S8-052 | `make flink-deploy` | Savepoint → cancel → submit mới | ⬜ |
| TC-S8-053 | `make flink-list` | Liệt kê running jobs | ⬜ |
| TC-S8-054 | Checkpoint recovery | Stop jobmanager → start → job resume từ checkpoint | ⬜ |
| TC-S8-055 | Avro auto-registration | `make register-schemas` → 4 schemas trên Apicurio | ⬜ |

---

## Task 4 — S8-TEST-KEYCLOAK: Pilot Realm (5 TCs)

| TC | Scenario | Expected | Status |
|----|----------|----------|--------|
| TC-S8-060 | pilot-admin login | `http://localhost:8082` → login thành công, role ADMIN | ⬜ |
| TC-S8-061 | pilot-operator login | Login thành công, role OPERATOR | ⬜ |
| TC-S8-062 | pilot-viewer login | Login thành công, role VIEWER (read-only) | ⬜ |
| TC-S8-063 | uip-mobile PKCE client | PKCE flow từ mobile app → token obtained | ⬜ |
| TC-S8-064 | JWT claims | Token có `iss`, `sub`, `tenant_id`, roles đúng | ⬜ |

**Pilot credentials (đổi trước external pilot — I-1 từ SA review):**

| User | Password (staging only) | Role |
|------|-------------------------|------|
| pilot-admin | pilot-admin-S8! | ADMIN |
| pilot-operator | pilot-op-S8! | OPERATOR |
| pilot-viewer | pilot-viewer-S8! | VIEWER |

---

## Task 5 — S8-TEST-SMOKE: Post-deploy Smoke (10 endpoints)

```bash
# Lấy token
TOKEN=$(curl -sf -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin_Dev#2026!"}' | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"
```

| TC | Endpoint | Expected HTTP | Status |
|----|----------|---------------|--------|
| TC-S8-070 | `GET /api/v1/dashboard` | 200 + `energyKwh`, `safetyStatus` | ⬜ |
| TC-S8-071 | `GET /api/v1/dashboard/stats` | 200 + `activeSensors` (backward compat) | ⬜ |
| TC-S8-072 | `GET /api/v1/alerts?page=0&size=10` | 200 + array | ⬜ |
| TC-S8-073 | `GET /api/v1/environment/sensors` | 200 + array | ⬜ |
| TC-S8-074 | `GET /api/v1/esg/summary?period=quarterly&year=2026&quarter=1` | 200 | ⬜ |
| TC-S8-075 | `GET /api/v1/buildings` | 200 + array | ⬜ |
| TC-S8-076 | `GET /api/v1/bms/devices` | 200 + array | ⬜ |
| TC-S8-077 | `GET http://localhost:8123/ping` (CH node-01) | `Ok.` | ⬜ |
| TC-S8-078 | `GET http://localhost:8124/ping` (CH node-02) | `Ok.` | ⬜ |
| TC-S8-079 | `GET http://localhost:8080/actuator/health` | `{"status":"UP"}` | ⬜ |

---

## Task 6 — S8-TEST-REPORT: Deliverables

Sau khi hoàn thành tất cả test tasks, tạo file:

**`docs/mvp3/qa/sprint8-manual-test-execution-report.md`**

Bao gồm:
- Executive Summary: tổng PASS/FAIL/SKIP
- Table kết quả từng TC
- Screenshots: Mobile Dashboard iOS + Android (TC-S8-030)
- Screenshots: Mobile Alerts với safety "Tốt" (TC-S8-043)
- Screenshots: CH failover (TC-S8-010) + Kafka failover (TC-S8-020)
- Bug reports (nếu có) với reproduction steps
- **Go/No-Go recommendation cho Gate Review 2026-06-17**

---

## SA Fixes cần verify đặc biệt

| Fix | TC | Mô tả |
|-----|----|-------|
| C-1: Keeper `<server_id>` | TC-S8-015 | Keeper container phải healthy |
| C-2: `/api/v1/dashboard` endpoint | TC-S8-070 | Phải trả đủ fields, không 404 |
| M-1: `SAFE` vs `GOOD` enum | TC-S8-043 | "Tốt" hiển thị khi building OK |
| M-3: Replicator role | TC-S8-029 | `usename = 'replicator'` trong pg_stat_replication |

---

## Khi nào chuyển DONE?

```
Tất cả 62 TCs PASS
→ Tester ký vào sprint8-manual-test-execution-report.md
→ PM update sprint8-plan.md: tất cả gates = ✅ PASS
→ Sprint 8 status: DONE — sẵn sàng Gate Review 2026-06-17
```

Nếu có bug **P0/P1** → tạo bug report → assign về dev → fix → retest trước 2026-06-16 EOD.

---

*Tester Handoff — Sprint 8 | 2026-06-04 | Gate Review: 2026-06-17 15:00 SGT*
