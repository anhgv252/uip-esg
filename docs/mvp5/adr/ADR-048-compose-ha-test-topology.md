# ADR-048: Compose HA Test Topology (overlay) — CH ReplicatedMergeTree + Kafka KRaft

**Date**: 2026-06-24
**Status**: Accepted
**Priority**: P1 (MVP5 pilot readiness — môi trường test phải phản ánh topology HA thật)
**Sprint**: M5-1
**Author**: Solution Architect
**Related**: ADR-036 (CH ReplicatedMergeTree HA), ADR-037 (Kafka 3-broker KRaft), ADR-047 (CH RowPolicy tenant isolation), ADR-050 (K8s readiness-only)
**Artifact**: `infrastructure/docker-compose.ha.yml` (HA overlay)

---

## 1. Context

### 1.1 Business driver

MVP5 pilot 2-3 building + chuẩn bị commercial 10-50 building. Môi trường **test phải phản ánh topology HA** mà production sẽ chạy — nếu test chỉ trên single-node, các lỗi failover/replication (RF, quorum, replica lag) sẽ không phát hiện đến khi production.

Gate **M5-G1** yêu cầu: "Compose HA sẵn sàng test 2-3 bldg".

### 1.2 Current state (verified by artifact review 2026-06-24)

| Component | Single-node (`docker-compose.yml`) | HA overlay (`docker-compose.ha.yml`) | ADR |
|---|---|---|---|
| ClickHouse | 1 server + 1 keeper | **2 server ReplicatedMergeTree + 3 keeper quorum** | ADR-036 |
| Kafka | 1 broker KRaft | **3 broker KRaft quorum, RF=3, min.insync.replicas=2** | ADR-037 |
| Kong | 1 instance | **1 instance (single-node)** | — (see §3.2) |
| Keycloak | 1 instance | **1 instance (single-node)** | — (see §3.2) |
| PostgreSQL | 1 instance | 1 instance (single-node) | — (see §3.2) |

`docker-compose.ha.yml` là **overlay** (`docker compose -f docker-compose.yml -f docker-compose.ha.yml up`): override CH/Kafka sang HA, giữ lại toàn bộ service business (backend, analytics-service, Flink submitters) + wire chúng tới cluster endpoints.

## 2. Decision

### 2.1 HA scope cho pilot (MVP5)

**HA = CH + Kafka only.** Lý do:

1. **Data durability là rủi ro #1** ở pilot — mất dữ liệu sensor/alert/audit = mất tín nhiệm customer. CH ReplicatedMergeTree + Kafka RF=3 cover được.
2. **Stateless gateway/IdP (Kong/Keycloak/backend) có thể single-node** ở pilot vì:
   - Failover = restart container (seconds), không mất state
   - Backend/analytics-service là Spring Boot stateless (session ngoài Redis)
   - Volume traffic pilot (2-3 building) thấp, single Kong instance đủ headroom
3. **DR (Disaster Recovery) DEFER MVP6+** — PO decision đã chốt: pilot single-region, no DR.

### 2.2 Topology cụ thể (artifact: `docker-compose.ha.yml`)

**ClickHouse cluster** (`test_cluster`, ReplicatedMergeTree):
- `clickhouse-01` (replica-01) + `clickhouse-02` (replica-02) — 2 node, cùng shard, ReplicatedMergeTree
- `clickhouse-keeper` + `clickhouse-keeper-02` + `clickhouse-keeper-03` — 3-node Raft quorum (chịu 1 failure, majority 2/3)
- analytics-service + backend + Flink submitters connect qua JDBC URL multi-host: `jdbc:clickhouse://clickhouse-01:8123,clickhouse-02:8123/analytics` (driver failover tự động)

**Kafka cluster** (KRaft, no Zookeeper):
- `kafka` + `kafka-2` + `kafka-3` — 3 broker, mỗi broker = `broker,controller`
- `KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093,2@kafka-2:9093,3@kafka-3:9093`
- All topics: `replication.factor=3`, `min.insync.replicas=2` (chịu 1 broker failure)
- backend/Flink connect: `kafka:9092,kafka-2:9092,kafka-3:9092`

### 2.3 Tenant isolation trên HA topology

ADR-047 (CH RowPolicy) **tương thích HA**: row policy + `analytics_policy` user áp dụng cluster-wide (DDL replicate qua keeper). `RowPolicyEngine.executeWithTenant` SET `tenant_id` per-connection — hoạt động trên cả 2 replica vì query routing đi qua cùng policy user.

## 3. Consequences

### 3.1 Positive

- Test phát hiện RF/quorum/replica-lag bug **trước** production
- CH/Kafka topology test = subset production → migration MVP6 (K8s) chỉ thay orchestrator, không thay topology
- Overlay design: single-node dev vẫn khả thi (`docker compose up`), HA opt-in

### 3.2 Negative / accepted risk

| Gap | Risk | Mitigation / Defer |
|---|---|---|
| **Kong single-node** | Gateway SPOF — Kong down = toàn API down | Pilot: restart nhanh, acceptable. **MVP6**: Kong HA (2 instance + upstream health) khi scale 10+ bldg |
| **Keycloak single-node** | IdP SPOF — Keycloak down = login fail, token refresh fail | Pilot: restart nhanh. Keycloak PG backend là single-node. **MVP6**: Keycloak HA (JDBC shared store + 2 instance) |
| **PostgreSQL single-node** | DB SPOF — mất metadata tenant/building/rule | Pilot: backup script (M5-5-T08). **MVP6**: PG primary-replica + Patroni hoặc managed DB |
| **No DR** | Toàn region down = mất dữ liệu từ last backup | PO decision: defer MVP6+. Pilot single-region |

### 3.3 Operational

- **Reset runbook**: khi switch single↔HA, phải reset volume (xóa CH data + Kafka recreate topics) — xem memory `feedback_mvp4_kafka_reset_runbook`
- **Resource**: HA overlay cần ~2.5× RAM single-node (2 CH + 3 keeper + 3 Kafka). CI/test máy dev cần ≥16GB
- **Smoke test**: M5-1-T02 deliverable yêu cầu smoke 100 RPS — runbook trong `docs/mvp5/deployment/`

## 4. Alternatives considered

1. **Full HA (Kong + Keycloak + PG cũng HA)** — rejected: chi phí vận hành pilot cao, SPOF stateless có thể che bằng restart, PO chốt single-region no-DR.
2. **Test trên production-like K8s** — rejected: ADR-050 K8s readiness-only, defer cutover MVP6. Compose đủ cho pilot test.
3. **CH Keeper 1-node** — rejected: Raft cần ≥3 node để majority, 1-node = không HA thật.

## 5. Open questions / Follow-up

- **M5-1-T02 chưa DONE**: `docker-compose.ha.yml` đã có CH+Kafka HA, nhưng thiếu (a) verify Kong/Keycloak wiring trong overlay, (b) start/runbook, (c) smoke 100 RPS recording. ADR-048 định nghĩa topology; T02 phải deliver runbook + smoke.
- **Kong/Keycloak HA**: defer MVP6 — cần ADR mới khi triển khai (out of MVP5 scope).

---

## 6. Vault Secret Injection (M5-1-T03, block gate G1)

**Status**: Accepted 2026-06-24
**Task**: M5-1-T03 (SP=3)
**Artifact**: `vault/` directory + `docker-compose.ha.yml` services `vault`, `vault-init`, `vault-agent`
**Audit report**: `docs/mvp5/reports/mvp5-sprint1-vault-secret-audit.md`

### 6.1 Decision

Replace plaintext `.env`-sourced secrets in HA-overlay services with HashiCorp Vault KV v2, injected via a `vault-agent` sidecar that renders secrets to a shared named volume as a POSIX env-file. Three new services in `docker-compose.ha.yml`:

| Service | Role |
|---|---|
| `vault` | Vault 1.15 dev server — single node, in-mem storage, auto-unsealed, root token `root`. DR deferred to MVP6 (single-region pilot per PO decision). |
| `vault-init` | One-shot bootstrap (`restart: "no"`): waits for Vault, enables KV v2 at `secret/`, writes 10 secret paths. Idempotent — safe to re-run. |
| `vault-agent` | Long-running sidecar: authenticates to Vault, pulls KV secrets, renders `/vault/secrets/uip.env`, serves cached values for 5m. |

Consuming services mount the `vault-secrets` named volume read-only at `/run/secrets` and use `env_file: /run/secrets/uip.env`. Wiring each consumer is opt-in per service (small overlay diff; tracked in audit report).

### 6.2 License

HashiCorp Vault 1.15 ships under **BSL 1.1** (Business Source License), **NOT AGPL**. BSL permits internal production use without open-sourcing dependent code; the only restriction is offering Vault-as-a-service to third parties, which UIP does not do. Acceptable for internal UIP pilot + commercial rollout. No AGPL dependency introduced — verified: `hashicorp/vault:1.15` is the only new image, BSL-only.

### 6.3 R6 mitigation — 5-minute in-memory cache

Requirement R6 mandates services survive brief Vault unavailability. The `vault-agent` config (`vault/vault-agent.hcl`) declares:

```hcl
cache {
  use_auto_auth_token = true
  ttl = "5m"   # 5-minute TTL
}
```

Behavior under Vault outage:
- **0–5 min**: agent serves cached secret values; services continue normally.
- **>5 min**: agent attempts renewal on each TTL expiry. If Vault still unreachable, the last-good rendered `uip.env` remains on disk (shared volume persists) and services keep reading it — stale-but-available is preferred over hard failure for pilot.
- **Restoration**: when Vault returns, agent re-renders `uip.env` atomically (`perms = 0600`, atomic rename); services that re-read the file pick up new values. Spring Boot apps need restart for env changes (acceptable for pilot; hot-reload is an open question — §6.5).

The vault-agent healthcheck validates freshness: `find /vault/secrets/uip.env -mmin -6` fails if the file is older than 6 minutes (5m TTL + 1m slack), catching a stuck renderer early.

### 6.4 Single-node accepted risk

| Concern | Pilot (MVP5) | MVP6+ target |
|---|---|---|
| Vault SPOF | Single dev node, in-mem storage. Restart = secrets re-loaded from `vault-init`. | Raft storage cluster (3 node), AppRole/JWT auth, sealed at rest. |
| Root token | Hardcoded `root` (dev mode) | Short-lived tokens via AppRole/K8s service-account auth. |
| Plaintext source | `infrastructure/.env` still holds plaintext values for `vault-init` to read at bootstrap | External secret store (AWS SM / Vault externally managed), `.env` removed. |

These are accepted risks for the pilot topology — they mirror the same single-node posture already accepted for Kong/Keycloak/PG in §3.2. DR + HA Vault is a **MVP6** follow-up.

### 6.5 Open questions / follow-up

- **Hot-reload**: Spring Boot reads env at startup; secret rotation needs a container restart. Evaluate Spring Cloud Vault or file-watch reload for commercial rollout.
- **`.env` cleanup**: `vault-init` still reads plaintext values from `infrastructure/.env`. A follow-up task (post-T03) must rotate each value and either delete `.env` or replace with non-sensitive config. The audit report tracks this debt.
- **Consumer wiring**: not every service is switched to `env_file: /run/secrets/uip.env` in T03 — the overlay keeps the diff minimal. Per-service migration is tracked in the audit table.

### 6.6 Runbook

```bash
# 1. Start Vault + bootstrap KV v2 + render secrets
make vault-init

# 2. Verify
make vault-verify          # checks uip.env freshness + key presence
make vault-status          # Vault seal status + KV path listing

# 3. Inspect a secret (dev only)
docker compose -f docker-compose.yml -f docker-compose.ha.yml exec \
  vault vault kv get secret/uip/postgres
```

Verification commands are documented in `docs/mvp5/reports/mvp5-sprint1-vault-secret-audit.md` §4.
