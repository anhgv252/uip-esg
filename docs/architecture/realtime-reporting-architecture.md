# Kiến Trúc Reporting Real-Time: Kappa Architecture & Flink Dual-Speed Pipeline

**Ngày**: 2026-04-03  
**Tác giả**: UIP Solution Architect  
**Trạng thái**: Draft — Cần Review  
**Context**: Tách từ [modular-architecture-evaluation.md](./modular-architecture-evaluation.md) — Phụ Lục A, Thách Thức 1

---

> **Phạm vi áp dụng: Phase 2 (Pilot) trở đi**
>
> Giải pháp trong tài liệu này **chưa cần thiết ở MVP1 (Phase 1)**. Lý do:
>
> - Phase 1 dùng **một TimescaleDB instance với nhiều schema** (`environment`, `esg`, `traffic`) — cross-schema JOIN vẫn hoàn toàn khả thi, bài toán "không có JOIN cross-module" chưa phát sinh.
> - Các Flink job đang hoạt động riêng biệt (`AlertDetectionJob`, `EsgFlinkJob`, `EnvironmentFlinkJob`) đã đáp ứng KR alert < 30s và ESG report < 10 phút.
> - **TimescaleDB Continuous Aggregates** đủ để thay thế ClickHouse cold path ở giai đoạn này — không cần thêm component mới.
>
> Tài liệu này trở nên cần thiết khi:
> - Module bắt đầu tách ra **database riêng biệt** (Phase 2) → SQL JOIN cross-module không còn khả thi
> - Sensor throughput vượt **ngưỡng TimescaleDB** không thể scale thêm bằng tuning
> - Yêu cầu **backfill lịch sử** phức tạp hoặc cross-module analytics real-time xuất hiện

---

## Mục Tiêu

Giải quyết bài toán **reporting đối với dữ liệu chạy real-time** trong modular architecture: mỗi module owns database riêng, không có SQL JOIN cross-module — nhưng ESG report cần tổng hợp dữ liệu từ nhiều module, đồng thời City Operations cần alert trong vòng 30 giây.

---

## 1. Bản Chất Vấn Đề — Dual-Speed Data Problem

Kiến trúc modular tách database theo module — mỗi module owns data của mình, không có SQL JOIN cross-module. Đây là **đúng về mặt nguyên tắc** nhưng tạo ra tension thực tế:

**Business yêu cầu hai tốc độ hoàn toàn khác nhau:**

```
REAL-TIME (Hot Events) — cần < 30 giây
  • AQI vượt ngưỡng nguy hiểm → alert ngay, không chờ 5 phút
  • Lũ lụt cảm biến mực nước tăng → notify operator tức thì
  • Traffic incident phát hiện → dashboard update ngay
  └─ Dùng để: Ra quyết định vận hành, gửi cảnh báo P0/P1

ANALYTICS (Cold Events) — chấp nhận 5-10 phút trễ
  • ESG report hàng tháng/quý → query dữ liệu tổng hợp
  • Dashboard trend chart (AQI 7 ngày qua) → không cần real-time
  • Cross-district comparison → cần JOIN từ nhiều module
  └─ Dùng để: Báo cáo, phân tích, lập kế hoạch
```

**Vấn đề cốt lõi:** Với modular architecture, mỗi tốc độ yêu cầu một chiến lược khác nhau — và **hai chiến lược này NẾU thiết kế riêng lẻ sẽ tạo ra data pipeline trùng lặp và khó maintain**.

---

## 2. Tại Sao Các Giải Pháp "Tưởng Đúng" Đều Thất Bại

**Giải pháp A — Cho ESG module gọi REST API của module khác:**
```
ESG-module → GET /environment/aqi?from=2025-Q1
           → GET /traffic/emission?from=2025-Q1   (5 calls × timeout × latency)
           → GET /energy/consumption?from=2025-Q1
```
Thất bại vì: N×API latency, tight coupling, không đảm bảo snapshot consistency, quarterly query = timeout.

**Giải pháp B — ESG module subscribe tất cả Kafka events và tự aggregate:**
```
ESG module listens: UIP.environment.aqi.* + UIP.traffic.* + UIP.energy.* + ...
                    → tự build materialized view trong memory/DB
```
Thất bại vì:

| Vấn Đề | Hệ Quả Thực Tế |
|--------|----------------|
| Backfill lịch sử | Recompute ESG report 2 năm trước → replay toàn bộ Kafka, block production hours |
| Schema drift ngầm | environment-module đổi schema lên v3, ESG consumer chưa update → **silent data loss không có error** |
| ESG module biết quá nhiều | ESG phải hiểu format của 5 module khác → coupling bị giấu vào consumer code |
| Cross-module time correlation | JOIN AQI reading + traffic incident cùng thời điểm trong consumer = manual stateful logic |

**Giải pháp C — Chỉ dùng hai pipeline riêng (real-time stream + batch ETL):**
```
Pipeline 1: Kafka → Flink Streaming → Redis  (real-time)
Pipeline 2: Kafka → batch job hàng giờ → ClickHouse  (analytics)
```
Thất bại vì: **Cùng một event phải xử lý hai lần** — logic transformation duplicate, schema change phải update ở 2 chỗ, operational overhead nhân đôi. Đây là bẫy của Lambda Architecture.

---

## 3. Giải Pháp — Kappa Architecture với Flink Unified Streaming

**Nguyên tắc cốt lõi:** Một pipeline Flink duy nhất xử lý **cùng một luồng event** nhưng fork ra hai output với window size khác nhau.

```
                Kafka Topics (tất cả modules)
                       │
          ┌────────────┴────────────────┐
          │                             │
UIP.environment.aqi.*.v1     UIP.traffic.incident.*.v1
UIP.environment.sensor.*.v1  UIP.energy.reading.*.v1
          │                             │
          └────────────┬────────────────┘
                       │
                       ▼
      ┌────────────────────────────────────┐
      │      FLINK UNIFIED STREAMING JOB   │
      │                                    │
      │  Source: Multi-topic Kafka consumer │
      │  Watermark: event-time, 30s delay  │
      │  State Backend: RocksDB            │
      │                                    │
      │  ┌──────────┐    ┌──────────────┐  │
      │  │ HOT PATH │    │  COLD PATH   │  │
      │  │ 30s-1min │    │  10min-1hour │  │
      │  │  window  │    │   window     │  │
      │  └────┬─────┘    └──────┬───────┘  │
      └───────│─────────────────│───────────┘
              │                 │
      ┌───────▼──────┐  ┌───────▼──────────┐
      │    Redis      │  │    ClickHouse     │
      │  (real-time   │  │  (denormalized    │
      │   cache +     │  │   analytics       │
      │   alert push) │  │   read model)     │
      └───────┬───────┘  └───────┬──────────┘
              │                  │
      ┌───────▼──────┐  ┌────────▼─────────┐
      │ City Ops Dash │  │ ESG Reports /    │
      │ (SSE/WS push) │  │ Analytics Dashboard│
      │ Alert Engine  │  │ (chậm 5-10 phút) │
      └──────────────┘  └──────────────────┘
```

**Lý do Kappa thắng Lambda ở đây:**
- Cùng 1 job → schema change chỉ update 1 chỗ
- Backfill: replay Kafka offset → cùng 1 job chạy lại, tự điền cả Redis lẫn ClickHouse
- Logic transformation viết 1 lần dùng cho cả 2 path

---

## 4. Hot Path — Xử Lý Event Khẩn Cấp Real-Time

**Yêu cầu:** AQI vượt ngưỡng → Flink phát hiện → alert trong vòng 30 giây kể từ khi sensor gửi.

```
Kafka event (sensor_timestamp: T)
    → Flink nhận (T + network ~1-2s)
    → Tumbling window 30s trigger (T + 30s max)
    → Alert logic evaluate
    → Publish back to Kafka alert topic (T + 31s)
    → Notification module consume → push to operator (T + 33s)
    → Operator nhận alert: T + ~35s  ✓ (đạt P1 SLA < 5 phút)
```

**Kỹ thuật Flink cho Hot Path:**

```java
// Multi-source consumer — 1 job đọc tất cả module topics
DataStream<SensorEvent> aqiStream = env
    .fromSource(KafkaSource.<SensorEvent>builder()
        .setTopics("UIP.environment.aqi.reading.v1")
        .setValueOnlyDeserializer(new SensorEventDeserializer())
        .build(), WatermarkStrategy
            // Sensor IoT đôi khi gửi trễ do buffer → cho phép 30s late arrival
            .forBoundedOutOfOrderness(Duration.ofSeconds(30))
            .withTimestampAssigner((e, ts) -> e.getSensorTimestamp()),
        "aqi-source");

// Hot Path: Cửa sổ nhỏ, detect threshold breach
DataStream<AlertEvent> hotAlerts = aqiStream
    .keyBy(SensorEvent::getDistrictCode)
    .window(TumblingEventTimeWindows.of(Time.seconds(30)))
    .aggregate(new AqiThresholdAggregator())
    .filter(result -> result.exceedsThreshold())
    .map(result -> AlertEvent.fromAqiResult(result));

// Publish alert ngược lại Kafka để Notification module consume
hotAlerts.sinkTo(KafkaSink.<AlertEvent>builder()
    .setRecordSerializer(KafkaRecordSerializationSchema.builder()
        .setTopic("UIP.environment.aqi.threshold-exceeded.v1")
        .build())
    .build());

// Publish real-time state vào Redis cho Dashboard
aqiStream
    .keyBy(SensorEvent::getDistrictCode)
    .window(TumblingEventTimeWindows.of(Time.minutes(1)))
    .aggregate(new AqiMinuteAggregator())
    .addSink(new RedisSink<>(redisConfig, new AqiRedisMapper()));
    // Redis key: "aqi:district:{code}:latest"
```

### 4.1 Watermark — Vấn Đề Thực Với IoT Sensor

IoT sensors không lý tưởng. Một sensor ở vùng sóng yếu có thể buffer 5-10 readings rồi gửi cùng lúc:

```
Thực tế nhận được tại Kafka broker:
  T+0s:  sensor_A, event_time=T-45s  ← đến trễ 45s
  T+1s:  sensor_B, event_time=T-2s   ← đến gần real-time
  T+2s:  sensor_A, event_time=T-40s  ← tiếp tục trễ
  T+3s:  sensor_A, event_time=T-35s

Nếu Watermark = 30s bounded out-of-orderness:
  → Event T-45s đến khi watermark đã qua T-30s → bị DROP như "late event"
  → Alert tính AQI thiếu dữ liệu từ sensor_A

Giải pháp: SideOutput cho late events
```

```java
OutputTag<SensorEvent> lateEventTag = new OutputTag<SensorEvent>("late-events"){};

SingleOutputStreamOperator<AqiResult> mainStream = aqiStream
    .keyBy(SensorEvent::getDistrictCode)
    .window(TumblingEventTimeWindows.of(Time.seconds(30)))
    .allowedLateness(Time.minutes(2))  // cửa sổ mở thêm 2 phút cho late events
    .sideOutputLateData(lateEventTag)  // events trễ hơn 2 phút → side output
    .aggregate(new AqiThresholdAggregator());

// Late events → ghi vào ClickHouse với flag "corrected" thay vì drop
DataStream<SensorEvent> lateEvents = mainStream.getSideOutput(lateEventTag);
lateEvents.addSink(new ClickHouseSink("sensor_readings_corrections"));
```

---

## 5. Cold Path — Analytics và ESG Reporting

**Yêu cầu:** ESG report cần cross-module aggregation. Chấp nhận lag 5-10 phút.

**Vấn đề khó nhất — Cross-Module Streaming Join:**

Để tính "Chỉ số sức khỏe môi trường theo district", Flink cần JOIN:
- AQI readings (environment-module stream)
- Vehicle count + emission (traffic-module stream)
- Cùng district_code, cùng time window 1 giờ

```java
// Cold Path: Window Join cross-module streams
DataStream<EnvTrafficCorrelation> coldJoin = aqiStream
    .keyBy(e -> e.getDistrictCode())
    .window(TumblingEventTimeWindows.of(Time.minutes(10)))
    .aggregate(new AqiHourlyAggregator())    // aggregate environment first

    // Interval Join với traffic stream trong cùng time window
    .keyBy(agg -> agg.getDistrictCode())
    .intervalJoin(
        trafficStream
            .keyBy(t -> t.getDistrictCode())
            .window(TumblingEventTimeWindows.of(Time.minutes(10)))
            .aggregate(new TrafficHourlyAggregator())
            .keyBy(agg -> agg.getDistrictCode())
    )
    .between(Time.minutes(-1), Time.minutes(1)) // tight window align
    .process(new EnvTrafficCorrelationJoiner());

// Sink vào ClickHouse — denormalized, không cần join khi query
coldJoin.addSink(new ClickHouseSink<>(
    "INSERT INTO city_metrics_hourly VALUES (?, ?, ?, ?, ?, ?)",
    new CityMetricsRowMapper()
));
```

### 5.1 ClickHouse Schema — Denormalized Read Model

```sql
-- Không bao giờ JOIN khi query — tất cả đã được flatten tại ingestion time
CREATE TABLE city_metrics_hourly (
    hour              DateTime,
    district_code     LowCardinality(String),
    -- từ environment-module (via Flink aggregation)
    aqi_avg           Float32,
    aqi_max           Float32,
    pm25_avg          Float32,
    pm10_avg          Float32,
    sensor_count      UInt16,     -- bao nhiêu sensors contribute
    -- từ traffic-module (via Flink join)
    vehicle_count     UInt32,
    co2_estimate_kg   Float32,
    incident_count    UInt8,
    -- từ energy-module
    kwh_consumed      Float32,
    renewable_pct     Float32,
    -- từ citizen-module
    complaints_count  UInt16,
    -- metadata
    data_completeness Float32,    -- 0.0 → 1.0, thiếu sensor → < 1.0
    flink_job_id      String,     -- tracing: job nào ghi row này
    ingested_at       DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(ingested_at)  -- idempotent upsert khi reprocess
PARTITION BY toYYYYMM(hour)
ORDER BY (district_code, hour);
```

### 5.2 ESG Report Query — Chỉ 1 Query, Không JOIN

```sql
-- ESG Q1/2026 toàn thành phố — query duy nhất
SELECT
    district_code,
    avg(aqi_avg)                              AS quarterly_aqi,
    sum(co2_estimate_kg) / 1000              AS total_co2_tonnes,
    avg(renewable_pct)                        AS avg_renewable_pct,
    sum(complaints_count)                     AS total_env_complaints,
    avg(data_completeness)                    AS data_quality_score
FROM city_metrics_hourly
WHERE hour BETWEEN '2026-01-01' AND '2026-03-31'
GROUP BY district_code
ORDER BY quarterly_aqi DESC;
-- Execution time: < 2 giây cho 90 ngày × 24 quận
```

---

## 6. State Management — Vấn Đề Ẩn Của Streaming Join

Cross-module join trong Flink yêu cầu **giữ state** — tức là buffer dữ liệu từ stream A chờ match với stream B. Với 100K events/sec, state có thể phình to nhanh.

```
Tình huống nguy hiểm:
  environment-module gửi 1000 AQI readings/phút
  traffic-module chỉ gửi 50 readings/phút (cập nhật thưa hơn)

  Flink phải buffer 1000 AQI readings chờ 50 traffic readings để JOIN
  Sau 1 giờ: 60,000 AQI events đang chờ trong RocksDB state
  Sau 1 ngày: state size ~ vài GB → OOM hoặc Flink checkpoint chậm
```

**Giải pháp — State TTL bắt buộc:**

```java
// State phải có TTL để tránh memory leak
StateTtlConfig ttlConfig = StateTtlConfig
    .newBuilder(Time.minutes(15))           // state sống tối đa 15 phút
    .setUpdateType(UpdateType.OnCreateAndWrite)
    .setStateVisibility(StateVisibility.NeverReturnExpired)
    .build();

ValueStateDescriptor<AqiAggregation> stateDescriptor =
    new ValueStateDescriptor<>("aqi-state", AqiAggregation.class);
stateDescriptor.enableTimeToLive(ttlConfig);
```

**Chiến lược JOIN khi stream lệch tốc độ:**

```
Option A — Enrichment Join (thay vì full join)
  Dùng khi: Một stream là "dimension" (thứ thay đổi chậm)
  Ví dụ: traffic data (thay đổi mỗi 5 phút) enrich AQI stream (thay đổi mỗi 30s)
  → AQI stream là driving stream, lookup traffic state gần nhất
  → Không cần chờ match → không tích lũy state vô hạn

Option B — Pre-aggregate rồi mới join (khuyến nghị cho Cold Path)
  Mỗi module stream → 10-minute window aggregate trước
  → Số lượng records giảm từ 100K/min xuống còn ~50 records (1 per district)
  → Join 50 records × 50 records = trivial
```

---

## 7. Backfill — Khi Cần Recompute Lại Lịch Sử

Đây là điểm mạnh nhất của Kappa Architecture so với Lambda:

```
Tình huống: Phát hiện lỗi trong AQI calculation formula vào 2026-04-01
            Cần recompute city_metrics_hourly từ 2025-01-01

Với Lambda (batch + streaming riêng):
  → Phải chạy lại batch job riêng
  → Đảm bảo batch job dùng cùng logic với streaming job (thường là KHÔNG)
  → 2 nơi cần fix → dễ inconsistent

Với Kappa (Flink Unified):
  → Fix logic trong 1 Flink job
  → Start job mới với Kafka offset = 2025-01-01
  → Job chạy lại, tự điền ClickHouse với dữ liệu corrected
  → ReplacingMergeTree trong ClickHouse → upsert tự động, không duplicate
  → Xong, không cần ETL script riêng
```

**Prerequisites cho backfill hoạt động:**
- Kafka retention phải đủ dài (khuyến nghị: **minimum 90 ngày** cho smart city data)
- Event phải mang `event_time` (sensor timestamp), không phải `processing_time`
- ClickHouse table dùng `ReplacingMergeTree` để idempotent reprocess

---

## 8. Tóm Tắt Event Journey

```
EVENT JOURNEY — từ sensor đến dashboard

Sensor gửi reading tại T=0
  │
  ▼ T+1s
Kafka topic (event_time preserved)
  │
  ├──────────────────────────────────────────────┐
  │                                              │
  ▼ (Hot Path)                                   ▼ (Cold Path)
T+1s: Flink nhận                           T+1s: Cùng Flink job
T+31s: 30s window trigger                  T+601s: 10min window trigger
T+32s: Threshold check                     T+605s: Cross-module join
T+33s: Alert published to Kafka            T+610s: Write to ClickHouse
T+35s: Notification module → operator     (data available for ESG query)
  │
  └─ T+60s: 1min aggregate → Redis
             Redis key: aqi:district:Q1:latest
             Dashboard SSE push to browser
```

---

## 9. Điều Kiện Tiên Quyết & Trade-off

| Yếu Tố | Mức Độ | Giải Quyết |
|--------|--------|------------|
| Flink operational complexity | **Cao** | Cần DevOps có kinh nghiệm Flink, checkpoint tuning, RocksDB sizing |
| Kafka retention cost (90 ngày) | Trung bình | ~2-3x storage cost, cần lên kế hoạch capacity |
| Schema Registry bắt buộc | **Cao** | Không có Schema Registry → cross-module join dễ break khi schema change |
| State backend sizing | Cao | Undersize RocksDB → Flink checkpoint slow → backpressure → alert delay |
| Late event strategy | Trung bình | Quyết định business: drop hay accept late → phải rõ ràng trong SLA |
| Watermark tuning per sensor type | Trung bình | Sensor nhanh (30s window) vs sensor chậm (5min) cần watermark khác nhau |

---

*Tài liệu này là draft, cần review bởi team trước khi chốt quyết định kiến trúc.*
