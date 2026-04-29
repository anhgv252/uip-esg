# ADR-013: Edge Computing Strategy — EMQX Edge + Flink Edge Jobs

**Status**: Accepted
**Date**: 2026-04-28
**Deciders**: Tech Lead, Solution Architect
**Scope**: T2→T4 — edge chỉ deploy khi có WAN bandwidth hoặc offline resilience trigger

---

## Context

MVP1 dùng kiến trúc centralized hoàn toàn: mọi sensor gửi MQTT trực tiếp đến EMQX broker trung tâm → Kafka → Flink → TimescaleDB. Đây là kiến trúc đúng cho T1 (1 tòa nhà, LAN/fiber đủ).

Khi UIP scale lên T2+ (5–20 tòa nhà trong cùng khu đô thị), vấn đề mới xuất hiện:

1. **WAN bandwidth**: 500 sensors × 1 reading/30s × avg 512 bytes = ~8 KB/s per building. 20 buildings = 160 KB/s bandwidth. Tăng lên T3 với 50K sensors: 800 KB/s liên tục. Đây là chi phí WAN không nhỏ ở Việt Nam.

2. **Offline resilience**: Khi WAN đứt (mất điện, cáp hỏng), building phải vẫn hoạt động được trong vài giờ. Sensor data không được mất.

3. **Latency cho local alert**: Cảnh báo trong tòa nhà (AQI phòng, power cutoff) không nên phụ thuộc WAN latency. Local processing = <100ms, cloud roundtrip = >50ms best case.

### Constraint hiện tại

EMQX Cloud CE đã được dùng. EMQX có EMQX Edge (lightweight, chạy trên Raspberry Pi hoặc mini PC tại tòa nhà). Flink có khả năng chạy ở scale nhỏ (1 JM + 1 TM) trên hardware yếu.

---

## Decision

### Chiến lược 3 lớp theo tier

#### T1 — Không có edge (centralized hoàn toàn)

```
Sensor → EMQX Central → Kafka → Flink → TimescaleDB
```

Đây là kiến trúc MVP1 hiện tại. Không thay đổi.
- Single building, LAN/fiber đến central
- Không có chi phí WAN đáng kể
- Không cần offline resilience

---

#### T2 — EMQX Edge as buffer (buffer only, no processing)

```
Sensor → EMQX Edge (tại tòa nhà)
              │ 
              ├── Local: retain messages 24h nếu WAN đứt
              │
              └── Bridge → EMQX Central → Kafka → Flink → TimescaleDB (unchanged)
```

**Mục đích:**
- Buffer local 24h: khi WAN đứt, sensor data không mất — được replay khi kết nối phục hồi
- Giảm số TCP connection đến EMQX Central: thay vì 500 sensors kết nối trực tiếp, chỉ 1 EMQX Edge bridge
- Không có processing logic tại edge — Flink vẫn ở central

**Deployment:**
- EMQX Edge: mini PC (Raspberry Pi 4 hoặc Intel NUC) tại mỗi tòa nhà
- Config: MQTT bridge đến EMQX Central với persistent session
- Storage: 8GB SD card đủ cho 24h buffer ở 500 sensors

**Trigger để deploy T2 edge buffer:**
```
WAN cost > 500K VND/tháng per building
HOẶC: WAN reliability <99% (>7 giờ downtime/tháng)
HOẶC: Số sensor > 200 per building (TCP connection pressure tại EMQX Central)
```

---

#### T3 — Flink Edge Jobs (aggregation tại building)

```
Sensor → EMQX Edge → Flink Edge Job (tại building cluster)
                          │
                          ├── Pre-aggregate: 30s raw → 5min window aggregate
                          │   Giảm data volume 10x trước khi gửi về central
                          │
                          ├── Local alert: AQI >200 → SMS trong <2s (không cần WAN)
                          │
                          └── Bridge (aggregated) → Kafka Central → Flink Central
```

**Mục đích:**
- **Giảm WAN bandwidth 10x**: gửi 5min aggregate thay vì 30s readings → 10x ít data hơn
- **Local alert <2s**: không phụ thuộc WAN roundtrip cho P0 alerts trong tòa nhà
- **Offline operation 4–8 giờ**: Flink Edge local có thể tiếp tục vận hành khi WAN đứt, ghi vào local TimescaleDB mini, sync khi online

**Flink Edge Job spec:**
```java
// esg-edge-aggregation-job — chạy tại building level
// Chỉ aggregation, không có business logic phức tạp
stream
    .keyBy(reading -> reading.getSensorId())
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .aggregate(new SimpleAverageAggregator())
    .addSink(new KafkaCentralSink());  // gửi về central Kafka
```

**Hardware tại building cluster:**
- 1 mini server (Lenovo ThinkCentre Tiny hoặc tương đương): i5, 16GB RAM, 256GB SSD
- Chạy: EMQX Edge + Flink (1JM + 1TM) + TimescaleDB mini (local buffer 7 ngày)
- Cost: ~8–10M VND/building cluster

**Trigger để deploy T3 edge:**
```
WAN bandwidth cost > 2M VND/tháng per cluster
HOẶC: P0 alert latency requirement <5s (không thể đảm bảo qua WAN)
HOẶC: Offline SLA: building phải tự vận hành >4h khi WAN đứt
```

---

#### T4 — Full Edge AI (local inference, only anomalies to cloud)

```
Sensor → EMQX Edge → Flink Edge + ML Model (tại building)
                          │
                          ├── Local ML inference: anomaly detection, AQI prediction
                          │   Chỉ gửi lên cloud khi: anomaly detected / threshold breach
                          │
                          ├── Routine data: compress + batch gửi mỗi 15 phút
                          │
                          └── AI City Brain (cloud) nhận events, không phải raw stream
```

**Mục đích:**
- **Giảm cloud bandwidth tối đa**: chỉ gửi anomalies và summaries, không gửi raw stream
- **Local AI**: mô hình nhẹ (ONNX, TensorFlow Lite) chạy tại edge phát hiện anomaly trước
- **Độc lập hoàn toàn**: building vận hành đầy đủ kể cả khi cloud không khả dụng

**Chỉ T4 vì:**
- Cần đội ML train và maintain model cho từng domain
- Deployment lifecycle phức tạp (model update, A/B testing tại edge)
- Hardware đắt hơn (cần GPU nhỏ hoặc NPU cho inference)

---

### Data flow tóm tắt theo tier

```
T1: Sensor ──────────────────────────────────→ Kafka Central → Flink → DB
T2: Sensor → EMQX Edge (buffer) ─────────────→ Kafka Central → Flink → DB
T3: Sensor → EMQX Edge → Flink Edge (agg) ───→ Kafka Central → Flink → DB
T4: Sensor → EMQX Edge → Flink+ML Edge ──────→ Event stream (anomalies only) → AI Brain
```

### Topic convention cho edge-to-cloud

Kafka topic từ edge phải phân biệt với direct sensor stream:

```
UIP.iot.sensor.reading.v1           ← raw từ sensor (T1/T2)
UIP.iot.sensor.aggregate-5m.v1      ← 5-minute aggregate từ Flink Edge (T3)
UIP.edge.anomaly.detected.v1        ← anomaly từ edge ML (T4)
```

---

## Consequences

### Tích cực

- **Tier-aware**: mỗi tier chỉ thêm complexity khi có trigger rõ ràng
- **Backward compatible**: central Flink không đổi khi edge được thêm
- **Offline resilience**: building vẫn hoạt động khi WAN đứt — critical cho critical infrastructure
- **WAN cost reduction**: T3 giảm 10x bandwidth cost

### Tiêu cực / Risks

| Rủi ro | Mức độ | Mitigation |
|--------|--------|-----------|
| Hardware failure tại edge (mini PC) | Medium | EMQX Edge buffer + watchdog restart; replace hardware <4h |
| Flink Edge job lỗi → local alert miss | High | Watchdog daemon; fallback alert via SMS direct từ EMQX rule engine |
| Data lag khi sync sau WAN recovery | Medium | Ordered replay theo event-time; Flink watermark xử lý late data |
| Edge deployment quản lý phức tạp (config drift) | Medium | Ansible/Helm cho edge; GitOps config management |
| Clock skew tại edge | Low | NTP sync bắt buộc; watermark 60s tolerance ở T3 |

### Không chọn

| Phương án | Lý do loại |
|-----------|------------|
| Triển khai Flink Edge ngay từ T2 | Quá sớm; buffer-only đủ cho T2 và rẻ hơn nhiều |
| Kubernetes tại edge (K3s) | Overkill cho T2/T3 edge; mini PC không đủ resource |
| Cloud-only (không bao giờ dùng edge) | WAN cost và offline resilience là yêu cầu thực tế của T3 |
| AWS Greengrass / Azure IoT Edge | Vendor lock-in; data sovereignty concern cho critical city infrastructure |

---

## Implementation Checklist

### T2 — EMQX Edge Buffer
- [ ] Procure 1 EMQX Edge device per building cluster (Raspberry Pi 4 đủ)
- [ ] Config MQTT bridge: local retain → central bridge với persistent session
- [ ] Test: WAN down → messages buffered local → WAN up → replay đúng thứ tự
- [ ] Monitor: buffer fill rate; alert khi buffer >80% capacity

### T3 — Flink Edge
- [ ] Cấu hình `esg-edge-aggregation-job`: 5-min tumbling window, output to Kafka Central
- [ ] Deploy TimescaleDB mini tại edge (local buffer 7 ngày)
- [ ] Test: offline 4h → local vẫn ghi → sync khi online → no data gap tại Central
- [ ] Validate: 5-min aggregate từ Edge == aggregate từ Central (cross-check)

---

## Related

- ADR-011: Module Extraction Order
- ADR-014: Telemetry Enrichment Pattern (tenant_id injection tại edge)
- [Demo & Roadmap 2026-04-25 — Section 4.1, 4.3](../project/demo-and-roadmap-2026-04-25.md)
