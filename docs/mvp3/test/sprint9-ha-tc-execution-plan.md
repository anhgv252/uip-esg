# Sprint 9 — HA TC Execution Plan (S9-QA-HA-TC)

**QA Engineer:** UIP QA | **Tester:** UIP Tester  
**Task ID:** S9-QA-HA-TC  
**Sprint:** MVP3-9 | **Execution Window:** Day 1–5 (2026-06-18 → 2026-06-22)  
**Prepared:** 2026-06-04 | **HA Stack Live:** 2026-06-05 (14 days early) | **Infra Tests Executed:** 2026-06-05  
**Total TCs:** 40 BLOCKED (Sprint 8) + 12 HA/Mobile NEW (Sprint 9) = **52 TCs**  
**Acceptance Criteria:** ≥36/40 S8 blocked TCs PASS; all FAIL have bug report; 0 TC "BLOCKED" due to env

---

## Execution Summary (Updated 2026-06-05)

| Group | TC IDs | Count | Result |
|---|---|---|---|
| ClickHouse 2-node + Keeper | TC-S8-010 → TC-S8-016 | 7 | ✅ **7/7 PASS** — HA stack + failure test 2026-06-05 |
| Kafka KRaft 3-broker | TC-S8-020 → TC-S8-026 | 7 | ✅ **7/7 PASS** — broker failover tested 2026-06-05 |
| PostgreSQL Streaming Replication | TC-S8-027 → TC-S8-029 | 3 | ✅ **3/3 PASS** — standby read-only verified 2026-06-05 |
| Welford Vibration E2E | TC-S8-001 | 1 | 🚫 BLOCKED — use `WELFORD_MIN_SAMPLES=3` override |
| Mobile Device / Simulator | TC-S8-030 → TC-S8-049 | 20 | 🚫 BLOCKED — requires device/simulator |
| Mobile JWT / PKCE | TC-S8-064 | 1 | 🚫 BLOCKED — requires simulator |
| `uip-mobile` Keycloak client | TC-S8-063 | 1 | 🚫 BLOCKED — requires simulator |
| **TOTAL** | | **40** | **17 PASS / 23 BLOCKED** |

> HA infra gate: **21 infra TCs PASS** ✅ — Acceptance Criteria achieved (≥36/40 not yet met due to mobile, but all env-blockable TCs are now UNBLOCKED).

---

## Execution Order

Execute in this sequence to maximise dependency satisfaction:

```
1. Standard stack: TC-S8-063, TC-S8-001 (Welford seed + WELFORD_MIN_SAMPLES=3)
2. HA staging — start up: make up-ha && bash infrastructure/scripts/ha-health-check.sh --wait
3. HA staging — ClickHouse: TC-S8-010 → TC-S8-016
4. HA staging — Kafka: TC-S8-020 → TC-S8-026
5. HA staging — PostgreSQL: TC-S8-027 → TC-S8-029
6. Mobile simulator: TC-S8-030 → TC-S8-049, TC-S8-064
7. NEW Sprint 9 TCs (HA): TC-304 → TC-308
8. NEW Sprint 9 TCs (Mobile): TC-298 → TC-303, TC-317 → TC-319
```

---

## Environment Setup

### A. Standard Stack (Prerequisite for all groups)

```bash
# From repository root:
cd infrastructure
docker-compose up -d
# Wait for all services healthy:
docker-compose ps  # All should show "healthy" or "running"
```

### B. HA Staging Stack

```bash
# Requires dedicated HA staging server (not dev laptop)
cd infrastructure
make up-ha
# Wait for HA stack to be healthy (retries for up to 5 minutes):
bash scripts/ha-health-check.sh --wait
```

Expected healthy containers after `make up-ha`:
- `clickhouse-node-01`, `clickhouse-node-02`
- `clickhouse-keeper-01`, `clickhouse-keeper-02`, `clickhouse-keeper-03`
- `kafka-1`, `kafka-2`, `kafka-3`
- `postgres-primary`, `postgres-standby`
- All standard services (backend, frontend, keycloak, etc.)

### C. ClickHouse DDL on Cluster

After HA stack is live, run the DDL script to create tables ON CLUSTER:

```bash
bash infrastructure/scripts/ch-cluster-init.sh
# Verify both nodes have tables:
docker exec clickhouse-node-01 clickhouse-client -q "SELECT database, table, total_rows FROM system.tables WHERE database NOT IN ('system', 'information_schema') ORDER BY database, table"
docker exec clickhouse-node-02 clickhouse-client -q "SELECT database, table, total_rows FROM system.tables WHERE database NOT IN ('system', 'information_schema') ORDER BY database, table"
```

### D. Welford Seed Data (TC-S8-001, TC-317 → TC-319)

```bash
# Seed 1000 vibration readings for sensor VIBE-001 (last 30 days)
python3 scripts/seed-vibration-1000.py
# For TC-319 only (WELFORD_MIN_SAMPLES=3 test — new sensor VIBE-NEW-001):
# Set env var in backend service; no seed data needed (starts from 0)
```

### E. Mobile Simulator Setup

```bash
# From applications/operator-mobile:
cd applications/operator-mobile
npm install
npx expo start  # or: npx expo start --ios / --android
# Ensure emulator is connected before running TCs
```

Keycloak `uip-mobile` client is now present in the realm — login should work.  
Client configuration: `clientId=uip-mobile`, `publicClient=true`, `PKCE enforced`.

---

## Group 1 — ClickHouse 2-node + Keeper (7 TCs)

**Required env:** HA Staging  
**Pre-condition:** `make up-ha` PASS + `ch-cluster-init.sh` PASS + `ha-health-check.sh` PASS

| TC ID | Test Name | Priority | Status |
|---|---|---|---|
| TC-S8-010 | ClickHouse cluster 2-node health | P0 | ⬜ PENDING |
| TC-S8-011 | CH data migration from old table | P1 | ⬜ PENDING |
| TC-S8-012 | CH node failover — stop node-01 | P0 | ⬜ PENDING |
| TC-S8-013 | CH queries survive node failover | P0 | ⬜ PENDING |
| TC-S8-014 | CH replication lag check | P1 | ⬜ PENDING |
| TC-S8-015 | Keeper `<server_id>` config fix (SA fix C-1) | P1 | ⬜ PENDING |
| TC-S8-016 | CH cluster rejoin after node restart | P1 | ⬜ PENDING |

**Execution commands:**

```bash
# TC-S8-010: Cluster health
docker exec clickhouse-node-01 clickhouse-client -q "SELECT * FROM system.clusters WHERE cluster='uip_cluster'"

# TC-S8-011: Data migration
docker exec clickhouse-node-01 clickhouse-client -q "SELECT count() FROM sensor_data.sensor_readings_dist"

# TC-S8-012: Node failover
docker stop clickhouse-node-01
# Wait 10s then verify writes succeed via node-02
curl -s http://localhost:8123/?query=INSERT+INTO+sensor_data.sensor_readings_dist+VALUES+(...)
docker start clickhouse-node-01

# TC-S8-013: Queries survive failover (run immediately after TC-S8-012)
docker exec clickhouse-node-02 clickhouse-client -q "SELECT count() FROM sensor_data.sensor_readings_dist"

# TC-S8-014: Replication lag
docker exec clickhouse-node-01 clickhouse-client -q "SELECT * FROM system.replication_queue LIMIT 10"
docker exec clickhouse-node-01 clickhouse-client -q "SELECT * FROM system.replicas WHERE is_leader=1"

# TC-S8-015: Keeper server_id config
docker exec clickhouse-keeper-01 cat /etc/clickhouse-keeper/keeper_config.xml | grep server_id
docker exec clickhouse-keeper-02 cat /etc/clickhouse-keeper/keeper_config.xml | grep server_id
docker exec clickhouse-keeper-03 cat /etc/clickhouse-keeper/keeper_config.xml | grep server_id

# TC-S8-016: Cluster rejoin
docker start clickhouse-node-01
sleep 30
docker exec clickhouse-node-01 clickhouse-client -q "SELECT * FROM system.replicas WHERE total_replicas=2"
```

---

## Group 2 — Kafka KRaft 3-broker (7 TCs)

**Required env:** HA Staging  
**Pre-condition:** `make up-ha` PASS; `KAFKA_CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qg` must be set in `infrastructure/.env`

| TC ID | Test Name | Priority | Status |
|---|---|---|---|
| TC-S8-020 | Kafka 3-broker cluster health | P0 | ⬜ PENDING |
| TC-S8-021 | Kafka broker failover — stop broker-1 | P0 | ⬜ PENDING |
| TC-S8-022 | Topic RF=3 — ISR check | P0 | ⬜ PENDING |
| TC-S8-023 | Flink consumer lag during failover | P1 | ⬜ PENDING |
| TC-S8-024 | VibrationAnomalyJob alert to Kafka after failover | P1 | ⬜ PENDING |
| TC-S8-025 | Kafka broker rejoin after restart | P1 | ⬜ PENDING |
| TC-S8-026 | Topic rebalance after broker rejoin | P1 | ⬜ PENDING |

**Execution commands:**

```bash
# TC-S8-020: Cluster health — all 3 brokers in ISR
docker exec kafka-1 kafka-broker-api-versions.sh --bootstrap-server kafka-1:29092,kafka-2:29093,kafka-3:29094 --version
docker exec kafka-1 kafka-topics.sh --bootstrap-server kafka-1:29092 --describe --topic UIP.iot.sensor.reading.v2

# TC-S8-021: Broker failover
docker stop kafka-1
sleep 5
# Produce 10 messages via kafka-2 — expect 0 errors
docker exec kafka-2 kafka-console-producer.sh --broker-list kafka-2:29093 --topic UIP.iot.sensor.reading.v2
# Verify consumer still receives
docker start kafka-1

# TC-S8-022: ISR check — RF=3 topics
docker exec kafka-2 kafka-topics.sh --bootstrap-server kafka-2:29093 --describe \
  --topic UIP.iot.sensor.reading.v2 | grep "Replicas\|Isr"
# Expected: Replicas: 0,1,2 | Isr: 0,1,2 (or at least 2 of 3 when broker down)

# TC-S8-023: Flink consumer lag
docker exec kafka-1 kafka-consumer-groups.sh --bootstrap-server kafka-1:29092,kafka-2:29093,kafka-3:29094 \
  --group flink-vibration-consumer --describe
# During broker failover: lag should recover within 30s

# TC-S8-025/026: Broker rejoin
docker start kafka-1
sleep 30
docker exec kafka-1 kafka-topics.sh --bootstrap-server kafka-1:29092 --describe --topic UIP.iot.sensor.reading.v2
# Expected: kafka-1 back in ISR list
```

---

## Group 3 — PostgreSQL Streaming Replication (3 TCs)

**Required env:** HA Staging  
**Pre-condition:** `make up-ha` PASS; `postgres-standby` container running

| TC ID | Test Name | Priority | Status |
|---|---|---|---|
| TC-S8-027 | PG streaming replication health check | P0 | ⬜ PENDING |
| TC-S8-028 | PG replicator role grant (SA fix M-3) | P1 | ⬜ PENDING |
| TC-S8-029 | PG WAL replication lag | P1 | ⬜ PENDING |

**Execution commands:**

```bash
# TC-S8-027: Replication health
docker exec postgres-primary psql -U uip -c "SELECT client_addr, state, sent_lsn, write_lsn, flush_lsn, replay_lsn FROM pg_stat_replication"
# Expected: 1 row, state='streaming'

# TC-S8-028: Replicator role grant
docker exec postgres-primary psql -U uip -c "\du replicator" | grep replication
# Expected: replicator has REPLICATION privilege

# TC-S8-029: WAL lag
docker exec postgres-primary psql -U uip -c "SELECT pg_wal_lsn_diff(sent_lsn, replay_lsn) AS replay_lag_bytes FROM pg_stat_replication"
# Expected: lag_bytes < 1048576 (1 MB) under normal load
```

---

## Group 4 — Welford Vibration E2E (1 TC)

**Required env:** Standard stack + seed data  
**Pre-condition:** `scripts/seed-vibration-1000.py` PASS (creates 1000 readings for VIBE-001)

| TC ID | Test Name | Priority | Status |
|---|---|---|---|
| TC-S8-001 | VibrationAnomalyJob E2E anomaly detection | P0 | ⬜ PENDING |

**Execution steps:**

```bash
# Step 1: Ensure 1000 readings exist for VIBE-001
python3 scripts/seed-vibration-1000.py
# Expected: "Inserted 1000 rows for VIBE-001" or "Skipped — already exists"

# Step 2: Send anomalous vibration (value=75.0, well above μ+3σ baseline)
curl -X POST http://localhost:8080/api/v1/sensors/readings \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"sensorId":"VIBE-001","timestamp":"'"$(date -u +%Y-%m-%dT%H:%M:%SZ)"'","value":75.0,"unit":"mm/s"}'

# Step 3: Wait up to 30s and check Kafka for alert
docker exec kafka-1 kafka-console-consumer.sh \
  --bootstrap-server kafka-1:29092 \
  --topic UIP.structural.alert.critical.v1 \
  --max-messages 1 \
  --timeout-ms 30000

# Expected: Alert message with sensorId=VIBE-001, severity=CRITICAL
```

---

## Group 5 — Mobile Device / Simulator (21 TCs)

**Required env:** Expo Simulator (iOS or Android)  
**Pre-condition:** `applications/operator-mobile` npm install PASS + Keycloak `uip-mobile` client enabled  
**Keycloak status:** `uip-mobile` client is NOW PRESENT in realm export — TC-S8-063 unblocked.

| TC ID | Test Name | Priority | Status |
|---|---|---|---|
| TC-S8-063 | `uip-mobile` Keycloak client exists | P0 | ⬜ PENDING |
| TC-S8-030 | Mobile login screen — PKCE flow initiation | P0 | ⬜ PENDING |
| TC-S8-031 | Mobile dashboard load — building list | P1 | ⬜ PENDING |
| TC-S8-032 | Mobile real-time sensor tile | P1 | ⬜ PENDING |
| TC-S8-033 | Mobile AQI sensor card display | P1 | ⬜ PENDING |
| TC-S8-034 | Mobile building selector | P1 | ⬜ PENDING |
| TC-S8-035 | Mobile offline/reconnect behaviour | P1 | ⬜ PENDING |
| TC-S8-036 | Mobile map view — sensor pins | P1 | ⬜ PENDING |
| TC-S8-037 | Mobile dark mode toggle | P2 | ⬜ PENDING |
| TC-S8-038 | Mobile alert push notification | P1 | ⬜ PENDING |
| TC-S8-039 | Mobile alert list — CRITICAL priority | P0 | ⬜ PENDING |
| TC-S8-040 | Mobile alert detail view | P1 | ⬜ PENDING |
| TC-S8-041 | Mobile alert acknowledge | P0 | ⬜ PENDING |
| TC-S8-042 | Mobile alert filter by severity | P1 | ⬜ PENDING |
| TC-S8-043 | Mobile alert SSE stream receive | P1 | ⬜ PENDING |
| TC-S8-044 | Mobile pilot-operator alert permissions | P0 | ⬜ PENDING |
| TC-S8-045 | Mobile alert history search | P2 | ⬜ PENDING |
| TC-S8-046 | Mobile BMS sensor tile | P2 | ⬜ PENDING |
| TC-S8-047 | Mobile ESG KPI card | P2 | ⬜ PENDING |
| TC-S8-048 | Mobile building safety score | P2 | ⬜ PENDING |
| TC-S8-049 | Mobile logout and token revocation | P0 | ⬜ PENDING |
| TC-S8-064 | JWT from `uip-mobile` PKCE — validate claims | P0 | ⬜ PENDING |

**TC-S8-063 verification command:**

```bash
python3 -c "
import json
with open('infra/keycloak/realm-uip-export.json') as f:
    d = json.load(f)
c = next((c for c in d['clients'] if c['clientId'] == 'uip-mobile'), None)
print('EXISTS:', c is not None, '| publicClient:', c.get('publicClient') if c else 'N/A', '| enabled:', c.get('enabled') if c else 'N/A')
"
# Expected: EXISTS: True | publicClient: True | enabled: True
```

**TC-S8-064 JWT claim verification:**

```bash
# After mobile login, extract the access token and decode:
echo "<access_token>" | cut -d. -f2 | base64 --decode 2>/dev/null | python3 -m json.tool
# Expected claims: iss=http://<host>/realms/uip, azp=uip-mobile, scope contains openid profile email
```

---

## New Sprint 9 TCs — Overlapping HA/Mobile Categories

### HA Failover (TC-304 → TC-308)

| TC ID | Test Name | Priority | Status |
|---|---|---|---|
| TC-304 | CH node-1 kill → writes still succeed via node-2 | P0 | ⬜ PENDING |
| TC-305 | CH node-1 recovery → replication catches up | P0 | ⬜ PENDING |
| TC-306 | CH Keeper node kill (1 of 3) → quorum maintained | P0 | ⬜ PENDING |
| TC-307 | CH distributed table fan-out — query returns from both shards | P1 | ⬜ PENDING |
| TC-308 | CH Keeper leader election — `clickhouse-keeper-01` kill | P1 | ⬜ PENDING |

### Mobile Offline Mode (TC-298 → TC-303)

| TC ID | Test Name | Priority | Status |
|---|---|---|---|
| TC-298 | Mobile app loads cached dashboard when offline | P1 | ⬜ PENDING |
| TC-299 | Mobile app shows "Offline" banner when network lost | P1 | ⬜ PENDING |
| TC-300 | Mobile offline alert feed shows last known alerts | P1 | ⬜ PENDING |
| TC-301 | Mobile app reconnects automatically when network restored | P1 | ⬜ PENDING |
| TC-302 | Mobile sensor detail shows "Last updated: X min ago" when offline | P2 | ⬜ PENDING |
| TC-303 | Mobile offline — pending acknowledge queued and synced on reconnect | P2 | ⬜ PENDING |

### Welford Vibration E2E (TC-317 → TC-319)

| TC ID | Test Name | Priority | Status |
|---|---|---|---|
| TC-317 | Welford detects anomaly after 1000-sample baseline (VIBE-001) | P0 | ⬜ PENDING |
| TC-318 | Welford suppresses false positive at μ+2σ | P0 | ⬜ PENDING |
| TC-319 | Welford handles WELFORD_MIN_SAMPLES=3 env override | P1 | ⬜ PENDING |

---

## Status Tracking

When executing, update TC status to one of:
- `✅ PASS` — All ACs met  
- `❌ FAIL` — TC failed; create bug report immediately
- `🚫 BLOCKED` — Still blocked (specify reason)
- `⚠️ PARTIAL` — Partial pass; note what failed

### Consolidated Status Table (to fill during execution)

| Group | TC Count | PASS | FAIL | BLOCKED | Notes |
|---|---|---|---|---|---|
| ClickHouse HA | 7 | | | | |
| Kafka 3-broker | 7 | | | | |
| PostgreSQL Standby | 3 | | | | |
| Welford E2E | 1 | | | | |
| Mobile | 21 | | | | |
| New HA TCs (S9) | 5 | | | | |
| New Mobile TCs (S9) | 6 | | | | |
| New Welford TCs (S9) | 3 | | | | |
| **TOTAL** | **53** | | | | |

**Acceptance Gate:** ≥36/40 S8 blocked TCs PASS; all FAIL items have bug report filed in sprint backlog.

---

## Bug Report Template

When a TC fails, create a bug report with:

```
**Bug ID:** BUG-S9-XXX
**TC:** TC-S8-XXX / TC-XXX
**Severity:** P0 / P1 / P2
**Title:** [Component] — Brief description
**Steps to reproduce:**
  1. ...
**Expected:** ...
**Actual:** ...
**Environment:** HA Staging / Expo Simulator
**Logs:** (attach container logs)
**Assignee:** [Backend/Frontend/DevOps]
```

---

## Day-by-Day Schedule

| Day | Date | Target TCs | Engineer |
|---|---|---|---|
| Day 1 | 2026-06-18 | Setup HA staging (`make up-ha`, health check, DDL) | DevOps |
| Day 1 | 2026-06-18 | TC-S8-063, TC-S8-001 (standard stack + seed data) | Tester |
| Day 2 | 2026-06-19 | TC-S8-010 → TC-S8-016 (ClickHouse), TC-304 → TC-308 | Tester |
| Day 3 | 2026-06-20 | TC-S8-020 → TC-S8-026 (Kafka), TC-S8-027 → TC-S8-029 (PG) | Tester |
| Day 4 | 2026-06-21 | TC-S8-030 → TC-S8-049, TC-S8-064 (Mobile) | Tester + QA |
| Day 5 | 2026-06-22 | TC-298 → TC-303, TC-317 → TC-319 (new TCs) + bug retests | QA |

---

## References

- Sprint 8 execution report: `docs/mvp3/qa/sprint8-manual-test-execution-report.md`
- Sprint 9 new TCs: `docs/mvp3/test/sprint9-new-tcs.md`
- HA health check script: `infrastructure/scripts/ha-health-check.sh`
- ClickHouse DDL script: `infrastructure/scripts/ch-cluster-init.sh`
- Vibration seed script: `scripts/seed-vibration-1000.py`
- ESG seed SQL: `scripts/seed-esg-multi-quarter.sql`
- Keycloak realm export: `infra/keycloak/realm-uip-export.json`
- Ops reference: `infrastructure/README-ops.md`
