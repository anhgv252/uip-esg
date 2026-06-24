#!/bin/sh
# ─────────────────────────────────────────────────────────────────────────────
# UIP Smart City — Vault bootstrap (KV v2 pre-load)
# Task M5-1-T03 — block gate G1
#
# Enables KV v2 secrets engine at secret/ and pre-loads every plaintext secret
# previously sourced from infrastructure/.env into docker-compose services.
#
# Run inside the vault-init container (image: hashicorp/vault:1.15).
# Safe to re-run: `vault kv put` overwrites the path idempotently.
#
# License note: HashiCorp Vault 1.15 is BSL 1.1 (not AGPL). Acceptable for
# internal UIP use. See ADR-048 §6.
# ─────────────────────────────────────────────────────────────────────────────
set -eu

# Wait until Vault is listening and unsealed.
echo "[vault-init] waiting for Vault API on ${VAULT_ADDR}..."
i=0
until vault status -format=json >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "[vault-init] ERROR: Vault not reachable after 60s" >&2
    exit 1
  fi
  sleep 1
done
echo "[vault-init] Vault reachable."

# Dev-mode server auto-unseals + mounts a root token at /vault/file or in-mem.
# The vault service sets VAULT_DEV_ROOT_TOKEN_ID=root in dev mode.
export VAULT_TOKEN="${VAULT_TOKEN:-root}"

# 1. Enable KV v2 at secret/ (idempotent — ignore "path already in use")
echo "[vault-init] enabling KV v2 at secret/ ..."
vault secrets enable -path=secret -version=2 kv >/dev/null 2>&1 || \
  echo "[vault-init] KV v2 already enabled at secret/ — skipping."

# ─────────────────────────────────────────────────────────────────────────────
# 2. Pre-load secrets. Values come from env vars populated by the vault-init
#    service in docker-compose.ha.yml (sourced from infrastructure/.env).
#    Every put creates a new KV v2 version — auditable via vault kv metadata.
# ─────────────────────────────────────────────────────────────────────────────

# PostgreSQL / TimescaleDB
vault kv put secret/uip/postgres \
  username="${POSTGRES_USER:-uip}" \
  password="${POSTGRES_PASSWORD}" \
  db="${POSTGRES_DB:-uip_smartcity}" \
  replication_password="${REPLICATION_PASSWORD:-${POSTGRES_PASSWORD}}" >/dev/null
echo "[vault-init] wrote secret/uip/postgres"

# ClickHouse (HA cluster uses shared credentials — ADR-036)
vault kv put secret/uip/clickhouse \
  user="${CLICKHOUSE_USER:-default}" \
  password="${CLICKHOUSE_PASSWORD:-}" \
  db="${CLICKHOUSE_DB:-analytics}" >/dev/null
echo "[vault-init] wrote secret/uip/clickhouse"

# Redis
vault kv put secret/uip/redis \
  password="${REDIS_PASSWORD}" >/dev/null
echo "[vault-init] wrote secret/uip/redis"

# Kafka (no native auth in pilot — credential placeholder for SCRAM upgrade)
vault kv put secret/uip/kafka \
  cluster_id="${KAFKA_CLUSTER_ID:-MkU3OEVBNTcwNTJENDM36Qg}" \
  sasl_username="" \
  sasl_password="" >/dev/null
echo "[vault-init] wrote secret/uip/kafka"

# Keycloak admin + realm client
vault kv put secret/uip/keycloak \
  admin="${KEYCLOAK_ADMIN:-admin}" \
  admin_password="${KEYCLOAK_ADMIN_PASSWORD}" \
  client_id="uip-api" \
  client_secret="${KEYCLOAK_CLIENT_SECRET:-uip-api-secret-dev}" \
  operator_password="${OPERATOR_PASSWORD:-Operator#2026!}" \
  admin_role_password="${ADMIN_PASSWORD:-Admin#2026!}" \
  citizen_password="${CITIZEN_PASSWORD:-citizen1_Dev#2026!}" >/dev/null
echo "[vault-init] wrote secret/uip/keycloak"

# Kong (DB-less — no DB password; JWT signing key is the sensitive value)
vault kv put secret/uip/kong \
  jwt_secret="${JWT_SECRET}" >/dev/null
echo "[vault-init] wrote secret/uip/kong"

# MinIO (S3 checkpoints — ADR: Flink checkpoints MUST be on S3/MinIO, never disk)
vault kv put secret/uip/minio \
  root_user="${MINIO_ROOT_USER:-minioadmin}" \
  root_password="${MINIO_ROOT_PASSWORD:-minioadmin}" >/dev/null
echo "[vault-init] wrote secret/uip/minio"

# EMQX MQTT broker dashboard
vault kv put secret/uip/emqx \
  dashboard_user="${EMQX_DASHBOARD_USER:-admin}" \
  dashboard_password="${EMQX_DASHBOARD_PASSWORD}" >/dev/null
echo "[vault-init] wrote secret/uip/emqx"

# JWT signing key (HMAC-SHA256 — shared by backend + Kong)
vault kv put secret/uip/jwt \
  secret="${JWT_SECRET}" \
  expiration_ms="${JWT_EXPIRATION_MS:-900000}" \
  refresh_expiration_ms="${JWT_REFRESH_EXPIRATION_MS:-604800000}" >/dev/null
echo "[vault-init] wrote secret/uip/jwt"

# AI provider API keys
vault kv put secret/uip/ai/claude \
  api_key="${CLAUDE_API_KEY:-}" >/dev/null
echo "[vault-init] wrote secret/uip/ai/claude"

# ─────────────────────────────────────────────────────────────────────────────
# 3. AppRole auth for vault-agent (REQUIRED — template block needs auto_auth
#    to source its token; static vault.token does NOT drive templates.
#    Verified M5-2-D2.)
# The role_id + secret_id are written to the shared vault-secrets volume at
# /vault/approle/ so vault-agent can read them. Consumers mount the SAME
# volume at /run/secrets but only read /run/secrets/uip.env (perms 0600
# owned by root); the approle creds live in a sibling dir and are also
# 0600 — services must NOT read them.
# ─────────────────────────────────────────────────────────────────────────────
echo "[vault-init] configuring AppRole auth for vault-agent..."
vault auth enable approle >/dev/null 2>&1 || \
  echo "[vault-init] approle already enabled — skipping enable."

# Policy: read-only on all UIP KV v2 secrets + metadata (for list).
vault policy write uip-agent - >/dev/null <<'POL'
path "secret/data/uip/*"   { capabilities = ["read"] }
path "secret/metadata/uip/*" { capabilities = ["list", "read"] }
POL

# Role: short-lived tokens, auto-renewed by agent. secret_id_ttl 24h is
# plenty for a sidecar that re-auths on token expiry (token_ttl 1h).
vault write auth/approle/role/uip-agent \
  secret_id_ttl=24h \
  secret_id_num_uses=0 \
  token_ttl=1h \
  token_max_ttl=4h \
  token_policies=uip-agent >/dev/null

ROLE_ID="$(vault read -field=role_id auth/approle/role/uip-agent/role-id)"
SECRET_ID="$(vault write -f -field=secret_id auth/approle/role/uip-agent/secret-id)"

# Stash creds on the shared volume so vault-agent can read them via
# role_id_file_path / secret_id_file_path (approle method requires files).
APPROLE_DIR="/vault/approle"
mkdir -p "${APPROLE_DIR}"
printf '%s' "${ROLE_ID}"    > "${APPROLE_DIR}/role_id"
printf '%s' "${SECRET_ID}"  > "${APPROLE_DIR}/secret_id"
chmod 0600 "${APPROLE_DIR}/role_id" "${APPROLE_DIR}/secret_id"
echo "[vault-init] AppRole configured — creds at ${APPROLE_DIR}/ (role_id + secret_id)"

echo "[vault-init] DONE — all secrets pre-loaded into KV v2 + AppRole for agent."
echo "[vault-init] Verify: docker compose -f docker-compose.yml -f docker-compose.ha.yml exec vault vault kv list secret/uip/"
