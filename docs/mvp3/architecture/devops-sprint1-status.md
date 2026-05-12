# DevOps Sprint MVP3-1 Status

**Date:** 2026-05-11  
**Owner:** DevOps  
**Sprint:** MVP3-1 (2026-05-12 → 2026-05-25)

---

## 1. ClickHouse POC

### Status: ✅ RUNNING

| Item | Detail |
|------|--------|
| Container | `uip-clickhouse` (từ `infrastructure/docker-compose.yml`) |
| Health | Up 2h+ (healthy) |
| Version | 23.8.16.16 (LTS) |
| HTTP endpoint | `http://localhost:8123` |
| `/ping` response | `Ok.` ✅ |

### Tables applied (V001 schema):

```
SHOW TABLES FROM analytics;
─────────────────────────
esg_readings
esg_readings_v
sensor_reading_hourly
```

**Note:** `building_hourly_rollup_mv` (MaterializedView) cần apply riêng — deferred Sprint 2 khi Flink dual-sink live.

### Schema fix applied (2026-05-11):
`V001__create_analytics_schema.sql` line 53: `DateTime64(3)` → `DateTime`
> **Lý do:** ClickHouse 23.8 không support `DateTime64` trong TTL expression. Phải dùng `DateTime`. Đây là known limitation của ClickHouse trước v24.x.

### Verify command:
```bash
curl -s http://localhost:8123/ping
# Expected: Ok.

curl -s "http://localhost:8123/" --data "SHOW TABLES FROM analytics"
# Expected: esg_readings, esg_readings_v, sensor_reading_hourly
```

---

## 2. Kong Gateway (Non-prod)

### Status: ⚠️ CONFIG READY — Deploy pending

| Item | File | Status |
|------|------|--------|
| DB-less declarative config | `infra/kong/kong.poc.yml` | ✅ Created |
| alg=none CI test script | `infra/kong/test-alg-none.sh` | ✅ Created (chmod+x) |
| Plugin order | cors → jwt → request-transformer → rate-limiting → prometheus → correlation-id | ✅ Locked (ADR-028) |

### Deploy command (non-prod):
```bash
# Kong DB-less mode
docker run -d --name uip-kong-poc \
  -e KONG_DATABASE=off \
  -e KONG_DECLARATIVE_CONFIG=/kong/kong.poc.yml \
  -e KONG_PROXY_LISTEN="0.0.0.0:8000" \
  -e KONG_ADMIN_LISTEN="0.0.0.0:8001" \
  -v $(pwd)/infra/kong/kong.poc.yml:/kong/kong.poc.yml \
  -p 8000:8000 -p 8001:8001 \
  kong:3.6-alpine

# Verify
curl -s http://localhost:8001/status | jq '.status.database.reachable'
# Expected: false (DB-less mode = correct)
```

### alg=none CI test:
```bash
./infra/kong/test-alg-none.sh http://localhost:8000 /api/v1/analytics
# Expected: PASS: Kong correctly rejected alg=none token → HTTP 401
```

---

## 3. Keycloak (Non-prod)

### Status: ⚠️ CONFIG READY — Deploy pending

| Item | File | Status |
|------|------|--------|
| JWT claims contract | `infra/keycloak/jwt-claims-contract.json` | ✅ Created |
| ADR-027 (Hybrid Auth strategy) | `docs/mvp3/architecture/ADR-027-keycloak-hybrid-auth.md` | ✅ Merged |

### Deploy command (non-prod):
```bash
docker run -d --name uip-keycloak-poc \
  -e KC_DB=postgres \
  -e KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak \
  -e KC_DB_USERNAME=keycloak \
  -e KC_DB_PASSWORD=keycloak_pass \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin_secure_pwd \
  -p 9090:8080 \
  quay.io/keycloak/keycloak:24.0 start-dev

# After startup:
# Create realm "uip"
# Import: infra/keycloak/realm-export.json (create Sprint 2)
# Verify token grant p95 <200ms
```

### Token grant performance test (after deploy):
```bash
# k6 script (brief inline)
k6 run - <<'EOF'
import http from 'k6/http';
import { check } from 'k6';
export let options = { vus: 10, duration: '30s', thresholds: { http_req_duration: ['p(95)<200'] } };
export default function () {
  let res = http.post('http://localhost:9090/realms/uip/protocol/openid-connect/token',
    { grant_type: 'client_credentials', client_id: 'uip-api', client_secret: 'test-secret' });
  check(res, { 'status is 200': (r) => r.status === 200 });
}
EOF
```

---

## 4. Items Requiring Staging Deployment

| Item | Sprint | Blocker |
|------|--------|---------|
| analytics-service shadow on Tier 2 | Sprint 1 end | `applications/analytics-service/` ready, Docker image needs CI build |
| Shadow diff monitoring 72h | Sprint 1 end | Requires analytics-service deployed AND real traffic |
| Kong alg=none CI test (live) | Sprint 1 | `infra/kong/test-alg-none.sh` ready, Kong not deployed yet |
| Keycloak token grant <200ms | Sprint 1 | Requires Keycloak non-prod up |

---

## 5. Gate Checklist Items — DevOps Verification

| Gate Item | Status | Evidence |
|-----------|--------|---------|
| ClickHouse POC `/ping` → `Ok.` | ✅ PASS | `curl http://localhost:8123/ping` = `Ok.` (2026-05-11) |
| ClickHouse schema V001 tables | ✅ PASS | `esg_readings`, `esg_readings_v`, `sensor_reading_hourly` confirmed |
| Kong non-prod: alg=none → 401 | ⚠️ PENDING | Script ready, Kong not deployed |
| Kong non-prod: token grant <200ms | ⚠️ PENDING | Keycloak not deployed |
