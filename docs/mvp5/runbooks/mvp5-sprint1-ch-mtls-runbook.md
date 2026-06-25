# MVP5 Sprint 1 — ClickHouse mTLS Runbook (T09 / GAP-046)

**Task**: M5-1-T09 — ClickHouse TLS mTLS between JDBC consumers and the 2-node
ClickHouse HA cluster (carry-over GAP-046).
**Scope**: transport-layer encryption + mutual authentication for the
analytics data path. **Single-region only** (DR deferred MVP6 per ADR-048).
**Out of scope**: ClickHouse RowPolicy tenant isolation (V32 — orthogonal,
operates at SQL layer), Kafka/Flink TLS (separate tasks).

---

## 1. What is mTLS-wired and what is not

| Component | Calls ClickHouse? | mTLS-wired (T09)? | Notes |
|---|---|---|---|
| `analytics-service` | Yes (JDBC, primary consumer) | **YES** | Switched to `:8443` + client cert |
| `backend` (Spring monolith) | Yes (JDBC, analytics fan-out) | **YES** | Switched to `:8443` + client cert |
| `flink-esg-job-submitter` | Yes (JDBC at submit time) | NO — deferred | See §5 follow-up |
| `flink-structural-job-submitter` | Yes (JDBC at submit time) | NO — deferred | See §5 follow-up |
| `forecast-service` | Yes (HTTP :8123 native client) | NO — deferred | Uses `CLICKHOUSE_HOST/PORT`, not JDBC URL — needs separate driver-level TLS config |
| **Kong** | **NO** | N/A | Kong terminates client JWT; it never touches ClickHouse. The T09 task brief considered Kong→CH mTLS but verified there is **no direct Kong→CH path** — Kong routes to `analytics-service`/`backend`, which are the actual CH consumers. |

**Conclusion**: T09 wires the actual JDBC consumers (`analytics-service` +
`backend`) for the encrypted + authenticated analytics data path. Flink and
forecast follow-up is tracked in §5.

---

## 2. Topology

```
JDBC consumer (analytics-service / backend)
   │  presents client.crt (CN=uip-jdbc-client, signed by uip-internal-ca)
   │  jdbc:clickhouse://clickhouse-01:8443,clickhouse-02:8443/analytics
   │         ?ssl=true&sslrootcert=...&sslcert=...&sslkey=...
   ▼
ClickHouse :8443 (HTTPS)  /  :9440 (native secure)
   │  presents server.crt (SAN=clickhouse-01,clickhouse-02)
   │  verifies client cert against ca.crt => mTLS
   ▼
analytics.esg_readings (ReplicatedMergeTree, RowPolicy V32 still applies)
```

Cert material layout (host path → container mount):

| Host file | Container path | Mounted on |
|---|---|---|
| `infrastructure/clickhouse/tls/ca.crt` | `/etc/clickhouse-server/tls/ca.crt` | clickhouse-01, clickhouse-02 |
| `infrastructure/clickhouse/tls/server.crt` | `/etc/clickhouse-server/tls/server.crt` | clickhouse-01, clickhouse-02 |
| `infrastructure/clickhouse/tls/server.key` | `/etc/clickhouse-server/tls/server.key` | clickhouse-01, clickhouse-02 |
| `infrastructure/clickhouse/tls/client.crt` | `/etc/clickhouse-tls/client.crt` | analytics-service, backend |
| `infrastructure/clickhouse/tls/client.key` | `/etc/clickhouse-tls/client.key` | analytics-service, backend |
| `infrastructure/clickhouse/tls/ca.crt` | `/etc/clickhouse-tls/ca.crt` | analytics-service, backend (as `sslrootcert`) |
| `infrastructure/clickhouse/tls-config.xml` | `/etc/clickhouse-server/config.d/tls-config.xml` | clickhouse-01, clickhouse-02 |

---

## 3. Cert generation + rotation

### 3.1 Generate (first-time or rotate)

```bash
# from repo root
infrastructure/scripts/gen-ch-mtls-certs.sh           # idempotent — refuses to overwrite
infrastructure/scripts/gen-ch-mtls-certs.sh --force   # regenerate (rotation)
```

Expiry (documented; configured via env at generation time):

| Cert | Default expiry | Override env |
|---|---|---|
| Internal CA (`ca.crt`) | 10 years (3650 d) | `CA_DAYS` |
| Server cert (`server.crt`) | 2 years (730 d) | `LEAF_DAYS` |
| Client cert (`client.crt`) | 2 years (730 d) | `LEAF_DAYS` |

**!!! POC / TEST-ENVIRONMENT DEV CERTS !!!**
The generated certs are committed to the repo so the test topology is
reproducible. They are NOT production-grade secrets (single internal CA,
no HSM, no automated rotation). Production MUST provision via
cert-manager (K8s `Certificate` CRD) or Vault PKI secrets engine before
pilot — see ADR-048 §6 for the Vault secret-injection pattern.

### 3.2 Rotation procedure (test env)

```bash
# 1. Regenerate
infrastructure/scripts/gen-ch-mtls-certs.sh --force

# 2. Roll ClickHouse (picks up new server.crt via volume mount)
docker compose -f docker-compose.yml -f docker-compose.ha.yml restart clickhouse-01 clickhouse-02

# 3. Roll consumers (picks up new client.crt via volume mount)
docker compose -f docker-compose.yml -f docker-compose.ha.yml restart analytics-service backend

# 4. Verify (§4)
infrastructure/scripts/ch-mtls-connection-test.sh
```

ClickHouse reloads TLS material on restart only (no in-process SIGHUP for
cert rotation in 23.8). The 2-replica cluster stays available if you roll
one node at a time.

---

## 4. Connection verification

Script: [`infrastructure/scripts/ch-mtls-connection-test.sh`](../../../infrastructure/scripts/ch-mtls-connection-test.sh)

```bash
infrastructure/scripts/ch-mtls-connection-test.sh
# default target: clickhouse-01
# override with: CH_NODE=clickhouse-02 infrastructure/scripts/ch-mtls-connection-test.sh
```

**Expected PASS output** (abridged):

```
[ch-mtls-test] Target container: uip-clickhouse-01
=== Test 1: mTLS handshake + SQL round-trip (client cert presented) ===
mTLS_OK  version()
Ok.      23.8.x.x
[ch-mtls-test] PASS: native mTLS (9440) round-trip Ok.

=== Test 2: HTTPS mTLS via curl (HTTP 8443, JDBC-equivalent path) ===
1
[ch-mtls-test] PASS: HTTPS mTLS (8443) round-trip Ok.

=== Test 3 (negative): connection WITHOUT client cert MUST be rejected ===
[ch-mtls-test] PASS: client-cert-less connection rejected (mTLS enforced).

[ch-mtls-test] ALL CHECKS PASSED — ClickHouse mTLS data path verified.
```

The three checks cover: (1) native-protocol mTLS via `clickhouse-client
--secure`, (2) HTTPS mTLS via curl on port 8443 (the JDBC-equivalent path),
(3) negative test confirming a client-cert-less connection is rejected.

---

## 5. Follow-up (out of T09 scope, tracked for next sprint)

1. **Flink submitters** (`flink-esg-job-submitter`,
   `flink-structural-job-submitter`, `flink-environment-job-submitter`)
   still use plain `:8123` JDBC URLs. They use the same `clickhouse-jdbc`
   driver so the same `ssl=true&sslrootcert=...&sslcert=...&sslkey=...`
   pattern applies, but flipping them needs a Flink job smoke test to
   avoid mid-pipeline breakage. Estimated 1 SP.
2. **`forecast-service`** uses host/port env vars, not a JDBC URL — needs
   driver-level TLS configuration (likely `CLICKHOUSE_SECURE=true` plus
   cert env vars on its HTTP client). Estimated 1 SP.
3. **Production cert rotation** via cert-manager (K8s) or Vault PKI.
   Deferred until pilot-readiness sprint.

---

## 6. Defects / notes discovered during T09

1. **No defects** in the existing `docker-compose.ha.yml`. The mTLS overlay
   mounts cleanly without touching the existing 8123 plain listener or the
   ReplicatedMergeTree / Keeper topology.
2. **Kong does not call ClickHouse directly** — confirmed by grepping
   `CLICKHOUSE_URL` / `jdbc:clickhouse` across all compose files. Kong
   routes HTTP to `analytics-service` / `backend`, which own the JDBC
   connections. T09 therefore wires those two services, not Kong.
3. The plain `:8123` listener on CH HA nodes is retained by design:
   Flink submitters and forecast-service are not yet mTLS-wired (§5), and
   the listener is bound to the container network only (no host port
   publishes 8123 to the host for HA nodes — they publish offset ports
   8125/8124 for operator access).
4. `docker compose -f docker-compose.yml -f docker-compose.ha.yml config -q`
   still exits 0 after the mTLS additions — verified.
