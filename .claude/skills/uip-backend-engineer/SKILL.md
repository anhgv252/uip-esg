---
name: uip-backend-engineer
description: >
  UIP Backend Engineer skill. Domain knowledge for: Java/Spring Boot implementation,
  IoT sensor data ingestion, Kafka consumer/producer for sensor streams, Flink stream
  processing jobs for urban metrics, TimescaleDB/ClickHouse/PostGIS queries,
  PostgreSQL JPA entities, unit tests (JUnit5/Mockito/Testcontainers), API implementation,
  ESG metric calculation services, alert notification services, HikariCP tuning,
  Spring AOP, @Transactional patterns, Kubernetes ConfigMap injection.
---

# UIP Backend Engineer

You are a **Senior Backend Engineer** for the UIP Smart City system. You write production-quality Java code following the project's established patterns for urban data platforms.

## Project Stack

- **Java 17** with modern features (records, sealed classes, text blocks, pattern matching)
- **Spring Boot 3.1.x** + Spring Modulith + Spring Data JPA + Spring Security
- **Apache Kafka 3.6.x** — sensor event streaming, exactly-once semantics
- **Apache Flink 1.19.x** — real-time stream processing, RocksDB state backend
- **TimescaleDB 2.13.x** — time-series sensor data (PostgreSQL extension)
- **ClickHouse 23.8.x** — analytics queries (columnar, partition-aware)
- **PostgreSQL 15** + **PostGIS 3.4** — operational data, spatial queries
- **Redis 7.2** — caching, distributed locks, rate limiting, real-time counters
- **Eclipse Mosquitto MQTT 5.0** — IoT device communication bridge
- **JUnit 5 + Mockito 5 + Testcontainers** — testing
- **Maven** — build management
- **Kubernetes** — deployment (ConfigMap, Secrets injection)

## Code Style & Conventions

### Java Code Standards
```java
// Use records for DTOs
public record SensorReadingDto(
    String sensorId,
    String location,
    double latitude,
    double longitude,
    Instant timestamp,
    Map<String, Double> metrics  // e.g. {"aqi": 125.5, "pm25": 45.2}
) {}

// Use constructor injection (NOT @Autowired field injection)
@Service
@RequiredArgsConstructor
@Slf4j
public class AirQualityIngestionService {
    private final SensorReadingRepository repository;
    private final EventBus eventBus;
    private final AlertThresholdConfig thresholdConfig;
}

// Use Optional — NEVER return null
public Optional<SensorReading> findLatestReading(String sensorId) {
    return repository.findTopBySensorIdOrderByTimestampDesc(sensorId);
}

// Use domain-specific exceptions
public class SensorNotFoundException extends RuntimeException {
    public SensorNotFoundException(String sensorId) {
        super("Sensor not found: " + sensorId);
    }
}
```

### Package Structure (per module)
```
com.uip.{module}/
├── api/          ← REST controllers, request/response DTOs
├── domain/       ← Entities, domain objects, enums
├── service/      ← Business logic services
├── repository/   ← JPA repositories, custom queries
├── event/        ← Event publishers, listeners
├── config/       ← Spring configuration beans
└── exception/    ← Domain-specific exceptions
```

### @Transactional Pattern
```java
// ALWAYS on service layer, NOT on repository
@Service
@Transactional(readOnly = true)
public class EnvironmentService {

    @Transactional
    public SensorReading recordReading(SensorReadingCommand cmd) {
        SensorDevice sensor = sensorRepository.findBySensorId(cmd.sensorId())
            .orElseThrow(() -> new SensorNotFoundException(cmd.sensorId()));
        SensorReading reading = SensorReading.from(cmd, sensor);
        SensorReading saved = repository.save(reading);
        // eventBus.publish() AFTER transaction — use @TransactionalEventListener
        return saved;
    }
}
```

## Kafka Patterns for Sensor Streams

### Topic Naming Convention — BẮT BUỘC từ Phase 1

```
UIP.{source-module}.{entity}.{event-type}.v{n}

Ví dụ:
  UIP.iot.sensor.reading.v1                    ← sensor gửi reading
  UIP.environment.aqi.threshold-exceeded.v1   ← environment module phát hiện ngưỡng
  UIP.flink.alert.detected.v1                 ← Flink job output alert
  UIP.esg.report.generated.v1                 ← ESG report hoàn thành
```

**Quy tắc cứng:**
- KHÔNG dùng tên ngắn kiểu `alert_events`, `sensor_data` — phải theo convention
- Topic name là constant trong code (`public static final String TOPIC = "UIP...."`)
- Không hardcode string literal trực tiếp trong `@KafkaListener` — dùng constant
- Sau khi thêm topic: **cập nhật `docs/deployment/kafka-topic-registry.xlsx`**

```java
// ✅ ĐÚNG — topic là constant, không hardcode string
public static final String TOPIC = "UIP.flink.alert.detected.v1";

@KafkaListener(topics = AlertEventKafkaConsumer.TOPIC, groupId = "uip-backend-alerts", ...)

// ❌ SAI — string literal trực tiếp
@KafkaListener(topics = "alert_events", ...)
```

### Producer
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class SensorEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(AqiThresholdExceededEvent event) {
        kafkaTemplate.send(
            "UIP.environment.aqi.threshold-exceeded.v1",
            event.sensorId(),  // partition key = sensor location cluster
            event
        ).whenComplete((result, ex) -> {
            if (ex != null) log.error("Failed to publish sensor event sensorId={}", event.sensorId(), ex);
        });
    }
}
```

### Consumer (exactly-once with manual commit + DLQ)
```java
@KafkaListener(topics = "UIP.iot.sensor.reading.v1",
               groupId = "environment-module",
               containerFactory = "kafkaListenerContainerFactory")
public void onSensorReading(
    @Payload SensorReadingEvent event,
    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
    Acknowledgment ack) {
    try {
        environmentService.processReading(event);
        ack.acknowledge();
    } catch (Exception e) {
        log.error("Processing failed for sensor={} at timestamp={}",
                  event.sensorId(), event.timestamp(), e);
        throw e;  // DLQ routing handled by error handler
    }
}
```

## Flink Stream Processing (IoT)

```java
public class AqiAlertDetectionJob {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.setStateBackend(new EmbeddedRocksDBStateBackend());
        env.getCheckpointConfig().setCheckpointInterval(30_000);  // 30s for IoT
        env.getCheckpointConfig().setCheckpointStorage("s3://minio/flink-checkpoints");

        KafkaSource<SensorReadingEvent> source = KafkaSource.<SensorReadingEvent>builder()
            .setBootstrapServers("kafka:9092")
            .setTopics("UIP.iot.sensor.reading.v1")
            .setGroupId("flink-aqi-alert")
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SensorReadingDeserializer())
            .build();

        env.fromSource(source, WatermarkStrategy
                .<SensorReadingEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                .withTimestampAssigner((event, ts) -> event.timestamp().toEpochMilli()),
            "Sensor Stream")
            .filter(e -> "AIR_QUALITY".equals(e.sensorType()))
            .keyBy(SensorReadingEvent::locationZone)
            .window(SlidingEventTimeWindows.of(Time.minutes(5), Time.minutes(1)))
            .aggregate(new AqiAverageAggregator())
            .filter(avg -> avg.value() > AQI_UNHEALTHY_THRESHOLD)
            .addSink(new AlertEventSink());  // publishes to UIP.environment.aqi.threshold-exceeded.v1

        env.execute("AQI Alert Detection Job");
    }
}
```

## TimescaleDB Query Patterns

```java
// Time-series query with hypertable partitioning
String query = """
    SELECT sensor_id,
           time_bucket('5 minutes', timestamp) AS bucket,
           avg(aqi_value) AS avg_aqi,
           max(aqi_value) AS max_aqi
    FROM sensor_readings
    WHERE timestamp >= NOW() - INTERVAL '1 hour'
      AND sensor_type = 'AIR_QUALITY'
      AND district_code = ?
    GROUP BY sensor_id, bucket
    ORDER BY bucket DESC
    """;

// Always use time range in WHERE clause — enables partition pruning
// Never use SELECT * on TimescaleDB hypertables
```

## PostGIS Spatial Query Patterns

```java
// Find all sensors within radius of incident
String spatialQuery = """
    SELECT s.sensor_id, s.sensor_name, s.sensor_type,
           ST_Distance(s.location::geography, ST_MakePoint(?, ?)::geography) AS distance_meters
    FROM sensors s
    WHERE ST_DWithin(s.location::geography, ST_MakePoint(?, ?)::geography, ?)
      AND s.is_active = true
    ORDER BY distance_meters
    """;
// Parameters: longitude, latitude, longitude, latitude, radius_meters
```

## ClickHouse Query Patterns

```java
// Analytics: ESG trend over time
String analyticsQuery = """
    SELECT toStartOfDay(recorded_at) AS date,
           avg(aqi_value)            AS avg_aqi,
           avg(pm25_value)           AS avg_pm25,
           countIf(aqi_value > 200)  AS unhealthy_hours
    FROM uip.environment_readings
    WHERE recorded_at BETWEEN toDateTime(?) AND toDateTime(?)
      AND city_zone IN (?)
    GROUP BY date
    ORDER BY date
    """;
// Always use partition pruning; never SELECT *
```

## Alert Notification Service

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertNotificationService {

    @EventListener
    @Async
    public void onAqiThresholdExceeded(AqiThresholdExceededEvent event) {
        AlertLevel level = determineLevel(event.aqiValue());

        log.info("AQI alert triggered sensorId={} zone={} aqi={} level={}",
                 event.sensorId(), event.locationZone(), event.aqiValue(), level);

        // P0/P1: always deliver — no silencing
        notificationGateway.broadcast(AlertNotification.builder()
            .level(level)
            .zone(event.locationZone())
            .message(buildAlertMessage(event))
            .channels(level.getChannels())  // P0: ALL_CHANNELS, P1: APP+SMS
            .build());
    }

    private AlertLevel determineLevel(double aqi) {
        if (aqi > 300) return AlertLevel.P0_EMERGENCY;
        if (aqi > 200) return AlertLevel.P1_WARNING;
        if (aqi > 150) return AlertLevel.P2_ADVISORY;
        return AlertLevel.P3_INFO;
    }
}
```

## Unit Testing Standards

```java
@ExtendWith(MockitoExtension.class)
class AirQualityIngestionServiceTest {

    @Mock SensorReadingRepository repository;
    @Mock EventBus eventBus;
    @InjectMocks AirQualityIngestionService service;

    @ParameterizedTest
    @CsvSource({"201, P1_WARNING", "301, P0_EMERGENCY", "150, P3_INFO"})
    @DisplayName("Should determine correct alert level for AQI value")
    void shouldDetermineAlertLevel(double aqiValue, String expectedLevel) {
        AlertLevel level = service.determineLevel(aqiValue);
        assertThat(level.name()).isEqualTo(expectedLevel);
    }
}

// Integration tests with Testcontainers
@SpringBootTest
@Testcontainers
class SensorReadingRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("timescale/timescaledb:2.13-pg15");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Test
    void shouldPersistAndRetrieveSensorReading() { ... }
}
```

## HikariCP Tuning (IoT high-frequency ingestion)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 10000
      connection-test-query: "SELECT 1"
```

## ESG Metric Calculation

```java
@Service
@RequiredArgsConstructor
public class EsgMetricAggregationService {

    @Scheduled(cron = "0 0 1 1 */3 *")  // Quarterly
    @Transactional
    public EsgReport generateQuarterlyReport(YearQuarter quarter) {
        EnvironmentalMetrics env = environmentRepository.aggregateForQuarter(quarter);
        SocialMetrics social = citizenRepository.aggregateSocialForQuarter(quarter);
        GovernanceMetrics gov = governanceRepository.aggregateForQuarter(quarter);

        EsgReport report = EsgReport.builder()
            .quarter(quarter)
            .environmental(env)
            .social(social)
            .governance(gov)
            .iso37120Score(iso37120Calculator.calculate(env, social, gov))
            .build();

        eventBus.publish(EsgReportGeneratedEvent.from(report));
        return repository.save(report);
    }
}
```

## Module Boundary Rules (Anti-Coupling)

**Nguyên tắc cứng: Mỗi module là một bounded context độc lập.**

### Coupling ngầm bị cấm — NEVER DO

```java
// ❌ FORBIDDEN: ESG module inject trực tiếp service của Environment module
@Service
public class EsgReportService {
    private final EnvironmentService environmentService;  // COUPLING NGẦM!
    // → ESG phụ thuộc hard vào internal của Environment module
}

// ❌ FORBIDDEN: Import entity từ module khác
import com.uip.environment.domain.SensorReading;  // ESG không được dùng
public class EsgReportService {
    private final SensorReadingRepository sensorRepo;  // COUPLING NGẦM!
}

// ❌ FORBIDDEN: Shared mutable state qua static/singleton cross-module
EnvironmentCache.INSTANCE.getLatestAqi(zone);  // global state = coupling

// ❌ FORBIDDEN: Package scan cross-module
@ComponentScan({"com.uip.environment", "com.uip.esg"})  // trộn lẫn
```

### Ranh giới tầng giao tiếp — hiểu đúng để chọn đúng

```
Frontend (React) ──── REST API ────► Controller (@RestController)
                                          │
                                    Service Layer
                                          │
                          ┌───────────────┴───────────────┐
                          │  Module nội bộ (same package)  │  Module khác (cross-module)
                          │  → gọi service trực tiếp OK    │  → Kafka hoặc gRPC hoặc Port
                          └────────────────────────────────┘
```

**REST chỉ dùng cho external** — frontend, mobile app, external partner. KHÔNG dùng REST để gọi giữa modules nội bộ.

### Cách đúng — Cross-module access chỉ qua contract API

```java
// ✅ CORRECT: ESG module dùng API contract, không dùng internal

// Option A — Port Interface (Phase 1, same JVM — đơn giản nhất)
@Service
public class EsgReportService {
    private final EnvironmentMetricsPort environmentMetrics;  // interface, không biết impl
}

// Option B — gRPC (Phase 2, separate service — khi tách microservice)
@Service
public class EsgReportService {
    private final EnvironmentMetricsGrpcClient environmentClient;  // gRPC stub
}

// Option C — Kafka event (async notification, không cần response)
@KafkaListener(topics = "UIP.environment.aqi.threshold-exceeded.v1")
public void onAqiThresholdExceeded(AqiThresholdExceededEvent event) {
    // ESG lắng nghe event, không gọi Environment trực tiếp
}
```

### Dấu hiệu code đang bị coupling ngầm

- Import path `com.uip.{moduleA}.*` trong code của `{moduleB}`
- JPA `@OneToMany` / `@ManyToOne` cross-module entity
- `ApplicationContext.getBean()` lấy bean từ module khác
- SQL JOIN cross-schema (xem section dưới)

---

## Cross-Schema Query Rules

**Quy tắc cứng: Mỗi module chỉ query schema của chính nó.**

Phase 1 có tất cả schemas trong cùng TimescaleDB instance — JOIN vẫn hoạt động về mặt kỹ thuật, nhưng **KHÔNG ĐƯỢC PHÉP** vì sẽ bị coupling DB khi tách ra Phase 2.

### Cross-schema JOIN — FORBIDDEN

```sql
-- ❌ FORBIDDEN: ESG JOIN với bảng của Environment
SELECT e.report_id, s.aqi_value
FROM esg.reports e
JOIN environment.sensor_readings s       -- cross-schema JOIN!
    ON e.district_code = s.district_code
WHERE e.quarter = '2026-Q1';

-- ❌ FORBIDDEN: Native query từ ESG repo dùng bảng Environment
@Query(value = """
    SELECT * FROM environment.sensor_readings   -- WRONG: ESG query environment schema
    WHERE timestamp >= :start
    """, nativeQuery = true)
```

### Đúng — Fetch tách biệt, join trong service layer

```java
// ✅ CORRECT: ESG service fetch từ 2 nguồn tách biệt
@Service
public class EsgReportService {
    private final EsgReportRepository esgRepo;             // ESG schema only
    private final EnvironmentMetricsGrpcClient envClient;  // gRPC to Environment module

    public EsgReport generateReport(YearQuarter quarter) {
        // Bước 1: Fetch ESG data từ schema riêng
        EsgBaseData baseData = esgRepo.findBaseDataForQuarter(quarter);

        // Bước 2: Fetch environment data qua gRPC (không query thẳng DB)
        AqiAggregateResponse aqiData = envClient.getAqiAggregate(
            quarter.startDate(), quarter.endDate()
        );

        // Bước 3: Combine trong service layer (không trong SQL)
        return EsgReport.combine(baseData, aqiData);
    }
}
```

### Kiểm tra query scope trước khi viết

Trước khi viết bất kỳ SQL/JPQL nào, kiểm tra:
- Tất cả bảng trong query có thuộc schema của module hiện tại không?
- Nếu cần data từ module khác → dùng gRPC hoặc Kafka, không JOIN

---

## Inter-Module Communication — Kafka vs gRPC

Hai cơ chế chính, chọn theo pattern phù hợp:

| Tiêu chí | **Kafka (async event)** | **gRPC (sync query)** |
|----------|------------------------|----------------------|
| **Response cần ngay** | Không | Có |
| **Caller chờ kết quả** | Không (fire-and-forget) | Có (request-response) |
| **Nhiều consumer cùng nghe** | Có (fan-out) | Không (1-1) |
| **Data thay đổi theo thời gian** | Có (stream events) | Không (snapshot query) |
| **Retry khi consumer down** | Tự động (Kafka retention) | Manual (caller retry) |
| **Use case điển hình** | Alert, notification, audit log | Query aggregate, fetch reference data |

### Khi nào dùng Kafka

```
Environment → Kafka → ESG (async)
  AqiThresholdExceededEvent → ESG lưu vào alert history
  SensorDataGapDetectedEvent → ESG đánh dấu data gap trong report

Traffic → Kafka → Notification (async)
  TrafficIncidentDetectedEvent → Notification gửi alert đến operator
```

```java
// Kafka: publish event, không quan tâm ai consume
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void publishAqiExceeded(AqiThresholdExceededEvent event) {
    kafkaTemplate.send("UIP.environment.aqi.threshold-exceeded.v1",
        event.sensorId(), event);
}
```

### Khi nào dùng gRPC

```
ESG cần AQI aggregate cho quarterly report → gRPC → Environment
  Request: GetAqiAggregateRequest { districtCode, startDate, endDate }
  Response: AqiAggregateResponse { avgAqi, maxAqi, unhealthyHours }

ESG cần citizen complaint data → gRPC → CitizenPortal
  Request: GetComplaintStatsRequest { quarter }
  Response: ComplaintStatsResponse { totalComplaints, resolvedRate }
```

```java
// gRPC client stub (generated từ .proto file)
@Component
@RequiredArgsConstructor
@Slf4j
public class EnvironmentMetricsGrpcClient {
    private final EnvironmentServiceGrpc.EnvironmentServiceBlockingStub stub;

    public AqiAggregateResponse getAqiAggregate(
            String districtCode, LocalDate start, LocalDate end) {
        try {
            return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                .getAqiAggregate(GetAqiAggregateRequest.newBuilder()
                    .setDistrictCode(districtCode)
                    .setStartDate(start.toString())
                    .setEndDate(end.toString())
                    .build());
        } catch (StatusRuntimeException e) {
            log.error("gRPC call failed districtCode={} status={}", districtCode, e.getStatus());
            throw new ExternalModuleException("Environment module unavailable", e);
        }
    }
}
```

### Proto file convention

```protobuf
// environment/src/main/proto/environment_metrics.proto
syntax = "proto3";
package com.uip.environment.grpc;
option java_package = "com.uip.environment.grpc";

service EnvironmentService {
    rpc GetAqiAggregate (GetAqiAggregateRequest) returns (AqiAggregateResponse);
    rpc GetSensorStatus (GetSensorStatusRequest) returns (SensorStatusResponse);
}

message GetAqiAggregateRequest {
    string district_code = 1;
    string start_date = 2;  // ISO-8601: "2026-01-01"
    string end_date = 3;
}

message AqiAggregateResponse {
    double avg_aqi = 1;
    double max_aqi = 2;
    int32 unhealthy_hours = 3;
    string data_quality = 4;  // "COMPLETE" | "PARTIAL" | "GAP_DETECTED"
}
```

### Phase 1 — gRPC chưa cần nếu cùng Spring context

Nếu Phase 1 chạy monolith (tất cả modules trong cùng Spring application), internal module communication có thể dùng **Spring ApplicationEvent** thay gRPC để giảm complexity:

```java
// Phase 1 OK: Spring ApplicationEvent (same JVM)
// Nhưng phải dùng interface contract, không inject service trực tiếp
public interface EnvironmentMetricsPort {  // Port interface — không expose internal
    AqiAggregateDto getAqiAggregate(String districtCode, LocalDate start, LocalDate end);
}

// Environment module implement port này
@Service
public class EnvironmentMetricsAdapter implements EnvironmentMetricsPort { ... }

// ESG module dùng port, không biết implementation
@Service
public class EsgReportService {
    private final EnvironmentMetricsPort environmentMetrics;  // interface only
}
```

Khi tách microservice → thay `EnvironmentMetricsAdapter` bằng `EnvironmentMetricsGrpcClient`, không sửa ESG code.

---

## Development Workflow

1. Đọc module README trước khi thêm code mới
2. **Check module boundary**: Import nào từ module khác? Có thể thay bằng Port interface không?
3. **Check query scope**: SQL/JPQL có touch schema ngoài module không?
4. Run `./mvnw verify -pl {module}` để xác nhận tests pass
5. Check SonarQube: coverage >= 80%, no critical issues
6. Review SQL/Hibernate logs khi có query performance issues
7. Dùng `@Slf4j` với MDC: `MDC.put("sensorId", sensorId)`, `MDC.put("zone", zone)`

## Khi Thêm Topic hoặc Biến Môi Trường Mới

**Bắt buộc update trước khi merge PR:**
- Kafka topic mới → cập nhật `docs/deployment/kafka-topic-registry.xlsx`
- Env var mới → cập nhật `docs/deployment/environment-variables.xlsx`

Docs reference: `docs/implementation/`, `docs/modules/`, `docs/api/`, `docs/architecture/modular-architecture-evaluation.md`, `docs/deployment/kafka-topic-registry.xlsx`, `docs/deployment/environment-variables.xlsx`
