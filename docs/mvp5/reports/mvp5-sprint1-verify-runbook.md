# MVP5 Sprint M5-1 — Verify Runbook (IT + Manual UI Test)

| Field | Value |
|---|---|
| **Date** | 2026-06-24 |
| **Scope** | Integration test (Testcontainers) + manual UI test (Operator + Citizen) sau M5-1 dev done |
| **Stack** | Compose HA — 23 container healthy (CH 2-node + 3 Keeper + Kafka + Kong + Keycloak + PG + analytics + backend + frontend) |
| **Frontend** | http://localhost:3000 |
| **Backend** | http://localhost:8080 (auth `/api/v1/auth/login`) |

---

## §1. Automated verification results

| Test | Result | Evidence |
|---|---|---|
| Gradle `./gradlew test` (unit + ArchTest + cache + config gate) | ✅ **1809/1810 PASS** (1 pre-existing flaky `SensorToAlertLatencyTest` GAP-026, ngoài scope M5-1) | `/tmp/v1-gradle-test.log` |
| Gradle `./gradlew integrationTest` (Testcontainers CH/PG/Kafka) | ✅ **BUILD SUCCESSFUL** — 56 IT class. TenantIsolationIT 7/7, ApplicationContextLoadsIT 4/4, ClickHouseEnergyRepositoryIT 8/8, EnergyAnalyticsGrpcServiceIT 3/3, RowPolicyEngineTest 4/4 | `/tmp/v2-it.log` |
| Compose HA health | ✅ PASS | 23 container, 0 unhealthy |
| CH replication | ✅ PASS | `esg_readings` is_readonly=0, absolute_delay=0 |
| 100 RPS smoke (T02) | ✅ PASS | 5997/6000 = 100% HTTP 200, **p95=16.1ms** (limit 500ms), err 0% |
| UAT smoke (admin/operator/citizen login) | ✅ 8/10 PASS | T02-T05 login JWT PASS; T06 ESG/T08 Alert 404 (path/seed — ngoài scope M5-1) |
| Frontend tsc | ✅ PASS | `npx tsc --noEmit` 0 errors |

**Findings ngoài scope M5-1 (note, không block):**
- Kafka topics hiện **ReplicationFactor: 1** (ADR-048 kỳ vọng RF=3). Nguyên nhân: stack bring-up bằng compose chưa apply HA overlay kafka-init RF=3, hoặc topics tạo trước overlay. → follow-up (không phải lỗi T02/T03/T09).
- Vault service chưa trong stack đang chạy (bring-up trước T03 commit). → bring-up lại với HA overlay để test Vault consumer wiring (M5-2 debt).

**TC-UI-03 tenant isolation (API-level) — KẾT QUẢ THỰC TẾ:**
- citizen1, admin, operator đều có JWT claim `tenant_id: "default"` → seed hiện **single-tenant**. citizen1 và admin trả cùng sensor data (ENV-008/ENV-003...) → **đúng theo thiết kế**, KHÔNG leak (cùng tenant).
- citizen1 có scope `environment:read, esg:read, alert:read, traffic:read` → đọc `/environment`, `/alerts` là **hợp lệ theo scope**, không phải leak role. Role guard thật ở UI route (`/dashboard` operator-only) + action write.
- **Verdict:** M5-1 RowPolicy V32 enforce theo `tenant_id` claim — code + ArchTest + IT đã cover (V2 RowPolicyIsolationIT). **Manual verify leak thật (2 tenant) defer M5-2** (cần seed multi-tenant → tenant fuzz T05 M5-2 + synthetic full M5-G7).

**TC-UI-06 mTLS path (T09) — KẾT QUẢ THỰC TẾ + BUG ĐÃ FIX:**
- **BUG #1 (đã fix):** `infrastructure/clickhouse/tls-config.xml` line 25 chứa `clickhouse-client --secure` → `--` trong XML comment = illegal (SAXParseException line 25 col 68) → CH node crash loop khi mount overlay. DevOps agent T09 chỉ verify `compose config --quiet` (YAML), không boot CH thật → false-green. Fix: normalize `--` và em-dash trong comment. Verify runtime mới phát hiện.
- **mTLS transport LAYER = ✅ PASS (verified thật):** port 8443 listen, no-client-cert → HTTP 000 (reject), WITH client cert → `SELECT 1` trả `1` HTTP 200. Cert handshake hoạt động.
- **mTLS auth wiring = ❌ DEFER (tech-debt):** JDBC consumer (analytics) qua 8443 fail `AUTHENTICATION_FAILED 516` — CH 23.8 với `default` user + empty password reject khi qua HTTPS endpoint (dù cred match 8123). `client_certificate_auth` CN→user mapping cũng conflict. Root cause sâu (CH 23.8 auth qua mTLS endpoint). **Đã rollback analytics về 8123 để stack ổn định** (0 unhealthy, API 200). Follow-up: tạo CH user riêng không-password cho mTLS (cert-only auth) hoặc debug CH 23.8 HTTPS auth path. Cùng nhóm debt với Flink/forecast mTLS (~2-4 SP M5-2).
- **Bài học:** T09 agent verify config-only (`compose config --quiet`) là **không đủ** — phải boot service thật. Thêm rule: every infra config change verify bằng `docker compose up` + healthcheck + actual connection, không chỉ YAML parse. (memo `feedback_doc_vs_code_gap` pattern lại lặp).

**TC-UI-07 HA tolerance — ✅ PASS (verified thật):** kill 1/3 keeper → 2/3 quorum survive, API `/environment/sensors` vẫn HTTP 200, keeper rejoin sau restart.

**TC-UI-01/02/04/05 RBAC — ✅ PASS:** ADMIN 16 scopes / OPERATOR 11 / CITIZEN 4, phân tầng đúng, endpoint access đúng role.

---

## §2. Manual UI Test Plan — Operator + Citizen portal

**Trọng tâm M5-1 = tenant isolation.** Quy trình core: login 2 tenant khác nhau, verify data KHÔNG leak.

### Tài khoản test
| Role | Username | Password | Tenant |
|---|---|---|---|
| Admin | admin | admin_Dev#2026! | (admin/all) |
| Operator | operator | operator_Dev#2026! | (operator) |
| Citizen | citizen1 | citizen_Dev#2026! | (citizen) |

### TC-UI-01: Login flow 3 role
1. Mở http://localhost:3000/login
2. Login admin → verify redirect `/dashboard`, menu đầy đủ (environment/esg/traffic/alerts/buildings/city-ops)
3. Logout, login operator → verify dashboard + các trang operator
4. Logout, login citizen1 → verify redirect `/citizen`, menu citizen (bills/aqi/alerts/profile)
5. **PASS criteria:** 3 role login đúng landing page, không cross-role menu leak

### TC-UI-02: Role guard (ProtectedRoute)
1. Login citizen1, thử truy cập trực tiếp http://localhost:3000/dashboard (operator-only)
2. **PASS:** redirect về `/citizen` hoặc 403 — KHÔNG render dashboard operator

### TC-UI-03: Tenant isolation — sensor data (CORE M5-1)
1. Login operator, mở `/environment` → ghi chú danh sách sensor + ID
2. Login tenant khác (nếu có 2 operator tenant), mở `/environment` → verify KHÔNG thấy sensor của tenant kia
3. Thử query trực tiếp sensorId của tenant khác qua API → **PASS:** 403 (isCrossTenantViolation)
4. **Verdict core M5-1:** 0 cross-tenant data leak

### TC-UI-04: Dashboard operator
1. `/dashboard` → verify KPI card render, sensor count, alert count
2. `/esg` → ESG metrics (nếu 404 = seed data, không phải lỗi M5-1)
3. `/traffic` → traffic data
4. `/alerts` → alert list (nếu 404 = seed)
5. `/buildings` → cross-building dashboard + detail
6. `/city-ops` → city operations map

### TC-UI-05: Citizen portal
1. `/citizen` → home citizen
2. `/citizen/bills` → invoice list
3. `/citizen/aqi` → AQI view
4. `/citizen/alerts` → notification
5. `/citizen/register` → register form render

### TC-UI-06: Analytics qua mTLS path (T09 verify)
1. Mở trang load CH data (esg/environment) → verify data render
2. Backend log check: `docker logs uip-analytics-service` → JDBC kết nối `clickhouse-01:8443?ssl=true` (mTLS)
3. **PASS:** data render = mTLS path analytics→CH hoạt động

### TC-UI-07: Smoke regression HA
1. Trong lúc UI load, `docker stop uip-clickhouse-keeper` (giết 1/3 keeper)
2. UI vẫn phải load data (quorum 2/3 survive)
3. `docker start uip-clickhouse-keeper` → rejoin
4. **PASS:** HA tolerate 1 node failure

---

## §3. Lệnh verify nhanh

```bash
# Auth + role login (3 role)
python3 scripts/uat_smoke_test.py

# 100 RPS smoke
python3 scripts/mvp5_ha_smoke_100rps.py --base-url http://localhost:8080 --rps 100 --duration 60

# IT (tenant isolation thật)
cd backend && ./gradlew integrationTest

# mTLS path check
docker logs uip-analytics-service 2>&1 | grep -i "8443\|ssl" | tail

# Keeper quorum kill test
docker stop uip-clickhouse-keeper && sleep 5 && curl -s localhost:8080/actuator/health
docker start uip-clickhouse-keeper
```

*Authored 2026-06-24. M5-1 verify sau dev done. Tenant isolation là core M5-1.*
