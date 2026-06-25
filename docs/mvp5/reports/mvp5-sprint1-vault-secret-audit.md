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

Per ADR-048 §6.4, `infrastructure/.env` is still the bootstrap source for `vault-init`. The plaintext values must be rotated + the `.env` removed in a follow-up task. Items:

| Debt item | Owner | Sprint | Status |
|---|---|---|---|
| Per-service consumer wiring via entrypoint wrapper (see §8) | DevOps | M5-2 | **D2 PARTIAL — 2 of ~10 services wired (analytics-service, backend)** |
| Rotate every plaintext value after switching ALL consumers to Vault | DevOps | M5-3 | Blocked on consumer wiring completion |
| Remove `POSTGRES_PASSWORD` etc. from `docker-compose.yml` `environment:` blocks once wrapper is wired for ALL consumers | DevOps | M5-3 | Blocked on consumer wiring completion |
| External secret store for prod (AWS SM / Vault externally managed) — eliminate `.env` bootstrap | DevOps + SA | MVP6 | Open |

### 5.1 Consumer-wiring status (M5-2-D2, updated 2026-06-25)

| Service | Wired to Vault? | Secrets sourced from Vault | Notes |
|---|---|---|---|
| **analytics-service** | YES (D2) | CLICKHOUSE_USER, CLICKHOUSE_PASSWORD, CLICKHOUSE_DB, JWT_SECRET | Verified: health 200, wrapper sourced 26 vars, PID 1 environ confirms Vault values |
| **backend** | YES (D2) | SPRING_DATASOURCE_USERNAME/PASSWORD, SPRING_DATA_REDIS_PASSWORD, JWT_SECRET, CLAUDE_API_KEY, OPERATOR/ADMIN/CITIZEN_PASSWORD | Verified: health 200, admin login returns JWT, PID 1 environ confirms Vault values (Admin#2026!) |
| keycloak | NO (debt) | KEYCLOAK_ADMIN_PASSWORD | Reads env at JVM start; needs same wrapper pattern. Pre-seeded realm users keep it working today. |
| redis | NO (debt) | REDIS_PASSWORD | Requires `redis-server ... --requirepass ${REDIS_PASSWORD}` from mounted config; defer to M5-3. |
| timescaledb / standby | NO (debt) | POSTGRES_PASSWORD, REPLICATION_PASSWORD | DB engine reads pwd at init; runtime rotation not supported without re-init. Defer. |
| clickhouse / clickhouse-01 / -02 | NO (debt) | CLICKHOUSE_PASSWORD | Same as PG — engine-level pwd. Defer. |
| minio | NO (debt) | MINIO_ROOT_USER, MINIO_ROOT_PASSWORD | MinIO reads root creds at startup from env; wrapper-compatible but low priority (S3 checkpoints). |
| emqx | NO (debt) | EMQX_DASHBOARD_USER/PASSWORD | EMQX dashboard creds; low priority. |
| uip-forecast-service | NO (debt) | POSTGRES_PASSWORD | Same wrapper pattern as backend; small service. |
| flink-*-submitter | NO (debt) | CLICKHOUSE_*, POSTGRES_*, MINIO_* | One-shot jobs; wrapper-compatible. |

**Why D2 stopped at 2 services**: the entrypoint-wrapper pattern is proven stable (both Spring Boot services boot healthy, real API calls succeed, PID 1 environ confirms Vault values are authoritative over compose `environment:`). The remaining services fall into two groups: (a) JVM/Java services that could use the same wrapper with ~15 min each of verification (forecast-service, flink submitters), and (b) engine services (PG, CH, Redis, MinIO, EMQX, Keycloak) that read secrets at process init in non-env ways and need per-engine investigation. Group (a) is low-risk follow-up; group (b) is M5-3 scope.

## 6. License confirmation

- `hashicorp/vault:1.15` — **BSL 1.1** (NOT AGPL). Internal use permitted.
- No new AGPL dependency introduced by T03. (Verified: Vault is the only new image; templating uses Vault's built-in consul-template engine, also BSL.)

## 7. Deliverable status

| Deliverable | Status |
|---|---|
| Vault server in `docker-compose.ha.yml` | DONE — `vault:1.15` dev mode, healthcheck (http addr fix D2), resource limits |
| KV v2 enabled + pre-loaded | DONE — `vault/vault-init.sh`, 10 paths |
| AppRole auth for vault-agent | DONE (D2) — `vault-init.sh` provisions `uip-agent` role + writes role_id/secret_id; required because the agent `template` block needs `auto_auth` (static `vault.token` does NOT drive templates) |
| Secret-injection sidecar | DONE — `vault-agent` rendering `uip.env` to named volume `vault-secrets` (AppRole auto_auth fix D2) |
| 5-min in-mem cache (R6) | DONE — token renewed by auth handler (ttl 1h, renew @90%); rendered file persists on disk during brief Vault unavailability |
| Audit log (this document) | DONE — §2 + §3 tables |
| `make vault-init` / `vault-verify` runbook | DONE — `infrastructure/Makefile` + ADR-048 §6.6 |
| ADR-048 Vault section | DONE — §6 |
| `docker compose ... config` validates | DONE — `OVERLAY_OK` (verified 2026-06-25) |
| Consumer wiring (M5-2-D2) | PARTIAL — analytics-service + backend wired + verified end-to-end; 8 services remain as debt (§5.1) |

**Remaining plaintext in WIRED HA-overlay services (analytics-service, backend)**: 0 — secrets come from Vault via the entrypoint wrapper. Other services still read plaintext from compose `environment:` (debt, §5.1).

## 8. Consumer-wiring pattern (M5-2-D2)

### Why not `env_file:`

docker-compose `env_file:` is resolved by the **compose client on the host** at config time — it reads the file from the host filesystem, NOT from inside a mounted named volume. Pointing `env_file: /run/secrets/uip.env` at a path inside the `vault-secrets` volume fails with `env file /run/secrets/uip.env not found` because the host has no such file. (Verified D2.)

### Entrypoint-wrapper pattern

The working pattern for Vault-rendered env-files in docker-compose:

1. `vault-init.sh` copies `vault-env-wrapper.sh` to the `vault-secrets` volume root.
2. `vault-agent` renders `uip.env` to the same volume.
3. Each consumer service overrides its entrypoint to the wrapper and passes the original command:

```yaml
service-name:
  entrypoint: ["/run/secrets/vault-env-wrapper.sh"]
  command: ["java", "-XX:+UseContainerSupport", ..., "-jar", "app.jar"]
  environment:
    # NON-SECRET wiring only (URLs, hosts, profile). Secret keys are dropped
    # so the wrapper-sourced vars are authoritative.
    SOME_URL: http://example:8080
  volumes:
    - vault-secrets:/run/secrets:ro
  depends_on:
    vault-agent:
      condition: service_healthy
```

The wrapper (`infrastructure/vault/vault-env-wrapper.sh`) sources `/run/secrets/uip.env` (POSIX KEY=value, comments + quoting handled), exports each var, then `exec`s the original command. Because the wrapper runs AFTER docker-compose sets `environment:`, wrapper-sourced vars override compose env — making Vault authoritative for the cut-over keys.

### Adding a new consumer

1. Ensure the secret keys the service needs are in `vault/vault-agent-template.tpl` (and the corresponding `vault kv put` in `vault-init.sh`). For Spring Boot services, emit BOTH the raw name (e.g. `POSTGRES_PASSWORD`) AND the Spring-form alias (`SPRING_DATASOURCE_PASSWORD`) — see the template's Spring Boot section.
2. In `docker-compose.ha.yml`, add to the service's override block:
   - `entrypoint: ["/run/secrets/vault-env-wrapper.sh"]`
   - `command: [<original-entrypoint-and-args>]`
   - `volumes: [- vault-secrets:/run/secrets:ro]`
   - `depends_on: [vault-agent: {condition: service_healthy}]`
3. REMOVE the secret keys from the service's `environment:` block (base compose or earlier overlay) so the wrapper is the source of truth. Leave non-secret wiring (URLs, hosts, profile, issuer URLs).
4. `docker compose ... up -d <service>`, then verify:
   - `docker inspect` health = healthy
   - `docker logs <service> | grep vault-env-wrapper` shows "sourced N vars"
   - A real API call returns 200 (not just container-started — D1 lesson)
   - `docker exec <service> cat /proc/1/environ | tr '\0' '\n' | grep <SECRET_KEY>` confirms the Vault value (NOT the host `.env` value) is in the Java process env

### Engine services (deferred to M5-3)

PostgreSQL, ClickHouse, Redis, MinIO, EMQX, Keycloak read secrets at process init in engine-specific ways (not via env-file sourcing). These need per-engine patterns (e.g. Redis `--requirepass` from a mounted config, PG `pg_hba.conf` + init script). Tracked in §5.1.
