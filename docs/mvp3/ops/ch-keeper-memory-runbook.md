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