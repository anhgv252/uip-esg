# Sprint 8 — Manual Test Cases

**Tester:** UIP Tester | **Date:** 2026-06-03
**Sprint:** MVP3-8 | Hybrid: Pilot Prep + Mobile + Infrastructure HA
**Based on:** [Sprint 8 User Stories](../project/sprint8-stories.md) + [Test Strategy](sprint8-test-strategy.md)

---

## 1. SLA-001 Fix — Flink Kafka Config (S8-C01)

### TC-S8-001: VibrationAnomalyJob Consumes from Kafka

| Field | Value |
|-------|-------|
| **Story** | S8-C01 |
| **Priority** | P0 |
| **Precondition** | Full stack running, Flink jobmanager UP, Kafka UP |
| **Type** | Integration |

**Steps:**
1. Verify VibrationAnomalyJob is RUNNING on Flink dashboard (`http://localhost:8081`)
2. Inject 3 vibration sensor readings >50mm/s within 10 seconds:
   ```
   POST /api/v1/sensors/SENSOR-VIB-001/readings
   {"value": 55.0, "unit": "mm/s", "sensorType": "STRUCTURAL_VIBRATION"}
   ```
   (repeat 3 times with <10s interval)
3. Wait 15 seconds
4. Check alerts: `GET /api/v1/alerts?module=STRUCTURAL`
5. Check Kafka topic: `kafka-console-consumer --topic UIP.structural.alert.critical.v1 --from-beginning`

**Expected Results:**
- ✅ Flink dashboard shows VibrationAnomalyJob status = RUNNING
- ✅ Structural alert appears in API response within 15 seconds of 3rd spike
- ✅ Kafka topic `UIP.structural.alert.critical.v1` contains alert event
- ✅ Alert has `requiresOperatorReview: true` (BR-010)

**Fail criteria:** No alert after 30 seconds = P0 BLOCKER

---

### TC-S8-002: Flink Checkpoint Recovery

| Field | Value |
|-------|-------|
| **Story** | S8-C01 |
| **Priority** | P0 |
| **Precondition** | VibrationAnomalyJob RUNNING with checkpoint |

**Steps:**
1. Verify job has at least 1 successful checkpoint
2. Stop Flink jobmanager: `docker compose stop flink-jobmanager`
3. Wait 30 seconds
4. Start Flink jobmanager: `docker compose start flink-jobmanager`
5. Verify job restarts from checkpoint
6. Inject 3 vibration spikes
7. Verify alert still generates

**Expected Results:**
- ✅ Job resumes from last checkpoint (check "Start Time" in Flink UI)
- ✅ No data gap >5 minutes
- ✅ Alert still generates correctly after recovery

---

## 2. ClickHouse 2-node HA (S8-OPS01)

### TC-S8-010: ClickHouse Node Failover

| Field | Value |
|-------|-------|
| **Story** | S8-OPS01 |
| **Priority** | P0 |
| **Precondition** | CH cluster running (clickhouse-01 + clickhouse-02 + keeper) |

**Steps:**
1. Verify both nodes healthy:
   ```bash
   curl "http://localhost:8123/?query=SELECT * FROM system.clusters"
   curl "http://localhost:8124/?query=SELECT * FROM system.clusters"
   ```
2. Insert test data via node-1:
   ```sql
   INSERT INTO analytics.sensor_reading_hourly VALUES (1, 1, 1, 'energy', now(), 100.0, 100.0, 1);
   ```
3. Query data from node-2 → verify row appears
4. **CHAOS:** Stop clickhouse-01: `docker compose stop clickhouse-01`
5. Query analytics API: `GET /api/v1/analytics/energy?buildingIds=BLDG-001`
6. Start clickhouse-01 again: `docker compose start clickhouse-01`
7. Verify node rejoins cluster

**Expected Results:**
- ✅ Step 3: Row appears on node-2 within 5 seconds
- ✅ Step 5: API returns 200 (not 500) — data from node-2
- ✅ Step 7: Node-1 rejoins, data consistent

---

### TC-S8-011: ClickHouse Data Migration Integrity

| Field | Value |
|-------|-------|
| **Story** | S8-OPS01 |
| **Priority** | P0 |
| **Precondition** | Pre-migration data exists in single-node CH |

**Steps:**
1. Count rows before migration: `SELECT count() FROM analytics.sensor_reading_hourly`
2. Execute migration (DevOps performs)
3. Count rows after migration on both nodes
4. Spot-check 10 random rows for data integrity

**Expected Results:**
- ✅ Row count matches exactly before/after migration
- ✅ Random spot-check: all fields match (tenant_id, building_id, avg_value, etc.)
- ✅ Both nodes have identical data

---

### TC-S8-012: ClickHouse BACKWARD Compat (Tier 1)

| Field | Value |
|-------|-------|
| **Story** | S8-OPS01 |
| **Priority** | P0 |
| **Precondition** | CLICKHOUSE_CLUSTER_ENABLED=false (Tier 1 mode) |

**Steps:**
1. Start stack with single-node config
2. Run all 82 existing analytics regression tests
3. Verify dashboard loads correctly
4. Verify ESG report generates

**Expected Results:**
- ✅ 82/82 analytics regression tests PASS
- ✅ Dashboard renders analytics data
- ✅ ESG report generates successfully

---

## 3. Kafka 3-broker KRaft (S8-OPS02)

### TC-S8-020: Kafka Broker Failover

| Field | Value |
|-------|-------|
| **Story** | S8-OPS02 |
| **Priority** | P0 |
| **Precondition** | 3 Kafka brokers running |

**Steps:**
1. Verify 3 brokers in cluster:
   ```bash
   kafka-broker-api-versions --bootstrap-server localhost:9092 | head
   kafka-metadata-quorum --bootstrap-server localhost:9092 describe --status
   ```
2. Start producing messages (k6 sensor simulation)
3. **CHAOS:** Stop kafka-1: `docker compose stop kafka-1`
4. Verify producers continue without error
5. Verify consumers continue receiving messages
6. Check no message loss (count before vs after)
7. Start kafka-1: `docker compose start kafka-1`

**Expected Results:**
- ✅ Step 2: 3 brokers visible in quorum
- ✅ Step 4: Producer acks continue (via kafka-2, kafka-3)
- ✅ Step 5: Consumer continues receiving messages
- ✅ Step 6: Zero message loss
- ✅ Step 7: kafka-1 rejoins quorum

---

### TC-S8-021: Kafka Topic Replication Verification

| Field | Value |
|-------|-------|
| **Story** | S8-OPS02 |
| **Priority** | P0 |
| **Precondition** | 3 brokers running, topics migrated |

**Steps:**
1. Check topic configuration:
   ```bash
   kafka-topics --bootstrap-server localhost:9092 --describe --topic UIP.iot.sensor.reading.v1
   ```
2. Verify `ReplicationFactor: 3`
3. Verify all partitions have ISR (In-Sync Replicas) = 3
4. Check min.insync.replicas config

**Expected Results:**
- ✅ ReplicationFactor: 3 for all 5+ topics
- ✅ ISR count = 3 for all partitions
- ✅ min.insync.replicas = 2

---

## 4. Mobile Dashboard (S8-M01)

### TC-S8-030: Mobile Dashboard — KPI Cards Display

| Field | Value |
|-------|-------|
| **Story** | S8-M01 |
| **Priority** | P0 |
| **Precondition** | App running on Expo Go, operator logged in via PKCE |
| **Platform** | iOS Simulator + Android Emulator |

**Steps:**
1. Open app → login with operator account
2. Dashboard loads automatically
3. Verify 4 KPI cards visible:
   - Energy (kWh) — numeric value + trend arrow
   - Safety Score (0-100) — gauge visualization
   - AQI (0-500) — color-coded value
   - Active Alerts — count + severity badge
4. Verify each card shows real data (not placeholder)
5. Verify bottom tab bar: Dashboard | Alerts | Buildings | Profile

**Expected Results:**
- ✅ 4 KPI cards visible with numeric values
- ✅ Values match API response (`GET /api/v1/dashboard`)
- ✅ Bottom tabs all present and tappable
- ✅ No console errors in Expo

**Screenshot:** Capture dashboard on both iOS + Android

---

### TC-S8-031: Mobile Dashboard — Card Navigation

| Field | Value |
|-------|-------|
| **Story** | S8-M01 |
| **Priority** | P0 |
| **Platform** | iOS + Android |

**Steps:**
1. On Dashboard, tap "Energy" KPI card
2. Verify navigates to Building Analytics screen
3. Go back → tap "Safety" KPI card
4. Verify navigates to Building Safety screen
5. Go back → tap "AQI" KPI card
6. Verify navigates to Environment screen
7. Go back → tap "Alerts" KPI card
8. Verify navigates to Alert List screen

**Expected Results:**
- ✅ Each card navigates to correct screen
- ✅ Back navigation works correctly
- ✅ No crash or blank screen on any navigation

---

### TC-S8-032: Mobile Dashboard — Responsive Layout

| Field | Value |
|-------|-------|
| **Story** | S8-M01 |
| **Priority** | P0 |
| **Platform** | iPhone SE (375px) + iPad (1024px) |

**Steps:**
1. Open app on iPhone SE simulator
2. Verify all 4 KPI cards visible (no horizontal scroll)
3. Verify bottom tabs accessible (not cut off)
4. Open app on iPad simulator
5. Verify layout fills screen properly (no excessive whitespace)
6. Verify cards are larger on iPad (responsive)

**Expected Results:**
- ✅ iPhone SE: all content visible, no overflow
- ✅ iPad: layout fills screen, proper spacing
- ✅ No text truncation on small screen

---

### TC-S8-033: Mobile Dashboard — Token Expiry Handling

| Field | Value |
|-------|-------|
| **Story** | S8-M01 |
| **Priority** | P0 |
| **Platform** | iOS + Android |

**Steps:**
1. Login with operator account
2. Manually expire token (clear SecureStore or set short expiry)
3. Wait for dashboard to auto-refresh (30s cycle)
4. Verify auto-refresh token → data loads successfully

**Expected Results:**
- ✅ Token auto-refreshed via refresh_token
- ✅ Dashboard data loads after token refresh
- ✅ No "Unauthorized" error shown to user

---

## 5. Mobile Alerts + Building Safety (S8-M02)

### TC-S8-040: Mobile Alert List — Sort by Severity

| Field | Value |
|-------|-------|
| **Story** | S8-M02 |
| **Priority** | P0 |
| **Platform** | iOS + Android |

**Steps:**
1. Open Alerts tab
2. Verify alerts listed in order: P0 first, then P1, then P2
3. Create a new P0 alert (via API inject)
4. Pull to refresh
5. Verify new P0 alert appears at top

**Expected Results:**
- ✅ Alerts sorted: P0 → P1 → P2
- ✅ New P0 alert appears at top after refresh
- ✅ Each alert shows severity badge with correct color

---

### TC-S8-041: Mobile Alert List — Severity Filter

| Field | Value |
|-------|-------|
| **Story** | S8-M02 |
| **Priority** | P0 |
| **Platform** | iOS + Android |

**Steps:**
1. Open Alerts tab
2. Tap filter chip "P0"
3. Verify only P0 alerts visible
4. Tap filter chip "P1"
5. Verify only P1 alerts visible
6. Tap "All" filter
7. Verify all alerts visible

**Expected Results:**
- ✅ Filter correctly shows only selected severity
- ✅ "All" filter restores complete list
- ✅ Filter state persists when switching tabs

---

### TC-S8-042: Mobile Safety Score Gauge

| Field | Value |
|-------|-------|
| **Story** | S8-M02 |
| **Priority** | P0 |
| **Platform** | iOS + Android |

**Steps:**
1. Navigate to Buildings tab
2. Select a building (BLDG-001)
3. Verify Safety Score gauge visible
4. Check color matches score:
   - Score ≥80 → green
   - Score 50-79 → amber
   - Score <50 → red
5. Inject vibration spike → wait for score to update

**Expected Results:**
- ✅ Safety gauge renders with correct score (0-100)
- ✅ Color matches score range
- ✅ Score updates within 5 minutes (cache TTL)

---

### TC-S8-043: Mobile Push Notification → Deep-link

| Field | Value |
|-------|-------|
| **Story** | S8-M02 |
| **Priority** | P0 |
| **Platform** | iOS + Android |

**Steps:**
1. App is in background (not killed)
2. Inject structural alert via API
3. Wait for push notification (<15s)
4. Tap notification
5. Verify app opens to alert detail screen

**Expected Results:**
- ✅ Push notification received within 15 seconds
- ✅ Notification shows alert summary text
- ✅ Tapping notification opens alert detail
- ✅ Alert detail shows full context (type, severity, building, time)

---

### TC-S8-044: Mobile Push — Cold Start

| Field | Value |
|-------|-------|
| **Story** | S8-M02 |
| **Priority** | P1 |
| **Platform** | iOS + Android |

**Steps:**
1. Kill app completely (swipe away)
2. Inject structural alert via API
3. Wait for push notification
4. Tap notification
5. Verify app cold-starts → login screen → auto-login → alert detail

**Expected Results:**
- ✅ App launches from cold start
- ✅ PKCE auto-login (token from SecureStore)
- ✅ Navigates to alert detail after auth

---

### TC-S8-045: Mobile Push — Foreground

| Field | Value |
|-------|-------|
| **Story** | S8-M02 |
| **Priority** | P1 |
| **Platform** | iOS + Android |

**Steps:**
1. App is in foreground (Dashboard screen)
2. Inject structural alert via API
3. Wait for notification
4. Verify behavior

**Expected Results:**
- ✅ In-app banner appears (not system notification)
- ✅ Banner shows alert summary
- ✅ Tapping banner navigates to alert detail

---

## 6. Flink CI/CD (S8-OPS03)

### TC-S8-050: Flink Job Build + Submit

| Field | Value |
|-------|-------|
| **Story** | S8-OPS03 |
| **Priority** | P0 |
| **Precondition** | Flink jobmanager running |

**Steps:**
1. Run `make flink-build`
2. Verify JAR exists: `ls flink-jobs/build/libs/flink-jobs-*.jar`
3. Check JAR name contains git hash
4. Run `make flink-list` → verify current running jobs
5. Run `make flink-submit`
6. Run `make flink-list` → verify new job appears

**Expected Results:**
- ✅ JAR built successfully with git hash in filename
- ✅ `make flink-submit` completes without error
- ✅ New job appears in Flink dashboard as RUNNING

---

### TC-S8-051: Flink Job Re-deploy with Savepoint

| Field | Value |
|-------|-------|
| **Story** | S8-OPS03 |
| **Priority** | P0 |
| **Precondition** | Job already running with state |

**Steps:**
1. Make a code change to Flink job
2. Run `make flink-build`
3. Run `make flink-deploy` (savepoint → cancel → submit)
4. Verify savepoint created in logs
5. Verify old job cancelled
6. Verify new job submitted and restores from savepoint
7. Inject sensor data → verify processing continues from correct offset

**Expected Results:**
- ✅ Savepoint created before cancel
- ✅ Old job state = CANCELED
- ✅ New job state = RUNNING
- ✅ New job restores from savepoint (check "Restore From" in Flink UI)
- ✅ No data gap after re-deploy

---

## 7. Keycloak Pilot Realm (S8-OPS05)

### TC-S8-060: Pilot Users Login

| Field | Value |
|-------|-------|
| **Story** | S8-OPS05 |
| **Priority** | P1 |

**Steps:**
1. Login as pilot-admin via Keycloak
2. Verify full access to all modules
3. Logout → login as pilot-operator
4. Verify access to: Buildings, Alerts, Safety, Dashboard
5. Verify NO access to: Admin panel, User management
6. Logout → login as pilot-viewer
7. Verify read-only access (no create/update/delete buttons)

**Expected Results:**
- ✅ pilot-admin: full access all modules
- ✅ pilot-operator: building + alerts + safety, no admin
- ✅ pilot-viewer: read-only, no write actions

---

## 8. Smoke Test — Post-Deploy

### TC-S8-070: Full Stack Smoke Test

| Field | Value |
|-------|-------|
| **Priority** | P0 |
| **Precondition** | Fresh deploy with HA config |

**Steps:**
1. `curl http://localhost:8080/actuator/health` → UP
2. `curl http://localhost:8082/actuator/health` → UP (analytics-service)
3. `curl http://localhost:8087/apis/registry/v2/health` → UP (Apicurio)
4. Login: `POST /api/v1/auth/login` → token received
5. Dashboard: `GET /api/v1/dashboard` → data returned
6. Buildings: `GET /api/v1/buildings` → list returned
7. Alerts: `GET /api/v1/alerts` → list returned
8. ESG report: `POST /api/v1/esg/reports/pdf` → PDF generated
9. Safety: `GET /api/v1/buildings/BLDG-001/safety` → score returned
10. Flink: `curl http://localhost:8081/jobs/overview` → jobs RUNNING

**Expected Results:**
- ✅ All 10 endpoints return 200
- ✅ Flink jobs RUNNING (VibrationAnomalyJob + EsgDualSinkJob)

---

## Test Execution Summary Template

| TC ID | Title | Priority | Platform | Result | Notes |
|-------|-------|----------|----------|--------|-------|
| TC-S8-001 | VibrationAnomalyJob consumes | P0 | Staging | ⬜ | |
| TC-S8-002 | Flink checkpoint recovery | P0 | Staging | ⬜ | |
| TC-S8-010 | CH node failover | P0 | Staging | ⬜ | |
| TC-S8-011 | CH data migration integrity | P0 | Staging | ⬜ | |
| TC-S8-012 | CH backward compat | P0 | Dev | ⬜ | |
| TC-S8-020 | Kafka broker failover | P0 | Staging | ⬜ | |
| TC-S8-021 | Kafka topic replication | P0 | Staging | ⬜ | |
| TC-S8-030 | Mobile KPI cards display | P0 | iOS+Android | ⬜ | |
| TC-S8-031 | Mobile card navigation | P0 | iOS+Android | ⬜ | |
| TC-S8-032 | Mobile responsive layout | P0 | iOS+Android | ⬜ | |
| TC-S8-033 | Mobile token expiry | P0 | iOS+Android | ⬜ | |
| TC-S8-040 | Alert list sort by severity | P0 | iOS+Android | ⬜ | |
| TC-S8-041 | Alert severity filter | P0 | iOS+Android | ⬜ | |
| TC-S8-042 | Safety score gauge | P0 | iOS+Android | ⬜ | |
| TC-S8-043 | Push deep-link | P0 | iOS+Android | ⬜ | |
| TC-S8-044 | Push cold start | P1 | iOS+Android | ⬜ | |
| TC-S8-045 | Push foreground | P1 | iOS+Android | ⬜ | |
| TC-S8-050 | Flink build + submit | P0 | CI | ⬜ | |
| TC-S8-051 | Flink redeploy with savepoint | P0 | CI | ⬜ | |
| TC-S8-060 | Pilot users login | P1 | Staging | ⬜ | |
| TC-S8-070 | Full stack smoke test | P0 | Staging | ⬜ | |
| **Total** | **21 manual TCs** | | | | |

---

*Document: Sprint 8 Manual Test Cases v1.0 | Tester | 2026-06-03*
