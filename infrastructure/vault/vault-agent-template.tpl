# ─────────────────────────────────────────────────────────────────────────────
# UIP Smart City — Vault Agent template
# Renders KV v2 secrets into a POSIX env-file consumed by services.
#
# Output path: /vault/secrets/uip.env  (mounted read-only into consumers)
# Format: KEY=value  (compatible with docker-compose `env_file:` directive)
#
# Secrets are pulled once by the agent, cached 5m (R6), and re-rendered on
# rotation. Services that need live rotation must re-read the file (e.g. via
# inotify) — Spring Boot apps need a restart for env changes (acceptable for
# pilot; see ADR-048 §6.3 open question on hot-reload).
# ─────────────────────────────────────────────────────────────────────────────

# PostgreSQL / TimescaleDB
POSTGRES_USER={{ with secret "secret/data/uip/postgres" }}{{ .Data.data.username }}{{ end }}
POSTGRES_PASSWORD={{ with secret "secret/data/uip/postgres" }}{{ .Data.data.password }}{{ end }}
POSTGRES_DB={{ with secret "secret/data/uip/postgres" }}{{ .Data.data.db }}{{ end }}
REPLICATION_PASSWORD={{ with secret "secret/data/uip/postgres" }}{{ .Data.data.replication_password }}{{ end }}

# ClickHouse (Tier 2 OLAP — single canonical DB `analytics`)
CLICKHOUSE_USER={{ with secret "secret/data/uip/clickhouse" }}{{ .Data.data.user }}{{ end }}
CLICKHOUSE_PASSWORD={{ with secret "secret/data/uip/clickhouse" }}{{ .Data.data.password }}{{ end }}
CLICKHOUSE_DB={{ with secret "secret/data/uip/clickhouse" }}{{ .Data.data.db }}{{ end }}

# Redis
REDIS_PASSWORD={{ with secret "secret/data/uip/redis" }}{{ .Data.data.password }}{{ end }}

# Keycloak
KEYCLOAK_ADMIN={{ with secret "secret/data/uip/keycloak" }}{{ .Data.data.admin }}{{ end }}
KEYCLOAK_ADMIN_PASSWORD={{ with secret "secret/data/uip/keycloak" }}{{ .Data.data.admin_password }}{{ end }}
KEYCLOAK_CLIENT_SECRET={{ with secret "secret/data/uip/keycloak" }}{{ .Data.data.client_secret }}{{ end }}
OPERATOR_PASSWORD={{ with secret "secret/data/uip/keycloak" }}{{ .Data.data.operator_password }}{{ end }}
ADMIN_PASSWORD={{ with secret "secret/data/uip/keycloak" }}{{ .Data.data.admin_role_password }}{{ end }}
CITIZEN_PASSWORD={{ with secret "secret/data/uip/keycloak" }}{{ .Data.data.citizen_password }}{{ end }}

# Kong JWT signing key
JWT_SECRET={{ with secret "secret/data/uip/kong" }}{{ .Data.data.jwt_secret }}{{ end }}
JWT_EXPIRATION_MS={{ with secret "secret/data/uip/jwt" }}{{ .Data.data.expiration_ms }}{{ end }}
JWT_REFRESH_EXPIRATION_MS={{ with secret "secret/data/uip/jwt" }}{{ .Data.data.refresh_expiration_ms }}{{ end }}

# MinIO (S3 checkpoints)
MINIO_ROOT_USER={{ with secret "secret/data/uip/minio" }}{{ .Data.data.root_user }}{{ end }}
MINIO_ROOT_PASSWORD={{ with secret "secret/data/uip/minio" }}{{ .Data.data.root_password }}{{ end }}

# EMQX dashboard
EMQX_DASHBOARD_USER={{ with secret "secret/data/uip/emqx" }}{{ .Data.data.dashboard_user }}{{ end }}
EMQX_DASHBOARD_PASSWORD={{ with secret "secret/data/uip/emqx" }}{{ .Data.data.dashboard_password }}{{ end }}

# AI provider
CLAUDE_API_KEY={{ with secret "secret/data/uip/ai/claude" }}{{ .Data.data.api_key }}{{ end }}
