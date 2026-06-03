# Kafka Avro Schema Versioning — UIP Smart City

**Sprint:** 7 (B1-4)
**ADR reference:** [ADR-034](ADR-034-structural-monitoring.md)
**Schema registry:** Apicurio Registry v2 — `http://apicurio-registry:8080/apis/registry/v2` (Docker) | port 8087 (host)
**Compatibility strategy:** BACKWARD (default for all artifacts)

---

## Topic Registry — v1 JSON + v2 Avro

| Logical topic | v1 JSON topic (current) | v2 Avro topic (Sprint 7+) | Avro schema file | Status |
|---------------|------------------------|--------------------------|-----------------|--------|
| BMS Reading | `UIP.bms.reading.raw.v1` | `UIP.bms.reading.raw.v2` | `avro/BmsReadingEvent.avsc` | ✅ Active |
| Sensor Reading | `UIP.iot.sensor.reading.v1` | `UIP.iot.sensor.reading.v2` | `avro/SensorReadingEvent.avsc` | ✅ Active |
| Alert Detected | `UIP.flink.alert.detected.v1` | `UIP.flink.alert.detected.v2` | `avro/AlertDetectedEvent.avsc` | ✅ Active |
| Hourly Rollup | `UIP.analytics.hourly.rollup.v1` | `UIP.analytics.hourly.rollup.v2` | `avro/HourlyRollupEvent.avsc` | ✅ Active |
| Structural Alert | `UIP.structural.alert.critical.v1` | _(planned Phase 3)_ | `avro/AlertDetectedEvent.avsc` (reuse) | v1 only |

> All v1 JSON topics remain fully active. v2 Avro topics are additive (dual-publish).

---

## Dual-Publish Pattern

```
Producer (BmsReadingKafkaProducer)
  │
  ├──▶ UIP.bms.reading.raw.v1  (JSON)   ──▶  Existing consumers (unaffected)
  └──▶ UIP.bms.reading.raw.v2  (Avro)   ──▶  New consumers (Avro-aware)
```

Implementation: `DualPublishKafkaProducer.publish()` + `AvroProducerConfig.avroKafkaTemplate`.

If Apicurio Registry is unavailable, v2 Avro publish fails silently (non-fatal). v1 JSON consumers are never affected.

---

## BACKWARD Compatibility Rules

A schema change is BACKWARD compatible if old consumers can still read data written with the new schema.

**Allowed changes:**
- Add optional field with default: `{"name": "newField", "type": ["null", "string"], "default": null}`
- Remove field with a default value

**NOT allowed (breaking):**
- Add required field without default
- Change field type (e.g., string → int)
- Rename field
- Remove required field

**Enforcement:** Apicurio Registry rejects schema registration if it violates BACKWARD compat.

---

## Avro Schema Files

Location: `backend/src/main/resources/avro/`

### BmsReadingEvent.avsc
Fields: `tenantId`, `deviceId`, `readingType`, `value`, `unit?`, `timestampMs`, `source?`
Maps from: `com.uip.backend.bms.api.dto.BmsReadingEvent` record

### SensorReadingEvent.avsc
Fields: `tenantId`, `sensorId`, `sensorType`, `value`, `unit?`, `district?`, `timestampMs`, `rawPayload?`
Covers: air quality, structural, traffic, environmental sensors

### AlertDetectedEvent.avsc
Fields: `tenantId`, `sensorId`, `module`, `measureType`, `value`, `threshold`, `severity`, `timestampMs`, `location?`, `buildingId?`
Maps from: Flink alert detection output

### HourlyRollupEvent.avsc
Fields: `tenantId`, `buildingId`, `metricType`, `hourEpochMs`, `sum`, `avg`, `min`, `max`, `count`, `unit?`
Maps from: Analytics aggregation pipeline output

---

## Schema Registration

Schemas are auto-registered on first publish when `apicurio.registry.auto-register=true` (default).

**Manual registration** (if auto-register disabled):
```bash
# Register BmsReadingEvent schema
curl -X POST \
  "http://localhost:8087/apis/registry/v2/groups/uip/artifacts" \
  -H "Content-Type: application/json; artifactType=AVRO" \
  -H "X-Registry-ArtifactId: BmsReadingEvent" \
  -d @backend/src/main/resources/avro/BmsReadingEvent.avsc

# Check compatibility
curl "http://localhost:8087/apis/registry/v2/groups/uip/artifacts/BmsReadingEvent/rules/COMPATIBILITY"
```

---

## Deprecation Plan for v1 JSON Topics

> Timeline: v1 topics remain active for **1 full release cycle** after Phase 3 pilot rollout.

| Phase | Action |
|-------|--------|
| Sprint 7 (now) | Dual-publish active — v1 + v2 both live |
| Phase 3 GA | Announce v1 deprecation to all consumer teams |
| Phase 3 + 1 release | Update all consumers to v2 Avro |
| Phase 3 + 2 releases | Set v1 retention = 1 day (allows drain) |
| Phase 3 + 3 releases | Delete v1 topics |

**Exception:** `UIP.structural.alert.critical.v1` remains JSON-only until structural monitoring reaches GA (planned Phase 4).

---

## Apicurio Registry Health Check

```bash
curl http://localhost:8087/apis/registry/v2/health
# Expected: {"status":"UP"}

# List all registered schemas
curl http://localhost:8087/apis/registry/v2/groups/uip/artifacts
```

---

*Kafka Avro Schema Versioning — Sprint 7 B1-4 | Updated 2026-06-02*
*See also: [ADR-034 Structural Monitoring](ADR-034-structural-monitoring.md)*
