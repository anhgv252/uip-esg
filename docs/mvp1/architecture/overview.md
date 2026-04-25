# UIP Smart City — Architecture Overview

> **Phiên bản:** 1.0
> **Ngày:** 28/03/2026
> **Dựa trên:** POC Architecture Assessment (26/03/2026)

---

## 1. Tổng quan kiến trúc

UIP Smart City sử dụng kiến trúc **event-driven streaming** với các thành phần rời rạc có thể scale độc lập.

### 1.1 Data Flow

```
IoT Devices / BMS / Third-party
        │
        ▼ MQTT / HTTP / CoAP
   EMQX CE (MQTT Broker)
        │
        ▼ MQTT Bridge
   ThingsBoard CE (Device Management + Rule Engine)
        │
        ▼ Kafka Publisher (Rule Engine node)
   Apache Kafka 3.7 KRaft
   ├─ raw_telemetry        (8 partitions)
   ├─ ngsi_ld_environment  (8 partitions)
   ├─ ngsi_ld_esg          (8 partitions)
   ├─ ngsi_ld_traffic      (4 partitions)
   ├─ alert_events         (4 partitions)
   └─ esg_dlq              (2 partitions)
        │
        ▼
   Redpanda Connect (ETL)
   ├─ Vendor format A/B → NGSI-LD normalization
   ├─ Per-module topic routing
   └─ HTTP adapter: Traffic external system → ngsi_ld_traffic
        │
        ▼
   Apache Flink (Java)
   ├─ EnvironmentFlinkJob → env.sensor_readings, env.aqi_index
   ├─ EsgFlinkJob         → esg.clean_metrics, esg.aggregate_metrics
   ├─ TrafficFlinkJob     → traffic.traffic_counts
   └─ AlertDetectionJob   → alerts.alert_events (Kafka output)
        │
        ▼
   PgBouncer (Connection Pooling)
        │
        ▼
   TimescaleDB (PostgreSQL 16)
   ├─ schema: esg          → clean_metrics, aggregate_metrics
   ├─ schema: environment  → sensor_readings, aqi_index
   ├─ schema: traffic      → traffic_counts, incidents
   ├─ schema: alerts       → alert_events, notification_log
   ├─ schema: citizens     → accounts, households, meters, invoices
   └─ schema: error_mgmt   → error_records
        │
   TimescaleDB
   [UAT: HikariCP direct | Prod: PgBouncer proxy]

   Redis (Cache + PubSub)
   ├─ Alert pub/sub channel
   └─ Session + API response cache
        │
        ▼
   Spring Boot API (Modular Monolith + Camunda 7 embedded)
        │
        ├─ SSE (Server-Sent Events) → real-time push
        ▼
   React Frontend (TypeScript + MUI)
```

---

## 2. Nguyên tắc kiến trúc

### 2.1 Event-time over processing-time
- Tất cả aggregation dùng **sensor event timestamp**, không dùng ingestion time
- Flink watermark: 30 giây để xử lý late arrivals
- Hypertable partitioned trên `event_ts`

### 2.2 Idempotent Upsert
- Mỗi Flink sink: `INSERT ... ON CONFLICT ... DO UPDATE`
- Dedup key deterministic từ source data (MD5 hash)
- At-least-once delivery → effectively exactly-once qua idempotency

### 2.3 Schema Isolation
- Mỗi domain module có schema riêng trong cùng 1 TimescaleDB
- Cho phép kiểm soát access, backup, và scale độc lập trong tương lai

### 2.4 Modular Monolith → Microservices path
- Spring Boot: 1 application, nhiều package/module
- Module boundaries rõ ràng (không gọi chéo internal methods)
- Có thể extract từng module thành microservice khi cần scale

### 2.5 Non-blocking Alert Path
- Alert detection trong Flink → publish vào `alert_events` Kafka topic
- Alert service consume → Redis pub/sub → SSE push tới client
- AI Workflow: async call Claude API, **không block alert path**

---

## 3. Database Schema Design

### 3.1 Schema: environment
```sql
-- Sensor raw readings
CREATE TABLE environment.sensor_readings (
    meter_id      TEXT NOT NULL,
    site_id       TEXT NOT NULL,
    building_id   TEXT,
    floor_id      TEXT,
    zone_id       TEXT,
    event_ts      TIMESTAMPTZ NOT NULL,
    measure_type  TEXT NOT NULL,  -- temp_celsius, humidity_pct, co2_ppm, aqi_pm25, ...
    value         DOUBLE PRECISION NOT NULL,
    unit          TEXT,
    quality_flag  TEXT DEFAULT 'VALID',
    ingested_at   TIMESTAMPTZ DEFAULT NOW()
);
SELECT create_hypertable('environment.sensor_readings', 'event_ts');

-- AQI computed index (1-hour buckets)
CREATE TABLE environment.aqi_index (
    site_id       TEXT NOT NULL,
    bucket        TIMESTAMPTZ NOT NULL,
    aqi_value     INTEGER NOT NULL,
    aqi_category  TEXT NOT NULL,  -- GOOD, MODERATE, UNHEALTHY, HAZARDOUS
    dominant_pollutant TEXT,
    PRIMARY KEY (site_id, bucket)
);
```

### 3.2 Schema: alerts
```sql
CREATE TABLE alerts.alert_events (
    id            BIGSERIAL PRIMARY KEY,
    alert_type    TEXT NOT NULL,      -- AQI_EXCEEDED, FLOOD_RISK, UTILITY_FAILURE, ...
    severity      TEXT NOT NULL,      -- P0, P1, P2
    site_id       TEXT NOT NULL,
    sensor_id     TEXT,
    triggered_at  TIMESTAMPTZ NOT NULL,
    message       TEXT NOT NULL,
    status        TEXT DEFAULT 'OPEN', -- OPEN, ACKNOWLEDGED, RESOLVED
    acknowledged_by TEXT,
    resolved_at   TIMESTAMPTZ,
    metadata      JSONB
);

CREATE TABLE alerts.notification_log (
    id            BIGSERIAL PRIMARY KEY,
    alert_id      BIGINT REFERENCES alerts.alert_events(id),
    recipient_id  TEXT,             -- user_id or 'broadcast'
    channel       TEXT NOT NULL,   -- SSE, EMAIL, PUSH
    sent_at       TIMESTAMPTZ DEFAULT NOW(),
    delivered     BOOLEAN DEFAULT FALSE
);
```

### 3.3 Schema: citizens
```sql
-- User accounts
CREATE TABLE citizens.accounts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT UNIQUE NOT NULL,
    phone         TEXT,
    full_name     TEXT NOT NULL,
    role          TEXT DEFAULT 'CITIZEN',  -- CITIZEN, OPERATOR, ADMIN
    created_at    TIMESTAMPTZ DEFAULT NOW(),
    pdpa_consent  BOOLEAN DEFAULT FALSE,
    pdpa_date     TIMESTAMPTZ
);

-- Household registration
CREATE TABLE citizens.households (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id    UUID REFERENCES citizens.accounts(id),
    building_id   TEXT NOT NULL,
    floor_id      TEXT,
    zone_id       TEXT,
    unit_number   TEXT NOT NULL,
    registered_at TIMESTAMPTZ DEFAULT NOW()
);

-- Utility meters linked to household
CREATE TABLE citizens.utility_meters (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id  UUID REFERENCES citizens.households(id),
    meter_id      TEXT NOT NULL,   -- links to esg.clean_metrics
    meter_type    TEXT NOT NULL,   -- ELECTRICITY, WATER
    contract_number TEXT
);

-- Invoices
CREATE TABLE citizens.invoices (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id  UUID REFERENCES citizens.households(id),
    meter_id      UUID REFERENCES citizens.utility_meters(id),
    period_start  DATE NOT NULL,
    period_end    DATE NOT NULL,
    consumption   DOUBLE PRECISION,
    unit          TEXT,
    amount        DECIMAL(12,2),
    status        TEXT DEFAULT 'UNPAID', -- UNPAID, PAID, OVERDUE
    generated_at  TIMESTAMPTZ DEFAULT NOW()
);
```

---

## 4. Notification Architecture (SSE)

```
Flink AlertDetectionJob
        │ Kafka: alert_events
        ▼
Alert Service (Spring Boot)
        │ @KafkaListener
        ▼
Redis PUBLISH "alerts:{site_id}"
        │
        ▼ Redis SUBSCRIBE
SSE Endpoint: GET /api/v1/alerts/stream
        │ SseEmitter (Spring)
        ▼
Browser Client (React EventSource)
```

**Extensibility:** Notification Service có abstract `NotificationChannel` interface:
- `SseNotificationChannel` (implemented — MVP1)
- `EmailNotificationChannel` (stub — future)
- `PushNotificationChannel` (stub — future)

---

## 5. AI Workflow Architecture (Camunda 7 + Claude API)

```
Trigger Event (Flink Alert hoặc manual)
        │
        ▼
Camunda 7 BPM Engine (embedded Spring Boot)
├─ Process Definition: BPMN 2.0 (.bpmn files)
├─ Process Instance execution:
│   ├─ ServiceTask: ThresholdEvaluationDelegate
│   ├─ ServiceTask: AiDecisionDelegate → Claude API (async, 10s timeout)
│   ├─ ServiceTask: NotificationDelegate → Redis pub/sub
│   ├─ UserTask: OperatorReviewTask (Camunda Tasklist / custom UI)
│   └─ ServiceTask: EscalationDelegate
├─ History: Process execution log (Camunda DB)
└─ REST API: /engine-rest/* (Camunda REST API)

Frontend (bpmn-js)
├─ Visualize BPMN diagram
├─ Highlight active tokens (process instances)
└─ Show AI decision results
```

**Camunda 7 Setup (Spring Boot):**
```xml
<!-- pom.xml -->
<dependency>
  <groupId>org.camunda.bpm.springboot</groupId>
  <artifactId>camunda-bpm-spring-boot-starter-rest</artifactId>
  <version>7.21.0</version>
</dependency>
```

**Claude API Integration (Camunda ServiceTask):**
- Delegate class: `AiDecisionDelegate implements JavaDelegate`
- Model: `claude-sonnet-4-6`
- Pattern: structured tool use, JSON response
- Context: sensor readings + location + historical alerts
- Không block alert path: Camunda async continuation (`camunda:async="true"`)
- Timeout: 10s, fallback về rule-based decision nếu Claude API không phản hồi

**7 BPMN Process Definitions:**
```
processes/
├─ citizen/
│   ├─ aqi-alert-citizen.bpmn           (AI-C01)
│   ├─ service-request-auto.bpmn        (AI-C02)
│   └─ emergency-evacuation.bpmn        (AI-C03)
└─ management/
    ├─ flood-emergency-response.bpmn    (AI-M01)
    ├─ aqi-traffic-control.bpmn         (AI-M02)
    ├─ utility-maintenance.bpmn         (AI-M03)
    └─ esg-anomaly-investigation.bpmn   (AI-M04)
```

---

## 6. Production Readiness (sau UAT)

| Item | UAT | Production |
|---|---|---|
| Kafka brokers | 1 (KRaft) | 3 (RF=3) |
| Flink HA | Không | K8s leader election |
| Flink checkpoint | In-memory | MinIO/S3 (incremental) |
| TimescaleDB | Single node | Patroni HA (1 primary + 2 replicas) |
| Connection Pool | HikariCP (embedded) | PgBouncer + HikariCP |
| Workflow Engine | Camunda 7 (embedded) | Camunda 7 cluster hoặc Camunda 8 Zeebe |
| K8s | Docker Compose | Kubernetes + Helm |
| Secrets | Env vars | HashiCorp Vault |
| Monitoring | Flink/Kafka UI | Prometheus + Grafana + Loki |
| TLS/Auth | JWT only | SASL_SSL + mTLS |

---

> Tài liệu này sẽ được cập nhật liên tục trong quá trình phát triển.
> Mọi thay đổi kiến trúc cần ghi thành ADR (Architecture Decision Record) tại `docs/architecture/adr/`.
