# MVP5 Sprint 1 — Vault Secret-Injection Audit (M5-1-T03)

**Date**: 2026-06-24
**Task**: M5-1-T03 (SP=3) — block gate G1
**Author**: DevOps
**ADR**: ADR-048 §6
**Artifacts**: `infrastructure/vault/`, `infrastructure/docker-compose.ha.yml` (services `vault`, `vault-init`, `vault-agent`)

---

## 1. Scope

Audit every plaintext secret referenced by Compose services (`infrastructure/docker-compose.yml` + `infrastructure/docker-compose.ha.yml`) and confirm migration to HashiCorp Vault KV v2 at path `secret/uip/*`.

Sources scanned:
- `infrastructure/.env` (gitignored — values for dev)
- `infrastructure/docker-compose.yml` (base — 836 lines)
- `infrastructure/docker-compose.ha.yml` (HA overlay — CH 2-node + Kafka 3-broker + Vault)
- `infrastructure/.env.staging` (staging overlay — NOT in scope for T03, tracked separately)

Grep signature: `_PASSWORD|_SECRET|_TOKEN|API_KEY|KEYCLOAK_ADMIN|MINIO_ROOT|KONG`.

## 2. Plaintext secrets found

| # | Secret name | Source file(s) | Consumers (service) | Default / example value (redacted) |
|---|---|---|---|---|
| 1 | `POSTGRES_PASSWORD` | `.env`, `docker-compose.yml` (lines 11, 316, 374, 433, 483, 534, 567) | timescaledb, backend, uip-forecast-service, all Flink submitters | `changeme_db_password` |
| 2 | `REPLICATION_PASSWORD` | `docker-compose.ha.yml` (lines 332, 362) | timescaledb (primary), timescaledb-standby | defaults to `POSTGRES_PASSWORD` |
| 3 | `CLICKHOUSE_PASSWORD` | `.env`, `docker-compose.yml` (44, 319, 377, 436, 486, 537, 674), `docker-compose.ha.yml` (103, 148) | clickhouse, clickhouse-01, clickhouse-02, backend, analytics-service, all Flink submitters | (empty in dev) |
| 4 | `REDIS_PASSWORD` | `.env`, `docker-compose.yml` (89, 98, 570) | redis, backend | `changeme_redis_password` |
| 5 | `JWT_SECRET` | `.env`, `docker-compose.yml` (581, 676) | backend, analytics-service | `changeme_jwt_secret_must_be_at_least_256_bits...` |
| 6 | `KEYCLOAK_ADMIN_PASSWORD` | `.env`, `docker-compose.yml` (782), `docker-compose.ha.yml` (332, 362) | keycloak | `admin_Dev#2026!` |
| 7 | `KEYCLOAK_CLIENT_SECRET` | (realm-uip-export.json — not compose) | Kong JWT plugin verification | `uip-api-secret-dev` |
| 8 | `OPERATOR_PASSWORD` / `ADMIN_PASSWORD` / `CITIZEN_PASSWORD` | `docker-compose.yml` (578–580) | backend (test user seeding) | `Operator#2026!`, `Admin#2026!`, `citizen1_Dev#2026!` |
| 9 | `MINIO_ROOT_PASSWORD` | `.env`, `docker-compose.yml` (263, 295, 322, 332, 382, 393, 438, 489, 540) | minio, minio-init, all Flink submitters | `minioadmin` |
| 10 | `MINIO_ROOT_USER` | same as above | same | `minioadmin` |
| 11 | `EMQX_DASHBOARD_PASSWORD` | `.env`, `docker-compose.yml` (200) | emqx | `changeme_emqx_password` |
| 12 | `EMQX_DASHBOARD_USER` | same | same | `admin` |
| 13 | `CLAUDE_API_KEY` | `.env`, `docker-compose.yml` (577) | backend (AI scenario) | `sk-ant-api03-...` |
| 14 | `KONG_JWT_SECRET` | derived from `JWT_SECRET` in compose (line 40 of `.env`) | kong (DB-less, no DB password) | derived |
| 15 | `KAFKA_CLUSTER_ID` | `.env`, `docker-compose.ha.yml` (254, 291) | kafka, kafka-2, kafka-3 | `MkU3OEVBNTcwNTJENDM36Qg` |
| 16 | `KAFKA SASL_*` | (not configured in pilot — PLAINTEXT listener) | — | placeholder empty |

Additional non-compose secrets (secrets/ dir, NOT migrated by T03 — file-based, out of scope):
- `secrets/firebase-adminsdk.json` — FCM push (loaded as file mount)
- `secrets/apns-auth-key.p8` — APNs push (loaded as file mount)

## 3. Migration to Vault KV v2

Each secret was migrated by `vault-init.sh` into KV v2 path `secret/uip/*`. Cache TTL 5m applies uniformly (R6 mitigation).

| Service / domain | Secret name(s) | Source file | Migrated to Vault path | Cache TTL |
|---|---|---|---|---|
| TimescaleDB / PostgreSQL | `POSTGRES_PASSWORD`, `POSTGRES_USER`, `POSTGRES_DB`, `REPLICATION_PASSWORD` | `.env`, `docker-compose.yml` | `secret/uip/postgres` | 5m |
| ClickHouse (HA + single) | `CLICKHOUSE_PASSWORD`, `CLICKHOUSE_USER`, `CLICKHOUSE_DB` | `.env`, `docker-compose.yml`, `docker-compose.ha.yml` | `secret/uip/clickhouse` | 5m |
| Redis | `REDIS_PASSWORD` | `.env`, `docker-compose.yml` | `secret/uip/redis` | 5m |
| Kafka | `KAFKA_CLUSTER_ID`, `SASL_USERNAME`, `SASL_PASSWORD` | `.env`, `docker-compose.ha.yml` | `secret/uip/kafka` | 5m |
| Keycloak | `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_CLIENT_SECRET`, `OPERATOR_PASSWORD`, `ADMIN_PASSWORD`, `CITIZEN_PASSWORD` | `.env`, `docker-compose.yml` | `secret/uip/keycloak` | 5m |
| Kong | `JWT_SECRET` (JWT signing key) | `.env` (derived) | `secret/uip/kong` | 5m |
| JWT (backend + Kong) | `JWT_SECRET`, `JWT_EXPIRATION_MS`, `JWT_REFRESH_EXPIRATION_MS` | `.env`, `docker-compose.yml` | `secret/uip/jwt` | 5m |
| MinIO (S3 checkpoints) | `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` | `.env`, `docker-compose.yml` | `secret/uip/minio` | 5m |
| EMQX | `EMQX_DASHBOARD_USER`, `EMQX_DASHBOARD_PASSWORD` | `.env`, `docker-compose.yml` | `secret/uip/emqx` | 5m |
| AI provider (Claude) | `CLAUDE_API_KEY` | `.env`, `docker-compose.yml` | `secret/uip/ai/claude` | 5m |

**Total secret paths created**: 10 (KV v2).
**Total individual secrets migrated**: 16 (table §2 items 1–13, 15; items 14 + 16 are derived/empty).

## 4. Verification commands

```bash
# A. Compose overlay validates (yaml parse + schema)
docker compose -f infrastructure/docker-compose.yml \
               -f infrastructure/docker-compose.ha.yml config --quiet
# Expected: exit 0, no output.

# B. Bootstrap Vault + render secrets
cd infrastructure && make vault-init
# Expected: "[vault-init] DONE — all secrets pre-loaded into KV v2."

# C. Verify rendered env-file
make vault-verify
# Expected: "OK: uip.env rendered, fresh (<6m), all critical keys present."

# D. List Vault KV paths
make vault-status
# Expected: secret/uip/ lists: ai, clickhouse, emqx, jwt, kafka, keycloak,
#           kong, minio, postgres, redis

# E. Inspect one secret (dev only)
docker compose -f docker-compose.yml -f docker-compose.ha.yml exec \
  vault vault kv get secret/uip/postgres
```

## 5. Remaining plaintext debt (tracked, NOT a T03 blocker)

Per ADR-048 §6.4, `infrastructure/.env` is still the bootstrap source for `vault-init`. The plaintext values must be rotated + the `.env` removed in a follow-up task post-T03. Items:

| Debt item | Owner | Sprint |
|---|---|---|
| Rotate every plaintext value after switching consumers to `env_file: /run/secrets/uip.env` | DevOps | M5-2 |
| Per-service consumer wiring (backend, analytics-service, Flink submitters, etc.) — add `vault-secrets` volume mount + `env_file` directive | Backend + DevOps | M5-2 |
| Remove `POSTGRES_PASSWORD` etc. from `docker-compose.yml` `environment:` blocks once env_file is wired | DevOps | M5-2 |
| External secret store for prod (AWS SM / Vault externally managed) — eliminate `.env` bootstrap | DevOps + SA | MVP6 |

## 6. License confirmation

- `hashicorp/vault:1.15` — **BSL 1.1** (NOT AGPL). Internal use permitted.
- No new AGPL dependency introduced by T03. (Verified: Vault is the only new image; templating uses Vault's built-in consul-template engine, also BSL.)

## 7. Deliverable status

| Deliverable | Status |
|---|---|
| Vault server in `docker-compose.ha.yml` | DONE — `vault:1.15` dev mode, healthcheck, resource limits |
| KV v2 enabled + pre-loaded | DONE — `vault/vault-init.sh`, 10 paths |
| Secret-injection sidecar | DONE — `vault-agent` rendering `uip.env` to named volume `vault-secrets` |
| 5-min in-mem cache (R6) | DONE — `cache { ttl = "5m" }` in `vault-agent.hcl` |
| Audit log (this document) | DONE — §2 + §3 tables |
| `make vault-init` / `vault-verify` runbook | DONE — `infrastructure/Makefile` + ADR-048 §6.6 |
| ADR-048 Vault section | DONE — §6 |
| `docker compose ... config` validates | DONE — `OVERLAY_OK` (verified 2026-06-24) |

**Remaining plaintext in targeted HA-overlay services after wiring**: 0 (once §5 follow-up wiring lands in M5-2). T03 itself delivers the Vault backbone; per-consumer wiring is intentionally deferred to keep the overlay diff reviewable.
