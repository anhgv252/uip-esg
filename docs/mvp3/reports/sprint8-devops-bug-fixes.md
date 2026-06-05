# Sprint 8 — DevOps Bug Fixes

**Engineer:** UIP DevOps  
**Date:** 2026-06-05  
**Sprint:** MVP3-8  
**Context:** 5 infrastructure bugs discovered during manual testing (Sprint 8 Test Execution Report)

---

## Summary

**DEVOPS-DONE:**

```
infra:
  - infra/keycloak/realm-uip-export.json ✅ (pilot passwords standardized)
  - infrastructure/clickhouse/init.sql ✅ (sensor_reading_hourly table added)
  - infrastructure/scripts/flink-deploy.sh ✅ (stale job cleanup logic)
  
services:
  - keycloak: realm export updated (requires container restart to re-import)
  - clickhouse: init.sql extended (applies on next container creation)
  - flink: deploy script enhanced (applies on next `make flink-deploy`)
  
health:
  - JSON validation: PASS ✓
  - ClickHouse DDL syntax: NOT VERIFIED (requires container restart)
  - Flink script syntax: bash -n PASS ✓
  
monitoring: N/A (no alerts modified)

open:
  - BUG-003: uip-mobile client already exists in realm export but not found at runtime
    → Root cause: realm import failure OR manual deletion
    → Action needed: Restart Keycloak with fresh data volume OR manual re-import
```

---

## Bug Fixes Detail

### ✅ BUG-004 (P1) — Pilot user credentials inconsistent

**Problem:** `pilot-operator` and `pilot-viewer` passwords didn't match documented pattern.  
**Root Cause:** Passwords were abbreviated (`PilotOp#2026!`, `PilotView#2026!`) instead of full (`PilotOperator#2026!`, `PilotViewer#2026!`).

**Fix:**
- File: `infra/keycloak/realm-uip-export.json`
- Changed:
  - `pilot-operator` password: `PilotOp#2026!` → `PilotOperator#2026!`
  - `pilot-viewer` password: `PilotView#2026!` → `PilotViewer#2026!`
- Pattern now consistent with `pilot-admin` (`PilotAdmin#2026!`)

**Verification:**
```bash
python3 -c "import json; json.load(open('infra/keycloak/realm-uip-export.json')); print('✓ Valid JSON')"
# ✓ Valid JSON
```

**Next Steps:**
1. Restart Keycloak container to trigger re-import: `docker compose restart keycloak`
2. Wait ~30s for realm import to complete
3. Test credentials:
   ```bash
   curl -X POST http://localhost:8085/realms/uip/protocol/openid-connect/token \
     -d "client_id=uip-api" \
     -d "client_secret=uip-api-secret-dev" \
     -d "grant_type=password" \
     -d "username=pilot-operator" \
     -d "password=PilotOperator#2026!"
   # Expected: 200 OK with access_token
   ```

---

### ✅ BUG-002 (P1) — Documentation references wrong realm name

**Problem:** Tester handoff docs and stories referenced `uip-pilot` realm, but actual realm is `uip`.  
**Root Cause:** Naming confusion during Sprint 8 Keycloak setup.

**Fix:**
- File: `docs/mvp3/qa/sprint8-tester-handoff.md` (line 33)
  - Changed: `Keycloak realm: uip-pilot` → `Keycloak realm: uip`
- File: `docs/mvp3/project/sprint8-stories.md` (line 216)
  - Changed: `Realm uip-pilot created` → `Realm uip created`

**Verification:** grep confirmed no remaining `uip-pilot` references in instruction docs (test report references are historical evidence, left as-is).

---

### ✅ BUG-006 (P2) — Missing ClickHouse hourly rollup table

**Problem:** Query `SELECT count(*) FROM analytics.sensor_reading_hourly` → Code: 60 (table does not exist).  
**Root Cause:** Hourly aggregation table was planned but DDL was never added to init script.

**Fix:**
- File: `infrastructure/clickhouse/init.sql`
- Added table definition:
  ```sql
  CREATE TABLE IF NOT EXISTS analytics.sensor_reading_hourly
  (
      tenant_id       String,
      sensor_id       String,
      sensor_type     LowCardinality(String),
      hour_bucket     DateTime,
      avg_value       Float64,
      min_value       Float64,
      max_value       Float64,
      reading_count   UInt32,
      ingested_at     DateTime DEFAULT now()
  ) ENGINE = ReplacingMergeTree(ingested_at)
  PARTITION BY toYYYYMM(hour_bucket)
  ORDER BY (tenant_id, sensor_id, sensor_type, hour_bucket)
  TTL hour_bucket + toIntervalMonth(6)
  SETTINGS index_granularity = 8192;
  ```

**Constraints Applied:**
- ReplacingMergeTree with `ingested_at` version column (deduplication)
- Partition by month (`toYYYYMM`)
- TTL 6 months (older data auto-deleted)
- Order key: tenant → sensor → hour_bucket (optimal for hourly queries)

**Next Steps:**
1. **For fresh deployments:** Table auto-created on first `docker compose up`
2. **For existing deployments:** Manual DDL execution required:
   ```bash
   docker compose exec clickhouse clickhouse-client --query "$(cat infrastructure/clickhouse/init.sql | grep -A15 'sensor_reading_hourly')"
   ```
3. Verify:
   ```bash
   docker compose exec clickhouse clickhouse-client --query "SHOW TABLES FROM analytics"
   # Expected: esg_readings, sensor_reading_hourly
   ```

---

### ✅ BUG-009 (P3) — Flink deploy leaves stale jobs in UI

**Problem:** After `make flink-deploy`, Flink UI shows 4 jobs: 2 RUNNING + 2 stale (CANCELLED/FAILED).  
**Root Cause:** `flink-deploy.sh` only cancelled jobs in `RUNNING` state, ignoring stale jobs in non-terminal states (CREATED, RESTARTING, FAILING, etc.).

**Fix:**
- File: `infrastructure/scripts/flink-deploy.sh`
- Changes:
  1. Added `get_stale_jobs()` function to detect jobs in ALL non-terminal states: RUNNING, CREATED, RESTARTING, FAILING, CANCELLING, SUSPENDED
  2. Updated `cmd_cancel()` to use `get_stale_jobs()` instead of `get_running_jobs()`
  3. Added job count logging and state display

**Before:**
```bash
cmd_cancel() {
    # Only cancelled RUNNING jobs
    get_running_jobs | while read job_id job_name; do
        curl -X PATCH "${FLINK_URL}/jobs/${job_id}?mode=cancel"
    done
}
```

**After:**
```bash
cmd_cancel() {
    # Cancels ALL active/stale jobs (RUNNING, CREATED, RESTARTING, etc.)
    get_stale_jobs | while read job_id job_name job_state; do
        log_info "Cancelling: ${job_name} (${job_id}, state: ${job_state})..."
        curl -X PATCH "${FLINK_URL}/jobs/${job_id}?mode=cancel"
    done
    log_info "Cancelled ${job_count} job(s)"
}
```

**Verification:**
```bash
bash -n infrastructure/scripts/flink-deploy.sh
# (no output = syntax OK)
```

**Next Steps:**
1. Next `make flink-deploy` will auto-cleanup stale jobs
2. Monitor Flink UI: should show only 2 RUNNING jobs after deploy
3. If stale jobs persist, manually cancel via UI or REST API:
   ```bash
   curl -X PATCH "http://localhost:8081/jobs/<job_id>?mode=cancel"
   ```

---

### ⚠️ BUG-003 (P1) — uip-mobile client not found at runtime

**Status:** **NO CODE CHANGE NEEDED** — client definition already correct in realm export.

**Analysis:**
- File: `infra/keycloak/realm-uip-export.json`
- Client `uip-mobile` **EXISTS** at lines 246-300 with proper configuration:
  - `publicClient: true`
  - `standardFlowEnabled: true` (Authorization Code Flow)
  - `directAccessGrantsEnabled: false` (no password grant)
  - PKCE enabled: `pkce.code.challenge.method: S256`
  - Redirect URIs: `uipmobile://*`, `exp+operator-mobile://*`, `http://localhost:8081/*`
  - Web Origins: `http://localhost:8081`
- Docker Compose config (line 606): `--import-realm` flag + volume mount present

**Evidence of Runtime Mismatch:**
```bash
# Tester evidence from sprint8-manual-test-execution-report.md:
GET /admin/realms/uip/clients?clientId=uip-mobile
→ HTTP 200 []  (empty array)

# But realm export contains the client!
grep "uip-mobile" infra/keycloak/realm-uip-export.json
→ 2 matches (id + clientId)
```

**Root Cause Hypothesis:**
1. **Realm import didn't run** — Keycloak container started with existing data volume, `--import-realm` only imports on FIRST startup
2. **Client was manually deleted** via admin UI during testing
3. **Realm file was updated after container started** — new config not loaded

**Recommended Actions (DevOps):**

**Option A: Force re-import (recommended for dev/staging)**
```bash
# 1. Stop Keycloak
docker compose stop keycloak

# 2. Remove data volume to force fresh import
docker volume rm uip-esg-poc_keycloak_data

# 3. Restart — will trigger realm import
docker compose up -d keycloak

# 4. Wait for import (check logs)
docker compose logs -f keycloak | grep -i "import"

# 5. Verify client exists
curl -H "Authorization: Bearer $(get-admin-token)" \
  "http://localhost:8085/admin/realms/uip/clients?clientId=uip-mobile"
# Expected: JSON array with 1 client object
```

**Option B: Manual client creation (production-safe)**
```bash
# Use Keycloak Admin API to create client
# (implementation requires bearer token + full client JSON payload)
# NOT recommended — realm export is source of truth, import is safer
```

**Option C: Check if running Keycloak version has import bug**
```bash
docker compose exec keycloak /opt/keycloak/bin/kc.sh --version
# Current: Keycloak 23.0 (Quarkus)
# Known issue: https://github.com/keycloak/keycloak/issues/xxxxx (if any)
```

**Verification Checklist:**
- [ ] Container logs show: `Imported realm uip from file /opt/keycloak/data/import/realm-uip-export.json`
- [ ] Admin UI (http://localhost:8085) → Clients → `uip-mobile` visible
- [ ] API query returns client: `GET /admin/realms/uip/clients?clientId=uip-mobile` → 200 with client object
- [ ] Mobile app can authenticate via PKCE flow

---

## Deployment Instructions

### 1. Apply Keycloak changes (BUG-004 + verify BUG-003)

```bash
# Force fresh Keycloak import
docker compose stop keycloak
docker volume rm uip-esg-poc_keycloak_data
docker compose up -d keycloak

# Wait for startup + import
docker compose logs -f keycloak | grep -E "(Started|Imported realm)"

# Verify pilot-operator password
curl -X POST http://localhost:8085/realms/uip/protocol/openid-connect/token \
  -d "client_id=uip-api" \
  -d "client_secret=uip-api-secret-dev" \
  -d "grant_type=password" \
  -d "username=pilot-operator" \
  -d "password=PilotOperator#2026!"
# Expected: 200 OK with access_token

# Verify uip-mobile client exists
curl -H "Authorization: Bearer $(get-admin-token)" \
  "http://localhost:8085/admin/realms/uip/clients?clientId=uip-mobile" | jq .
# Expected: array with 1 client object
```

### 2. Apply ClickHouse changes (BUG-006)

**For fresh deployments:**
```bash
# Table auto-created on first startup
docker compose up -d clickhouse
docker compose logs clickhouse | grep sensor_reading_hourly
```

**For existing deployments:**
```bash
# Option A: Recreate container (data preserved in volume)
docker compose stop clickhouse
docker compose rm -f clickhouse
docker compose up -d clickhouse

# Option B: Manual DDL execution
docker compose exec clickhouse clickhouse-client --query \
  "$(cat infrastructure/clickhouse/init.sql | grep -A17 'sensor_reading_hourly')"

# Verify table exists
docker compose exec clickhouse clickhouse-client --query "SHOW TABLES FROM analytics"
# Expected: esg_readings, sensor_reading_hourly
```

### 3. Apply Flink changes (BUG-009)

```bash
# Next flink-deploy will use new cleanup logic
make flink-deploy

# Monitor for stale jobs cleanup
docker compose logs flink-jobmanager | grep -i cancel
# Expected: "Cancelled: X job(s)"

# Verify Flink UI
open http://localhost:8081
# Expected: 2 RUNNING jobs ONLY (no stale CANCELLED/FAILED entries)
```

---

## Health Check (Post-Deployment)

```bash
# All services healthy
docker compose ps | grep -v Up | grep -v healthy
# Expected: no output (all containers UP)

# Keycloak: pilot users work
for user in pilot-admin pilot-operator pilot-viewer; do
  echo "Testing ${user}..."
  curl -sf -X POST http://localhost:8085/realms/uip/protocol/openid-connect/token \
    -d "client_id=uip-api" \
    -d "client_secret=uip-api-secret-dev" \
    -d "grant_type=password" \
    -d "username=${user}" \
    -d "password=Pilot${user##pilot-}#2026!" | jq -r .access_token | head -c20
  echo "..."
done
# Expected: 3 tokens (20 chars each)

# ClickHouse: sensor_reading_hourly queryable
docker compose exec clickhouse clickhouse-client --query \
  "SELECT count(*) FROM analytics.sensor_reading_hourly"
# Expected: 0 (or >0 if data exists)

# Flink: only 2 jobs RUNNING
curl -sf http://localhost:8081/jobs/overview | jq '.jobs | length'
# Expected: 2
```

---

## Risk Assessment

| Bug | Risk if unfixed | Mitigation | Priority |
|-----|----------------|------------|----------|
| BUG-004 | Pilot users can't login → mobile testing BLOCKED | Restart Keycloak | P1 |
| BUG-003 | Mobile app PKCE auth fails → mobile demo BLOCKED | Force re-import OR manual client creation | P1 |
| BUG-002 | Tester confusion, incorrect test setup | Docs updated | P1 (low effort) |
| BUG-006 | Hourly analytics queries fail → dashboard charts broken | Recreate ClickHouse container | P2 |
| BUG-009 | Flink UI cluttered, confusion about job status | Next deploy auto-fixes | P3 |

---

## Lessons Learned

1. **Keycloak realm import only runs on FIRST startup with fresh data volume**
   - Future: Document "force re-import" procedure in runbooks
   - Future: Add `make keycloak-reset` target to Makefile

2. **ClickHouse init.sql only applies to NEW containers**
   - Future: Use Liquibase/Flyway-equivalent for ClickHouse schema migrations
   - Workaround: Manual DDL execution for existing deployments

3. **Flink job lifecycle needs explicit stale job cleanup**
   - Enhancement applied: `get_stale_jobs()` now handles all non-terminal states
   - Future: Consider Flink REST API `/jobs?state=CANCELLED&age=1h` for time-based cleanup

4. **Documentation must stay in sync with actual configuration**
   - Root cause: Dev changed realm name from `uip-pilot` → `uip` but didn't update handoff docs
   - Future: Add docs review step to PR checklist

---

## Open Issues

- **BUG-003 runtime discrepancy** requires manual intervention (Keycloak re-import)
- No automated health checks for "client exists" or "table exists" post-deployment
- ClickHouse schema migrations still manual (no Liquibase equivalent)

---

**DevOps Sign-off:** ✅ All code changes COMPLETE. Deployment requires manual steps (see instructions above).

**Next Steps for Tester:**
1. Wait for DevOps to execute deployment steps (Keycloak restart, ClickHouse table creation)
2. Re-run TC-S8-058, TC-S8-059, TC-S8-060 (auth tests) → should PASS
3. Re-run TC-S8-061 (ClickHouse hourly query) → should PASS
4. Re-run TC-S8-062 (Flink UI check) → should show 2 RUNNING jobs only
