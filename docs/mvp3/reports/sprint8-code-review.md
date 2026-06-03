# SA Code Review — Sprint 8

**Reviewer:** SA Agent  
**Date:** 2026-06-04  
**Sprint:** MVP3-8 — Pilot Prep + Mobile + Infrastructure HA  
**Commits reviewed:** a6e16383, 76005b08, 810aa591, ea8a8cc4, 33196902, d3e7f0e3, 29c755a7, d426c078, fc178b36

---

## Overall Status: APPROVED (post-fix)

Initial review: **BLOCKED** — 2 CRITICAL, 3 MAJOR, 4 MINOR, 3 INFO  
After fixes applied 2026-06-04: **APPROVED WITH NOTES**

---

## CRITICAL Findings (FIXED)

### C-1 — ClickHouse Keeper won't boot [FIXED ✅]

**File:** `infrastructure/clickhouse/keeper-config.xml:13-16`  
**Severity:** CRITICAL  
**Issue:** Invalid `<tip><enable>true</enable></tip>` block + missing mandatory `<server_id>`. Keeper requires `<server_id>` matching an `<id>` in `<raft_configuration>`. Without it Keeper fails to start, and `clickhouse-01/02` (which `depends_on` keeper healthy) never come up — the whole S8-OPS01 HA stack is dead.  
**Fix applied:** Replaced `<tip>` block with `<server_id>1</server_id>`.

### C-2 — Mobile dashboard API contract mismatch [FIXED ✅]

**File:** `applications/operator-mobile/src/hooks/useDashboard.ts:46` + `backend/src/main/java/com/uip/backend/dashboard/api/DashboardController.java`  
**Severity:** CRITICAL  
**Issue:** `useDashboard.ts` calls `GET /api/v1/dashboard` expecting `DashboardData` (energyKwh, safetyScore, aqi, onlineSensors…). Backend only exposed `GET /api/v1/dashboard/stats` returning flat `Map{activeSensors, openAlerts, totalBuildings, generatedAt}`. Wrong path AND wrong shape → every query 404s, all 4 KPI cards stuck on "—".  
**Fix applied:** Added `GET /api/v1/dashboard` aggregation endpoint to `DashboardController` querying: `esg.clean_metrics` (energy 24h), `environment.sensor_readings` (AQI 1h avg), `alerts.alert_events` (safety score + active alerts), `environment.sensors` (online/total sensors). Existing `/stats` endpoint preserved.

---

## MAJOR Findings (FIXED)

### M-1 — Safety status enum drift: `SAFE` vs `GOOD` [FIXED ✅]

**Files:** `useDashboard.ts:9,19`, `AlertsScreen.tsx:85`  
**Issue:** Backend `SafetyScoreResponse` emits `SAFE|WARNING|CRITICAL|OFFLINE`; mobile types + `AlertsScreen.tsx:85` checked `=== 'GOOD'`. A healthy building fell through to else and displayed "Offline" in the pilot UI.  
**Fix applied:** Changed `'GOOD'` → `'SAFE'` in `BuildingSafetyScore`, `DashboardData` interfaces, and `AlertsScreen.tsx:85` ternary check.

### M-2 — Kafka rebalance plan parsing fragile [FIXED ✅]

**File:** `infrastructure/scripts/kafka-rebalance.sh:84`  
**Issue:** `tail -2 | head -1` to extract reassignment JSON — CLI output shift yields empty plan; `-s` guard skips the topic silently, leaving replication.factor=1 while reporting success.  
**Fix applied:** Replaced with `grep -E '^\{"version"' | tail -1` — explicitly matches the JSON line regardless of CLI output layout.

### M-3 — PG replication uses superuser [FIXED ✅]

**File:** `infrastructure/timescaledb/standby-entrypoint.sh:38-46`  
**Issue:** `pg_basebackup -U ${PGUSER}` defaulted to superuser `uip`; no dedicated `REPLICATION` role. Least-privilege violation.  
**Fix applied:**
- Added `create-replication-role.sh` init script for primary (creates `replicator` role with `REPLICATION LOGIN`)
- Updated `standby-entrypoint.sh` to use `REPLICATION_USER` / `REPLICATION_PASSWORD` env vars
- Updated `docker-compose.ha.yml` to pass `REPLICATION_USER` + `REPLICATION_PASSWORD` to both primary and standby; mount init script on primary

---

## MINOR Findings (Deferred to Sprint 9)

| ID | File | Issue | Action |
|----|------|-------|--------|
| m-1 | `useDashboard.ts:62-76` | AQI color red ≤200 vs label 'Kém' ≤200 — confirm with VN scale (EPA or QCVN) | Clarify with UI Designer in Sprint 9 |
| m-2 | `infrastructure/scripts/ch-migrate.sh` | Header comment implies historical data copy but only verifies cluster | Update comment to clarify Kafka-repopulation assumption |
| m-3 | `Makefile` | Dead `--help` probe line in `kafka-rebalance` target | Remove in Sprint 9 cleanup |
| m-4 | `keycloak/pilot-realm.json` | `uip-mobile` redirect `http://localhost:8081/*` is DEV/pilot-only | Document as pilot-only; PKCE S256 + 4 protocol mappers correct |

---

## INFO

| ID | Note |
|----|------|
| I-1 | Plaintext pilot passwords in commit `d426c078` message — DEV-only, **rotate before external pilot** |
| I-2 | Flink CI/CD `flink-deploy.sh` savepoint→cancel→submit + idempotent skip = correct (ADR-038) ✅ |
| I-3 | Avro `register-avro-schemas.sh` genuinely idempotent (409/Conflict treated as success) ✅ |

---

## Anti-pattern Checklist

| Check | Result |
|-------|--------|
| Cross-module direct dependency | PASS — DashboardController uses JdbcTemplate (satisfies ModuleBoundaryArchTest) |
| Business logic in Flink job | PASS — Welford+CEP only, BR-010 preserved |
| SELECT * in queries | PASS — explicit columns throughout |
| ClickHouse compression | PASS — ZSTD+DoubleDelta on `esg_readings` |
| PII in logs | PASS — no PII logged |
| JWT claims | PASS — iss, sub, tenant_id all present in Keycloak mappers |
| AGPL dependency | PASS — no AGPL licenses introduced |

---

## Verdict per Story

| Story | Verdict |
|-------|---------|
| S8-C01 VibrationAnomalyJob fix | APPROVED ✅ |
| S8-M01 Mobile Dashboard | APPROVED ✅ (C-2 fixed) |
| S8-M02 Mobile Alerts + Safety | APPROVED ✅ (M-1 fixed) |
| S8-OPS01 ClickHouse HA | APPROVED ✅ (C-1 fixed) |
| S8-OPS02 Kafka 3-broker | APPROVED ✅ (M-2 fixed) |
| S8-OPS03 Flink CI/CD | APPROVED ✅ |
| S8-OPS04 PG Replication | APPROVED ✅ (M-3 fixed) |
| S8-OPS05 Keycloak pilot realm | APPROVED WITH NOTES (m-4, I-1) |
| S8-OPS06 Avro auto-register | APPROVED ✅ |

---

## G12 Gate: **PASS** ✅

All CRITICAL and MAJOR findings resolved. 4 MINOR items deferred to Sprint 9 cleanup.  
**DevOps may proceed with build + smoke test.**

---

*SA Code Review — Sprint 8 | 2026-06-04*
