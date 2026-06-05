# UIP Pilot Runbook — Incident Response Procedures

**Version:** 1.0
**Last Updated:** 2026-06-05
**Approved by:** [Backend Lead] / [DevOps]
**Environment:** HA Staging / Production Pilot

---

## Quick Reference

| Service | URL | Port |
|---------|-----|------|
| UIP Backend API | `https://api.uip.local` | 8080 |
| Keycloak | `https://auth.uip.local` | 8080 |
| Kong API Gateway | `https://gateway.uip.local` | 8443 |
| ClickHouse Node 1 | `uip-clickhouse-01` | 8123 (HTTP), 9000 (Native) |
| ClickHouse Node 2 | `uip-clickhouse-02` | 8123 (HTTP), 9000 (Native) |
| Kafka Broker 1-3 | `uip-kafka-{1,2,3}` | 9092 |
| Flink JobManager | `https://flink.uip.local` | 8081 |
| Grafana | `https://grafana.uip.local` | 3000 |
| Prometheus | `https://prometheus.uip.local` | 9090 |
| EMQX MQTT | `mqtt.uip.local` | 1883 (MQTT), 8083 (WS) |
| MinIO S3 | `https://s3.uip.local` | 9000 |
| Redis | `uip-redis` | 6379 |

### Emergency Contacts

| Role | Escalation |
|------|-----------|
| Backend Lead | First responder — API/data issues |
| DevOps | Infrastructure, deployment, network |
| SA | Architecture decisions, cross-module |
| PO | Business-critical decisions, rollback approval |

---

## Scenario 1: Keycloak Secret Rotation Failure

### Symptoms
- Backend login returns `401 Unauthorized` after secret rotation
- Logs: `Invalid client secret` or `JWT verification failed`
- Grafana alert: `uip_auth_login_failures_total` spike

### Root Cause
- Old client secret cached in backend Spring Boot config
- Keycloak client secret changed but backend env var not updated
- Both old and new secrets invalid (double rotation)

### Recovery Steps

```bash
# Step 1: Check current secret in Keycloak
# Login to Keycloak Admin Console → Clients → uip-api → Credentials → Client Secret

# Step 2: Get current backend secret
kubectl get secret uip-api-secrets -o jsonpath='{.data.KEYCLOAK_CLIENT_SECRET}' | base64 -d

# Step 3: If mismatch, update backend secret
kubectl set env deployment/uip-api \
  KEYCLOAK_CLIENT_SECRET=$(cat /path/to/new-secret)

# Step 4: Restart backend pods
kubectl rollout restart deployment/uip-api
kubectl rollout status deployment/uip-api

# Step 5: If rotation completely broken, rollback to previous realm
# Import the last Keycloak realm backup
/opt/keycloak/bin/kc.sh import --file /backups/keycloak-realm-$(date +%Y%m%d).json
```

### Rollback: Re-import Old Realm
```bash
# If rotation broke Keycloak completely
/opt/keycloak/bin/kc.sh import --file /backups/keycloak-realm-pre-rotation.json
# Restart Keycloak
kubectl rollout restart deployment/keycloak
```

### Verification
```bash
# Test login with valid credentials
curl -X POST https://api.uip.local/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@hcm-uip.vn","password":"<password>"}'
# Expected: 200 + JWT token

# Verify old secret is rejected
# Should fail with 401
```

---

## Scenario 2: ClickHouse Node Failure

### Symptoms
- Analytics queries return errors or timeout
- Logs: `ClickHouse exception: Connection refused` or `DB::Exception: ZooKeeper session expired`
- Grafana: One CH node DOWN, replication queue growing

### Root Cause
- CH node process crashed (OOM, disk full)
- ZooKeeper connection lost
- Hardware failure (disk, network)

### Recovery Steps

```bash
# Step 1: Check cluster status on surviving node
clickhouse-client --host uip-clickhouse-01 --query \
  "SELECT hostname(), * FROM system.replicas WHERE database = 'analytics'"

# Step 2: Check if failed node process is alive
ssh uip-clickhouse-02 "systemctl status clickhouse-server"
# Or: docker ps | grep clickhouse

# Step 3: If process down, restart
ssh uip-clickhouse-02 "systemctl restart clickhouse-server"
# Or: docker restart uip-clickhouse-02

# Step 4: Wait for replication catch-up
clickhouse-client --host uip-clickhouse-02 --query \
  "SELECT database, table, absolute_delay FROM system.replicas"

# Step 5: If disk full, clean old data
ssh uip-clickhouse-02 "df -h /var/lib/clickhouse"
# Clean old part files if needed
clickhouse-client --host uip-clickhouse-02 --query \
  "ALTER TABLE analytics.sensor_readings DROP PARTITION WHERE toYYYYMM(timestamp) < 202501"
```

### Verification
```bash
# Both nodes should show OK status
clickhouse-client --query "SELECT * FROM system.clusters WHERE cluster = 'uip_cluster'"
# Expected: 2 hosts, all available

# Run a test analytics query
curl -s "https://api.uip.local/api/v1/dashboard" -H "Authorization: Bearer $TOKEN"
# Expected: 200 + dashboard data
```

---

## Scenario 3: Kafka Broker Down

### Symptoms
- Sensor data ingestion delays
- Flink job consumer lag increases
- Logs: `Connection to node -1 could not be established`
- Grafana: `kafka_consumergroup_lag` increasing

### Root Cause
- Broker process crashed (OOM, disk, network)
- Partition leadership imbalance
- ZooKeeper session expired

### Recovery Steps

```bash
# Step 1: Check broker status
kafka-broker-api-versions --bootstrap-server uip-kafka-01:9092
kafka-broker-api-versions --bootstrap-server uip-kafka-02:9092
kafka-broker-api-versions --bootstrap-server uip-kafka-03:9092

# Step 2: Check partition leadership
kafka-topics.sh --bootstrap-server uip-kafka-01:9092 \
  --describe --topic ngsi_ld_environment
# Look for: Leader: -1 (no leader) or ISR count < replication factor

# Step 3: If broker down, restart
docker restart uip-kafka-02
# Or: systemctl restart kafka

# Step 4: Verify preferred replica election
kafka-preferred-replica-election.sh --bootstrap-server uip-kafka-01:9092

# Step 5: Check consumer group lag
kafka-consumer-groups.sh --bootstrap-server uip-kafka-01:9092 \
  --describe --group uip-flink-consumer
```

### Verification
```bash
# All topics should have leaders and ISR
kafka-topics.sh --bootstrap-server uip-kafka-01:9092 \
  --describe --topic ngsi_ld_environment | grep -c "Leader: [0-9]"
# Expected: matches partition count

# Inject test reading and verify it flows through
curl -X POST https://api.uip.local/api/v1/test/inject-reading \
  -H "Authorization: Bearer $TOKEN" \
  -d "sensorId=TEST-001&sensorType=TEMPERATURE&value=25.0"
# Expected: 200 + reading injected
```

---

## Scenario 4: Application Deployment Rollback

### Symptoms
- New deployment causes 500 errors
- Frontend cannot connect to API
- Specific endpoint returning unexpected responses
- Health check failing

### Root Cause
- Bad code deployed (bug, config error)
- Database migration issue
- Dependency version conflict

### Recovery Steps

```bash
# Step 1: Check current deployment status
kubectl rollout status deployment/uip-api
kubectl get pods -l app=uip-api

# Step 2: Check health endpoint
curl https://api.uip.local/api/v1/health
# If DOWN or timeout, proceed to rollback

# Step 3: Rollback to previous revision
kubectl rollout undo deployment/uip-api
kubectl rollout status deployment/uip-api

# Step 4: If using Docker Compose
cd /opt/uip/infra
docker compose pull uip-api
docker compose up -d uip-api --no-deps
# If bad image, pin to previous version
# Edit docker-compose.yml: image: uip-api:v1.x.x-previous
docker compose up -d uip-api --no-deps

# Step 5: Verify
curl https://api.uip.local/api/v1/health
# Expected: {"status":"UP"}
```

### Blue-Green Rollback
```bash
# If using blue-green deployment
kubectl patch service uip-api -p '{"spec":{"selector":{"version":"blue"}}}'
# Where "blue" is the stable version, "green" was the new deploy
```

### Verification
```bash
# Run smoke tests
./scripts/smoke-test.sh
# Expected: 9/9 PASS

# Check Grafana dashboard for error rate
# Error rate should drop below 0.1%
```

---

## Scenario 5: Database Connection Pool Exhaustion

### Symptoms
- API responses slow (latency > 5s) or timeout (503)
- Logs: `HikariPool - Connection is not available, request timed out after 30000ms`
- Grafana: `hikaricp_connections_active` ≥ `hikaricp_connections_max`
- Database CPU high

### Root Cause
- Sudden traffic spike exceeding pool capacity
- Connection leak (unclosed connections in code)
- Long-running queries blocking pool
- Database server overloaded

### Recovery Steps

```bash
# Step 1: Check current connection pool metrics
curl -s http://localhost:8081/actuator/metrics/hikaricp.connections.active
curl -s http://localhost:8081/actuator/metrics/hikaricp.connections.pending

# Step 2: Scale backend pods (immediate relief)
kubectl scale deployment/uip-api --replicas=4

# Step 3: Check for long-running queries on PostgreSQL
psql -c "SELECT pid, now() - pg_stat_activity.query_start AS duration, query \
  FROM pg_stat_activity WHERE state = 'active' ORDER BY duration DESC LIMIT 10;"

# Step 4: Kill long-running queries if needed
psql -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity \
  WHERE state = 'active' AND now() - query_start > interval '5 minutes';"

# Step 5: Adjust HikariCP if needed (config change)
# In application.yml or env var:
# spring.datasource.hikari.maximum-pool-size=30 (default 20)
# spring.datasource.hikari.connection-timeout=20000
```

### Verification
```bash
# Pool metrics should show available connections
curl -s http://localhost:8081/actuator/metrics/hikaricp.connections.idle
# Expected: > 5 idle connections

# API latency should recover
curl -o /dev/null -s -w "%{time_total}" https://api.uip.local/api/v1/dashboard
# Expected: < 1 second
```

---

## Scenario 6: SSE Connection Storm

### Symptoms
- Backend memory usage spikes
- API response times degrade
- Logs: `SseEmitter` timeout or `OutOfMemoryError`
- Grafana: Active SSE connections > 1000

### Root Cause
- Mass client reconnection after network issue
- Mobile clients not implementing exponential backoff
- SSE connection leak (clients not disconnecting properly)

### Recovery Steps

```bash
# Step 1: Check active SSE connections
curl -s http://localhost:8081/actuator/metrics/sse.emitters.active
# If > 1000, proceed with mitigation

# Step 2: Enable SSE rate limiting (if not already)
# Verify Kong rate limiting plugin is active
curl -s http://localhost:8001/plugins | jq '.data[] | select(.name=="rate-limiting")'

# Step 3: Restart affected pods to clear stale connections
kubectl rollout restart deployment/uip-api
kubectl rollout status deployment/uip-api

# Step 4: If mobile clients, verify reconnect logic
# Mobile app should use EventSource with:
# - Max 3 reconnect attempts
# - Exponential backoff: 1s, 2s, 4s
# - Fallback to polling after max retries

# Step 5: Adjust SSE timeout if needed
# spring.mvc.async.request-timeout=300000  (5 min default)
# sse.emitter.timeout=300000
```

### Verification
```bash
# Active connections should stabilize
curl -s http://localhost:8081/actuator/metrics/sse.emitters.active
# Expected: < 200 (normal load for 5 pilot buildings)

# Memory usage should be normal
curl -s http://localhost:8081/actuator/metrics/jvm.memory.used
# Expected: < 512MB
```

---

## Rollback Decision Matrix

| Condition | Action | Authority |
|-----------|--------|-----------|
| Single endpoint failing | Fix forward | Backend Lead |
| Multiple endpoints failing | Rollback deployment | DevOps |
| Database corruption | Restore from backup | DevOps + Backend Lead |
| Keycloak down | Restart Keycloak, rollback realm | DevOps |
| CH cluster degraded | Single node recovery, no rollback | DevOps |
| Kafka data loss risk | Stop producers, assess damage | SA + Backend Lead |
| Full system down > 5 min | Rollback all services, notify PO | DevOps + PO |

---

## Backup Locations

| Data | Location | Frequency |
|------|----------|-----------|
| PostgreSQL | `/backups/postgres/` | Daily at 02:00 SGT |
| ClickHouse | `/backups/clickhouse/` | Daily at 03:00 SGT |
| Keycloak Realm | `/backups/keycloak/` | Before any config change |
| Kafka Topics | MinIO `uip-kafka-backups` | Weekly |
| Flink Checkpoints | MinIO `uip-flink-checkpoints` | Every 60 seconds |

---

*Document: Pilot Runbook v1.0 | Created 2026-06-05*
*Reviewers: [Backend Lead], [DevOps] — Sign-off required before pilot*
