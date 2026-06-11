# ClickHouse Keeper Memory Monitoring Runbook

**Version:** 1.0
**Last Updated:** 2026-06-09
**Scope:** ClickHouse Keeper memory monitoring trên HA staging + pilot

---

## 1. Memory Thresholds

| Metric | Warning | Critical | Action |
|--------|---------|----------|--------|
| Keeper RSS | > 512 MB | > 768 MB | Investigate + compact |
| Keeper Heap | > 400 MB | > 600 MB | Restart nếu cần |
| Snapshot Size | > 200 MB | > 400 MB | Compact snapshot |
| Log Size | > 500 MB | > 1 GB | Purge old logs |

**Default memory limit:** 1 GB (configurable qua `KEEPER_MAX_HEAP`)

---

## 2. Monitoring Commands

### Check Keeper Memory
```bash
# Docker stats
docker stats clickhouse-keeper --no-stream --format "{{.MemUsage}}"

# Keeper metrics endpoint
curl -s http://localhost:9363/metrics | grep -E "process_resident_memory_bytes|jvm_memory_bytes_used"

# Snapshot size
docker exec clickhouse-keeper du -sh /keeper-data/snapshots/
docker exec clickhouse-keeper du -sh /keeper-data/logs/
```

### Prometheus Queries
```promql
# Keeper memory RSS
process_resident_memory_bytes{job="clickhouse-keeper"}

# Heap usage ratio
jvm_memory_bytes_used{job="clickhouse-keeper", area="heap"} 
/ jvm_memory_bytes_max{job="clickhouse-keeper", area="heap"} * 100

# Alert: memory > 80%
jvm_memory_bytes_used{job="clickhouse-keeper", area="heap"} 
/ jvm_memory_bytes_max{job="clickhouse-keeper", area="heap"} > 0.8
```

---

## 3. Mitigation Steps

### Step 1: Compact Snapshot
```bash
docker exec clickhouse-keeper clickhouse-keeper-tool \
  --snapshot-dir /keeper-data/snapshots \
  --compact
```

### Step 2: Increase Memory Limit
```yaml
# docker-compose.ha.yml
clickhouse-keeper:
  deploy:
    resources:
      limits:
        memory: 2G  # increase from 1G
  environment:
    JAVA_OPTS: "-Xmx1536m -Xms256m"
```

### Step 3: Restart Keeper (Non-Disruptive)
```bash
# Keeper là cluster 3-node — restart 1 node không ảnh hưởng
docker compose restart clickhouse-keeper-01

# Verify rejoin
docker exec clickhouse-keeper-02 clickhouse-keeper-client \
  --command "ruok"  # → imok
```

---

## 4. Grafana Dashboard

Dashboard: `clickhouse-keeper-monitoring.json` (infrastructure/monitoring/grafana/dashboards/)

Panels:
1. **Memory RSS** — gauge chart, threshold 512MB/768MB
2. **Heap Usage %** — time series, 80% alert line
3. **Snapshot Size** — gauge, trend over 24h
4. **Log Size** — gauge
5. **Keeper Alive** — stat panel (1=alive, 0=dead)

---

*Document: CH Keeper Memory Runbook v1.0 | Created 2026-06-09*

---

## 5. Memory Monitoring — Prometheus Alert Rules (S11-INFRA-01)

Prometheus alert rules được cấu hình tại `infra/monitoring/alert-rules-ch-keeper.yml`.

### Alert Reference

| Alert | Severity | Condition | Action |
|-------|----------|-----------|--------|
| `ClickHouseKeeperMemoryHigh` | warning | Memory >70% limit (>5min) | Investigate, check snapshot size |
| `ClickHouseKeeperMemoryCritical` | critical | Memory >90% limit (>2min) | **Restart Keeper immediately** |
| `ClickHouseKeeperHeapUsageHigh` | warning | JVM heap >75% (>5min) | Check GC pause, consider restart |
| `ClickHouseKeeperGoroutinesHigh` | warning | Goroutines >1000 (>10min) | Check Keeper version for leak fixes |
| `ClickHouseKeeperSnapshotSizeLarge` | warning | Snapshot >500MB (>15min) | Run compact, tune auto_clean_interval |

### Grafana Dashboard

Dashboard: **ClickHouse Keeper Overview** (`ch-keeper-overview`)
- File: `infra/monitoring/grafana/dashboards/ch-keeper-overview.json`
- UID: `ch-keeper-overview`
- Panels:
  1. Keeper Memory Usage (bytes — used vs limit, time series)
  2. Memory Usage % of Limit (gauge — green/yellow/red thresholds)
  3. Goroutine Count (time series with threshold line at 1000)
  4. Snapshot Size (bytes — trend over time)
  5. gRPC Connections (stat — active client connections)

### Quick Check via API

```bash
# Check Keeper memory via Prometheus
curl -s 'http://localhost:9090/api/v1/query' \
  --data-urlencode 'query=clickhouse_keeper_memory_usage_bytes/clickhouse_keeper_memory_limit_bytes' \
  | python3 -m json.tool

# Check goroutine count
curl -s 'http://localhost:9090/api/v1/query' \
  --data-urlencode 'query=clickhouse_keeper_goroutines' \
  | python3 -m json.tool

# Check active alerts
curl -s 'http://localhost:9090/api/v1/alerts' \
  | python3 -c "import sys,json; alerts=[a for a in json.load(sys.stdin)['data']['alerts'] if 'keeper' in a['labels'].get('component','')]; [print(f\"{a['state']}: {a['labels']['alertname']}\") for a in alerts]"
```

### Remediation by Alert

#### memory-high (>70%)
1. Check current snapshot size: `docker exec clickhouse-keeper du -sh /keeper-data/snapshots/`
2. If snapshot large (>200MB): run compact (see Section 3, Step 1)
3. Monitor for 15 minutes — if trend is flat, no action needed
4. If trend rising: proactively increase memory limit to 1.5GB

#### memory-critical (>90%)
1. **Immediate**: restart Keeper container: `docker compose restart clickhouse-keeper-01`
2. Verify rejoin: `docker exec clickhouse-keeper-02 clickhouse-keeper-client --command "ruok"`
3. Post-restart: increase memory limit in docker-compose to prevent recurrence

#### heap-high (>75%)
1. Check GC log: `docker logs clickhouse-keeper 2>&1 | grep "GC pause" | tail -20`
2. If GC pauses >500ms: restart Keeper
3. If persistent: increase heap: `-Xmx1536m` in JAVA_OPTS

#### goroutines-high (>1000)
1. Check Keeper version — known goroutine leak fixed in v24.3+
2. If on older version: upgrade Keeper
3. Temporary: restart to reset goroutine count
4. Check gRPC connection count for connection leak

#### snapshot-large (>500MB)
1. Run compaction: see Section 3, Step 1
2. Increase `auto_clean_interval` in keeper config
3. Consider snapshot compression: `snapshot_compression=true`