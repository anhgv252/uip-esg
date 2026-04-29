# ADR-014: Telemetry Enrichment Pattern — Injecting tenant_id into the Stream

**Status**: Accepted
**Date**: 2026-04-28
**Deciders**: Tech Lead, Solution Architect
**Scope**: MVP3 — áp dụng khi deploy multi-tenant trên shared Flink cluster

---

## Context

Raw telemetry payload từ IoT devices (MQTT/ThingsBoard) chỉ chứa `meter_id`, `timestamp`, và giá trị đo — không có `tenant_id`. Flink cần biết một event thuộc tenant nào để aggregate đúng và ghi vào TimescaleDB với `tenant_id` chính xác.

### Constraint từ kiến trúc hiện tại

Redpanda Connect đã làm enrichment `site_id`, `building_id`, `zone_id` tại pipeline config level (mỗi pipeline YAML tương ứng với một source cụ thể). Đây là tiền lệ quan trọng: **nguồn dữ liệu đã được gán ngữ cảnh tại tầng ingestion**, không phải tại tầng processing.

### Vấn đề

Khi UIP chuyển sang T2+ multi-tenant với shared Flink cluster, có hai kịch bản khác nhau cần phân biệt:

**Kịch bản A — Gateway-per-tenant** (phổ biến trong triển khai thực tế):
- Mỗi tenant có ThingsBoard instance hoặc MQTT namespace riêng.
- Redpanda Connect pipeline đã biết tenant khi cấu hình source.
- `tenant_id` có thể inject tĩnh tại RC pipeline, giống như `site_id` hiện tại.

**Kịch bản B — Shared gateway** (ít phổ biến hơn):
- Nhiều tenant chia sẻ một MQTT broker, phân biệt qua `device_id`.
- RC pipeline không biết tenant khi nhận payload.
- Cần lookup `device_id → tenant_id` in-flight.

Kịch bản B không thể giải bằng per-event DB lookup trong Flink vì gây N+1 query problem tại streaming throughput.

### Quyết định scope

ADR này quyết định chiến lược cho **cả hai kịch bản**, theo thứ tự ưu tiên.

---

## Decision

### Chiến lược chính: Tenant injection tại Redpanda Connect (Kịch bản A)

Mở rộng pipeline config hiện tại để inject `tenant_id` như một trường tĩnh tại Bloblang transform. Cách tiếp cận này nhất quán với pattern đã có cho `site_id`, `building_id`, `zone_id`.

```yaml
# Mỗi RC pipeline config tương ứng một source của một tenant cụ thể
pipeline:
  processors:
    - bloblang: |
        root = {
          "meter_id":    this.deviceId,
          "tenant_id":   "tenant_abc",        # inject tĩnh từ pipeline config
          "site_id":     metadata.site,
          "building_id": metadata.building,
          "zone_id":     metadata.zone,
          "timestamp":   this.ts,
          "measure_type": this.type,
          "value":       this.value,
          "unit":        this.unit,
          "source_id":   "tenant-abc-site-a-mqtt"
        }
```

Khi onboard tenant mới, Tenant Setup Service tạo một pipeline config mới với `tenant_id` tương ứng. Thay đổi config không cần restart Flink job.

**Ưu điểm**: không thêm dependency mới, không thay đổi Flink job, nhất quán với pattern hiện tại, dễ debug (tenant rõ ràng trong từng message).

### Chiến lược dự phòng: Flink Broadcast State (Kịch bản B)

Chỉ áp dụng khi deployment có shared gateway và không thể phân tách nguồn theo tenant tại RC level.

Cơ chế: Flink duy trì bảng mapping `{meter_id → tenant_id}` trong Broadcast State, được cập nhật bất đồng bộ qua Debezium CDC từ bảng `devices` trên PostgreSQL.

```
PostgreSQL: devices table (meter_id, tenant_id)
        ↓  Debezium CDC
Kafka: uip.cdc.devices
        ↓  BroadcastStream
Flink esg-cleansing-job:
    - Nhận BroadcastStream cập nhật mapping
    - Enrich mỗi event: lookup meter_id → tenant_id từ in-memory state
        ↓
Kafka: ngsi_ld_telemetry (đã có tenant_id)
```

**Điều kiện triển khai**:
- `wal_level = logical` phải được bật trên PostgreSQL instance.
- Debezium connector cần quyền `REPLICATION` trên DB user.
- Phải xử lý cold-start: khi Flink job khởi động lần đầu, Broadcast State trống — cần pre-load snapshot từ bảng `devices` trước khi xử lý live stream.
- Khi `meter_id` không có trong Broadcast State (device mới chưa được onboard), route event sang `esg_error_stream` với error code `UNKNOWN_DEVICE`.

### Nguyên tắc chung áp dụng cho cả hai kịch bản

`tenant_id` phải được đặt vào message **trước khi vào Kafka topic `ngsi_ld_telemetry`**. Flink jobs (`esg-cleansing-job`, `esg-aggregation-job`) không chịu trách nhiệm resolve tenant — chúng nhận `tenant_id` đã có sẵn trong message.

---

## Consequences

### Tích cực

- **Kịch bản A không tăng complexity**: RC pipeline injection tái sử dụng pattern hiện tại, không thay đổi Flink job.
- **Flink job đơn giản hơn**: jobs chỉ cần đọc `tenant_id` từ message, không cần quản lý mapping state.
- **Debug dễ**: `tenant_id` visible trong Kafka message từ đầu pipeline.
- **Kịch bản B có solution rõ ràng**: Broadcast State + CDC là pattern đã được kiểm chứng ở production streaming systems.

### Tiêu cực / Risks

| Rủi ro | Mức độ | Mitigation |
|--------|--------|-----------|
| RC config sai `tenant_id` → data ghi nhầm tenant | Critical | Pipeline config được review trước khi apply; integration test sau onboard |
| Broadcast State cold-start: device mới bị drop | Medium | Pre-load snapshot khi job start; `UNKNOWN_DEVICE` route sang error stream thay vì drop |
| Debezium lag làm Broadcast State stale | Low | Debezium CDC latency thường < 1s; device assignment là slow-changing operation |
| `wal_level = logical` tăng nhẹ WAL size | Low | Overhead nhỏ (~10-15%); acceptable |

### Không chọn

| Giải pháp thay thế | Lý do loại |
|--------------------|------------|
| Per-event DB lookup trong Flink | N+1 query tại streaming throughput; giết pipeline |
| Resolve tenant tại `esg-aggregation-job` | Mixing enrichment với aggregation logic; vi phạm single responsibility |
| Hardcode `tenant_id` trong Flink job config | Flink job không thể restart mỗi khi onboard tenant mới |

---

## Out of Scope

- Chi tiết Debezium connector configuration: thuộc infrastructure runbook.
- Tenant onboarding workflow (tạo RC pipeline config, test, activate): thuộc ADR-016 hoặc ops playbook.

---

## Implementation Checklist

### MVP2 (immediate — Kịch bản A prep)
- [ ] Bổ sung `tenant_id` vào Bloblang transform của tất cả RC pipeline configs hiện tại (inject `'default'`)
- [ ] Cập nhật Messaging Contract: `tenant_id` là required field trong `ngsi_ld_telemetry` schema
- [ ] Flink jobs: thêm validation `tenant_id` not null tại `esg-cleansing-job`

### MVP3 (Kịch bản B — nếu có shared gateway deployment)
- [ ] Bật `wal_level = logical` trên PostgreSQL
- [ ] Deploy Debezium connector cho bảng `devices`
- [ ] Implement Flink `TenantEnrichmentFunction` với Broadcast State + cold-start snapshot
- [ ] Route `UNKNOWN_DEVICE` events sang `esg_error_stream`
- [ ] Integration test: onboard device mới → verify enrichment đúng trước khi event vào aggregation

---

## Related

- ADR-010: Multi-Tenant Isolation Strategy
- ADR-013: TimescaleDB partitioning for shared infra (planned)
- [uip-esg-architecture.md — Section 3.2, 3.4](../uip-esg-architecture.md)
