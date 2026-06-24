# MVP5 Sprint 1 — Compose HA Runbook (T02)

**Task**: M5-1-T02 (remainder) — runbook + 100 RPS smoke test
**Scope**: HA = ClickHouse 2-node ReplicatedMergeTree + 3-node Keeper quorum + Kafka 3-broker KRaft RF=3. **Out of scope** (per ADR-048): Kong/Keycloak/PostgreSQL HA (defer MVP6).
**Overlay artifact**: `infrastructure/docker-compose.ha.yml`
**Architecture authority**: `docs/mvp5/adr/ADR-048-compose-ha-test-topology.md`

---

## 1. Prerequisites

| Requirement | Minimum | Recommended (pilot) |
|---|---|---|
| Docker Engine | 24.x | 25.x |
| Docker Compose v2 | 2.20+ | 2.24+ |
| RAM | 12 GB free | 16 GB free (HA overlay ~2.5× single-node) |
| CPU | 4 cores free | 8 cores |
| Disk | 10 GB | 30 GB (CH/Kafka volumes) |
| `.env` | populated | `infrastructure/.env` + `infrastructure/.env.security` |

Env vars consumed by the overlay (defaults shown):
```
CLICKHOUSE_DB=analytics
CLICKHOUSE_USER=default
CLICKHOUSE_PASSWORD=                 # leave empty for POC, set for staging
KAFKA_CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qg
POSTGRES_USER=uip
POSTGRES_PASSWORD=...               # REQUIRED (no default — fails closed)
REPLICATION_USER=replicator
REPLICATION_PASSWORD=...            # defaults to POSTGRES_PASSWORD
```

Validate the merged YAML **before** bringing up the stack:
```bash
cd infrastructure
docker compose -f docker-compose.yml -f docker-compose.ha.yml config -q
# exit 0 = valid
```

---

## 2. Topology at a glance

```
ClickHouse (ReplicatedMergeTree, ADR-036)
  ├── clickhouse-01 (replica-01) : HTTP 8125  native 9002
  ├── clickhouse-02 (replica-02) : HTTP 8124  native 9003
  └── Keeper quorum (Raft, majority 2/3)
        ├── clickhouse-keeper    : client 9181  raft 9234
        ├── clickhouse-keeper-02 : client 9182  raft 9234
        └── clickhouse-keeper-03 : client 9183  raft 9234

Kafka (KRaft, RF=3, min.insync.replicas=2, ADR-037)
  ├── kafka    (node 1, broker+controller) : 9092  controller 9093
  ├── kafka-2  (node 2, broker+controller) : 9092
  └── kafka-3  (node 3, broker+controller) : 9092

PostgreSQL streaming replication (manual failover only)
  ├── timescaledb           (primary, RW)         : 5432
  └── timescaledb-standby   (hot standby, RO)     : 5433

Stateless (single-node — pilot-acceptable, ADR-048 §3.2):
  Kong, Keycloak, backend, analytics-service, Flink JM/TM, EMQX, Redis
```

---

## 3. Connection strings

Use these in clients / smoke tests / Flink jobs:

| Service | Connection string |
|---|---|
| ClickHouse HTTP (node 1, host) | `http://localhost:8125` |
| ClickHouse HTTP (node 2, host) | `http://localhost:8124` |
| ClickHouse JDBC (multi-host failover) | `jdbc:clickhouse://clickhouse-01:8123,clickhouse-02:8123/analytics` |
| ClickHouse Native (node 1) | `clickhouse-01:9000` (host `localhost:9002`) |
| Kafka bootstrap (in-cluster) | `kafka:9092,kafka-2:9092,kafka-3:9092` |
| Kafka bootstrap (from host) | `localhost:29092` (only `kafka` advertises host port) |
| PostgreSQL primary | `localhost:5432` |
| PostgreSQL standby (RO) | `localhost:5433` |
| Keeper client (Ruok) | `echo ruok | nc localhost 9181` (or `9182`/`9183`) |

---

## 4. Start order

The overlay encodes correct `depends_on: condition: service_healthy`, so a single `up -d` honors ordering. For manual / debug, the intended order is:

```
1. Keeper quorum (3 nodes)       ← Raft needs majority to elect leader
2. ClickHouse 01 → ClickHouse 02 ← ReplicatedMergeTree needs Keeper
3. Kafka brokers (3)              ← KRaft quorum 2/3 to elect controller
4. kafka-init (creates topics RF=3)
5. timescaledb → timescaledb-standby
6. Redis, EMQX, MinIO
7. Kong, Keycloak
8. backend, analytics-service, Flink submitters
```

**Bring up**:
```bash
cd infrastructure
make up-ha
# equivalent to:
# docker compose -f docker-compose.yml -f docker-compose.ha.yml up -d
```

**Wait for healthy** (blocks up to 5 min, retries every 10 s):
```bash
make ha-health-check ARGS=--wait
# equivalent to:
# ./scripts/ha-health-check.sh --wait
```
Exit 0 = all critical containers healthy. The script checks the 17 critical + 5 optional containers listed in `infrastructure/scripts/ha-health-check.sh`.

---

## 5. Healthcheck verification (post-start, per node)

### 5.1 Keeper quorum — expect majority 2/3 alive

```bash
# Each keeper returns "imok" when in quorum
for p in 9181 9182 9183; do
  printf "keeper :${p} -> "; echo ruok | nc -w 2 localhost ${p} || echo "DOWN"
done
# At least 2 of 3 must respond "imok" for quorum
```

Inside any keeper container:
```bash
docker exec -it uip-clickhouse-keeper clickhouse-keeper-client --host localhost --port 9181 \
  -q "ls /clickhouse"
# Should list ReplicatedMergeTree znodes once CH has registered
```

### 5.2 ClickHouse 2-node — verify replication + lag

```bash
# Both nodes respond to /ping
curl -sf http://localhost:8125/ping   # node 1
curl -sf http://localhost:8124/ping   # node 2

# ReplicatedMergeTree replica status (run on either node):
curl -s 'http://localhost:8125/?query=SELECT+database,table,replica_name,is_leader,is_readonly,is_session_active+FROM+system.replicas+FORMAT+PrettyCompact'

# Replication log queue (absolute_delay = seconds behind leader; target 0-2s)
curl -s 'http://localhost:8125/?query=SELECT+database,table,replica_name,queue_size,absolute_delay+FROM+system.replicas+WHERE+table=%27esg_readings%27+FORMAT+PrettyCompact'

# Replicated table exists on both replicas
curl -s 'http://localhost:8125/?query=SELECT+hostName()+AS+host,database,name,engine+FROM+system.tables+WHERE+name=%27esg_readings%27+FORMAT+PrettyCompact'
```

Pass criteria: `is_readonly=0` on both replicas, `absolute_delay ≤ 5s` after write, both nodes have `ReplicatedReplacingMergeTree` engine.

### 5.3 Kafka 3-broker — verify RF=3, min.insync=2, controller quorum

```bash
# List brokers (must return 3)
docker exec uip-kafka kafka-broker-api-versions --bootstrap-server kafka:9092,kafka-2:9092,kafka-3:9092 \
  | grep -c "ApiVersionsResponse"

# Topic list (RF=3 across all created topics)
docker exec uip-kafka kafka-topics --bootstrap-server kafka:9092,kafka-2:9092,kafka-3:9092 --list

# Inspect replication on a key topic — ReplicationFactor: 3, Isr min 2
docker exec uip-kafka kafka-topics --bootstrap-server kafka:9092 \
  --describe --topic UIP.bms.reading.raw.v2

# Controller quorum alive
docker exec uip-kafka kafka-metadata-quorum --bootstrap-server kafka:9092 \
  --human-readable describe --status
# LeaderId + 3 voters, HighWatermark advances
```

Pass criteria: every topic shows `ReplicationFactor: 3` and `Isr: 1,2,3` (or at least 2 in ISR).

### 5.4 PostgreSQL streaming replication

```bash
# Primary: WAL sender active
docker exec uip-timescaledb psql -U "${POSTGRES_USER:-uip}" -d "${POSTGRES_DB:-uip_smartcity}" \
  -c "SELECT application_name, state, sync_state, sent_lsn, write_lsn, flush_lsn, replay_lsn,
             (sent_lsn - replay_lsn) AS lag_bytes
      FROM pg_stat_replication;"

# Standby: in recovery
docker exec uip-timescaledb-standby psql -U "${POSTGRES_USER:-uip}" -d "${POSTGRES_DB:-uip_smartcity}" \
  -c "SELECT pg_is_in_recovery(), pg_last_wal_receive_lsn(), pg_last_wal_replay_lsn();"
```

Pass criteria: `state = streaming`, `lag_bytes < 1 MB` under write load, standby `pg_is_in_recovery = true`.

### 5.5 Kong auth still active (after every HA bring-up)

```bash
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8000/api/v1/analytics)
[ "${HTTP_CODE}" = "401" ] || { echo "FAIL: Kong auth bypass — got ${HTTP_CODE}"; exit 1; }
```

---

## 6. Stop / drain order

**Graceful drain** (preserve state):
```bash
cd infrastructure
# Stop app layer first (no new writes), then stateful, then quorum
docker compose -f docker-compose.yml -f docker-compose.ha.yml stop \
  backend analytics-service flink-esg-job-submitter flink-structural-job-submitter \
  flink-environment-job-submitter
# Drain Kafka (consumers commit offsets)
docker compose -f docker-compose.yml -f docker-compose.ha.yml stop kafka-3 kafka-2 kafka
# Stop CH nodes before Keeper (so ReplicatedMergeTree flushes)
docker compose -f docker-compose.yml -f docker-compose.ha.yml stop clickhouse-02 clickhouse-01
docker compose -f docker-compose.yml -f docker-compose.ha.yml stop \
  clickhouse-keeper-03 clickhouse-keeper-02 clickhouse-keeper
docker compose -f docker-compose.yml -f docker-compose.ha.yml stop
```

**Full teardown (DESTRUCTIVE — data loss)**:
```bash
# No dedicated Makefile target — run docker compose directly.
docker compose -f docker-compose.yml -f docker-compose.ha.yml down -v --remove-orphans
# (`make down` is the single-node equivalent and is NOT volume-aware for the HA overlay.)
```

---

## 7. Single-node failure simulations

These exercise the HA guarantees that single-node testing cannot catch. Run **after** §5 passes.

### 7.1 Kill 1 Keeper → quorum survives (2/3 majority)

```bash
docker stop uip-clickhouse-keeper-03
# Verify quorum still serves
echo ruok | nc localhost 9181   # "imok"
echo ruok | nc localhost 9182   # "imok"

# CH write still replicates:
curl -s 'http://localhost:8125/?query=INSERT+INTO+analytics.esg_readings+(tenant_id,building_id,source_id,metric_type,value,recorded_at)+VALUES+(\x27hcm\x27,\x27bld-001\x27,\x27smoke-test\x27,\x27power\x27,42.0,now())'
# Read from replica-02 (the other CH node)
curl -s 'http://localhost:8124/?query=SELECT+count()+FROM+analytics.esg_readings+WHERE+source_id=%27smoke-test%27'

# Restore
docker start uip-clickhouse-keeper-03
```
**Pass**: write on node 1 → read on node 2 within ≤5 s; cluster still accepts writes throughout.

### 7.2 Kill 1 Kafka broker → min.insync=2 still serves

```bash
docker stop uip-kafka-3
# Producer still acks with acks=all (min.insync=2 of 3)
docker exec uip-kafka kafka-console-producer --bootstrap-server kafka:9092,kafka-2:9092 \
  --topic UIP.bms.reading.raw.v2 <<EOF
{"v":"smoke","ts":"$(date -u +%FT%TZ)"}
EOF

# Consumer still reads (ISR shrinks to 1,2)
docker exec uip-kafka kafka-topics --bootstrap-server kafka:9092 --describe --topic UIP.bms.reading.raw.v2

# Restore
docker start uip-kafka-3
```
**Pass**: producer does not block; `Isr` line drops to 2 brokers but topic still accepts produce/consume. After restart, ISR returns to 3.

### 7.3 Kill 1 ClickHouse node → analytics-service fails over via JDBC multi-host

```bash
docker stop uip-clickhouse-02
# analytics-service URL is jdbc:clickhouse://clickhouse-01:8123,clickhouse-02:8123/analytics
# Driver transparently retries next host — analytics queries keep returning 200.
curl -s -H "Authorization: Bearer ${JWT}" http://localhost:8080/api/v1/analytics/dashboard
# Restore
docker start uip-clickhouse-02
```

### 7.4 Promote standby (DRILL ONLY — not part of routine smoke)

Manual, no auto-failover (per ADR-048). Documented in `infrastructure/README-ops.md`. Not required for T02 sign-off.

---

## 8. Smoke test — 100 RPS, 60 s, p95 ≤ 500 ms

Script: [`scripts/mvp5_ha_smoke_100rps.py`](../../../scripts/mvp5_ha_smoke_100rps.py)

Targets a read endpoint on the backend (which fans out to CH cluster through analytics-service via Kong). Authenticates once with seed admin credentials, then drives 100 RPS for 60 s using a thread pool.

**Run** (stack already healthy per §4-5):
```bash
# from repo root
python3 scripts/mvp5_ha_smoke_100rps.py \
  --base-url http://localhost:8000 \
  --endpoint /api/v1/environment/sensors/ENV-001/readings \
  --rps 100 --duration 60 \
  --username admin --password 'admin_Dev#2026!'

# alt: via Kong (port 8000) to also exercise gateway path
# alt: via backend directly (port 8080) to isolate app-layer latency
```

Environment-variable form (for CI):
```bash
HA_SMOKE_BASE_URL=http://localhost:8000 \
HA_SMOKE_RPS=100 \
HA_SMOKE_DURATION=60 \
HA_SMOKE_USERNAME=admin \
HA_SMOKE_PASSWORD='admin_Dev#2026!' \
python3 scripts/mvp5_ha_smoke_100rps.py
```

**Pass criteria** (asserted by the script, exits non-zero on failure):
| Metric | Threshold |
|---|---|
| Total requests | ≥ 95% of rps × duration (e.g. ≥ 5700 / 6000) |
| p50 latency | ≤ 200 ms |
| **p95 latency** | **≤ 500 ms** |
| Error rate | ≤ 0.01% (≤ 1 in 10 000) |
| HTTP 4xx/5xx | ≤ 0.01% |

Output: JSON results printed to stdout + saved to `g5-rerun-ha-smoke.json` alongside the existing `g5-rerun.jtl`.

---

## 9. Verify (acceptance for T02 DONE)

Acceptance checklist — all must pass:

```
[ ] docker compose -f docker-compose.yml -f docker-compose.ha.yml config -q   exits 0
[ ] make up-ha followed by make ha-health-check ARGS=--wait                  exits 0
[ ] §5.1: ≥ 2/3 keepers return "imok"
[ ] §5.2: both CH replicas is_readonly=0, absolute_delay ≤ 5s after write
[ ] §5.3: every topic ReplicationFactor=3, Isr ≥ 2
[ ] §5.4: pg_stat_replication state=streaming, lag_bytes < 1MB
[ ] §5.5: curl /api/v1/analytics without token returns 401
[ ] §7.1: kill 1 keeper → write still replicates
[ ] §7.2: kill 1 kafka broker → producer still acks
[ ] §7.3: kill 1 CH node → analytics-service still serves
[ ] §8: 100 RPS smoke — p95 ≤ 500ms, error ≤ 0.01%
```

---

## 10. Reset (single ↔ HA switch)

When switching between single-node `docker-compose.yml` and HA overlay, **volumes must be reset** (per memory `feedback_mvp4_kafka_reset_runbook`). The CH path layout differs (`/clickhouse/tables/{shard}/...`) and Kafka broker assignments change, so leftover metadata causes silent corruption.

```bash
docker compose -f docker-compose.yml -f docker-compose.ha.yml down -v --remove-orphans
# Then bring up desired topology fresh.
# kafka-init will recreate topics with the correct RF for that topology.
# Flink jobs must be re-submitted via the submitter services (see §11).
```

---

## 11. Re-submit Flink jobs after reset

Easily forgotten (per Sprint 4 lessons). After a fresh HA bring-up:
```bash
docker compose -f docker-compose.yml -f docker-compose.ha.yml restart \
  flink-esg-job-submitter \
  flink-structural-job-submitter \
  flink-environment-job-submitter
# Verify jobs are RUNNING:
curl -s http://localhost:8081/jobs | jq '.jobs[] | {id,jid,state}'
```

---

## 12. Defects / notes discovered during T02

1. **No defects** in the existing `docker-compose.ha.yml` topology. YAML validates clean. All services carry `healthcheck`, `deploy.resources.limits`, and named volumes per DevOps standards.
2. **Keeper raft port 9234** is NOT published in `ports:` — this is correct (inter-keeper traffic uses the shared `uip-network`; only the client port 9181 needs host publish). Not a defect.
3. **`kafka` (broker 1) healthcheck** is defined in base `docker-compose.yml` and inherited — no override needed in the HA overlay. Confirmed.
4. Kong/Keycloak remain single-node **by design** (ADR-048 §3.2) — not a defect; flagged for MVP6.
5. **ClickHouse mTLS (T09 / GAP-046)** — `analytics-service` + `backend`
   now connect to CH on `:8443` with mutual TLS. See
   [`mvp5-sprint1-ch-mtls-runbook.md`](./mvp5-sprint1-ch-mtls-runbook.md)
   for cert generation, rotation, and connection verification. Kong does
   NOT call ClickHouse directly; mTLS is wired on the JDBC consumer path.
