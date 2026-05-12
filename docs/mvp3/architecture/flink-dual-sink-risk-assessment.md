# Flink Dual-Sink Risk Assessment

**Ngày:** 2026-05-12  
**Scope:** `EsgDualSinkJob.java` + `ClickHouseSink.java` + test coverage  
**Audience:** Backend Lead, SA, QA, PM  
**Tóm tắt:** 5 vấn đề CRITICAL cần fix trước Sprint 2. 4 vấn đề MEDIUM ghi nhận.

---

## Bối cảnh: Tại sao dual-sink?

```
IoT Sensor
    │
    ▼
Kafka topic: ngsi_ld_esg
    │
    ├──► Flink → TimescaleDB (esg.clean_metrics)
    │         └─ Real-time: last 90 ngày, row-level RLS, API queries
    │
    └──► Flink → ClickHouse (analytics.esg_readings)
              └─ OLAP: toàn bộ lịch sử, cross-building aggregate, p95 < 5s
```

**TimescaleDB** = nguồn sự thật cho real-time, RLS, alerting.  
**ClickHouse** = OLAP read-only replica cho cross-building dashboard, ESG report.

Hai hệ thống phục vụ mục đích khác nhau — không thể thay thế nhau — nên phải dual-write.

---

## CRITICAL Issues (phải fix trước Sprint 2)

### C-1: Hai Flink jobs cùng consume ngsi_ld_esg — duplicate write vào TimescaleDB

**Vị trí:**
- `EsgFlinkJob.java:62` — group ID: `flink-esg-job`
- `EsgDualSinkJob.java:72` — group ID: `flink-esg-dual-sink-job`

**Vấn đề:** Cả hai jobs đều subscribe `ngsi_ld_esg`. Nếu chạy đồng thời trong giai đoạn transition:
- `EsgFlinkJob` insert vào `esg.clean_metrics` **không có** `ON CONFLICT DO NOTHING`
- `EsgDualSinkJob` insert vào `esg.clean_metrics` **có** `ON CONFLICT DO NOTHING`
- Kết quả: `EsgFlinkJob` tạo duplicate rows

**Schema mismatch thêm vào:**

| Job | INSERT columns | ON CONFLICT |
|-----|---------------|-------------|
| `EsgFlinkJob` | `source_id, metric_type, timestamp, value, unit, raw_payload` | KHÔNG CÓ |
| `EsgDualSinkJob` | `source_id, metric_type, timestamp, value, unit, tenant_id, building_id` | `DO NOTHING` |

`EsgFlinkJob` insert `raw_payload::jsonb` nhưng bỏ qua `tenant_id` và `building_id` — data thiếu tenant context → RLS broken.

**Fix Sprint 2:**
1. Dừng `EsgFlinkJob` khi deploy `EsgDualSinkJob` (chỉ 1 job consume ngsi_ld_esg tại một thời điểm)
2. Thêm `ON CONFLICT DO NOTHING` vào `EsgFlinkJob` nếu cần run song song tạm thời
3. Deprecate `EsgFlinkJob` — `EsgDualSinkJob` là job chính từ Sprint 2

---

### C-2: ClickHouse sink KHÔNG phải exactly-once — duplicate risk sau restart

**Vị trí:** `ClickHouseSink.java:40` — dùng `JdbcSink.sink()` (non-XA)

**Vấn đề:**
- `CheckpointingMode.EXACTLY_ONCE` ở `EsgDualSinkJob.java:64` chỉ áp dụng cho **Kafka source offset commit**
- `JdbcSink.sink()` là at-least-once sink — không implement `TwoPhaseCommitSinkFunction`
- ClickHouse không hỗ trợ XA/2PC transactions
- Khi Flink restart sau checkpoint: batch đang flush dở (`BATCH_SIZE=5000`, `BATCH_INTERVAL=2000ms`) sẽ được replay → **duplicate rows trong ClickHouse**

**Thực tế mức độ nguy hiểm:**
- Checkpoint interval = 30s → worst case 30s * throughput duplicate rows
- Ví dụ: 1000 events/sec × 30s = tối đa 30,000 rows duplicate sau mỗi failure
- ClickHouse `MergeTree` không auto-dedup → duplicate tích lũy vô thời hạn

**Fix Sprint 2:**
```sql
-- Option A: Đổi sang ReplacingMergeTree (dedup by ORDER BY key)
CREATE TABLE analytics.esg_readings (...)
ENGINE = ReplacingMergeTree(recorded_at)
ORDER BY (tenant_id, building_id, metric_type, source_id, recorded_at);
-- Query cần thêm: SELECT ... FINAL (force dedup on read)

-- Option B: INSERT với dedup key bằng ClickHouse built-in deduplication
-- Set insert_deduplicate=1 và dùng insert_deduplication_token
```

---

### C-3: Checkpoint trên local disk — data gap sau pod restart

**Vị trí:** `EsgDualSinkJob.java:67`
```java
env.getCheckpointConfig().setCheckpointStorage("file:///flink/checkpoints");
```

**Vấn đề:** Trong Kubernetes, pod restart → `/flink/checkpoints` mất → job khởi động từ `OffsetsInitializer.latest()` → **mất tất cả data trong khoảng thời gian pod down**.

**Ví dụ:** Pod down 5 phút, 50 sensors gửi 1 reading/phút → mất 250 readings. Nếu là CO2/energy trong giờ cao điểm → ESG report thiếu dữ liệu.

**Fix Sprint 2 (DevOps):**
```yaml
# Flink ConfigMap
env.checkpoint-storage: filesystem
state.checkpoints.dir: s3://uip-flink-checkpoints/esg-dual-sink/
# Hoặc với MinIO on-prem:
state.checkpoints.dir: s3://flink-checkpoints/esg-dual-sink/
fs.s3a.endpoint: http://minio.uip.svc.cluster.local:9000
```

---

### C-4: dual-sink-verify.sh KHÔNG test Flink pipeline thực sự

**Vị trí:** `tests/performance/dual-sink-verify.sh`

**Vấn đề:** Script này:
1. Insert 100K rows trực tiếp vào TimescaleDB bằng `generate_series()`
2. Export CSV từ TimescaleDB và POST vào ClickHouse bằng HTTP API
3. So sánh count và SUM

Đây là **data copy test**, không phải **pipeline test**. Delta = 0 là tất nhiên vì cùng một data nguồn. Không verify:
- Kafka deserialization có đúng không
- `flatMap` metric expansion có chính xác không
- `extractBuildingId` chạy trên dữ liệu thực tế
- Flink checkpoint/recovery
- JDBC batch flush behaviour

**Fix Sprint 2:**
```bash
# Viết integration test thực sự:
# 1. Publish 1000 NgsiLdMessage lên Kafka test topic
# 2. Chạy EsgDualSinkJob với mini-cluster mode (Flink MiniCluster)
# 3. Poll TS + CH, verify count + SUM + building_id extraction
```

---

### C-5: OffsetsInitializer.latest() — mất data trên first deploy

**Vị trí:** `EsgDualSinkJob.java:73`
```java
.setStartingOffsets(OffsetsInitializer.latest())
```

**Vấn đề:** Lần đầu deploy, Flink bắt đầu từ message mới nhất → tất cả historical messages trong Kafka (retention mặc định 7 ngày) bị bỏ qua → ClickHouse thiếu data ngay từ đầu.

**Fix Sprint 2:**
```java
// Lần đầu deploy: backfill mode
.setStartingOffsets(OffsetsInitializer.earliest())
// Sau khi backfill xong: chuyển về committed offset
.setStartingOffsets(OffsetsInitializer.committedOffsets(OffsetResetStrategy.LATEST))
```

---

## MEDIUM Issues (ghi nhận, fix Sprint 2-3)

### M-1: extractBuildingId fragile — silent empty building_id

```java
// Chỉ handle 2 pattern:
// SENSOR-{building}-{num}  e.g. SENSOR-PERF-BLD-001-001
// {building}-SENSOR-{num}  e.g. BLD-001-SENSOR-01
```

Device ID format từ BMS thực tế không chuẩn — nhiều vendor dùng format riêng. Empty `building_id` → cross-building analytics silently broken cho sensors đó. **Không có alert, không có metric.**

**Fix:** Thêm metric counter `Gauge(building_id_empty_count)` + alert khi > 1% messages.

---

### M-2: Empty tenant_id đi qua cả hai sinks

```java
// EsgDualSinkJob.java:85
String tenantId = msg.getTenantId() != null ? msg.getTenantId() : "";
```

Empty string tenant_id vẫn pass qua → insert vào cả TS (RLS bypass do empty = admin) và CH (ô nhiễm analytics). Nguồn: sensors chưa cấu hình tenant, hoặc lỗi BMS mapping.

**Fix:** Filter hoặc route sang DLQ: `msg.getTenantId() == null || msg.getTenantId().isBlank()`.

---

### M-3: Không có Dead Letter Queue

Message lỗi deserialization, NPE trong flatMap, hoặc connection timeout bị drop silently. Không có replay, không có alert. Trong production với 50+ sensors, silent data loss khó phát hiện.

**Fix Sprint 3:** Flink side-output → Kafka topic `ngsi_ld_esg_dlq` → alert khi DLQ rate > 0.1%.

---

### M-4: Batch size asymmetry — TS vs CH flush timing khác nhau

| Sink | Batch size | Interval |
|------|-----------|---------|
| TimescaleDB | 500 rows | 1,000ms |
| ClickHouse | 5,000 rows | 2,000ms |

Trong 1 Flink checkpoint (30s), TS flush ~30 lần, CH flush ~15 lần. Sau partial failure tại giây thứ 20: TS đã flush row 1-10,000, CH chỉ flush 1-7,500 → **tạm thời diverge 2,500 rows** cho đến checkpoint tiếp theo hoặc CH flush. Trong khoảng thời gian này, dashboard cross-building sẽ hiển thị số thấp hơn thực tế.

**Mức độ:** Chấp nhận được (trong vài giây), nhưng cần document rõ trong SLA.

---

## Câu hỏi PO sẽ hỏi — câu trả lời chuẩn bị

**Q: Nếu ClickHouse chết, dữ liệu có mất không?**

> TimescaleDB vẫn nhận đủ data (hai sinks độc lập). Khi ClickHouse recover, Flink replay từ checkpoint cuối + Kafka offset → backfill. Rủi ro: nếu ClickHouse down > Kafka retention (7 ngày), phần đó cần manual resync từ TS→CH. **Sprint 2 fix:** tăng Kafka retention cho topic này lên 30 ngày.

**Q: Nếu TimescaleDB chết, ClickHouse có tiếp tục nhận không?**

> Không — hiện tại `JdbcSink` (TS) failure sẽ trigger Flink retry và cuối cùng là job failure, làm cả CH sink cũng dừng. **Đây là thiết kế đúng** — TimescaleDB là nguồn sự thật, nếu nó không nhận được, không ghi CH để tránh diverge.

**Q: Chi phí tài nguyên dual-sink là bao nhiêu?**

> - Memory: +~200MB/Flink task (RocksDB state backend cho checkpoint)
> - CPU: ~10-15% overhead thêm (serialize/write cả hai sinks)
> - Network: gấp đôi write throughput từ Flink → DB
> - ClickHouse node: ~2 CPU, 4GB RAM cho 5M rows/ngày (single-node POC)
> - **Sprint 4:** scale ClickHouse lên 2-node khi data > 50M rows

**Q: Dữ liệu hai chỗ có lúc nào khác nhau không?**

> Có — trong khoảng **tối đa 2 giây** sau mỗi event (do batch interval khác nhau). Đây là eventual consistency, chấp nhận được cho analytics dashboard (không phải real-time alerting). Alerting vẫn dùng TimescaleDB (source of truth).

**Q: Khi nào đạt exactly-once thực sự?**

> Sprint 2: migrate ClickHouse sang `ReplacingMergeTree` + `SELECT ... FINAL` để dedup on read. Đây là pattern chuẩn của ClickHouse — không cần XA. Flink side: giữ at-least-once delivery + ClickHouse-level dedup = effective exactly-once semantics.

---

## Action Items

| # | Issue | Owner | Sprint | Priority |
|---|-------|-------|--------|---------|
| C-1 | Dừng EsgFlinkJob khi deploy EsgDualSinkJob | Backend Lead | Sprint 2 Day 1 | BLOCKER |
| C-2 | Migrate CH table sang ReplacingMergeTree | Backend Eng 2 | Sprint 2 W1 | CRITICAL |
| C-3 | Checkpoint storage → S3/MinIO | DevOps | Sprint 2 W1 | CRITICAL |
| C-4 | Viết Flink integration test thực sự | QA | Sprint 2 W2 | HIGH |
| C-5 | Document backfill procedure cho first deploy | Backend Lead | Sprint 2 Day 1 | HIGH |
| M-1 | Metric counter cho empty building_id | Backend Eng 2 | Sprint 2 W2 | MEDIUM |
| M-2 | Filter/DLQ cho empty tenant_id | Backend Eng 1 | Sprint 2 W2 | MEDIUM |
| M-3 | DLQ side-output → Kafka | Backend Lead | Sprint 3 | MEDIUM |
| M-4 | Document eventual consistency SLA (2s) | BA/PM | Sprint 2 | LOW |
