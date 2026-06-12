#!/usr/bin/env bash
# ============================================================
# UIP Smart City — Generate strong passwords for staging/prod
#
# Usage:
#   ./scripts/generate-passwords.sh > .env.generated
#   Then merge into .env.staging or .env.production
#
# SECURITY: Never commit the generated file to git.
#           .env.generated is in .gitignore
# ============================================================

set -euo pipefail

gen_password() {
  # 32-char alphanumeric + special chars
  openssl rand -base64 32 | tr -d '\n' | head -c 32
}

gen_hex() {
  # 64-char hex string (for JWT secrets)
  openssl rand -hex 32
}

cat <<EOF
# ============================================================
# UIP Smart City — Auto-generated passwords
# Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
# SECURITY: DO NOT commit this file to git
# ============================================================

# -- PostgreSQL --
POSTGRES_PASSWORD=$(gen_password)

# -- Redis --
REDIS_PASSWORD=$(gen_password)

# -- Kafka --
KAFKA_CLUSTER_ID=\$(python3 -c "import base64,uuid; print(base64.b64encode(uuid.uuid4().bytes).decode().rstrip('='))")

# -- EMQX --
EMQX_DASHBOARD_PASSWORD=$(gen_password)

# -- Keycloak --
KEYCLOAK_ADMIN_PASSWORD=$(gen_password)

# -- JWT --
JWT_SECRET=$(gen_hex)

# -- MinIO --
MINIO_ROOT_USER=uip-minio
MINIO_ROOT_PASSWORD=$(gen_password)

# -- Grafana --
GRAFANA_ADMIN_PASSWORD=$(gen_password)

# -- Application users (pilot defaults) --
ADMIN_PASSWORD=$(gen_password)
OPERATOR_PASSWORD=$(gen_password)
CITIZEN_PASSWORD=$(gen_password)
EOF
