# HA Staging Environment Setup Guide

**Sprint 9 S9-QA-ENV** — Production-ready high-availability staging environment

**Gate-0 Deadline**: 2026-06-19 — HA staging env LIVE with all containers HEALTHY

---

## Overview

The HA (High Availability) staging environment provides a production-like setup with:

- **ClickHouse 2-node cluster** + ClickHouse Keeper for coordination (ADR-036)
- **Kafka 3-broker KRaft quorum** for event streaming (ADR-037)
- **TimescaleDB streaming replication** (primary + hot standby)
- **Redis** for caching
- **Flink cluster** (job manager + task manager) for stream processing
- All critical services with health checks and resource limits

This setup validates:
- Fault tolerance (broker/node failure recovery)
- Replication lag monitoring
- Load balancing across replicas
- HA operational procedures

---

## Prerequisites

Before setting up the HA staging environment, ensure:

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| Docker | 24.0+ | 25.0+ |
| Docker Compose | v2.20+ | v2.27+ |
| RAM | 16 GB | 32 GB |
| CPU cores | 4 | 8 |
| Disk space | 100 GB free | 200 GB free |
| OS | Linux, macOS | Linux (production-like) |

**Verification commands:**
```bash
docker --version            # Should be 24.0 or higher
docker compose version      # Should be v2.20 or higher
free -h                     # Check available RAM
df -h                       # Check disk space
```

---

## One-Time Setup

### Step 1: Clone repository and navigate to infrastructure directory

```bash
cd /path/to/uip-esg-poc/infrastructure
```

### Step 2: Create environment file

```bash
# Copy example if .env doesn't exist
cp -n .env.example .env 2>/dev/null || true

# Verify critical variables are set:
# - POSTGRES_PASSWORD
# - KAFKA_CLUSTER_ID (must be consistent across all brokers)
# - CLICKHOUSE_PASSWORD
# - KEYCLOAK_ADMIN_PASSWORD
```

**IMPORTANT**: For HA mode, ensure `KAFKA_CLUSTER_ID` is set to the same value in all Kafka brokers. Default: `MkU3OEVBNTcwNTJENDM2Qg`

### Step 3: Start HA stack

```bash
make up-ha
```

This command will:
- Start all base services from `docker-compose.yml`
- Apply HA overrides from `docker-compose.ha.yml`
- Create named volumes for persistent data
- Configure replication and clustering

**Expected output:**
```
=== HA Stack starting ===
  ClickHouse: clickhouse-01 (:8123) + clickhouse-02 (:8124) + keeper (:9181)
  Kafka: kafka (:9092) + kafka-2 (:9092) + kafka-3 (:9092)
  TimescaleDB: timescaledb (:5432) + standby (:5433)

Run 'make ch-migrate' after stack is healthy.
```

### Step 4: Wait for all services to be healthy

```bash
# Check health status (waits up to 5 minutes)
infrastructure/scripts/ha-health-check.sh --wait
```

**Expected output (when healthy):**
```
[HA-CHECK] Starting health check for HA environment...

[HA-CHECK] uip-kafka ... ✓ healthy
[HA-CHECK] uip-kafka-2 ... ✓ healthy
[HA-CHECK] uip-kafka-3 ... ✓ healthy
[HA-CHECK] uip-clickhouse-keeper ... ✓ healthy
[HA-CHECK] uip-clickhouse-01 ... ✓ healthy
[HA-CHECK] uip-clickhouse-02 ... ✓ healthy
...
[HA-CHECK] Result: 17/17 critical services healthy
[HA-CHECK] ✓ ALL CRITICAL SERVICES HEALTHY
```

**If services are not healthy after 5 minutes**, see [Troubleshooting](#troubleshooting) section.

### Step 5: Initialize ClickHouse cluster schema

```bash
make ch-cluster-init
```

This runs `ON CLUSTER` DDL migrations to create replicated tables across both ClickHouse nodes. The script is **idempotent** — safe to run multiple times.

**Verify replication:**
```bash
# Check that tables exist on both nodes
docker compose exec clickhouse-01 clickhouse-client -q "SHOW TABLES FROM analytics"
docker compose exec clickhouse-02 clickhouse-client -q "SHOW TABLES FROM analytics"

# Both should return the same list
```

### Step 6: Initialize Kafka topics (if not auto-created)

```bash
# If you have a kafka-topics-init target:
make kafka-topics-init

# Or manually create topics with replication factor 3:
docker compose exec kafka kafka-topics --create \
  --bootstrap-server kafka:9092 \
  --topic sensor-events \
  --partitions 6 \
  --replication-factor 3 \
  --config min.insync.replicas=2
```

**Verify topics:**
```bash
docker compose exec kafka kafka-topics --list --bootstrap-server kafka:9092
docker compose exec kafka kafka-topics --describe --bootstrap-server kafka:9092 --topic sensor-events
```

Expected replication factor: **3** (across kafka, kafka-2, kafka-3)

---

## Verify HA Configuration

### Test 1: Kafka broker failure recovery

**Simulate broker-2 failure:**
```bash
docker compose stop kafka-2
```

**Verify Kafka cluster still accepts writes:**
```bash
# Producer should still work (min.insync.replicas=2, we have kafka + kafka-3)
docker compose exec kafka kafka-console-producer \
  --bootstrap-server kafka:9092 \
  --topic sensor-events <<EOF
{"sensor_id": "test-001", "value": 42}
EOF
```

**Restore broker:**
```bash
docker compose start kafka-2

# Wait for broker to rejoin
docker compose exec kafka kafka-broker-api-versions --bootstrap-server kafka-2:9092
```

**Verify replication is in sync:**
```bash
docker compose exec kafka kafka-topics --describe --bootstrap-server kafka:9092 --topic sensor-events
# Check ISR (in-sync replicas) column — should show [1,2,3] after recovery
```

### Test 2: ClickHouse node failure recovery

**Simulate clickhouse-02 failure:**
```bash
docker compose stop clickhouse-02
```

**Verify queries still work on clickhouse-01:**
```bash
docker compose exec clickhouse-01 clickhouse-client -q "SELECT count() FROM analytics.sensor_readings"
```

**Restore node:**
```bash
docker compose start clickhouse-02

# Wait for node to be healthy
docker compose exec clickhouse-02 clickhouse-client -q "SELECT 1"

# Verify replication caught up
docker compose exec clickhouse-01 clickhouse-client -q \
  "SELECT replica_name, is_leader, total_replicas FROM system.replicas WHERE table='sensor_readings'"
```

### Test 3: TimescaleDB replication lag

**Check replication status:**
```bash
# On primary
docker compose exec timescaledb psql -U uip -d uip_smartcity -c \
  "SELECT client_addr, state, sync_state FROM pg_stat_replication;"

# On standby (read-only)
docker compose exec timescaledb-standby psql -U uip -d uip_smartcity -c \
  "SELECT pg_last_wal_receive_lsn(), pg_last_wal_replay_lsn();"
```

**Expected**: Replication lag < 1MB under normal load.

---

## Daily Operations

### Start HA environment

```bash
make up-ha
infrastructure/scripts/ha-health-check.sh --wait
```

### Stop HA environment (preserves data)

```bash
make down
# or with compose directly:
docker compose -f docker-compose.yml -f docker-compose.ha.yml down
```

**IMPORTANT**: This stops containers but **preserves volumes**. Data is NOT lost.

### Check health status (quick check)

```bash
make ha-health-check
# or:
infrastructure/scripts/ha-health-check.sh
```

### View logs

```bash
# All services
docker compose -f docker-compose.yml -f docker-compose.ha.yml logs -f

# Specific service
docker compose -f docker-compose.yml -f docker-compose.ha.yml logs -f kafka-2

# All Kafka brokers
docker compose -f docker-compose.yml -f docker-compose.ha.yml logs -f kafka kafka-2 kafka-3
```

### Restart a single service

```bash
docker compose -f docker-compose.yml -f docker-compose.ha.yml restart clickhouse-02
```

### Scale Flink task managers (if needed)

```bash
docker compose -f docker-compose.yml -f docker-compose.ha.yml up -d --scale flink-taskmanager=3
```

---

## Troubleshooting

### Issue: Port conflicts (8123, 9092, 5432 already in use)

**Symptom:**
```
Error response from daemon: driver failed programming external connectivity on endpoint uip-clickhouse-01: 
Bind for 0.0.0.0:8123 failed: port is already allocated
```

**Solution:**
```bash
# Find process using the port
lsof -i :8123
# or on Linux:
sudo netstat -tulpn | grep :8123

# Kill the process or change port mapping in docker-compose.ha.yml
```

### Issue: Container OOM killed (Out of Memory)

**Symptom:**
```bash
docker compose ps
# Shows container with status "Exited (137)"
```

**Solution:**
1. Check Docker Desktop memory limit (increase to 16GB+)
2. Add memory limits to containers in `docker-compose.ha.yml`:
   ```yaml
   services:
     kafka:
       deploy:
         resources:
           limits:
             memory: 2G
   ```
3. Reduce number of replicas for testing (e.g., 2 Kafka brokers instead of 3)

### Issue: ClickHouse Keeper not electing leader

**Symptom:**
```bash
docker compose logs clickhouse-keeper
# Shows repeated election timeout messages
```

**Solution:**
```bash
# Reset Keeper data (DESTRUCTIVE — only for staging)
docker compose down
docker volume rm uip-clickhouse-keeper-data
docker compose -f docker-compose.yml -f docker-compose.ha.yml up -d clickhouse-keeper

# Wait 30s and check logs
docker compose logs -f clickhouse-keeper | grep -i "leader"
```

### Issue: Kafka broker "NOT_ENOUGH_REPLICAS"

**Symptom:**
```
org.apache.kafka.common.errors.NotEnoughReplicasException: 
Messages are rejected since there are fewer in-sync replicas than required.
```

**Root cause:** Only 1-2 brokers are healthy, but `min.insync.replicas=2` requires at least 2.

**Solution:**
```bash
# Check broker health
docker compose ps kafka kafka-2 kafka-3

# Start unhealthy brokers
docker compose start kafka-2 kafka-3

# Verify all brokers joined
docker compose exec kafka kafka-broker-api-versions --bootstrap-server kafka:9092,kafka-2:9092,kafka-3:9092
```

### Issue: TimescaleDB standby won't start (replication slot conflict)

**Symptom:**
```
FATAL: could not start WAL streaming: ERROR: replication slot "standby_slot" already exists
```

**Solution:**
```bash
# Drop old replication slot on primary
docker compose exec timescaledb psql -U uip -d uip_smartcity -c \
  "SELECT pg_drop_replication_slot('standby_slot');"

# Restart standby
docker compose restart timescaledb-standby
```

### Issue: Health check script reports "not found" for optional services

**Symptom:**
```
[HA-CHECK] uip-kafka-ui ... ✗ not found
```

**Expected behavior:** Optional services (kafka-ui, redpanda-connect) are informational only. They won't fail the overall health check.

**If you want to start them:**
```bash
docker compose -f docker-compose.yml -f docker-compose.ha.yml up -d kafka-ui
```

---

## Production Deployment Checklist

Before deploying HA environment to production:

- [ ] All services pass `ha-health-check.sh`
- [ ] Kafka replication factor = 3, min.insync.replicas = 2
- [ ] ClickHouse cluster queries return same results from both nodes
- [ ] TimescaleDB replication lag < 1 MB
- [ ] Tested broker failure + recovery (Test 1)
- [ ] Tested ClickHouse node failure + recovery (Test 2)
- [ ] Prometheus alerts configured for:
  - Kafka under-replicated partitions
  - ClickHouse replication lag
  - TimescaleDB replication lag
  - Flink job failure
- [ ] Resource limits configured on all containers
- [ ] Secrets managed via external secrets manager (not `.env`)
- [ ] Backup strategy verified (ClickHouse, TimescaleDB, Kafka)
- [ ] Runbook for manual failover procedures

---

## References

- **ADR-036**: ClickHouse HA with Keeper coordination
- **ADR-037**: Kafka 3-broker KRaft quorum
- **Sprint 9 S9-QA-ENV**: HA staging environment task
- [ClickHouse Replication Guide](https://clickhouse.com/docs/en/engines/table-engines/mergetree-family/replication)
- [Kafka KRaft Mode](https://kafka.apache.org/documentation/#kraft)
- [TimescaleDB Replication](https://docs.timescale.com/self-hosted/latest/replication-and-ha/)

---

## Next Steps

After HA staging is verified:

1. **Load testing**: Run K6 tests to validate throughput under load
2. **Chaos engineering**: Use `make chaos-inject` to simulate network partitions
3. **Monitoring setup**: Configure Grafana dashboards for HA metrics
4. **Runbook updates**: Document manual failover procedures
5. **Production deployment**: Apply HA config to production K8s cluster

**Gate-0 sign-off (2026-06-19)**: HA staging env must pass all acceptance criteria before production deployment.
