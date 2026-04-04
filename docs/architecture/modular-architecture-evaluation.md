# Đánh Giá Kiến Trúc Backend: Modular Architecture

**Ngày**: 2026-04-03  
**Tác giả**: UIP Solution Architect  
**Trạng thái**: Draft — Cần Review

---

## Mục Lục

1. [Bối Cảnh & Vấn Đề](#1-bối-cảnh--vấn-đề)
2. [Tại Sao Cần Module Độc Lập](#2-tại-sao-cần-module-độc-lập)
3. [Enable/Disable Module](#3-enabledisable-module)
4. [Tích Hợp Module Từ Đối Tác](#4-tích-hợp-module-từ-đối-tác)
5. [Bảng Ưu Nhược Điểm Toàn Diện](#5-bảng-ưu-nhược-điểm-toàn-diện)
6. [So Sánh Technology Options](#6-so-sánh-technology-options)
7. [Architecture Decision Records](#7-architecture-decision-records)
8. [Kiến Trúc Khuyến Nghị](#8-kiến-trúc-khuyến-nghị)
9. [Lộ Trình Thực Hiện](#9-lộ-trình-thực-hiện)
10. [Ý Kiến Phản Biện — 3 Thách Thức Cốt Lõi](#phụ-lục-a-ý-kiến-phản-biện--3-thách-thức-cốt-lõi)

---

## 1. Bối Cảnh & Vấn Đề

### 1.1 Thực Trạng Phase 1 — Đang Ở Đâu

UIP đang xây dựng **Modular Monolith** — một Spring Boot application, nhiều package/module tách biệt, một TimescaleDB với nhiều schema. Đây là lựa chọn đúng cho giai đoạn hiện tại:

- **Team**: 5 người, 9 tuần, 1 thành phố duy nhất
- **Yêu cầu thực tế**: Tất cả module đều cần thiết, không có city nào cần bật/tắt module
- **Chưa có partner thật**: Traffic dùng HTTP adapter fake data, không có Singapore/Korea platform

> **Kết luận Phase 1:** Modular Monolith với package boundary rõ ràng là **đủ và đúng**. Không cần Spring Modulith, không cần Kafka cross-module events nội bộ, không cần tách DB vật lý. Những gì tài liệu này mô tả là **đích đến Phase 2-3**, không phải yêu cầu hiện tại.

### 1.2 Tại Sao Cần Nghĩ Đến Modular Ngay Bây Giờ

Dù chưa cần implement đầy đủ, **thiết kế module boundary đúng từ đầu** ảnh hưởng trực tiếp đến khả năng scale sau này:

- Code không gọi chéo business logic giữa các module → dễ tách ra sau
- Schema DB tách biệt (logical) → dễ tách physical khi cần
- Event contract rõ ràng → không phải redesign khi thêm city

**Ba câu hỏi kiến trúc cần trả lời cho Phase 2+:**

1. **Tại sao cần tách thành module độc lập?** — Coupling ngầm gây ra rủi ro gì khi scale?
2. **Enable/Disable module theo deployment** — Làm thế nào để cùng một codebase phục vụ nhu cầu khác nhau của từng thành phố?
3. **Đối tác đã có module sẵn** — Tích hợp trực tiếp hay cần thêm lớp trung gian?

### 1.3 Nhu Cầu Thực Tế Per-City (Khi Scale)

| Thành Phố / Đối Tác | Cần | Không Cần | Đã Có Sẵn |
|---------------------|-----|-----------|-----------|
| TP.HCM (Phase 1) | Tất cả modules | — | — |
| Đà Nẵng | Environment + Traffic | Energy (chưa có smart grid) | — |
| Đối tác Singapore | ESG reporting | IoT ingestion | IoT Platform riêng |
| Đối tác Hàn Quốc | Citizen portal | Environment | Environment system riêng |

---

## 2. Tại Sao Cần Module Độc Lập

### 2.1 Nguyên Tắc Bounded Context

Mỗi module đại diện cho một **bounded context** — một miền nghiệp vụ độc lập với:
- Ngôn ngữ domain riêng (ubiquitous language)
- Schema dữ liệu riêng
- Vòng đời deploy độc lập
- Team ownership rõ ràng

### 2.2 Lợi Ích Kỹ Thuật

**Fault Isolation:**
```
Monolith:     Sensor crash → ESG down → Notification down → Dashboard down
Modular:      Sensor crash → chỉ iot-module affected → ESG vẫn chạy từ cache
```

**Independent Scaling:**
```
IoT ingestion: 100K events/sec → cần scale iot-module x10
ESG reporting: 1 request/hour → giữ nguyên esg-module x1
```

**Deploy Independence:**
```
Fix bug ở notification-module → deploy chỉ notification-module
Không ảnh hưởng environment-module đang xử lý sensor data
```

### 2.3 Giao Tiếp Giữa Modules — Event-Driven

Modules KHÔNG gọi trực tiếp nhau. Tất cả giao tiếp qua Kafka EventBus:

```
[environment-module]
        │
        │ publish: UIP.environment.aqi.threshold-exceeded.v1
        ▼
[Kafka EventBus]
        │
        ├──▶ [alert-module]        (consume → tạo alert)
        ├──▶ [esg-module]          (consume → cập nhật ESG metric)
        └──▶ [notification-module] (consume → gửi thông báo)
```

**Kafka Topic Naming Convention:**
```
UIP.{module}.{entity}.{event-type}.v{n}

Ví dụ:
  UIP.iot.sensor.reading.v1
  UIP.environment.aqi.threshold-exceeded.v1
  UIP.traffic.incident.detected.v1
  UIP.citizen.complaint.submitted.v1
  UIP.esg.report.generated.v1
```

### 2.4 Database Separation

| Module | Database | Lý Do |
|--------|----------|-------|
| iot-module | TimescaleDB | Time-series sensor data, 100K/sec ingestion |
| environment-module | TimescaleDB + PostGIS | Time-series + spatial queries |
| esg-module | PostgreSQL + ClickHouse | ACID transactions + analytics aggregation |
| notification-module | PostgreSQL + Redis | Queue state + cache |
| analytics-module | ClickHouse | OLAP, columnar, fast GROUP BY |

**Anti-pattern cần tránh:**
- Module A KHÔNG được direct query database của Module B
- Chỉ giao tiếp qua Event hoặc API contract đã định nghĩa

---

## 3. Enable/Disable Module

### 3.1 Cơ Chế — Spring Modulith + Config

```yaml
# deployment-config-danang.yaml
uip:
  modules:
    iot:
      enabled: true
      provider: internal
    environment:
      enabled: true
      provider: internal
    traffic:
      enabled: true
      provider: internal
    energy:
      enabled: false          # Đà Nẵng chưa cần
    citizen:
      enabled: true
      provider: internal
    esg:
      enabled: true
      provider: internal
    notification:
      enabled: true
      provider: internal
```

```java
// Module tự load conditionally
@ConditionalOnProperty("uip.modules.energy.enabled")
@ApplicationModule(allowedDependencies = {"iot-module", "shared"})
public class EnergyModuleConfiguration {
    // Bean chỉ được tạo khi energy.enabled = true
}
```

### 3.2 Các Mức Enable/Disable

| Mức | Cách Thực Hiện | Use Case |
|-----|---------------|----------|
| **Feature Flag** | `@ConditionalOnProperty` | Tắt tính năng nhỏ, A/B test |
| **Module Config** | Spring Modulith + YAML | Tắt cả module cho một city |
| **Deploy Topology** | K8s — không deploy pod | Module không cần thiết cho region |
| **Kafka Routing** | Consumer group filter | Module không consume topic nhất định |

### 3.3 Config Matrix Per City

```
                    HCM   DaNang   Singapore   Korea
iot-module           ✓      ✓        -           ✓
environment-module   ✓      ✓        -           -
traffic-module       ✓      ✓        ✓           ✓
energy-module        ✓      -        ✓           ✓
citizen-module       ✓      ✓        -           ✓
esg-module           ✓      ✓        ✓           ✓
notification-module  ✓      ✓        ✓           ✓

(-) = disabled hoặc replaced bởi partner module
```

---

## 4. Tích Hợp Module Từ Đối Tác

### 4.1 Anti-Corruption Layer (ACL) — Bắt Buộc

Khi đối tác đã có hệ thống riêng, UIP KHÔNG tích hợp trực tiếp. Phải qua **Anti-Corruption Layer**:

```
[Partner System]
      │
      │ Partner's own format/protocol
      ▼
[ACL Adapter Service]        ← UIP kiểm soát lớp này
      │
      │ Transform → UIP Canonical Event Schema
      ▼
[Kafka EventBus]
      │
      ▼
[UIP Internal Modules]       ← Không biết partner tồn tại
```

**Lý do ACL bắt buộc:**
- Partner thay đổi API format → chỉ cần cập nhật adapter, không ảnh hưởng core
- Schema validation tập trung tại một điểm
- Audit log đầy đủ tại boundary

### 4.2 Canonical Event Schema

Mọi data — dù từ internal module hay partner — đều phải conform schema này trước khi vào Kafka:

```json
{
  "eventType": "UIP.iot.sensor.reading.v1",
  "eventId": "uuid-v4",
  "timestamp": "2026-04-03T10:00:00Z",
  "source": {
    "type": "internal | partner",
    "partnerId": "sg-smartcity-platform",
    "adapterVersion": "1.2.0"
  },
  "sensorId": "SG-ENV-001",
  "location": {
    "lat": 1.3521,
    "lon": 103.8198,
    "districtCode": "SG-CENTRAL"
  },
  "readings": {
    "pm25": 15.3,
    "pm10": 28.1,
    "aqi": 62
  },
  "quality": {
    "confidence": 0.95,
    "rawValueHash": "sha256:abc123"
  }
}
```

### 4.3 Adapter Implementation Pattern

```java
// Adapter cho Singapore IoT Platform
@Component
@ConditionalOnProperty(
    name = "uip.modules.iot.provider",
    havingValue = "sg-partner"
)
public class SingaporeIoTAdapter implements IoTDataPort {

    private final KafkaProducer kafkaProducer;
    private final SchemaValidator validator;

    // Nhận event từ Singapore platform (WebSocket/REST/MQTT)
    public void onSingaporeEvent(SgSensorEvent sgEvent) {
        // Transform sang canonical schema
        SensorReadingEvent uipEvent = transform(sgEvent);

        // Validate trước khi publish
        validator.validate(uipEvent);

        // Publish vào UIP Kafka
        kafkaProducer.publish("UIP.iot.sensor.reading.v1", uipEvent);
    }

    private SensorReadingEvent transform(SgSensorEvent sgEvent) {
        return SensorReadingEvent.builder()
            .sensorId(sgEvent.getDeviceId())           // map field name
            .location(mapLocation(sgEvent.getCoords())) // transform format
            .readings(mapReadings(sgEvent.getData()))   // unit conversion nếu cần
            .source(Source.partner("sg-smartcity"))
            .build();
    }
}
```

---

## 5. Bảng Ưu Nhược Điểm Toàn Diện

### 5.1 Modular Internal Architecture

| Khía Cạnh | Ưu Điểm | Nhược Điểm | Mức Độ |
|-----------|---------|-----------|--------|
| **Deploy** | Deploy độc lập, zero-downtime per module | Nhiều pipeline CI/CD hơn | Medium |
| **Scale** | Scale từng module theo load thực tế | Chi phí K8s pods tăng | Low |
| **Team** | Team ownership rõ ràng, ít conflict | Cần phối hợp khi thay đổi event schema | Medium |
| **Fault Isolation** | Crash 1 module không kéo theo module khác | Distributed tracing phức tạp hơn | High+ |
| **Testing** | Unit test isolated, contract test qua Kafka | Integration test cần full test harness | Medium |
| **City Customization** | Enable/disable per deployment dễ dàng | Quản lý config matrix nhiều môi trường | Medium |
| **Data Sovereignty** | Mỗi module owns data của mình | Cross-module reporting cần event join | Low |

### 5.2 Partner Module Integration

| Khía Cạnh | Ưu Điểm | Nhược Điểm | Mức Độ |
|-----------|---------|-----------|--------|
| **Time-to-Market** | Không cần build lại những gì đối tác đã có | Adapter development vẫn mất thời gian | Medium |
| **Cost** | Tiết kiệm development cost | Chi phí license/API call đối tác | Medium |
| **Quality** | Đối tác chuyên sâu domain của họ | Khó kiểm soát data quality từ partner | High |
| **Reliability** | Đối tác tự maintain uptime | SLA phụ thuộc vào đối tác | **High** |
| **Vendor Lock-in** | Onboard nhanh | Lock-in nếu không có ACL đúng cách | **High** |
| **Debugging** | Đối tác chịu trách nhiệm module của họ | Khó debug khi incident cross-boundary | High |
| **Data Sovereignty** | Không cần đầu tư infrastructure | Data citizen có thể ra ngoài hệ thống | **Critical** |
| **Schema Evolution** | Partner tự upgrade | Breaking change từ partner ảnh hưởng ACL | High |

### 5.3 So Sánh Ba Chiến Lược Deployment

| Tiêu Chí | Monolith | Modular Internal | Partner Integration |
|---------|:--------:|:----------------:|:-------------------:|
| Initial Development Cost | Thấp | Cao | Trung bình |
| Ongoing Maintenance Cost | Cao (tech debt) | Trung bình | Trung bình-Cao |
| City Customization | Khó | **Dễ** | Dễ |
| Fault Isolation | Kém | **Tốt** | Phức tạp |
| Data Sovereignty | **Tốt** | **Tốt** | Rủi ro |
| Vendor Lock-in Risk | Thấp | Thấp | **Cao** nếu không có ACL |
| Operational Complexity | Thấp | Cao | Trung bình |
| Scale Flexibility | Kém | **Tốt** | Phụ thuộc partner |
| Team Autonomy | Kém | **Tốt** | Trung bình |
| Time-to-first-city | **Nhanh** | Chậm | Trung bình |

---

## 6. So Sánh Technology Options

Phần này so sánh các lựa chọn công nghệ cho **5 vấn đề kiến trúc cốt lõi** của modular architecture. Mỗi mục nêu rõ context, các option, tiêu chí đánh giá và khuyến nghị cuối cùng.

---

### 6.1 Module Framework — Cách Tổ Chức Code Thành Modules

**Context:** Chọn framework để enforce module boundaries và manage inter-module dependencies trong Java.

| Tiêu Chí | Spring Modulith | Plain Spring Boot (packages) | OSGi / Karaf | Microservices ngay từ đầu |
|----------|:--------------:|:---------------------------:|:------------:|:------------------------:|
| Enforce boundary tại compile time | **Tốt** | Không có | Tốt | N/A (network boundary) |
| Học và setup | **Dễ** | Dễ nhất | Khó | Trung bình |
| Deploy linh hoạt (mono → micro) | **Tốt** | Kém | Trung bình | Không (đã micro rồi) |
| Runtime overhead | Thấp | Thấp | Cao | Cao (network latency) |
| Test isolation | Tốt | Kém | Tốt | Tốt |
| Ecosystem & community | Tốt | Tốt | Nhỏ, giảm dần | Tốt |
| Phù hợp team size nhỏ-vừa | **Tốt** | Tốt | Kém | Kém |
| Path to microservice khi cần | **Rõ ràng** | Khó refactor | Phức tạp | Đã là micro |

**Kết luận:** **Spring Modulith** — cho phép bắt đầu như monolith có discipline, tách ra microservice từng bước khi cần mà không phải rewrite.

> **Rủi ro cần lưu ý:** Spring Modulith còn khá mới (stable từ 1.1/2023), community nhỏ hơn các lựa chọn khác. Cần cân nhắc nếu team chưa quen.

---

### 6.2 Event Bus / Message Broker — Giao Tiếp Giữa Modules

**Context:** Chọn hệ thống messaging để modules giao tiếp async, đảm bảo at-least-once delivery cho sensor events và alerts.

| Tiêu Chí | Apache Kafka | RabbitMQ | AWS SQS/SNS | Redis Pub/Sub | NATS JetStream |
|----------|:-----------:|:--------:|:-----------:|:-------------:|:--------------:|
| Throughput (sensor 100K/sec) | **Xuất sắc** | Tốt | Tốt | Tốt | Xuất sắc |
| Message retention & replay | **Tốt** (configurable) | Kém (queue-based) | Trung bình | Không có | Tốt |
| Schema evolution | **Tốt** (Schema Registry) | Trung bình | Trung bình | Không có | Trung bình |
| Ordering guarantee | **Partition-level** | Queue-level | Không | Không | Subject-level |
| Flink integration | **Xuất sắc** | Trung bình | Trung bình | Kém | Trung bình |
| Operational complexity | Cao | Trung bình | Thấp (managed) | Thấp | Trung bình |
| Self-hosted cost | Trung bình | Thấp | N/A | Thấp | Thấp |
| Cloud-managed option | MSK (AWS), Confluent | CloudAMQP | Native | Redis Cloud | Synadia Cloud |
| Vendor lock-in | Thấp | Thấp | **Cao** | Thấp | Thấp |
| Dead Letter Queue | Tốt | **Tốt** | Tốt | Không có | Tốt |

**Kết luận:** **Apache Kafka** — throughput phù hợp IoT 100K/sec, replay capability cho sensor data reprocessing, tích hợp Flink tốt nhất. Dùng **Confluent Schema Registry** để quản lý event schema versioning.

> **Cân nhắc thay thế:** Nếu team nhỏ và ops burden là concern chính, **NATS JetStream** là lựa chọn nhẹ hơn với performance tương đương. Tuy nhiên ecosystem Flink với NATS kém hơn Kafka đáng kể.

---

### 6.3 Module Configuration & Feature Flag — Quản Lý Enable/Disable

**Context:** Chọn giải pháp để enable/disable module per deployment (per city) mà không cần rebuild code.

| Tiêu Chí | Spring Cloud Config | HashiCorp Consul | LaunchDarkly | Flagsmith (OSS) | Kubernetes ConfigMap |
|----------|:------------------:|:----------------:|:------------:|:---------------:|:--------------------:|
| Dynamic reload không restart | Tốt | **Tốt** | **Tốt** | Tốt | Kém (cần restart) |
| Per-environment config | **Tốt** | Tốt | Tốt | Tốt | Tốt |
| Git-backed config | **Tốt** | Kém | Không | Trung bình | Không |
| Audit trail thay đổi | Trung bình | Tốt | **Tốt** | Tốt | Kém |
| Self-hosted | **Tốt** | **Tốt** | Không (SaaS) | **Tốt** | Tốt |
| Integration với Spring Boot | **Tốt** | Tốt | Trung bình | Trung bình | Tốt |
| Complexity | Thấp | Cao | Thấp | Trung bình | Thấp |
| Feature flag granularity | Cơ bản | Cơ bản | **Cao** | **Cao** | Không có |

**Kết luận (2 tầng):**
- **Tầng 1 — Module enable/disable:** Spring Cloud Config + K8s ConfigMap — đơn giản, Git-backed, phù hợp per-city deployment
- **Tầng 2 — Feature flags chi tiết (A/B, gradual rollout):** Flagsmith OSS — self-hosted, không vendor lock-in, đủ tính năng

> **Không khuyến nghị:** LaunchDarkly — SaaS, chi phí cao cho enterprise plan, không self-hosted.

---

### 6.4 API Gateway — Entry Point Cho Tất Cả Modules

**Context:** Chọn API gateway để route requests đến các modules, xử lý authn/authz, rate limiting, và hỗ trợ partner integration.

| Tiêu Chí | Kong (OSS) | AWS API Gateway | Nginx + Lua | Traefik | Spring Cloud Gateway |
|----------|:----------:|:---------------:|:-----------:|:-------:|:--------------------:|
| Plugin ecosystem | **Tốt** | Trung bình | Kém | Trung bình | Trung bình |
| Rate limiting | **Tốt** | Tốt | Custom | Tốt | Tốt |
| JWT / OAuth2 | **Tốt** | Tốt | Custom | Trung bình | Tốt |
| gRPC support | Tốt | Trung bình | Kém | **Tốt** | Trung bình |
| K8s native | Tốt (Ingress) | Không | Trung bình | **Tốt** | Tốt |
| Declarative config | Tốt | Tốt | Kém | **Tốt** | Tốt |
| Admin UI | Tốt (Konga) | Tốt | Không | **Tốt** | Không |
| Performance | **Cao** | Cao | Cao | Cao | Trung bình |
| Self-hosted | **Tốt** | Không | **Tốt** | **Tốt** | **Tốt** |
| Vendor lock-in | Thấp | **Cao** | Thấp | Thấp | Thấp |

**Kết luận:** **Kong OSS** — plugin ecosystem phong phú (rate limiting, JWT, CORS, logging), self-hosted, phù hợp multi-tenant per-city routing. Kết hợp với **Istio** cho service mesh ở tầng nội bộ (East-West traffic).

> **Lưu ý phân tầng:**
> - **Kong** → North-South traffic (external clients → modules)
> - **Istio** → East-West traffic (module → module, chỉ cho REST; Kafka event không qua đây)

---

### 6.5 Database Strategy — Time-Series, Analytics, và Operational

**Context:** Chọn database phù hợp cho từng loại workload: sensor time-series, OLAP analytics, và ACID transactions.

#### 6.5.1 Time-Series Database (Hot Sensor Data — 30 ngày gần nhất)

| Tiêu Chí | TimescaleDB | InfluxDB OSS | Victoria Metrics | QuestDB | Apache IoTDB |
|----------|:-----------:|:------------:|:----------------:|:-------:|:------------:|
| PostgreSQL compatible | **Tốt** | Không | Không | Không | Không |
| Ingestion rate | Tốt | **Cao** | **Cao** | **Cao** | Cao |
| SQL support | **Tốt** | Flux/InfluxQL | MetricsQL | **Tốt** | SQL-like |
| Compression | **Tốt** | Tốt | **Tốt** | Tốt | Tốt |
| Retention policy | Tốt | Tốt | Tốt | Tốt | Tốt |
| PostGIS integration | **Tốt** | Không | Không | Không | Không |
| Ecosystem maturity | **Tốt** | Tốt | Trung bình | Trung bình | Đang phát triển |
| Self-hosted ops | Trung bình | Dễ | Dễ | Dễ | Trung bình |

**Kết luận:** **TimescaleDB** — SQL compatibility giúp reuse PostgreSQL skills, PostGIS integration cho spatial queries trên sensor location, ecosystem mature. Trade-off: ingestion rate không bằng InfluxDB/VictoriaMetrics nhưng đủ cho 100K/sec với tuning đúng.

#### 6.5.2 Analytics Database (Dashboard, Aggregation, Reporting)

| Tiêu Chí | ClickHouse | Apache Druid | Apache Pinot | DuckDB | BigQuery |
|----------|:----------:|:------------:|:------------:|:------:|:--------:|
| Query speed (GROUP BY time) | **Xuất sắc** | Xuất sắc | Xuất sắc | Tốt | Tốt |
| Real-time ingestion | Tốt | **Tốt** | **Tốt** | Kém | Kém |
| Self-hosted complexity | Trung bình | Cao | Cao | **Thấp** | Không |
| SQL standard | **Tốt** | Trung bình | Trung bình | **Tốt** | Tốt |
| Flink sink support | **Tốt** | Tốt | Tốt | Kém | Tốt |
| Horizontal scaling | Tốt | **Tốt** | **Tốt** | Không | Tốt |
| Vendor lock-in | Thấp | Thấp | Thấp | Thấp | **Cao** |
| Community | **Tốt** | Trung bình | Trung bình | Đang tăng | Tốt |

**Kết luận:** **ClickHouse** — query performance tốt nhất cho dashboard aggregation, SQL compatible, Flink sink mature, self-hosted feasible. Trade-off: operational phức tạp hơn DuckDB nhưng scale tốt hơn nhiều.

#### 6.5.3 Tóm Tắt Database Matrix

```
Workload                    Database              Lý Do
────────────────────────────────────────────────────────────────────
Sensor time-series (hot)    TimescaleDB           Time-partition, PostGIS, SQL
Analytics & dashboards      ClickHouse            Columnar OLAP, fast aggregation
Spatial queries (GIS)       PostGIS               ST_Within, spatial index
ACID: config, audit, BPMN   PostgreSQL            Consistency, transactions
Cache, real-time state      Redis                 In-memory, TTL, pub/sub
Cold archive (>30 days)     MinIO (S3)            Object storage, low cost
```

---

### 6.6 Service Communication — Internal Modules (East-West)

**Context:** Khi modules cần giao tiếp synchronous (query state, không phải event), chọn protocol gì.

| Tiêu Chí | REST (HTTP/1.1) | gRPC | GraphQL | Async Event (Kafka) | Internal Direct Call |
|----------|:--------------:|:----:|:-------:|:-------------------:|:-------------------:|
| Performance | Trung bình | **Cao** | Trung bình | Cao (async) | **Cao nhất** |
| Type safety | Kém | **Tốt** | Tốt | Tốt (Schema Registry) | Tốt (Java type) |
| Contract first | Tốt (OpenAPI) | **Tốt** (proto) | Tốt | Tốt (Avro/JSON Schema) | Không |
| Browser friendly | **Tốt** | Kém | **Tốt** | Không | Không |
| Streaming support | Kém | **Tốt** | Subscription | **Tốt** | Không |
| Module coupling | Thấp | Thấp | Thấp | **Rất thấp** | **Cao** |
| Debugging | **Dễ** | Trung bình | Trung bình | Trung bình | Dễ |

**Kết luận (theo use case):**

| Use Case | Protocol | Lý Do |
|----------|----------|-------|
| Module → Module business events | Kafka (async) | Loose coupling, replay, fanout |
| Module expose API cho Application | REST (OpenAPI) | Browser-friendly, dễ debug |
| High-frequency internal queries | gRPC | Performance, type-safe, streaming |
| Direct method call (cùng JVM) | Chỉ trong module | Không cross module boundary |

---

### 6.7 Partner Integration Protocol — ACL Adapter Input

**Context:** Đối tác có thể dùng nhiều protocol khác nhau để push data vào ACL adapter.

| Protocol | Phù Hợp Khi | Ưu Điểm | Nhược Điểm |
|----------|------------|---------|-----------|
| **MQTT** | IoT device / sensor gateway | Lightweight, low bandwidth, QoS levels | Cần MQTT broker, không phổ biến ở enterprise |
| **REST Webhook** | Partner push event khi có dữ liệu | Đơn giản, universal, dễ test | Cần expose public endpoint, retry logic ở partner |
| **REST Polling** | Partner không hỗ trợ push | Không cần partner config | Latency cao, polling overhead |
| **Kafka (direct)** | Partner là tech company, có Kafka | Throughput cao, tự nhiên | Partner phải chạy Kafka compatible |
| **WebSocket** | Real-time streaming từ partner | Low latency | Stateful connection, complex management |
| **SFTP/Batch file** | Legacy partner, batch reporting | Đơn giản với legacy | Latency cao (minutes-hours), không real-time |

**Khuyến nghị ACL adapter hỗ trợ (theo priority):**

```
Priority 1 (bắt buộc): REST Webhook + REST Polling
  → Universal, mọi partner đều implement được

Priority 2 (khuyến khích): MQTT
  → IoT-native partners, performance tốt

Priority 3 (tùy chọn): Kafka Mirror Maker 2
  → Partner có hạ tầng Kafka riêng

Priority 4 (legacy support): SFTP Batch
  → Partner cũ chưa có real-time capability
```

---

### 6.8 Tổng Hợp — Technology Stack Recommendation

| Layer | Khuyến Nghị | Thay Thế Chấp Nhận Được | Không Nên Dùng |
|-------|------------|------------------------|----------------|
| Module Framework | Spring Modulith | Plain Spring Boot (strict packages) | OSGi, Microservices ngay |
| Event Bus | Apache Kafka | NATS JetStream | RabbitMQ (replay kém), SQS (lock-in) |
| Config Management | Spring Cloud Config | Consul | LaunchDarkly (SaaS cost) |
| Feature Flags | Flagsmith OSS | Spring Cloud Config (basic) | LaunchDarkly |
| API Gateway | Kong OSS | Traefik | AWS API Gateway (lock-in) |
| Service Mesh | Istio | Linkerd (nhẹ hơn) | Bỏ qua nếu team nhỏ |
| Time-Series DB | TimescaleDB | InfluxDB OSS | VictoriaMetrics (ít SQL) |
| Analytics DB | ClickHouse | Apache Druid | BigQuery (lock-in) |
| Operational DB | PostgreSQL | — | MySQL (spatial kém hơn) |
| Spatial | PostGIS | — | Separate GIS service |
| Cache | Redis | — | Memcached (no pub/sub) |
| Object Storage | MinIO | AWS S3 (nếu cloud-first) | Local filesystem |
| Partner Protocol | REST Webhook | MQTT, Kafka MM2 | Direct DB access từ partner |

---

## 7. Architecture Decision Records

### ADR-001: Module Autonomy via Bounded Context + Kafka

**Date**: 2026-04-03 | **Status**: Proposed

**Context:**
Cần support nhiều thành phố với nhu cầu khác nhau. Các module hiện tại coupling ngầm qua shared database và direct method calls.

**Decision:**
Áp dụng **Modular Architecture** với Spring Modulith. Mỗi module:
- Có package boundary được enforce tại compile time
- Giao tiếp với module khác chỉ qua Kafka events
- Có database schema riêng (logical separation tối thiểu, physical preferred)
- Có thể enable/disable độc lập qua `@ConditionalOnProperty`

**Consequences:**
- **Positive**: Fault isolation, independent scaling, clear team ownership, enable/disable per city
- **Trade-offs**: Tăng complexity ops, cần mature DevOps practices, distributed tracing bắt buộc

**Điều Kiện Để Thành Công:**
- Schema Registry cho Kafka event versioning
- Contract testing giữa modules
- Centralized config management (Spring Cloud Config)

---

### ADR-002: Anti-Corruption Layer cho Partner Integration

**Date**: 2026-04-03 | **Status**: Proposed

**Context:**
Một số đối tác đã có sẵn IoT platform, environment monitoring system. Tích hợp trực tiếp tạo tight coupling với partner's internal format.

**Decision:**
Mọi partner module integration **bắt buộc qua ACL Adapter Service**. ACL:
- Translate partner format → UIP Canonical Event Schema
- Validate schema trước khi publish vào Kafka
- Implement retry + DLQ cho reliability
- Emit audit log tại integration boundary

**Consequences:**
- **Positive**: Partner thay đổi internal không ảnh hưởng UIP core; schema validation tập trung; có thể swap partner mà không ảnh hưởng downstream modules
- **Trade-offs**: Cần maintain adapter per partner; thêm latency ~5-15ms; adapter là single point of failure nếu không có HA

**Non-negotiable:**
- KHÔNG bao giờ bypass ACL để tích hợp trực tiếp
- ACL phải có HA deployment (min 2 replicas)
- Partner SLA phải được contract rõ ràng

---

### ADR-003: Database Per Module (Logical Separation)

**Date**: 2026-04-03 | **Status**: Proposed

**Context:**
Hiện tại tất cả modules dùng chung schema trong một PostgreSQL instance.

**Decision:**
**Phase 1** (PoC → Pilot): Separate schemas trong cùng PostgreSQL instance — `schema_iot`, `schema_environment`, `schema_esg`. Module chỉ được access schema của mình.

**Phase 2** (Production): Separate PostgreSQL instances per module cluster. Cross-module data chỉ qua Kafka events hoặc read-only API.

**Consequences:**
- **Positive**: Ngăn chặn cross-module database coupling; mỗi module tự optimize schema; migrate database độc lập
- **Trade-offs**: Cross-module reporting phức tạp hơn; cần event-driven data sync; JOIN query không còn possible

---

## 7. Kiến Trúc Khuyến Nghị

### 7.1 Target Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      Partner Systems                            │
│  [Singapore IoT Platform]      [Korean Env. System]             │
└──────────────┬──────────────────────────┬───────────────────────┘
               │ Partner format           │ Partner format
               ▼                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                   ACL Layer (UIP Controls)                      │
│  [sg-iot-adapter]              [kr-env-adapter]                 │
│      Transform + Validate          Transform + Validate          │
└──────────────┬──────────────────────────┬───────────────────────┘
               │ UIP Canonical Schema      │ UIP Canonical Schema
               ▼                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Kafka EventBus                               │
│   UIP.iot.sensor.reading.v1  │  UIP.environment.aqi.*.v1       │
│   UIP.traffic.incident.*.v1  │  UIP.citizen.complaint.*.v1     │
└──────┬──────────┬─────────────┬──────────┬────────────┬─────────┘
       ▼          ▼             ▼          ▼            ▼
┌────────────┐ ┌──────────┐ ┌──────┐ ┌────────┐ ┌──────────────┐
│ environment│ │ traffic  │ │ esg  │ │citizen │ │notification  │
│  -module   │ │ -module  │ │-mod  │ │ -mod   │ │  -module     │
│            │ │          │ │      │ │        │ │              │
│ TimescaleDB│ │Timescale │ │PG +  │ │  PG    │ │  PG + Redis  │
│ + PostGIS  │ │+ PostGIS │ │Click │ │        │ │              │
└────────────┘ └──────────┘ └──────┘ └────────┘ └──────────────┘
       │              │          │         │              │
       └──────────────┴──────────┴─────────┴──────────────┘
                              │
                    ┌─────────▼──────────┐
                    │  analytics-module  │
                    │    ClickHouse      │
                    │  (read from all)   │
                    └────────────────────┘
                              │
                    ┌─────────▼──────────┐
                    │   Applications     │
                    │ City Ops / ESG Dash│
                    │ Citizen Portal     │
                    └────────────────────┘
```

### 7.2 Module Interaction Rules

| Rule | Được Phép | Không Được Phép |
|------|-----------|----------------|
| Module → Module | Kafka event publish/consume | Direct REST call, direct DB query |
| Module → Shared Library | Import shared utilities | Import business logic từ module khác |
| Partner → UIP | Qua ACL adapter | Direct Kafka publish, direct DB write |
| Application → Module | REST API của module đó | Direct DB query, bypass API |

---

## 8. Lộ Trình Thực Hiện

### Phase 1: PoC Stabilization (Hiện Tại)
- [ ] Enforce package boundaries trong codebase hiện tại
- [ ] Document event contracts giữa các chức năng
- [ ] Setup Spring Modulith để detect cross-module violations

### Phase 2: Pilot — Một Thành Phố (3-6 tháng)
- [ ] Tách `environment-module` thành service riêng
- [ ] Tách `notification-module` thành service riêng
- [ ] Setup Kafka Schema Registry
- [ ] Build ACL framework chung

### Phase 3: Multi-City (6-12 tháng)
- [ ] Config matrix per-city deployment
- [ ] Admin Console để manage module enable/disable
- [ ] Partner onboarding toolkit (ACL template + schema validator)
- [ ] Distributed tracing (Jaeger/Zipkin) across modules

### Phase 4: Platform (12+ tháng)
- [ ] Module marketplace — partner tự đăng ký adapter
- [ ] Schema compliance certification cho partner modules
- [ ] Self-service city deployment (GitOps per city)

---

## Phụ Lục A: Ý Kiến Phản Biện — 3 Thách Thức Cốt Lõi

Phần này tóm tắt 3 thách thức thực tế của kiến trúc modular, phân biệt rõ **áp dụng ngay** vs **để Phase 2+**. Phân tích chi tiết và code sample cho phần Phase 2+ được lưu tại [phase2-architecture-challenges.md](./phase2-architecture-challenges.md).

---

### Thách Thức 1: Reporting — Real-Time vs. Data Lake

**Vấn đề cốt lõi:** Khi module tách ra database riêng (Phase 2+), SQL JOIN cross-module không còn khả thi. ESG report cần tổng hợp từ nhiều module, đồng thời City Operations cần alert < 30 giây — hai yêu cầu này xung đột trực tiếp nếu không có chiến lược rõ ràng.

> **Lưu ý Phase 1 (MVP1 hiện tại):** Tất cả module đang dùng chung một TimescaleDB instance với nhiều schema (`environment`, `esg`, `traffic`). Cross-schema JOIN vẫn khả thi. Các Flink job hiện tại (`AlertDetectionJob`, `EsgFlinkJob`) đã đáp ứng KR alert < 30s và ESG report < 10 phút. **TimescaleDB Continuous Aggregates** đủ thay thế cold path phức tạp ở giai đoạn này — chưa cần Kappa Architecture hay ClickHouse.

**Các giải pháp sai phổ biến (khi module đã tách DB):**
- ESG module gọi REST API từng module → N×latency, tight coupling, quarterly query timeout
- ESG subscribe tất cả Kafka topics và tự aggregate → schema drift ngầm, silent data loss, ESG biết quá nhiều về internal format của module khác
- Hai pipeline riêng (Lambda Architecture) → logic transformation duplicate, schema change phải update 2 chỗ

**Giải pháp khuyến nghị cho Phase 2+ — Kappa Architecture với Flink Unified Streaming:**

Một Flink job duy nhất fork hai path từ cùng Kafka stream:
- **Hot Path** (window 30s → 1min): Threshold detection → alert Kafka → Redis cache → SSE push dashboard
- **Cold Path** (window 10min → 1h): Cross-module stream join → ClickHouse denormalized read model

ClickHouse lưu `city_metrics_hourly` đã flatten sẵn (AQI + traffic + energy + citizen) → ESG query chỉ cần 1 câu SQL, không JOIN, < 2 giây cho 90 ngày dữ liệu.

**Điều kiện tiên quyết:** Module đã tách database riêng, Kafka retention ≥ 90 ngày, Schema Registry, RocksDB state sizing, State TTL cho streaming join.

> Phân tích chi tiết (Flink code, ClickHouse schema, State TTL, Backfill strategy, Watermark handling) — áp dụng từ Phase 2:  
> → [realtime-reporting-architecture.md](./realtime-reporting-architecture.md)

---

### Thách Thức 2: Stale Data & Partner Reliability

> **Áp dụng ngay (Phase 1):** Stale sensor detection — áp dụng cho mọi sensor, kể cả internal.  
> **Phase 2+ (khi có partner thật):** Circuit Breaker, SLA tracking, ESG data completeness report.

**Vấn đề thực sự — áp dụng ngay cả khi chưa có partner:**

Trong event-driven architecture, **"không có event" và "sensor bình thường không gửi gì"** trông giống hệt nhau từ góc nhìn downstream. Sensor mất kết nối, battery chết, sóng yếu → dashboard vẫn hiển thị giá trị cũ mà không cảnh báo.

**Giải pháp Phase 1 — Stale Data Detection:**

```java
// Nếu sensor không có reading trong N phút → đánh dấu data gap
@Scheduled(fixedDelay = 60_000)
public void detectDataGaps() {
    List<Sensor> staleSensors = sensorRepo.findSensorsWithNoReadingSince(
        Instant.now().minus(sensorStalenessThreshold)
    );
    staleSensors.forEach(sensor ->
        eventBus.publish(SensorDataGapDetectedEvent.of(sensor))
    );
}
```

**Graceful Degradation trên UI:**
- Hiển thị giá trị cuối cùng + timestamp rõ ràng ("Data from HH:MM") — không fake real-time
- Dashboard đánh dấu sensor mất kết nối bằng màu khác, không im lặng

**Phase 2+ — khi onboard partner thật:**

Bài toán phức tạp hơn: partner system down → ACL adapter không nhận data → không rõ là "không có sự kiện" hay "có sự kiện nhưng bị mất". Cần thêm: Heartbeat metric per adapter, ESG report completeness check, SLA contract rõ ràng trước khi ký.

> **Câu hỏi phải trả lời trước khi onboard partner:** Nếu partner down 1 ngày, ai chịu trách nhiệm về data gap trong ESG report gửi thành phố — UIP hay partner? Đây là quyết định business, không phải kỹ thuật.

---

### Thách Thức 3: Deployment Governance — Multi-City Consistency

> **Áp dụng ngay (Phase 1):** Nguyên tắc Stable Core / Flexible Edge làm kim chỉ nam khi ra quyết định thiết kế.  
> **Phase 2+ (khi có city thứ 2):** Release Train, GitOps per city, Deployment Dashboard.

**Vấn đề sẽ xuất hiện ở Phase 2:** Module flexibility mà không có governance rõ ràng → version matrix chaos khi có 3+ cities. Mỗi city chạy version khác nhau, không ai biết tại sao, security patch không propagate kịp.

**Nguyên tắc áp dụng ngay — Stable Core / Flexible Edge:**

Dùng như mental model khi thiết kế tính năng, không phải tooling cần implement ngay:

| Stable Core — Platform quyết định | Flexible Edge — City có thể tùy chỉnh |
|----------------------------------|--------------------------------------|
| Security patches (bắt buộc, không exception) | Module enable/disable per city |
| Kafka event schema (versioned, backward compatible) | Partner adapter (city viết, platform review) |
| AuthN/AuthZ (không city nào có exception) | Feature flags trong phạm vi cho phép |
| Monitoring/Alerting stack | UI config (ngôn ngữ, mã quận huyện) |

**Phase 2+ — khi onboard city thứ 2:**

Bài toán version matrix thực sự xuất hiện. Cần: Release Train schedule (tất cả cities upgrade đồng bộ, có exception process), GitOps per city (ArgoCD + helm values repo), Deployment Dashboard (ai đang chạy gì ở đâu).

> **Câu hỏi phải trả lời trước khi onboard city thứ 2:** Khi một city có compliance requirement về data residency (dữ liệu phải ở trong lãnh thổ), module nào cần thay đổi? Ai quyết định — platform team hay city team? Ranh giới governance này phải rõ trước, không phải sau khi có vấn đề.

---

### Tổng Hợp — Ma Trận Quyết Định Cho 3 Thách Thức

| Thách Thức | Làm Ngay (Phase 1) | Phase 2+ |
|------------|-------------------|----------|
| **Reporting Real-time vs Lake** | TimescaleDB cross-schema JOIN + Continuous Aggregates | Kappa Architecture — Flink Unified + ClickHouse (khi module tách DB riêng) |
| **Stale Data & Partner Reliability** | Stale sensor detection + UI data gap marker | Circuit Breaker adapter, ESG completeness check, Partner SLA contract |
| **Deployment Governance** | Nguyên tắc Stable Core / Flexible Edge (mental model) | Release Train, GitOps per city, Deployment Dashboard (khi có city thứ 2) |

---

### Kết Luận Phản Biện Tổng Thể

Modular architecture là **hướng đúng** cho UIP platform. Ba thách thức trên **có thực** nhưng xuất hiện ở các giai đoạn khác nhau — giải quyết sớm hơn mức cần thiết là over-engineering, giải quyết muộn hơn là nợ kỹ thuật.

Nguyên tắc chung: **xây khi vấn đề thực sự xuất hiện, thiết kế để dễ thêm vào sau** — không phải thiết kế cho mọi kịch bản từ đầu.

---

## Phụ Lục B: Checklist Trước Khi Tích Hợp Partner

- [ ] Partner cung cấp SLA uptime (minimum 99.5%)?
- [ ] ACL adapter đã được build và test?
- [ ] Schema validation tại ACL boundary đã bật?
- [ ] DLQ (Dead Letter Queue) cho failed transformations đã setup?
- [ ] Data sovereignty review — citizen PII có bị expose ra ngoài không?
- [ ] Incident runbook cho khi partner system down?
- [ ] Contract testing giữa adapter và UIP core?
- [ ] Monitoring alert khi adapter error rate > 1%?

---

*Tài liệu này là draft, cần review bởi team trước khi chốt quyết định kiến trúc.*
