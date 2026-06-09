# Pilot Host RAM Profile — 16GB Optimization

**Version:** 1.0
**Last Updated:** 2026-06-09
**Scope:** Pilot deployment trên 16GB RAM host

---

## 1. Memory Allocation Table

| Service | Count | Memory/instance | Total | JVM Heap | Notes |
|---------|-------|-----------------|-------|----------|-------|
| **PostgreSQL** | 1 (primary) | 1.5 GB | 1.5 GB | — | shared_buffers=512MB, effective_cache=1GB |
| **PostgreSQL** | 1 (standby) | 1.0 GB | 1.0 GB | — | Hot standby, reduced buffers |
| **Kafka Broker** | 3 | 512 MB | 1.5 GB | -Xmx384m | KRaft mode, no ZooKeeper |
| **ClickHouse** | 2 | 1.0 GB | 2.0 GB | — | mark_cache=256MB, uncompressed_cache=128MB |
| **ClickHouse Keeper** | 1 | 512 MB | 0.5 GB | -Xmx384m | Embedded Keeper |
| **Backend (monolith)** | 1 | 1.5 GB | 1.5 GB | -Xmx1024m | Spring Boot, 10+ modules |
| **Analytics Service** | 1 | 512 MB | 0.5 GB | -Xmx384m | ClickHouse queries only |
| **Frontend (nginx)** | 1 | 128 MB | 0.1 GB | — | Static files only |
| **Redis** | 1 | 256 MB | 0.25 GB | — | maxmemory 200mb, allkeys-lru |
| **Keycloak** | 1 | 512 MB | 0.5 GB | -Xmx384m | Single realm, low traffic |
| **Kong** | 1 | 256 MB | 0.25 GB | — | DB-less mode |
| **Flink JobManager** | 1 | 512 MB | 0.5 GB | -Xmx384m | 1 job, low parallelism |
| **Flink TaskManager** | 1 | 512 MB | 0.5 GB | — | 1 slot |
| **Apicurio Registry** | 1 | 256 MB | 0.25 GB | -Xmx192m | 4 schemas only |
| **EMQX** | 1 | 256 MB | 0.25 GB | — | MQTT broker, low connections |
| **Prometheus** | 1 | 256 MB | 0.25 GB | — | 15d retention |
| **Grafana** | 1 | 128 MB | 0.1 GB | — | 8 dashboards |
| **OS + Docker daemon** | — | — | 2.0 GB | — | Reserved |
| **TOTAL** | — | — | **~13.0 GB** | — | **3 GB buffer** |

---

## 2. Docker Compose Memory Limits

```yaml
services:
  postgres:
    deploy:
      resources:
        limits: { memory: 1536M }
        reservations: { memory: 1024M }

  postgres-standby:
    deploy:
      resources:
        limits: { memory: 1024M }
        reservations: { memory: 768M }

  kafka-1:
    deploy:
      resources:
        limits: { memory: 512M }
  kafka-2:
    deploy:
      resources:
        limits: { memory: 512M }
  kafka-3:
    deploy:
      resources:
        limits: { memory: 512M }

  clickhouse-01:
    deploy:
      resources:
        limits: { memory: 1024M }

  clickhouse-02:
    deploy:
      resources:
        limits: { memory: 1024M }

  clickhouse-keeper:
    deploy:
      resources:
        limits: { memory: 512M }

  backend:
    deploy:
      resources:
        limits: { memory: 1536M }
    environment:
      JAVA_OPTS: "-Xmx1024m -Xms256m -XX:+UseG1GC"

  analytics-service:
    deploy:
      resources:
        limits: { memory: 512M }
    environment:
      JAVA_OPTS: "-Xmx384m -Xms128m -XX:+UseG1GC"

  frontend:
    deploy:
      resources:
        limits: { memory: 128M }

  redis:
    deploy:
      resources:
        limits: { memory: 256M }
    command: redis-server --maxmemory 200mb --maxmemory-policy allkeys-lru

  keycloak:
    deploy:
      resources:
        limits: { memory: 512M }
    environment:
      JAVA_OPTS: "-Xmx384m -Xms128m"

  kong:
    deploy:
      resources:
        limits: { memory: 256M }

  flink-jobmanager:
    deploy:
      resources:
        limits: { memory: 512M }
    environment:
      JVM_ARGS: "-Xmx384m"

  flink-taskmanager:
    deploy:
      resources:
        limits: { memory: 512M }

  apicurio:
    deploy:
      resources:
        limits: { memory: 256M }

  emqx:
    deploy:
      resources:
        limits: { memory: 256M }

  prometheus:
    deploy:
      resources:
        limits: { memory: 256M }

  grafana:
    deploy:
      resources:
        limits: { memory: 128M }
```

---

## 3. Priority Order (Khi Cần Giảm Memory)

Nếu tổng memory vượt 14GB, giảm theo thứ tự:

1. **Kafka broker** 512→384 MB (-384 MB total) — pilot có ít producers
2. **Flink TaskManager** 512→384 MB (-128 MB) — 1 job, 1 slot
3. **Grafana** 128→96 MB (-32 MB)
4. **Prometheus** 256→192 MB (-64 MB) — giảm retention 15d→7d
5. **EMQX** 256→192 MB (-64 MB) — pilot <10 connections
6. **ClickHouse** 1024→768 MB each (-512 MB total) — pilot data nhỏ

**Maximum savings:** ~1.2 GB nếu cần

---

## 4. Verify Script

```bash
#!/bin/bash
# verify-memory.sh — check total Docker memory usage
echo "=== Pilot Host Memory Profile ==="
echo ""

# Docker stats
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}" | sort

echo ""
echo "=== Total ==="
docker stats --no-stream --format "{{.MemUsage}}" | \
  awk -F'/' '{gsub(/[MiB GiB]/,"",$1); gsub(/[MiB GiB]/,"",$2);
  if($2 ~ /GiB/) {total+=$1*1024; limit+=$2*1024}
  else {total+=$1; limit+=$2}} END {printf "Used: %.0f MB / Limit: %.0f MB (%.1f%%)\n", total, limit, total/limit*100}'

# System memory
echo ""
free -h | head -2
```

Usage:
```bash
chmod +x scripts/verify-memory.sh
./scripts/verify-memory.sh
```

---

## 5. Alert Thresholds

| Metric | Warning | Critical |
|--------|---------|----------|
| Total Docker memory | > 13 GB (81%) | > 14 GB (87.5%) |
| Host swap usage | > 0 | > 100 MB |
| OOM kills | > 0 | Any |
| Single container > limit | — | Immediate restart |

---

*Document: Pilot Host RAM Profile v1.0 | Created 2026-06-09*
