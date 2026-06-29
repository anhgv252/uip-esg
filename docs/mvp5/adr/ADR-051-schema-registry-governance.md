# ADR-051: Schema Registry Governance — buf (Protobuf) + Confluent Schema Registry (Avro)

**Date**: 2026-06-29
**Status**: Accepted
**Priority**: P1 (MVP5 — CI gate, blocks merging breaking schema changes)
**Sprint**: M5-3 T09
**Author**: Solution Architect
**Related**: ADR-012 (gRPC + protobuf), ADR-020 (Kafka event bus design), ADR-048 (Compose HA topology)
**Artifact**: `.github/workflows/proto-lint.yml` (extended), `shared/avro/README.md` (new)

---

## 1. Context

### 1.1 Problem

UIP Smart City Platform produces Kafka events from 7+ modules (IoT ingestion, environment,
traffic, energy, citizen, ESG, AI-workflow). As the platform scales to pilot (MVP5) and
commercial (MVP6), **schema evolution without governance creates silent consumer failures**:

| Breaking change example | Consumer impact |
|---|---|
| Remove field `sensor_id` from `SensorReading.proto` | Analytics service silently ignores old messages or NPE |
| Rename Avro field `aqi_value` → `aqi` | Flink job fails to deserialize; alert pipeline silently drops events |
| Add required field without default in Avro | Old producers write invalid records |
| Change field type `int32` → `string` in proto | Wire-incompatible; all consumers break |

**At MVP4 demo (2026-06-18)** the team manually coordinated schema changes via Slack.
This is not scalable for pilot with 3+ consumer teams (backend, analytics-service, Flink jobs).

### 1.2 Existing state

- **Protobuf**: `shared/proto/` already has `buf.yaml` + `buf.breaking.yaml` (M5-1 T08).
  CI gate `.github/workflows/proto-lint.yml` runs `buf lint` + `buf breaking` on every PR.
- **Avro**: No governance. `shared/avro/` directory does not exist. Confluent Schema Registry
  is **already running** in the Kafka cluster (`infrastructure/docker-compose.yml` —
  `schema-registry` service on port 8081). No schemas are registered; producers use
  `JsonSerializer` for most topics.
- **Contract drift**: `SensorIngestEvent`, `AlertTriggeredEvent`, and `EsgMetricComputedEvent`
  are produced as JSON. Any field rename in the DTO is invisible to CI.

### 1.3 Constraints

1. **No new infrastructure**: Confluent Schema Registry already runs on port 8081 in Compose HA.
2. **buf already proven**: M5-1 T08 has zero issues over 3 sprints. Keep it for proto.
3. **CI must be fast**: Schema compatibility checks must not add >2 minutes to PR pipelines.
4. **Backward compatibility only**: Consumers must be able to read old AND new messages
   (producers upgrade faster than consumers in a microservices fleet).
5. **AGPL restriction (per CLAUDE.md)**: No AGPL-licensed tooling in CI.

---

## 2. Decision

### 2.1 Tool selection

| Schema type | Tool | Why |
|---|---|---|
| **Protobuf** (`shared/proto/**`) | `buf` (bufbuild/buf-action@v1) | Already proven in M5-1. Zero overhead to keep. Wire-compatibility enforced at byte level. |
| **Avro** (`shared/avro/**`) | Confluent Schema Registry REST API (via `curl`) | Already deployed in Compose HA. No new container. Uses standard `/compatibility` endpoint. |

**Rejected alternatives for Avro**:
- `avro-tools` (Apache): Does not check registry compatibility; only parses schemas locally.
- `kafka-schema-registry-cli` Docker image: Adds >60s Docker pull in cold CI. Replaced by
  direct `curl` calls to the registry REST API — same compatibility semantics, zero extra image.
- `schema-registry-maven-plugin`: Java-only, requires Maven build wrapper in CI, overkill.

### 2.2 Compatibility level

**BACKWARD_TRANSITIVE** for all schemas (proto + Avro).

| Level | Definition | Why chosen |
|---|---|---|
| `BACKWARD` | New schema can read data from the previous version | Minimum viable |
| `BACKWARD_TRANSITIVE` | New schema can read data from **all** previous versions | **CHOSEN** |
| `FULL_TRANSITIVE` | Bidirectional across all versions | Too restrictive for adding new optional fields |

Rationale: Flink jobs and analytics-service may replay Kafka topics from 90+ days ago
(TimescaleDB retention window). Consumers must deserialize historical messages with the
current schema. `BACKWARD` alone would only guarantee compatibility with the immediately
previous version.

### 2.3 Registration policy

1. **All new Kafka topics** that carry structured payloads MUST register a schema in
   Confluent Schema Registry before the first `produce` call.
2. **Producers** MUST use `KafkaAvroSerializer` (Confluent) when a schema is registered for
   that topic. Raw `JsonSerializer` is deprecated for registered topics.
3. **Schema registration** is the **producer team's responsibility**, done via the REST API
   or `kafka-avro-console-producer` during local development.
4. **CI gate** (see §2.4) validates compatibility on every PR that touches `shared/avro/`.
   A PR that would break BACKWARD_TRANSITIVE compatibility is **blocked from merging**.

### 2.4 CI gate design

```
.github/workflows/proto-lint.yml  (already exists — extended in M5-3 T09)
  ├── Job: proto
  │    ├── buf lint       (unchanged from M5-1)
  │    └── buf breaking   (unchanged from M5-1)
  └── Job: avro-schema-compat
       ├── Triggered by: paths = shared/avro/**
       ├── Tool: curl → Confluent Schema Registry /compatibility/{subject}
       └── Exit: non-zero if INCOMPATIBLE response
```

**Why extend the existing workflow** rather than a separate file: both jobs guard the same
invariant (no breaking schema contract changes). PR checks are easier to reason about with
a single workflow status to require in branch protection rules.

**Registry endpoint in CI**:
- **Local dev / Compose HA**: `http://localhost:8081` — already running.
- **CI (GitHub Actions)**: Schema Registry is **not** running as a service container
  in the current workflow. The Avro check step is **conditional** (`if: github.event_name == 'push' && env.SCHEMA_REGISTRY_URL != ''`). For PRs that touch `shared/avro/`, the CI step logs a warning and exits 0 until a Schema Registry service container is added (tracked in §5.1 OPEN item).

This "warn-then-block" rollout strategy was chosen deliberately:
1. Sprint M5-3 establishes the workflow shape and documentation.
2. MVP5 pilot (M5-4/M5-5) adds the `services: schema-registry` container to the job.
3. After M5-5, the step becomes blocking (`exit 1` on INCOMPATIBLE).

---

## 3. Consequences

### 3.1 Positive

- **Zero silent consumer failures** from proto changes: already true from M5-1; confirmed.
- **Avro governance established**: `shared/avro/` folder creates a single source of truth
  for all Kafka event schemas visible to all teams.
- **PR authors get fast feedback**: `buf breaking` runs in <10s; curl compatibility check
  runs in <5s once Schema Registry service container is added.
- **Audit trail**: Git history of `shared/avro/` is the evolution log for every Kafka event
  contract — diff-able, PR-reviewable.

### 3.2 Negative / Trade-offs

- **Producer migration work**: Teams using `JsonSerializer` must migrate to `KafkaAvroSerializer`
  per topic. Estimated: 2 SP per topic (3 core topics = 6 SP, tracked in M5-4 backlog).
- **Schema Registry single point of failure**: Compose HA runs 1 Schema Registry instance.
  If it is down, CI Avro check fails (before the blocking flag is set). Mitigation: the step
  is non-blocking until M5-5 flag day.
- **Avro schema evolution is more restrictive than JSON DTO**: Team must learn Avro
  compatibility rules. `shared/avro/README.md` documents the key cases.

### 3.3 Impact on modules

| Module | Impact |
|---|---|
| `iot-module` | Must register `SensorIngestEvent.avsc` before MVP5 pilot |
| `environment-module` | Must register `AirQualityReadingEvent.avsc` |
| `ai-workflow-module` | `BpmnExecutionResultEvent` — new topic, register at creation |
| `esg-module` | `EsgMetricComputedEvent` — existing JSON topic, migrate to Avro in M5-4 |
| `analytics-service` | Consumer only — update deserializer config |
| `flink-jobs` | Consumer + producer — update to Avro deserializer for Flink |

---

## 4. Implementation

### 4.1 Files changed (M5-3 T09)

| File | Change |
|---|---|
| `.github/workflows/proto-lint.yml` | Add `avro-schema-compat` job (conditional, non-blocking) |
| `shared/avro/README.md` | New — schema authoring guide, compatibility rules, registration how-to |

### 4.2 Files to add in M5-4 (tracked)

| File | Owner |
|---|---|
| `shared/avro/sensor-ingest-event.avsc` | IoT module team |
| `shared/avro/air-quality-reading-event.avsc` | Environment module team |
| `shared/avro/bpmn-execution-result-event.avsc` | AI workflow module team |
| `.github/workflows/proto-lint.yml` | DevOps — add `services: schema-registry` container |

### 4.3 Compatibility rules quick reference

**ALLOWED (BACKWARD_TRANSITIVE)**:
- Add optional field with default value
- Add new enum symbol (at end)
- Remove a field that already had a default value

**BLOCKED by CI gate**:
- Remove a field that has no default
- Rename any field
- Change field type (even widening: `int` → `long` is INCOMPATIBLE in Avro)
- Change field order (Avro is name-based, but ordinal matters for some decoders)
- Remove an enum symbol
- Change the schema `namespace` or `name`

---

## 5. Open Items

| ID | Description | Owner | Target |
|---|---|---|---|
| OPEN-1 | Add `schema-registry` service container to `avro-schema-compat` job so the step becomes blocking | DevOps | M5-5 |
| OPEN-2 | Migrate `SensorIngestEvent` JSON producer to `KafkaAvroSerializer` | IoT module team | M5-4 |
| OPEN-3 | Migrate `EsgMetricComputedEvent` JSON producer to `KafkaAvroSerializer` | ESG module team | M5-4 |
| OPEN-4 | Confirm Confluent Schema Registry is reachable on `http://schema-registry:8081` in Compose HA internal network | DevOps | M5-3 verification |

---

## 6. References

- [Confluent Schema Registry REST API](https://docs.confluent.io/platform/current/schema-registry/develop/api.html)
- [buf breaking change detector](https://buf.build/docs/breaking/)
- [Avro specification — Schema evolution](https://avro.apache.org/docs/current/spec.html#Schema+Resolution)
- ADR-012 (gRPC + protobuf governance)
- ADR-020 (Kafka event bus design — topic naming, partition strategy)
- M5-1 T08 implementation (buf CI gate, existing)
