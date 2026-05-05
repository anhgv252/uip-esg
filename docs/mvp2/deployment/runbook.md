# UIP ESG POC — Operations Runbook

**Version:** 1.0  
**Last updated:** 2026-06-20  
**Reviewed by:** DevOps Lead, Backend Lead  
**Environment:** Production (HCMC Smart City Deployment)

---

## Table of Contents

1. [Deploy Procedure (Blue-Green)](#1-deploy-procedure-blue-green)
2. [Rollback Procedure](#2-rollback-procedure)
3. [Scale Kafka Partitions](#3-scale-kafka-partitions)
4. [Add New Tenant](#4-add-new-tenant)
5. [Rotate Vault Secrets](#5-rotate-vault-secrets)
6. [Add New Secret to Vault](#6-add-new-secret-to-vault)
7. [Restore PostgreSQL from Backup](#7-restore-postgresql-from-backup)
8. [Health Checks Reference](#8-health-checks-reference)

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
