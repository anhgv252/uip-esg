# UIP ESG POC — Operations Runbook

**Version:** 1.0  
**Last updated:** 2026-06-20  
**Reviewed by:** DevOps Lead, Backend Lead  
**Environment:** Production (HCMC Smart City Deployment)

---

**Last updated:** 2026-05-09

## Table of Contents

1. [Deploy Procedure (Blue-Green)](#1-deploy-procedure-blue-green)
2. [Rollback Procedure](#2-rollback-procedure)
3. [Scale Kafka Partitions](#3-scale-kafka-partitions)
4. [Add New Tenant](#4-add-new-tenant)
5. [Rotate Vault Secrets](#5-rotate-vault-secrets)
6. [Add New Secret to Vault](#6-add-new-secret-to-vault)
7. [Restore PostgreSQL from Backup](#7-restore-postgresql-from-backup)
8. [Health Checks Reference](#8-health-checks-reference)
9. [Kafka Consumer Lag](#9-kafka-consumer-lag)
10. [Flink Job Hang / Failure](#10-flink-job-hang--failure)
11. [Redis OOM / Cache Stampede](#11-redis-oom--cache-stampede)
12. [TimescaleDB — HikariCP Exhausted / Slow Query](#12-timescaledb--hikaricp-exhausted--slow-query)
13. [Spring Boot OOM / Flyway Migration Failure](#13-spring-boot-oom--flyway-migration-failure)
14. [Tenant Data Leak (RLS)](#14-tenant-data-leak-rls)

---

## 1. Deploy Procedure (Blue-Green)

### Prerequisites

- Helm 3.12+, kubectl configured, Vault token valid
- Image tag available in ACR: `uip-backend:<git-sha>`
- Feature flags không có breaking changes
- Flyway migrations reviewed (no destructive ops)

### Steps

```bash
# 1. Verify current active slot
kubectl get svc uip-backend-active -n uip-prod -o jsonpath='{.spec.selector.slot}'
# Expected: "blue" or "green"

# 2. Deploy to inactive slot (ví dụ: green đang inactive)
helm upgrade --install uip-backend-green \
  ./infra/helm/uip-backend \
  -f infra/helm/values/values-prod.yaml \
  --set image.tag=<git-sha> \
  --set slot=green \
  -n uip-prod \
  --wait --timeout=5m

# 3. Smoke test inactive slot (port-forward nếu cần)
kubectl port-forward svc/uip-backend-green 8081:8080 -n uip-prod &
curl -f http://localhost:8081/actuator/health
curl -H "Authorization: Bearer $TEST_TOKEN" http://localhost:8081/api/v1/esg/metrics?tenantId=test

# 4. Switch traffic
kubectl patch svc uip-backend-active -n uip-prod \
  -p '{"spec":{"selector":{"slot":"green"}}}'

# 5. Monitor 5 phút — xem Grafana dashboard
# Dashboard: https://grafana.uip.vn/d/uip-backend/api-latency
# Alert: HighP95Latency threshold 200ms

# 6. Scale down old slot sau 15 phút
helm upgrade uip-backend-blue ./infra/helm/uip-backend \
  -f infra/helm/values/values-prod.yaml \
  --set replicas=0 \
  -n uip-prod
```

### Validation Checklist

- [ ] `/actuator/health` trả `UP`
- [ ] `/actuator/health/db` trả `UP`
- [ ] `/actuator/health/redis` trả `UP`
- [ ] ESG endpoint trả 200 với valid JWT
- [ ] Kafka consumer lag < 1000 (check `uip.esg.telemetry.v1`)
- [ ] Error rate < 0.1% trong 5 phút đầu sau switch

---

## 2. Rollback Procedure

**Target RTO: < 5 phút**

### Rollback traffic (immediate)

```bash
# Xác định slot đang active
ACTIVE=$(kubectl get svc uip-backend-active -n uip-prod -o jsonpath='{.spec.selector.slot}')
INACTIVE=$([ "$ACTIVE" = "blue" ] && echo "green" || echo "blue")

echo "Switching back from $ACTIVE to $INACTIVE"

# Switch traffic sang slot cũ
kubectl patch svc uip-backend-active -n uip-prod \
  -p "{\"spec\":{\"selector\":{\"slot\":\"$INACTIVE\"}}}"

# Verify
kubectl get endpoints uip-backend-active -n uip-prod
```

**Thời gian thực tế:** ~30 giây (kubectl patch instant, DNS TTL 60s).

### Rollback database migration (nếu cần)

Flyway không support automatic rollback. Procedure:

```bash
# 1. Identify migration gây lỗi
kubectl exec -n uip-prod deploy/uip-backend -- \
  java -jar flyway-commandline.jar info \
  -url=jdbc:postgresql://$PG_HOST:5432/uip_prod \
  -user=$PG_USER -password=$PG_PASSWORD

# 2. Chạy undo script thủ công (phải prepare trước khi deploy)
# File: db/undo/undo-V<version>.sql
kubectl exec -n uip-prod deploy/uip-db-admin -- \
  psql $DATABASE_URL -f /undo/undo-V<version>.sql

# 3. Update flyway_schema_history
# DELETE FROM flyway_schema_history WHERE version='<version>';
```

> **Quan trọng:** Mọi migration **phải có** file undo script tương ứng trong `db/undo/`.

---

## 3. Scale Kafka Partitions

> **Lưu ý:** Tăng partition là one-way operation. Không giảm được không cần recreate topic.

### Tăng partitions cho topic có throughput cao

```bash
# Kiểm tra current partition count
kubectl exec -n kafka kafka-0 -- \
  kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --topic uip.esg.telemetry.v1

# Tăng partitions (ví dụ: từ 6 → 12)
kubectl exec -n kafka kafka-0 -- \
  kafka-topics.sh --bootstrap-server localhost:9092 \
  --alter --topic uip.esg.telemetry.v1 \
  --partitions 12

# Verify
kubectl exec -n kafka kafka-0 -- \
  kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --topic uip.esg.telemetry.v1
```

### Rebalance consumers sau khi tăng partition

```bash
# Restart consumer group để pick up new partitions
kubectl rollout restart deployment/uip-backend -n uip-prod

# Monitor consumer lag
kubectl exec -n kafka kafka-0 -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group uip-esg-consumer-group
```

### Guideline tăng partition

| Topic | Current | Max | Trigger |
|-------|---------|-----|---------|
| `uip.esg.telemetry.v1` | 6 | 24 | Lag > 10k sustained 10m |
| `uip.alerts.v1` | 3 | 12 | Lag > 5k sustained 5m |
| `uip.esg.telemetry.error.v1` | 2 | 6 | DLQ backlog > 1k |

---

## 4. Add New Tenant

### Step 1: Database setup

```sql
-- Chạy trong psql với superuser
-- 1. Tạo tenant record
INSERT INTO public.tenants (id, name, slug, plan_tier, features_json, created_at)
VALUES (
  '<uuid>',
  'Tên Công Ty',
  'ten-cong-ty',
  'STANDARD',
  '{"city-ops": true, "esg-module": true, "environment-module": true}',
  NOW()
);

-- 2. Tạo schema riêng cho tenant
CREATE SCHEMA IF NOT EXISTS "tenant_<slug>";

-- 3. Grant privileges cho app user
GRANT USAGE ON SCHEMA "tenant_<slug>" TO uip_app_user;
GRANT ALL ON ALL TABLES IN SCHEMA "tenant_<slug>" TO uip_app_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA "tenant_<slug>" TO uip_app_user;

-- 4. Set default privileges
ALTER DEFAULT PRIVILEGES IN SCHEMA "tenant_<slug>"
  GRANT ALL ON TABLES TO uip_app_user;
```

### Step 2: Application config

```bash
# Tạo file config cho tenant
cat > backend/src/main/resources/application-tenant-<slug>.yml << EOF
tenant:
  id: <uuid>
  slug: ten-cong-ty
  features:
    city-ops: true
    esg-module: true
EOF

# Deploy config (Kubernetes ConfigMap)
kubectl create configmap tenant-config-<slug> \
  --from-file=application-tenant-<slug>.yml \
  -n uip-prod
```

### Step 3: Tạo Tenant Admin user

```bash
# Gọi API để tạo tenant admin
curl -X POST https://api.uip.vn/api/v1/admin/tenants/<tenantId>/users/invite \
  -H "Authorization: Bearer $SUPER_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@ten-cong-ty.vn",
    "role": "TENANT_ADMIN",
    "displayName": "Tenant Administrator"
  }'
# → Email với invite link được gửi tới admin@ten-cong-ty.vn
```

### Step 4: Verify

```bash
# Verify tenant record
psql $DATABASE_URL -c "SELECT id, name, slug, plan_tier FROM public.tenants WHERE slug='ten-cong-ty';"

# Verify schema
psql $DATABASE_URL -c "\dn" | grep "tenant_ten-cong-ty"

# Test API với tenant context
curl -H "Authorization: Bearer $TENANT_ADMIN_TOKEN" \
  "https://api.uip.vn/api/v1/esg/metrics?tenantId=<uuid>"
```

---

## 5. Rotate Vault Secrets

### Database credentials

```bash
# 1. Generate new credentials
vault write database/rotate-role/uip-app-user

# 2. Verify new credentials work
vault read database/creds/uip-app-user

# 3. Update Kubernetes secret
NEW_PASSWORD=$(vault read -field=password database/creds/uip-app-user)
kubectl create secret generic uip-db-secret \
  --from-literal=password=$NEW_PASSWORD \
  -n uip-prod \
  --dry-run=client -o yaml | kubectl apply -f -

# 4. Rolling restart để pick up new credentials
kubectl rollout restart deployment/uip-backend -n uip-prod
kubectl rollout status deployment/uip-backend -n uip-prod
```

### JWT signing key

```bash
# 1. Tạo new key pair
openssl genrsa -out /tmp/jwt-new.pem 4096
openssl rsa -in /tmp/jwt-new.pem -pubout -out /tmp/jwt-new-pub.pem

# 2. Store in Vault
vault kv put secret/uip/jwt \
  private_key=@/tmp/jwt-new.pem \
  public_key=@/tmp/jwt-new-pub.pem \
  rotation_date=$(date -I)

# 3. Update k8s secret
kubectl create secret generic uip-jwt-keys \
  --from-file=private.pem=/tmp/jwt-new.pem \
  --from-file=public.pem=/tmp/jwt-new-pub.pem \
  -n uip-prod \
  --dry-run=client -o yaml | kubectl apply -f -

# 4. Rolling restart (old tokens vẫn valid trong 1h window)
kubectl rollout restart deployment/uip-backend -n uip-prod

# 5. Xóa temp files
rm /tmp/jwt-new.pem /tmp/jwt-new-pub.pem
```

### Claude API key

```bash
vault kv put secret/uip/claude api_key=<new-key>
kubectl rollout restart deployment/uip-backend -n uip-prod
```

---

## 6. Add New Secret to Vault

Dùng khi cần thêm credential/API key mới vào Vault (ví dụ: tích hợp external service mới).

### Thêm secret mới (không restart required)

```bash
# 1. Write secret vào Vault KV store
vault kv put secret/uip/<service-name> \
  api_key=<value> \
  endpoint=<url>

# 2. Verify secret được lưu
vault kv get secret/uip/<service-name>

# 3. Update Vault policy nếu backend cần đọc secret mới
# Sửa infra/vault/vault-policy-backend.hcl và apply:
vault policy write backend-policy infra/vault/vault-policy-backend.hcl

# 4. Thêm env reference vào application.yml (nếu Spring Cloud Vault):
# ${vault:secret/uip/<service-name>.api_key}

# 5. Rolling restart để app pick up secret (Vault Agent Injector tự refresh)
# Nếu dùng env injection: cần restart
kubectl rollout restart deployment/uip-backend -n uip-prod
# Nếu dùng Vault Agent Injector + dynamic secrets: không cần restart
```

### Secret không cần restart (zero-downtime)

Secret rotation KHÔNG cần restart khi:
- Vault Agent Injector được cấu hình (`infra/vault/vault-agent-config.hcl`)
- `VAULT_ENABLED=true` trong pod environment
- `SecretRotationListener` đã active (log: `[VAULT] Secret rotation listener active`)

Xác nhận: `kubectl logs -n uip-prod -l app=uip-backend | grep "VAULT"`

---

## 7. Restore PostgreSQL from Backup

**Target RTO: < 1 giờ**  
**RPO:** 1 giờ (hourly WAL archiving)

### Restore từ daily backup (PITR)

```bash
# 1. Identify backup point (pgBackRest)
pgbackrest --stanza=uip-prod info

# 2. Stop application traffic
kubectl patch svc uip-backend-active -n uip-prod \
  -p '{"spec":{"selector":{"slot":"maintenance"}}}'

# 3. Scale down backend
kubectl scale deployment uip-backend-blue uip-backend-green \
  --replicas=0 -n uip-prod

# 4. Restore (target time format: 2026-06-20 14:30:00)
pgbackrest --stanza=uip-prod restore \
  --delta \
  --type=time \
  "--target=2026-06-20 14:30:00" \
  --target-action=promote

# 5. Start PostgreSQL
pg_ctl start -D $PGDATA

# 6. Verify data integrity
psql $DATABASE_URL -c "SELECT count(*) FROM public.tenants;"
psql $DATABASE_URL -c "SELECT count(*) FROM environment.sensor_readings WHERE timestamp > NOW() - INTERVAL '24h';"

# 7. Run Flyway repair nếu cần
./mvnw flyway:repair -Dflyway.url=$DATABASE_URL

# 8. Scale up backend
kubectl scale deployment uip-backend-blue --replicas=3 -n uip-prod
kubectl rollout status deployment/uip-backend-blue -n uip-prod

# 9. Switch traffic back
kubectl patch svc uip-backend-active -n uip-prod \
  -p '{"spec":{"selector":{"slot":"blue"}}}'
```

### Restore drill results

| Date | RTO Actual | Recovery Point | Operator |
|------|-----------|---------------|----------|
| 2026-06-15 | 42 phút | -0 giờ (full restore) | DevOps Team |

**Target:** RTO < 60 phút. Last drill: 42 phút. ✅

---

## 8. Health Checks Reference

| Endpoint | Expected | Failure Action |
|----------|---------|---------------|
| `GET /actuator/health` | `{"status":"UP"}` | Alert P0 ngay |
| `GET /actuator/health/db` | `{"status":"UP"}` | Check PostgreSQL pod |
| `GET /actuator/health/redis` | `{"status":"UP"}` | Check Redis pod; app có fallback |
| `GET /actuator/health/kafka` | `{"status":"UP"}` | Check EMQX + Kafka pods |
| `GET /actuator/metrics` | HTTP 200 | Non-critical |

### Circuit Breaker states

```bash
# Xem trạng thái circuit breakers
curl http://localhost:8080/actuator/circuitbreakers

# Tên các CBs:
# - claudeApiService: gọi Claude API
# - esgMetricCache: Redis fallback
```

---

## 9. Kafka Consumer Lag

**Dấu hiệu:** Consumer lag > 100K messages trên `ngsi_ld_esg` / `ngsi_ld_environment`, alert từ Grafana dashboard `uip-kafka-lag`.

**Target RTO:** < 30 phút để lag drain xuống < 1K.

### Bước 1: Xác định nguyên nhân

```bash
# Xem lag tất cả consumer groups
kubectl exec -n kafka kafka-0 -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --all-groups | grep -E "GROUP|LAG"

# Xem lag theo topic cụ thể
kubectl exec -n kafka kafka-0 -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group flink-esg-cleansing

# Kiểm tra Flink job có đang chạy không
kubectl get pods -n flink -l component=taskmanager
```

### Bước 2: Nếu Flink job đang chạy nhưng lag không drain

```bash
# Kiểm tra Flink TaskManager logs xem có backpressure không
kubectl logs -n flink -l component=taskmanager --tail=100 | grep -E "backpressure|checkpoint|WARNING|ERROR"

# Tăng parallelism của EsgCleansingJob tạm thời (nếu Flink standalone mode)
# Sửa flink-conf.yaml: parallelism.default: 4
kubectl edit configmap flink-config -n flink
kubectl rollout restart deployment/flink-jobmanager -n flink
```

### Bước 3: Nếu Flink job bị down

Xem mục [10. Flink Job Hang / Failure](#10-flink-job-hang--failure).

### Bước 4: Scale Kafka partitions nếu throughput vượt capacity

Xem mục [3. Scale Kafka Partitions](#3-scale-kafka-partitions).

> **Trigger:** `ngsi_ld_esg` lag > 10K sustained 10 phút → tăng partition từ 12 → 24.

### Kiểm tra sau khi xử lý

```bash
# Lag phải drain dần — check mỗi 2 phút
watch -n 120 "kubectl exec -n kafka kafka-0 -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group flink-esg-cleansing | grep -E 'TOPIC|LAG'"
```

---

## 10. Flink Job Hang / Failure

**Dấu hiệu:** Flink job status = RUNNING nhưng không emit output trong > 5 phút, checkpoint liên tục timeout, hoặc job ở trạng thái FAILED.

**Target RTO:** < 15 phút (savepoint → restart).

### Bước 1: Xác định trạng thái

```bash
# Xem tất cả jobs qua Flink REST API
curl http://flink-jobmanager:8081/jobs/overview

# Xem chi tiết job (thay <job-id>)
curl http://flink-jobmanager:8081/jobs/<job-id>

# Xem exceptions
curl http://flink-jobmanager:8081/jobs/<job-id>/exceptions
```

### Bước 2: Trigger savepoint trước khi can thiệp

```bash
# Tạo savepoint (giữ state xử lý để không mất offset)
curl -X POST "http://flink-jobmanager:8081/jobs/<job-id>/savepoints" \
  -H "Content-Type: application/json" \
  -d '{"cancel-job": false, "target-directory": "s3://uip-flink-checkpoints/savepoints/"}'

# Kiểm tra savepoint completed
curl http://flink-jobmanager:8081/jobs/<job-id>/savepoints/<savepoint-id>
# Chờ: status.id = "COMPLETED"
```

### Bước 3: Cancel job và restart từ savepoint

```bash
# Cancel job
curl -X PATCH "http://flink-jobmanager:8081/jobs/<job-id>?mode=cancel"

# Submit lại từ savepoint
curl -X POST "http://flink-jobmanager:8081/jars/<jar-id>/run" \
  -H "Content-Type: application/json" \
  -d '{
    "savepointPath": "s3://uip-flink-checkpoints/savepoints/<savepoint-id>",
    "parallelism": 2,
    "programArgs": "--kafka-bootstrap-servers kafka:9092"
  }'
```

### Bước 4: Nếu savepoint không tạo được (job bị FAILED)

```bash
# Restart từ latest checkpoint tự động
curl -X POST "http://flink-jobmanager:8081/jars/<jar-id>/run" \
  -H "Content-Type: application/json" \
  -d '{
    "savepointPath": "s3://uip-flink-checkpoints/checkpoints/<job-id>/chk-<latest>",
    "allowNonRestoredState": true
  }'
```

> **Lưu ý `allowNonRestoredState: true`:** Dùng khi schema thay đổi giữa checkpoint và JAR mới. Một số state có thể bị drop — chấp nhận được nếu Kafka offset vẫn còn trong retention (7 ngày).

### Bước 5: Nếu không có checkpoint hợp lệ

```bash
# Restart từ đầu — Kafka sẽ replay từ earliest hoặc committed offset
# EsgCleansingJob có idempotency key nên replay an toàn
curl -X POST "http://flink-jobmanager:8081/jars/<jar-id>/run" \
  -H "Content-Type: application/json" \
  -d '{"parallelism": 2, "programArgs": "--kafka-bootstrap-servers kafka:9092 --kafka-group-id flink-esg-cleansing-recovery"}'
```

### Kiểm tra sau khi restart

```bash
# Job phải về RUNNING trong 60s
curl http://flink-jobmanager:8081/jobs/overview | jq '.jobs[] | {id, status}'

# Consumer lag phải bắt đầu giảm
kubectl exec -n kafka kafka-0 -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group flink-esg-cleansing
```

---

## 11. Redis OOM / Cache Stampede

### Scenario A: Redis OOM — `maxmemory` reached

**Dấu hiệu:** Backend log: `ERR OOM command not allowed when used memory > 'maxmemory'`, `/actuator/health/redis` trả DOWN.

```bash
# 1. Kiểm tra memory usage
kubectl exec -n uip-prod redis-0 -- redis-cli INFO memory | grep -E "used_memory_human|maxmemory"

# 2. Xem top keys chiếm nhiều memory nhất
kubectl exec -n uip-prod redis-0 -- redis-cli --memkeys --memkeys-samples 200 | head -20

# 3. Option A: Flush ESG cache keys (safe — sẽ rebuild từ DB khi request tới)
kubectl exec -n uip-prod redis-0 -- redis-cli --scan --pattern "esg:*" | xargs redis-cli DEL

# 4. Option B: Tăng maxmemory tạm thời
kubectl exec -n uip-prod redis-0 -- redis-cli CONFIG SET maxmemory 2gb

# 5. Option C: FLUSHDB (last resort — flush all)
# ⚠️ Chỉ dùng khi option A/B không giải quyết được
kubectl exec -n uip-prod redis-0 -- redis-cli FLUSHDB
```

> **Sau FLUSHDB:** Cache stampede sẽ xảy ra — tất cả ESG requests gọi DB trong ~60s đầu. `EsgCacheWarmupService` sẽ pre-warm khi backend restart lần sau. Nếu cần warm ngay, restart 1 backend pod.

### Scenario B: Cache Stampede sau Redis restart

**Dấu hiệu:** Redis restart → tất cả cache key expired → DB query spike → TimescaleDB CPU 100%.

```bash
# Restart 1 backend pod để trigger EsgCacheWarmupService
kubectl rollout restart deployment/uip-backend -n uip-prod --replicas=1

# Monitor DB query rate trong 60s đầu
kubectl exec -n uip-prod postgres-0 -- \
  psql -U uip -c "SELECT count(*), state FROM pg_stat_activity GROUP BY state;"

# Sau khi warmup xong (~30s), scale back lên full
kubectl scale deployment/uip-backend --replicas=3 -n uip-prod
```

---

## 12. TimescaleDB — HikariCP Exhausted / Slow Query

### Scenario A: HikariCP connection pool exhausted

**Dấu hiệu:** Backend log: `Connection is not available, request timed out after 30000ms`, API trả 500 hoặc timeout.

```bash
# 1. Xem active connections
kubectl exec -n uip-prod postgres-0 -- psql -U uip -c \
  "SELECT count(*), state, wait_event_type, wait_event
   FROM pg_stat_activity
   WHERE datname='uip_prod'
   GROUP BY state, wait_event_type, wait_event
   ORDER BY count DESC;"

# 2. Kill long-running queries (> 30s)
kubectl exec -n uip-prod postgres-0 -- psql -U uip -c \
  "SELECT pg_terminate_backend(pid)
   FROM pg_stat_activity
   WHERE datname='uip_prod'
     AND state = 'active'
     AND query_start < NOW() - INTERVAL '30 seconds'
     AND pid <> pg_backend_pid();"

# 3. Tăng HikariCP maxPoolSize tạm thời (environment variable)
kubectl set env deployment/uip-backend \
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=30 \
  -n uip-prod
```

> **Nguyên nhân thường gặp:** ESG SUM query trên large dataset không dùng continuous aggregate → mỗi request tốn ~96ms DB time → thread contention. Fix lâu dài: xem V22 migration (`esg.daily_esg_summary` cagg).

### Scenario B: Slow query / missing index

```bash
# Xem slow queries (> 1s)
kubectl exec -n uip-prod postgres-0 -- psql -U uip -c \
  "SELECT pid, now() - query_start AS duration, query
   FROM pg_stat_activity
   WHERE datname='uip_prod'
     AND state = 'active'
     AND query_start < NOW() - INTERVAL '1 second'
   ORDER BY duration DESC LIMIT 10;"

# EXPLAIN ANALYZE query cụ thể
kubectl exec -n uip-prod postgres-0 -- psql -U uip -c \
  "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
   SELECT SUM(value) FROM esg.clean_metrics
   WHERE tenant_id='hcm' AND metric_type='ENERGY'
     AND timestamp BETWEEN '2026-01-01' AND '2026-04-01';"

# Refresh continuous aggregate thủ công nếu stale
kubectl exec -n uip-prod postgres-0 -- psql -U uip -c \
  "CALL refresh_continuous_aggregate('esg.daily_esg_summary',
    NOW() - INTERVAL '7 days', NOW());"
```

### Scenario C: TimescaleDB disk full

```bash
# Kiểm tra disk usage
kubectl exec -n uip-prod postgres-0 -- df -h /var/lib/postgresql/data

# Xem chunk sizes
kubectl exec -n uip-prod postgres-0 -- psql -U uip -c \
  "SELECT hypertable_name, pg_size_pretty(SUM(total_bytes))
   FROM timescaledb_information.chunks
   GROUP BY hypertable_name ORDER BY SUM(total_bytes) DESC;"

# Drop old chunks (giữ 90 ngày)
kubectl exec -n uip-prod postgres-0 -- psql -U uip -c \
  "SELECT drop_chunks('esg.clean_metrics', NOW() - INTERVAL '90 days');
   SELECT drop_chunks('environment.sensor_readings', NOW() - INTERVAL '90 days');"
```

---

## 13. Spring Boot OOM / Flyway Migration Failure

### Scenario A: OutOfMemoryError

**Dấu hiệu:** Pod restart liên tục, `kubectl describe pod` thấy `OOMKilled`, hoặc log: `java.lang.OutOfMemoryError: Java heap space`.

```bash
# 1. Xem pod restart history
kubectl get pod -n uip-prod -l app=uip-backend | grep -v Running

# 2. Xem heap dump nếu đã bật -XX:+HeapDumpOnOutOfMemoryError
kubectl cp uip-prod/<pod-name>:/tmp/heap.hprof ./heap.hprof

# 3. Tăng heap size tạm thời
kubectl set env deployment/uip-backend \
  JAVA_TOOL_OPTIONS="-Xmx2g -Xms512m" \
  -n uip-prod

# 4. Tìm nguyên nhân: thường là large page query không paginate
# Kiểm tra endpoint nào gọi nhiều nhất lúc OOM qua Actuator metrics
curl http://localhost:8080/actuator/metrics/http.server.requests | jq '.measurements'
```

### Scenario B: Flyway migration fail on startup

**Dấu hiệu:** App không start, log: `FlywayException: Validate failed: ... checksums do not match`.

```bash
# 1. Xem migration history
kubectl exec -n uip-prod postgres-0 -- psql -U uip -c \
  "SELECT version, description, success, installed_on
   FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;"

# 2. Nếu migration fail ở giữa (success=false) — repair và retry
kubectl exec -n uip-prod deploy/uip-backend -- \
  java -jar app.jar --spring.flyway.repair-on-migrate=true

# 3. Nếu checksum mismatch (ai đó sửa file migration đã chạy)
# Xóa entry lỗi và chạy lại — CHỈ làm trên non-prod
kubectl exec -n uip-prod postgres-0 -- psql -U uip -c \
  "DELETE FROM flyway_schema_history WHERE version='<version>' AND success=false;"

# 4. Nếu migration có lỗi SQL — chạy undo script thủ công trước
# Xem db/undo/undo-V<version>.sql
kubectl exec -n uip-prod postgres-0 -- psql -U uip -f /undo/undo-V<version>.sql
```

---

## 14. Tenant Data Leak (RLS)

**Mức độ: P0 — Xử lý ngay lập tức**

**Dấu hiệu:** Tenant A thấy data của Tenant B trong API response, hoặc alert từ monitoring rule `TenantDataLeak`.

### Bước 1: Cô lập ngay

```bash
# Tắt traffic vào backend (maintenance mode)
kubectl patch svc uip-backend-active -n uip-prod \
  -p '{"spec":{"selector":{"slot":"maintenance"}}}'

# Log thời điểm phát hiện, tenant bị ảnh hưởng
echo "$(date -Iseconds) — DATA LEAK DETECTED — Tenant: <tenant-id>" >> /var/log/uip-incidents.log
```

### Bước 2: Điều tra

```bash
# Kiểm tra RLS policy còn active không
kubectl exec -n uip-prod postgres-0 -- psql -U uip -c \
  "SELECT tablename, rowsecurity FROM pg_tables
   WHERE schemaname IN ('esg','environment','alerts')
   ORDER BY tablename;"
# Tất cả cột rowsecurity phải là 't'

# Kiểm tra current_setting được set đúng không
kubectl exec -n uip-prod postgres-0 -- psql -U uip -c \
  "SELECT current_setting('app.current_tenant_id', true);"
# Phải trả về tenant_id hợp lệ, không phải empty string

# Kiểm tra policy definition
kubectl exec -n uip-prod postgres-0 -- psql -U uip -c \
  "SELECT schemaname, tablename, policyname, qual
   FROM pg_policies WHERE schemaname='esg';"
```

### Bước 3: Fix nếu RLS bị disable

```bash
# Re-enable RLS trên các tables bị ảnh hưởng
kubectl exec -n uip-prod postgres-0 -- psql -U uip -c "
  ALTER TABLE esg.clean_metrics ENABLE ROW LEVEL SECURITY;
  ALTER TABLE environment.sensor_readings ENABLE ROW LEVEL SECURITY;
  ALTER TABLE alerts.alert_events ENABLE ROW LEVEL SECURITY;
"

# Verify không có cross-tenant query leak
kubectl exec -n uip-prod postgres-0 -- psql -U uip -c "
  SET app.current_tenant_id = 'hcm';
  SELECT DISTINCT tenant_id FROM esg.clean_metrics LIMIT 5;
  -- Phải chỉ thấy 'hcm', không thấy tenant khác
"
```

### Bước 4: Restore traffic sau khi verify

```bash
kubectl patch svc uip-backend-active -n uip-prod \
  -p '{"spec":{"selector":{"slot":"blue"}}}'
```

### Bước 5: Post-incident

- Ghi incident report với timeline, scope, root cause
- Notify các tenant bị ảnh hưởng trong 24h
- Review tất cả Flyway migration có `ALTER TABLE` liên quan đến RLS
