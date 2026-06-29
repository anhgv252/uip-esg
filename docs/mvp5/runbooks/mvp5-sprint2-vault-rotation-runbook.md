# MVP5 Sprint 2 — Vault Secret Rotation Runbook

**Date**: 2026-06-26
**Owner**: DevOps
**Sprint**: M5-2 (T09)
**Related**: ADR-048 (HA topology), M5-1-T03 (Vault KV v2 + agent)

---

## 1. Rotation Policy

### 1.1 Scope

All secrets stored in Vault KV v2 engine (`secret/uip/*`) rotate every **30 days**. This is a **Risk R6 mitigation** (secret compromise exposure window).

**10 KV v2 paths in scope:**

| Path | Consumer | Rotation criticality |
|---|---|---|
| `secret/uip/postgres` | backend, analytics-service, keycloak | ⚠️ HIGH — requires coordinated restart |
| `secret/uip/clickhouse` | analytics-service, flink-jobs | ⚠️ HIGH — StatefulSet restart |
| `secret/uip/redis` | backend (session store) | ✅ SAFE — hot-reload supported |
| `secret/uip/kafka` | backend, iot-ingestion-service, flink-jobs | ⚠️ HIGH — coordinated restart |
| `secret/uip/keycloak` | keycloak, kong (OIDC) | ⚠️ CRITICAL — impacts all auth |
| `secret/uip/kong` | kong (admin API) | ⚠️ MEDIUM — API gateway admin |
| `secret/uip/minio` | flink-jobs (checkpoints), backup scripts | ✅ SAFE — client retry |
| `secret/uip/emqx` | iot-ingestion-service | ⚠️ MEDIUM — IoT broker |
| `secret/uip/jwt` | backend (JWT signing key) | 🔥 CRITICAL — must coordinate with Keycloak |
| `secret/uip/ai-claude` | backend (AI service) | ✅ SAFE — API key rotation |

### 1.2 Rotation frequency

**Production schedule:**
```cron
0 2 1 * *    # 1st day of each month, 2:00 AM (low traffic)
```

**Pilot POC schedule (MVP5):**
- Manual rotation via `make vault-rotate secret=<name>` — no cron yet
- Operator must run rotation drill **before production cutover**

### 1.3 Automated vs manual (MVP5 scope)

| Secret type | MVP5 (POC) | MVP6+ (Production) |
|---|---|---|
| Postgres, ClickHouse, Redis passwords | Manual `openssl rand` | Vault dynamic secrets (PostgreSQL backend) |
| JWT signing key | Manual + Keycloak realm update | Vault PKI engine + Keycloak automation |
| API keys (Claude, EMQX) | Manual rotation | Vault lease + renewal hook |

**MVP5 limitation**: rotation is **semi-automated** — Makefile target generates new value, writes to KV, but **operator must verify** services pick up the new secret (via vault-agent cache refresh).

---

## 2. Rotation Procedure (per secret)

### 2.1 Standard rotation (non-JWT, non-Keycloak)

**Example: rotate Redis password**

```bash
# Step 1: Generate new credential
make vault-rotate secret=redis

# This runs:
#   docker exec uip-vault vault kv put secret/uip/redis \
#     password=$(openssl rand -base64 32)

# Step 2: Vault agent auto-refreshes within 5 minutes (TTL-based)
# No manual action needed — agent re-renders /vault/secrets/uip.env

# Step 3: Consumer services hot-reload credential
# Redis clients: reconnect with new password within 1 connection cycle

# Step 4: Verify service health
docker exec uip-vault vault kv get secret/uip/redis
curl -f http://localhost:8080/actuator/health  # backend health

# Step 5: Check rotation status
make vault-rotation-status
```

**Expected behavior:**
- KV version increments (e.g., v3 → v4)
- `created_time` updated to rotation timestamp
- Services remain healthy (no 401/500 errors)

### 2.2 Critical secrets (requires coordination)

#### 2.2.1 Postgres password rotation

**Pre-rotation checklist:**
- [ ] Announce maintenance window (5 min)
- [ ] Scale down consumers: backend, analytics-service, keycloak
- [ ] Verify no active connections: `SELECT count(*) FROM pg_stat_activity;`

```bash
# Rotate password
make vault-rotate secret=postgres

# Update PostgreSQL server
docker exec uip-postgres psql -U postgres -c \
  "ALTER USER postgres WITH PASSWORD '<new_password_from_vault>';"

# Restart consumers (vault-agent will inject new password)
docker compose -f docker-compose.yml -f docker-compose.ha.yml restart backend analytics-service keycloak

# Verify
docker logs uip-backend 2>&1 | grep -i "HikariPool.*started"
```

**Rollback window:** 10 minutes. If consumers fail to connect, run `make vault-rollback secret=postgres`.

#### 2.2.2 JWT signing key rotation

**⚠️ CRITICAL:** JWT rotation requires **dual-write** to Vault + Keycloak realm. Mismatch = all JWT validation fails (401 on all API calls).

```bash
# Step 1: Generate new RS256 key pair
openssl genrsa -out jwt-private.pem 2048
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem

# Step 2: Write to Vault
docker exec uip-vault vault kv put secret/uip/jwt \
  private="$(cat jwt-private.pem)" \
  public="$(cat jwt-public.pem)"

# Step 3: Update Keycloak realm "uip" keys (via Admin Console or REST API)
# Manual: Keycloak Admin → Realm Settings → Keys → Add keystore → RS256
# TODO: automate via Keycloak Admin API in MVP6

# Step 4: Restart backend (picks up new key from vault-agent)
docker compose -f docker-compose.yml -f docker-compose.ha.yml restart backend

# Step 5: Smoke test
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}' | jq .token

# Verify token validates
export TOKEN=$(curl -s ... | jq -r .token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/sensors
```

**Rollback:** Revert Keycloak realm key + rollback Vault KV:
```bash
make vault-rollback secret=jwt
# Then revert Keycloak realm key via Admin Console
```

---

## 3. Vault Agent Cache Behavior

### 3.1 Cache TTL + invalidation

From `infrastructure/vault/vault-agent.hcl`:

```hcl
cache {
  use_auto_auth_token = true
  ttl = "5m"  # R6 mitigation: short cache window
}
```

**Invalidation trigger:** After `vault kv put`, agent cache expires within **5 minutes**. No manual flush needed.

### 3.2 Rendered file location

All consumers mount Vault-rendered secrets from:
```
uip-vault-secrets:/vault/secrets/uip.env
```

Services read this file via:
```yaml
env_file:
  - /vault/secrets/uip.env
```

**Freshness check:**
```bash
make vault-verify
# Verifies:
#   - uip.env exists
#   - mtime < 6 minutes (1 min buffer beyond 5 min TTL)
#   - Contains POSTGRES_PASSWORD, JWT_SECRET, CLAUDE_API_KEY
```

---

## 4. Rollback Procedure

Vault KV v2 supports **versioned secrets** — every `kv put` creates a new version, preserving history.

### 4.1 List versions

```bash
docker exec uip-vault vault kv metadata get secret/uip/redis
# Output shows:
#   Version 3 (created_time: 2026-06-01T02:00:00Z)
#   Version 4 (created_time: 2026-06-26T02:00:00Z)  ← current
```

### 4.2 Rollback to previous version

```bash
# Rollback to version N-1
docker exec uip-vault vault kv rollback -version=3 secret/uip/redis

# Verify rollback
docker exec uip-vault vault kv get secret/uip/redis
# Version should now be 5 (rollback creates a new version = copy of v3)
```

### 4.3 Emergency rollback (all secrets)

```bash
# List all paths + current version
make vault-rotation-status

# Rollback each path to previous version
for secret in postgres clickhouse redis kafka keycloak kong minio emqx jwt ai-claude; do
  PREV_VERSION=$(docker exec uip-vault vault kv metadata get secret/uip/$secret | grep "Version" | tail -2 | head -1 | awk '{print $2}')
  docker exec uip-vault vault kv rollback -version=$PREV_VERSION secret/uip/$secret
done

# Restart all consumers
docker compose -f docker-compose.yml -f docker-compose.ha.yml restart
```

**Recovery time objective (RTO):** < 10 minutes for full rollback.

---

## 5. Monitoring & Alerts

### 5.1 Rotation metrics (to be implemented in MVP6)

**Prometheus metrics** (exported by custom vault-exporter):

| Metric | Description | Alert threshold |
|---|---|---|
| `vault_secret_last_rotation_timestamp_seconds` | Last rotation time per path | > 35 days (rotation overdue) |
| `vault_secret_version` | Current version number | N/A (trend only) |
| `vault_agent_cache_hit_rate` | Cache hit rate | < 90% (cache thrashing) |

### 5.2 Manual rotation audit (MVP5)

After each rotation, log to **docs/mvp5/reports/vault-rotation-log.md**:

```markdown
## 2026-06-26 — Redis password rotation

**Operator**: anhgv
**Duration**: 3 minutes
**Downtime**: 0 (hot-reload)
**Outcome**: ✅ Success

Pre-rotation version: 3
Post-rotation version: 4
Services restarted: none (hot-reload)
Verification: `curl http://localhost:8080/actuator/health` → 200 OK
```

---

## 6. Automation Roadmap (MVP6+)

### 6.1 Vault dynamic secrets (PostgreSQL backend)

**Replace static passwords with Vault-generated, time-limited credentials:**

```hcl
# Vault config (MVP6)
vault secrets enable database

vault write database/config/postgresql \
  plugin_name=postgresql-database-plugin \
  allowed_roles="uip-backend" \
  connection_url="postgresql://{{username}}:{{password}}@postgres:5432/uip_prod" \
  username="vault" \
  password="<vault_admin_password>"

vault write database/roles/uip-backend \
  db_name=postgresql \
  creation_statements="CREATE USER '{{name}}' WITH PASSWORD '{{password}}' VALID UNTIL '{{expiration}}';" \
  default_ttl="1h" \
  max_ttl="24h"
```

**Services fetch credentials on startup:**
```bash
export POSTGRES_USER=$(vault read -field=username database/creds/uip-backend)
export POSTGRES_PASSWORD=$(vault read -field=password database/creds/uip-backend)
```

Vault auto-rotates + revokes credentials every 1 hour.

### 6.2 JWT rotation via Vault PKI engine

```bash
# Generate key pair via Vault PKI
vault secrets enable pki
vault write pki/root/generate/internal common_name="uip.local" ttl=8760h

# Backend fetches JWT signing key on startup
vault read -field=certificate pki/issue/jwt ttl=720h common_name="jwt.uip.local"
```

**Keycloak integration:** Vault plugin writes generated key pair directly to Keycloak realm keys API (no manual step).

---

## 7. Makefile Targets (added in M5-2-T09)

```bash
# Rotate a specific secret
make vault-rotate secret=redis

# Show rotation status for all 10 secrets
make vault-rotation-status

# Verify vault-agent cache freshness
make vault-verify
```

---

## 8. References

- **ADR-048** §6: Vault KV v2 + agent architecture
- **M5-1-T03 report**: `docs/mvp5/reports/mvp5-sprint1-vault-secret-audit.md`
- **Vault agent config**: `infrastructure/vault/vault-agent.hcl`
- **Vault init script**: `infrastructure/vault/vault-init.sh`
