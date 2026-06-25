# M5-1 RowPolicy Smoke Test Runbook

**ADR**: [ADR-047](../../adr/ADR-047-clickhouse-row-policy-tenant-isolation.md) — ClickHouse Row-Level Policy for Tenant Isolation
**Sprint**: M5-1 | **Task**: #6 (DevOps) | **Env**: single-node PoC (HA = ADR-036, deferred)

> Verify CH RowPolicy tenant isolation works end-to-end against a real ClickHouse
> container. This is the Spike S1 proof (ADR-047 §3) plus the operational sanity
> check before QA fuzz tests (Task #5) consume the env.

---

## Prerequisites

- Docker + Docker Compose
- `.env` file at repo root with (do NOT commit real secrets):
  ```
  CLICKHOUSE_POLICY_USER=analytics_policy
  CLICKHOUSE_POLICY_PASSWORD=changeme   # override in real env
  CLICKHOUSE_DB=analytics
  ```
- Files mounted by `infrastructure/docker-compose.yml`:
  - `infrastructure/clickhouse/init.sql` → `/docker-entrypoint-initdb.d/01-init.sql`
  - `infrastructure/clickhouse/02-row-policy.sql` → `/docker-entrypoint-initdb.d/02-row-policy.sql`
  - `infra/clickhouse/schema/V032__row_policy_tenant_iso.sql` → `/docker-entrypoint-initdb.d/03-v032-row-policy.sql`

ClickHouse runs init scripts **alphabetically**: `01` (schema) → `02` (user) → `03` (policy + GRANT).

---

## Step 1 — Bring up ClickHouse

```bash
cd infrastructure
docker compose up -d clickhouse
```

Wait for healthy:
```bash
until docker exec uip-clickhouse clickhouse-client --query "SELECT 1" >/dev/null 2>&1; do
  echo "waiting for clickhouse..."; sleep 2
done
echo "clickhouse ready"
```

## Step 2 — Verify policies exist

```bash
docker exec uip-clickhouse clickhouse-client --query \
  "SHOW POLICIES ON analytics.esg_readings FORMAT TabSeparated"
```
Expected output includes:
```
tenant_iso_esg_readings	SELECT	RESTRICTIVE	1	analytics_policy
```

```bash
docker exec uip-clickhouse clickhouse-client --query \
  "SHOW POLICIES ON analytics.sensor_reading_hourly FORMAT TabSeparated"
```
Expected: `tenant_iso_sensor_hourly  SELECT  RESTRICTIVE  1  analytics_policy`

If empty → V032 mount failed. Check logs:
```bash
docker logs uip-clickhouse 2>&1 | grep -i "row policy\|initdb"
```

## Step 3 — Seed two tenants

```bash
docker exec uip-clickhouse clickhouse-client --query "
INSERT INTO analytics.esg_readings
  (tenant_id, building_id, source_id, metric_type, value, unit, recorded_at)
VALUES
  ('tenant_A','B1','s1','ENERGY',100.0,'kWh',now()),
  ('tenant_A','B1','s2','ENERGY',50.0,'kWh',now()),
  ('tenant_B','B2','s3','ENERGY',999.0,'kWh',now())
"
```

## Step 4 — Verify cross-tenant isolation (L2 enforcement)

**The key test:** a query that OMITS `WHERE tenant_id` must still be restricted
by the RowPolicy. Connect as `analytics_policy` and SET the session setting.

```bash
# As tenant_A → must see ONLY tenant_A rows (2), never tenant_B
docker exec -e CLICKHOUSE_POLICY_PASSWORD=changeme uip-clickhouse clickhouse-client \
  --user analytics_policy --password changeme --query "
SET tenant_id = 'tenant_A';
SELECT count() FROM analytics.esg_readings;
"
```
**Expected:** `2` (tenant_A rows only). If `3` → RowPolicy NOT applied. If `0` → session setting not honored.

```bash
# As tenant_B → must see ONLY tenant_B rows (1)
docker exec uip-clickhouse clickhouse-client \
  --user analytics_policy --password changeme --query "
SET tenant_id = 'tenant_B';
SELECT count() FROM analytics.esg_readings;
"
```
**Expected:** `1`.

## Step 5 — Fail-closed (no session setting)

```bash
# Connect WITHOUT SET tenant_id → currentSetting('tenant_id') = '' → matches no row
docker exec uip-clickhouse clickhouse-client \
  --user analytics_policy --password changeme --query "
SELECT count() FROM analytics.esg_readings;
"
```
**Expected:** `0` — RowPolicy denies all rows when tenant context missing. This is the fail-closed guarantee.

## Step 6 — Pool bleed check (Spike S1 §3.3)

Open **one** persistent connection, run tenant_A query, then WITHOUT re-setting:

```bash
docker exec -it uip-clickhouse clickhouse-client --user analytics_policy --password changeme --multiquery
```
Inside the client:
```sql
SET tenant_id = 'tenant_A';
SELECT count() FROM analytics.esg_readings;   -- expect 2
SET tenant_id = '';                             -- simulate RowPolicyEngine reset
SELECT count() FROM analytics.esg_readings;   -- expect 0 (no bleed)
```
**Expected:** second count is `0`. If `2` → setting leaked → `RowPolicyEngine` reset logic (Task #3) is load-bearing.

## Step 7 — Teardown

```bash
cd infrastructure
docker compose down -v   # -v removes clickhouse_data volume → clean slate next run
```

---

## Kafka volume reset note (memory: Kafka Reset Runbook)

This smoke test only touches ClickHouse — no Kafka state. But if you switch this
single-node compose ↔ `docker-compose.ha.yml` (ADR-036) in the same workspace:

1. Delete all 3 Kafka volumes + recreate topics via `create-topics.sh`
2. Re-submit Flink jobs via REST `entryClass`
3. Confirm `create-topics.sh` covers the 7 MVP4 topics

Failing to reset Kafka volumes causes consumer offset corruption across compose variants.

---

## Pass criteria

| Step | Must show |
|------|-----------|
| 2 | 2 RESTRICTIVE policies on esg_readings + sensor_reading_hourly |
| 4 | tenant_A → 2 rows, tenant_B → 1 row (no cross-tenant leak) |
| 5 | No session setting → 0 rows (fail-closed) |
| 6 | After reset → 0 rows (no pool bleed) |

All 4 green = Spike S1 PASS → **View-per-Tenant fallback NOT needed**. Proceed to Task #5 (fuzz test) on this env.

Any red = Spike S1 FAIL → escalate to SA, evaluate View-per-Tenant fallback (+2 SP per ADR-047 §3.4).
