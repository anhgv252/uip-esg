# UIP Kafka Topic Registry
**Cập nhật:** 2026-05-08 | **Source:** MVP2-25 (ADR-014)

Tài liệu này là nguồn duy nhất (single source of truth) cho tất cả Kafka topics trong hệ thống UIP Smart City.

---

## Naming Convention

```
UIP.<domain>.<entity>.<event>.v<version>
```

| Segment | Ví dụ | Mô tả |
|---------|-------|-------|
| `UIP` | `UIP` | Namespace cố định cho toàn platform |
| `domain` | `esg`, `flink`, `admin`, `workflow` | Bounded context |
| `entity` | `telemetry`, `alert` | Đối tượng nghiệp vụ |
| `event` | `error`, `detected`, `updated` | Loại sự kiện |
| `v<N>` | `v1` | Version schema |

Topics dạng `ngsi_ld_*` là **legacy ingestion topics** (input từ NGSI-LD broker, tên giữ nguyên theo chuẩn IoT Agent).

---

## Topic Registry

### IoT Ingestion (NGSI-LD Input)

| Topic | Producer | Consumer | Retention | Schema |
|-------|----------|----------|-----------|--------|
| `ngsi_ld_esg` | NGSI-LD IoT Agent | `EsgCleansingJob` (Flink) | 7 days | NgsiLdMessage JSON |
| `ngsi_ld_environment` | NGSI-LD IoT Agent | `EnvironmentFlinkJob`, `AlertDetectionJob` (Flink) | 7 days | NgsiLdMessage JSON |
| `ngsi_ld_traffic` | NGSI-LD IoT Agent | `TrafficFlinkJob` (Flink) | 7 days | NgsiLdMessage JSON |

**Required fields trong mọi `ngsi_ld_*` message:**
```json
{
  "deviceId": { "value": "SENSOR-001" },
  "observedAt": "2026-05-08T10:00:00Z",
  "meta": {
    "tenant_id": "hcm"
  }
}
```
> **ADR-014:** `tenant_id` trong `meta` là **bắt buộc** từ MVP2 Sprint 3. Message thiếu `tenant_id` sẽ bị route tới `UIP.esg.telemetry.error.v1`.

---

### Processed Events (Flink Output)

| Topic | Producer | Consumer | Retention | Schema |
|-------|----------|----------|-----------|--------|
| `UIP.flink.alert.detected.v1` | `AlertDetectionJob` (Flink) | `AlertEventKafkaConsumer`, `GenericKafkaTriggerService` (Backend) | 14 days | `AlertEvent` JSON |
| `UIP.esg.telemetry.error.v1` | `EsgCleansingJob` (Flink) | `TelemetryErrorConsumer` (Backend) | 7 days | `TelemetryErrorDto` JSON |

**Schema `UIP.flink.alert.detected.v1`:**
```json
{
  "sensorId": "SENSOR-AIR-001",
  "measureType": "aqi",
  "severity": "CRITICAL",
  "avgValue": 215.3,
  "windowStart": "2026-05-08T09:55:00Z",
  "windowEnd": "2026-05-08T10:00:00Z",
  "detectedAt": "2026-05-08T10:00:01Z"
}
```

**Schema `UIP.esg.telemetry.error.v1`:**
```json
{
  "errorCode": "MISSING_TENANT_ID",
  "sensorId": "SENSOR-001",
  "message": "tenant_id is required but was null or blank",
  "detectedAt": "2026-05-08T10:00:00Z"
}
```

---

### Admin / Config Events

| Topic | Producer | Consumer | Retention | Schema |
|-------|----------|----------|-----------|--------|
| `UIP.admin.trigger-config.updated.v1` | Backend Admin API | `TriggerConfigCacheInvalidator` (Backend) | 1 day | `TriggerConfigUpdatedEvent` JSON |

---

### Dead Letter Queues

| Topic | DLQ For | Producer | Consumer | Retention |
|-------|---------|----------|----------|-----------|
| `UIP.workflow.trigger.dlq.v1` | `UIP.flink.alert.detected.v1` workflow trigger failures | `GenericKafkaTriggerService` | Ops monitoring | 30 days |

---

## Partition & Replication Strategy

| Tier | Partitions | Replication Factor | Use Case |
|------|-----------|-------------------|---------|
| Ingestion (`ngsi_ld_*`) | 12 | 3 | Throughput: 100K events/sec |
| Processed events | 6 | 3 | Alert routing, moderate volume |
| Admin/Config | 1 | 3 | Low volume, ordering required |
| DLQ | 3 | 3 | Ops tooling |

---

## Security

Tất cả topics yêu cầu SASL authentication kể từ MVP2 Sprint 3 (ADR-014):

| Environment | Protocol | Mechanism |
|-------------|----------|-----------|
| Local dev | `PLAINTEXT` | — |
| Staging | `SASL_PLAINTEXT` | `PLAIN` |
| Production | `SASL_SSL` | `SCRAM-SHA-512` |

Env vars: `KAFKA_SECURITY_PROTOCOL`, `KAFKA_SASL_MECHANISM`, `KAFKA_SASL_JAAS_CONFIG`

---

## Consumer Groups

| Group ID | Topics | Service |
|----------|--------|---------|
| `flink-esg-cleansing` | `ngsi_ld_esg` | EsgCleansingJob |
| `flink-environment` | `ngsi_ld_environment` | EnvironmentFlinkJob |
| `flink-alert-detection` | `ngsi_ld_environment` | AlertDetectionJob |
| `flink-traffic` | `ngsi_ld_traffic` | TrafficFlinkJob |
| `uip-backend-alert` | `UIP.flink.alert.detected.v1` | AlertEventKafkaConsumer |
| `uip-workflow-generic` | `UIP.flink.alert.detected.v1` | GenericKafkaTriggerService |
| `uip-telemetry-error` | `UIP.esg.telemetry.error.v1` | TelemetryErrorConsumer |
| `uip-trigger-config-cache` | `UIP.admin.trigger-config.updated.v1` | TriggerConfigCacheInvalidator |

---

## Adding a New Topic

1. Thêm vào bảng registry trong file này
2. Định nghĩa JSON schema (required fields, optional fields, type)
3. Tạo topic trên Kafka cluster (`kafka-topics.sh --create ...`)
4. Cập nhật `infra/kafka/topics.sh` (provisioning script)
5. Cập nhật OpenAPI nếu topic relate tới HTTP endpoint
6. Notify consumer teams (FE nếu cần real-time update qua SSE)
