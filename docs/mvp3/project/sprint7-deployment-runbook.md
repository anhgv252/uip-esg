# Sprint 7 — Deployment Runbook

**Version:** 1.0 | **Date:** 2026-06-02 | **Owner:** DevOps + SA

---

## 1. Pre-Deploy Checklist

```
[ ] DB migration V34 applied (structural alert rules + building_id column)
[ ] All Tier 1 unit tests PASS (./gradlew test --exclude-task integrationTest)
[ ] docker-compose config validated (apicurio depends_on kafka)
[ ] Kafka v2 topics created (create-topics.sh run)
[ ] Redis flush NOT done (cache warm)
[ ] Flink checkpoint exists and recent (<30 min)
[ ] Rollback artifact tagged: git tag -a sprint6-stable
```

---

## 2. Blue-Green Deployment (rollback <30s)

```bash
# Step 1: Build + tag new image
docker build -t uip-backend:sprint7 ./backend

# Step 2: Start green instance on port 8090 (while blue stays on 8080)
docker run -d --name backend-green -p 8090:8080 \
  -e SPRING_PROFILES_ACTIVE=docker uip-backend:sprint7

# Step 3: Health check green
curl -f http://localhost:8090/api/v1/health && echo "GREEN OK"

# Step 4: Switch Kong upstream to green
curl -X PATCH http://localhost:8001/upstreams/backend-upstream/targets \
  -d "target=backend-green:8080" -d "weight=100"

# Step 5: Drain blue (zero traffic), wait 30s, stop
docker stop backend-blue && docker rm backend-blue

# ROLLBACK (if green fails):
curl -X PATCH http://localhost:8001/upstreams/backend-upstream/targets \
  -d "target=backend-blue:8080" -d "weight=100"
# Time to rollback: <30s (Kong upstream switch is instant)
```

---

## 3. Pilot Deployment Guide (Site Engineer)

### Environment Setup

```bash
# 1. Clone repository
git clone https://github.com/uip-smartcity/uip-esg-poc.git
cd uip-esg-poc

# 2. Set environment variables
cp .env.example .env
# Fill: POSTGRES_PASSWORD, REDIS_PASSWORD, JWT_SECRET, KAFKA_CLUSTER_ID

# 3. Start all services
cd infrastructure
docker compose up -d

# 4. Wait for health checks
docker compose ps  # All should show "healthy" within 3 minutes
```

### Health Check URLs

| Service | URL | Expected |
|---------|-----|----------|
| Backend API | `http://localhost:8080/api/v1/health` | `{"status":"UP"}` |
| Apicurio Registry | `http://localhost:8087/apis/registry/v2/health` | `{"status":"UP"}` |
| Keycloak | `http://localhost:8085/health` | `{"status":"UP"}` |
| Flink Dashboard | `http://localhost:8082` | Jobs: RUNNING |
| Grafana | `http://localhost:3001` | Dashboards loaded |

### Seed Structural Sensor Data

```sql
-- Run via psql or DBeaver after docker compose up
-- Seeds structural sensors for BLDG-001
INSERT INTO environment.sensors (sensor_id, sensor_name, sensor_type, building_id, is_active, tenant_id)
VALUES
  ('SENSOR-BLDG001-VIB-X',  'Vibration X-axis F1', 'STRUCTURAL_VIBRATION', 'BLDG-001', true, 'hcm'),
  ('SENSOR-BLDG001-TILT-1', 'Tilt Sensor F1',      'STRUCTURAL_TILT',      'BLDG-001', true, 'hcm'),
  ('SENSOR-BLDG001-CRACK-1','Crack Width Sensor',   'STRUCTURAL_CRACK',     'BLDG-001', true, 'hcm');
```

### Welford Cold Start Warm-up

```bash
# VibrationAnomalyJob requires 1000 readings per sensor before anomaly detection activates.
# Use the flood test producer to inject historical readings:
curl -X POST http://localhost:8080/api/v1/flood/test/inject-structural \
  -H "Content-Type: application/json" \
  -d '{"sensorId":"SENSOR-BLDG001-VIB-X","count":1200,"value":5.0,"tenantId":"hcm"}'
# This takes ~2 minutes to complete
```

---

## 4. Incident Scenarios

### (1) Flink Job Crash — VibrationAnomalyJob or FloodAlertJob

**Symptoms:** Structural P0 alerts stop generating. Flink dashboard shows `FAILED`.

**Resolution:**
```bash
# 1. Check Flink logs
docker logs uip-flink-jobmanager --tail=100 | grep ERROR

# 2. Check checkpoint availability
ls -la /flink/checkpoints/

# 3. Restart job from latest checkpoint
curl -X POST http://localhost:8082/jars/{jar_id}/run \
  -d '{"savepointPath": "/flink/checkpoints/latest"}'

# 4. Verify: check Flink dashboard → job status = RUNNING
# Time to restore: ~2 minutes (from checkpoint)
```

### (2) Kafka Topic Full / Consumer Lag

**Symptoms:** Kafka consumer group lag >10,000. Alerts arriving late or not at all.

**Resolution:**
```bash
# 1. Check consumer lag
kafka-consumer-groups --bootstrap-server kafka:9092 \
  --describe --group uip-backend-structural-alerts

# 2. If lag high, check consumer logs
docker logs uip-backend --tail=50 | grep "structural"

# 3. If disk full, increase retention
kafka-configs --bootstrap-server kafka:9092 \
  --entity-type topics --entity-name UIP.structural.alert.critical.v1 \
  --alter --add-config retention.ms=86400000  # 1 day

# 4. If consumer is stuck, restart consumer group
docker restart uip-backend
# Time to clear lag: ~5-10 minutes depending on backlog size
```

### (3) Redis Down

**Symptoms:** Safety score endpoint returns 500. Cache miss errors in logs.

**Resolution:**
```bash
# 1. Check Redis health
redis-cli -h localhost -p 6379 -a $REDIS_PASSWORD ping  # Expected: PONG

# 2. If Redis is down, restart
docker restart uip-redis

# 3. After Redis restarts, cache is empty — first calls hit DB (normal)
# No data loss — cache is only for performance

# 4. Verify cache refill (within 5 min of first requests)
redis-cli keys "safety:score:*" | head -5

# Impact: For ~5 minutes after Redis restart, safety score API may be slow (hits DB)
# Not a data loss event — transparent to users
```

### (4) DB Connection Pool Exhausted (HikariCP)

**Symptoms:** Requests hang or timeout. Logs show "Connection timeout after 30000ms".

**Resolution:**
```bash
# 1. Check active connections
psql $DB_URL -c "SELECT count(*) FROM pg_stat_activity WHERE state='active';"

# 2. Kill idle connections blocking pool
psql $DB_URL -c "
  SELECT pg_terminate_backend(pid) 
  FROM pg_stat_activity 
  WHERE state='idle' AND query_start < now() - interval '30 minutes';"

# 3. If persistent, increase pool size temporarily
docker exec uip-backend env | grep HIKARI
# Edit application.yml or pass env var:
# spring.datasource.hikari.maximum-pool-size=30  (from default 20)

# 4. Restart backend to apply new pool size
docker restart uip-backend

# Prevention: check for N+1 queries or missing indexes
```

### (5) ClickHouse Query Timeout

**Symptoms:** Analytics endpoints (ESG, forecasts) return 504. ClickHouse logs show long-running queries.

**Resolution:**
```bash
# 1. Check running queries
clickhouse-client --query "SELECT query, elapsed, rows_read FROM system.processes ORDER BY elapsed DESC LIMIT 5;"

# 2. Kill long-running query
clickhouse-client --query "KILL QUERY WHERE elapsed > 30;"

# 3. Check ClickHouse system load
clickhouse-client --query "SELECT * FROM system.metrics WHERE metric LIKE '%Query%';"

# 4. If ClickHouse is overloaded, reduce concurrent queries
# In application.yml: uip.clickhouse.max-connections=5  (from 10)

# Impact: During ClickHouse recovery, analytics use PostgreSQL fallback
```

### (6) Keycloak Token Failure

**Symptoms:** Users can't log in. JWT validation fails. 401 on all authenticated endpoints.

**Resolution:**
```bash
# 1. Check Keycloak health
curl http://localhost:8085/health  # Expected: {"status":"UP"}

# 2. If Keycloak is down, restart
docker restart uip-keycloak

# 3. Check Keycloak DB connectivity (Keycloak uses separate DB)
docker logs uip-keycloak --tail=50 | grep -i "error\|database"

# 4. If JWT issuer URL changed (ngrok/cloudflare), update application.yml:
# jwt.keycloak.issuer: http://new-issuer-url/realms/uip
# Restart backend after change

# 5. Emergency: HMAC fallback tokens still work if Keycloak is down
# (uip-legacy issuer)
# Time to restore: ~3 minutes (Keycloak boot time)
```

---

*Runbook Version 1.0 | Sprint 7 | Updated 2026-06-02*
