# Avro Schema Registry — UIP Smart City Platform

**Governance**: ADR-051 (Schema Registry Governance)
**Compatibility level**: `BACKWARD_TRANSITIVE`
**Registry endpoint (local/Compose HA)**: `http://localhost:8081`

---

## Overview

This directory contains Avro schema definitions (`.avsc` files) for Kafka topics
that carry structured payloads. All schemas here are the **single source of truth**
for Kafka event contracts between producers and consumers.

Kafka topics **without** a schema file in this directory use `JsonSerializer` (legacy/deprecated).
The migration plan is tracked in ADR-051 §3.3.

---

## Directory structure

```
shared/avro/
  README.md                          ← this file
  sensor-ingest-event.avsc           ← IoT module: raw sensor reading (planned M5-4)
  air-quality-reading-event.avsc     ← Environment module: AQI reading (planned M5-4)
  bpmn-execution-result-event.avsc   ← AI workflow module (planned M5-4)
  esg-metric-computed-event.avsc     ← ESG module migration from JSON (planned M5-4)
```

Schema files follow the naming convention: `{topic-name-kebab}.avsc`

---

## How to register a new schema

### Prerequisites

- Confluent Schema Registry running: `docker compose up schema-registry` (see `infrastructure/docker-compose.yml`)
- `curl` or `kafka-avro-console-producer` available

### Option A: Register via REST API (recommended for CI)

```bash
# Register a new schema
curl -X POST \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data "{\"schema\": $(cat shared/avro/your-event.avsc | python3 -c 'import sys,json; print(json.dumps(sys.stdin.read()))')}" \
  http://localhost:8081/subjects/your-event-value/versions

# Check current compatibility level
curl http://localhost:8081/config/your-event-value

# Set compatibility level for a subject (run once after first registration)
curl -X PUT \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data '{"compatibility": "BACKWARD_TRANSITIVE"}' \
  http://localhost:8081/config/your-event-value
```

### Option B: Register via kafka-avro-console-producer (local testing)

```bash
kafka-avro-console-producer \
  --broker-list localhost:9092 \
  --topic uip.sensor.ingest \
  --property schema.registry.url=http://localhost:8081 \
  --property value.schema="$(cat shared/avro/sensor-ingest-event.avsc)"
```

### Option C: Spring Boot producer (application code)

Add to `application.yml`:
```yaml
spring:
  kafka:
    producer:
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
    properties:
      schema.registry.url: http://localhost:8081
```

---

## Compatibility rules — what is ALLOWED vs BLOCKED

The registry enforces `BACKWARD_TRANSITIVE`: a new schema version can deserialize data
written by **any** previous version.

### ✅ ALLOWED changes

| Change | Example |
|---|---|
| Add optional field **with default** | `{"name": "tenant_id", "type": "string", "default": ""}` |
| Add new enum symbol (at end only) | `["NORMAL", "WARNING", "CRITICAL"]` → add `"UNKNOWN"` at end |
| Remove a field that already has a default | Field with `"default": null` can be removed |
| Add a new record type (new `.avsc` file) | Any new schema — no existing consumers affected |
| Widen a union | `["null", "string"]` → `["null", "string", "int"]` |

### ❌ BLOCKED changes (CI gate will warn/fail)

| Change | Why blocked |
|---|---|
| Remove a field **without** default | Old messages still contain the field; new schema cannot read it |
| **Rename** any field | Old messages use old name; name-based resolution breaks |
| Change field type (`int` → `long`, `string` → `bytes`) | Wire-incompatible even for widening types |
| Change `namespace` or record `name` | Breaks subject resolution in registry |
| Remove an enum symbol | Existing records may carry the removed symbol |
| Change field order (for ordinal-based decoders) | Some decoders rely on field position |

### Example: adding a field correctly

```json
// Before (v1)
{
  "type": "record",
  "name": "SensorIngestEvent",
  "namespace": "com.uip.events",
  "fields": [
    {"name": "sensor_id", "type": "string"},
    {"name": "value",     "type": "double"},
    {"name": "timestamp", "type": "long"}
  ]
}

// After (v2) — CORRECT: new field has default
{
  "type": "record",
  "name": "SensorIngestEvent",
  "namespace": "com.uip.events",
  "fields": [
    {"name": "sensor_id", "type": "string"},
    {"name": "value",     "type": "double"},
    {"name": "timestamp", "type": "long"},
    {"name": "unit",      "type": "string", "default": ""}
  ]
}
```

---

## CI gate behavior

The `.github/workflows/proto-lint.yml` workflow contains an `avro-compat` job that:

1. Runs when any file in `shared/avro/` changes on a PR or push.
2. Calls `GET /compatibility/subjects/{subject}/versions/latest` for each `.avsc` file.
3. Reports INCOMPATIBLE schemas as warnings.

**Current mode (M5-3)**: Non-blocking — CI logs warnings but does not fail the PR.
**Planned mode (M5-5)**: Blocking — incompatible changes will fail the PR.

To check compatibility manually before pushing:

```bash
# Check a specific schema against the registry
curl -X POST \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data "{\"schema\": $(cat shared/avro/your-event.avsc | python3 -c 'import sys,json; print(json.dumps(sys.stdin.read()))')}" \
  http://localhost:8081/compatibility/subjects/your-event-value/versions/latest

# Expected COMPATIBLE response:
# {"is_compatible":true}

# Expected INCOMPATIBLE response:
# {"is_compatible":false,"messages":["...reason..."]}
```

---

## Kafka topic → subject naming convention

Confluent Schema Registry subjects follow the **TopicNameStrategy** (default):

| Kafka topic | Schema subject (value) | Schema subject (key) |
|---|---|---|
| `uip.sensor.ingest` | `uip.sensor.ingest-value` | `uip.sensor.ingest-key` (if key is Avro) |
| `uip.environment.aqi` | `uip.environment.aqi-value` | — |
| `uip.ai.bpmn.result` | `uip.ai.bpmn.result-value` | — |
| `uip.esg.metric.computed` | `uip.esg.metric.computed-value` | — |

Schema file names map 1:1 to topic names (replace `.` with `-`, drop `-value` suffix):
`uip.sensor.ingest` → `sensor-ingest-event.avsc`

---

## References

- ADR-051: Schema Registry Governance (this decision)
- ADR-020: Kafka event bus design (topic naming, partition strategy)
- [Confluent Schema Registry REST API docs](https://docs.confluent.io/platform/current/schema-registry/develop/api.html)
- [Avro specification — Schema Resolution](https://avro.apache.org/docs/current/spec.html#Schema+Resolution)
