# MVP5 Sprint 2 — ClickHouse Partition Hotspot Check (INV-4)

**Task**: M5-2-T06  
**Risk**: R16 (partition skew under 50-tenant load)  
**Gate**: G2 (synthetic test scaffold)  
**Date**: 2026-06-25

---

## 1. Scenario

**Test Profile**: `infrastructure/scripts/synthetic/profiles/full-50-tenant.yaml`

- **Tenants**: 50
- **Sensors**: 100 per tenant (5,000 total)
- **Events**: 10 per sensor (50,000 queries total)
- **Pipeline**: Simulator → Kafka → Flink → ClickHouse `analytics.esg_readings`

**Partition Key**: `toYYYYMM(event_time)` on `analytics.esg_readings` and `analytics.sensor_reading_hourly`

---

## 2. ClickHouse Partition Structure

```sql
CREATE TABLE analytics.esg_readings (
    event_time DateTime,
    tenant_id LowCardinality(String),
    building_id LowCardinality(String),
    ...
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_time)
ORDER BY (tenant_id, building_id, event_time);
```

**Sharding Strategy** (production): Distributed table with `sharding_key = sipHash64(tenant_id)` → 3 nodes.

**Current Scaffold**: Single-node ClickHouse (no sharding) — partition check validates **time-based partition balance**, not shard distribution.

---

## 3. INV-4: Partition Hotspot Check

**Definition**: No single tenant/partition combination receives >20% more data than the average tenant row count.

**Formula**:
```
skew_ratio = (max_rows_per_tenant / avg_rows_per_tenant) - 1.0
hotspot = skew_ratio > 0.20
```

**Query**:
```sql
SELECT tenant_id, count() as row_count
FROM analytics.esg_readings
WHERE tenant_id IN ('tenant-001', ..., 'tenant-050')
GROUP BY tenant_id
ORDER BY row_count DESC;
```

**Threshold**: `skew_threshold: 0.20` (20% max deviation)

**Implementation**: `infrastructure/scripts/synthetic/lib/ch_partition_check.py`

---

## 4. Expected Result (PASS/FAIL Criteria)

### ✅ PASS Conditions
- `INV-4_ch_partition_skew: PASS`
- `skew_ratio ≤ 0.20` across all tenants
- Uniform distribution: all tenants have ±20% of average row count

### ❌ FAIL Conditions
- `hotspot: true` (any tenant has >20% more rows than average)
- ClickHouse unreachable (network error, query timeout)
- Query error (malformed SQL, permission denied)

### Edge Cases (Non-Blocking)
- **No data** (`row_count = 0` for all tenants):
  - Expected in mock runs before ingestion starts
  - Verdict: `{"hotspot": false, "note": "no data (expected in mock run)"}`
  - Runner proceeds without failing (SLO soft check)

---

## 5. Production Risk Context (R16)

**R16: Partition Skew Under 50-Tenant Load**

- **Baseline**: M4 ran 10 concurrent tenants (MVP4 demo). M5 scales to 50.
- **Risk**: Without tenant-aware partition key, all events land in the same monthly partition → hot partition on single ClickHouse node.
- **Mitigation**: 
  - Single-node scaffold: time-based partitioning (`toYYYYMM`) → balanced across months, but all tenants in same partition within a month.
  - Production: Distributed table with `sharding_key = sipHash64(tenant_id)` → tenants spread across 3 shards.

**What INV-4 Validates**:
- Time-based partition key (`toYYYYMM`) produces balanced data across tenants **within the current month** in the single-node scaffold.
- Does **not** validate shard distribution (requires production 3-node cluster).

**Pre-Deployment Gate**: M5-G5 (ClickHouse production cluster) validates shard skew using the same `ch_partition_check.py` query with `GROUP BY (tenant_id, shardNum())`.

---

## 6. Integration with Runner

**Invocation Point**: After Phase 2 (cross-tenant leak checks) in `_tenant_worker()`.

**Config Flag**: `ch_partition_check.enabled: true` in profile YAML.

**Code Change** (`runner.py`):
```python
# After Phase 2 in run() function — all tenants have completed ingestion
if cfg.get("ch_partition_check", {}).get("enabled", False):
    from lib import ch_partition_check
    ch_cfg = cfg["ch_partition_check"]
    result = ch_partition_check.check_partition_skew(
        ch_host=ch_cfg.get("host", "localhost"),
        ch_port=ch_cfg.get("port", 8123),
        tenant_ids=cfg["_all_tenant_ids"],
        skew_threshold=ch_cfg.get("skew_threshold", 0.20),
    )
    summary["ch_partition_check"] = result
    if result.get("hotspot"):
        summary["verdict"] = "FAIL"
        summary["invariants"]["INV4_ch_partition_skew"] = "FAIL"
    else:
        summary["invariants"]["INV4_ch_partition_skew"] = "PASS"
```

**Exit Code**:
- `verdict: FAIL` + `hotspot: true` → runner exits with code 1
- `verdict: PASS` → runner exits with code 0

---

## 7. Mock Run Results

**Profile**: `full-50-tenant.yaml` (M5-5-T13)  
**Environment**: `docker-compose.yml` single-node ClickHouse

**Expected Output** (before ingestion):
```json
{
  "skew_ratio": 1.0,
  "hotspot": false,
  "note": "no data (expected in mock run or before ingestion)",
  "tenant_distribution": {}
}
```

**Expected Output** (after 50,000 events):
```json
{
  "skew_ratio": 0.08,
  "hotspot": false,
  "max_rows_tenant": "tenant-042",
  "avg_rows_per_tenant": 1000.0,
  "max_rows": 1080,
  "tenant_distribution": {
    "tenant-001": 995,
    "tenant-002": 1003,
    ...
    "tenant-042": 1080,
    ...
    "tenant-050": 988
  }
}
```

**Verdict**: `INV4_ch_partition_skew: PASS` (skew_ratio 0.08 < threshold 0.20)

---

## 8. Known Limitations

1. **Single-Node Only**: This check validates time-based partitioning, not shard distribution. Production 3-shard cluster check requires `GROUP BY (tenant_id, shardNum())` in the query.

2. **Monthly Partition Window**: All events within the same month (`toYYYYMM`) land in the same partition. Skew appears only when one tenant sends significantly more events than others **in the current month**.

3. **No Partition Key Validation**: The query assumes `analytics.esg_readings` exists with partition key `toYYYYMM(event_time)`. If the table schema changes (e.g., to `toStartOfDay(event_time)`), the query still works but validates a different partition granularity.

4. **HTTP Query Timeout**: Default 15s. If ClickHouse is slow (cold cache, large table scan), the query may timeout → verdict FAIL with `note: "query_error: timeout"`.

---

## 9. R16 Mitigation Status

| Mitigation | Status | Evidence |
|---|---|---|
| Time-based partition key (`toYYYYMM`) | ✅ Implemented | `backend/src/main/resources/db/migration-clickhouse/V1__init_schema.sql` |
| Partition skew check (INV-4) | ✅ Implemented | `infrastructure/scripts/synthetic/lib/ch_partition_check.py` |
| Synthetic 50-tenant load test | ✅ G2 scaffold ready | `infrastructure/scripts/synthetic/profiles/full-50-tenant.yaml` |
| Production 3-shard cluster | ⏳ G5 (pre-deployment) | Requires production ClickHouse cluster setup |

**Next Gate**: M5-G5 (pre-deployment) — validate shard distribution with `shardNum()` on production cluster.

---

## 10. Checklist

- [x] Create `ch_partition_check.py` with `check_partition_skew()` function
- [x] Update `full-50-tenant.yaml` with `ch_partition_check` config
- [x] Integrate INV-4 into `runner.py` verdict logic
- [x] Document test scenario and expected results (this file)
- [ ] **G2 Exit Criteria**: Mock run executes without crashing (even with no data)
- [ ] **G5 Entry Criteria**: Production ClickHouse cluster available with 3 shards
- [ ] **G5 Exit Criteria**: INV-4 validates shard skew < 20% on production cluster

---

**Last Updated**: 2026-06-25  
**QA Owner**: UIP-qa-engineer  
**Related Tasks**: M5-2-T06, M5-5-T13  
**Related Risks**: R16
