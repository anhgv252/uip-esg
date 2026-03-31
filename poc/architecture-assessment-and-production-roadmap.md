# UIP ESG Telemetry — Architecture Assessment & Production Roadmap

> **Tác giả:** Senior Solution Architect Review  
> **Ngày đánh giá:** 26/03/2026  
> **Đối tượng:** POC `uip-esg-poc` → Production-grade system  
> **Scope:** Đánh giá toàn diện + lộ trình nâng cấp

---

## 1. Tổng quan POC

### 1.1 Mục tiêu POC đã đạt được

POC này chứng minh được một pipeline **end-to-end hoàn chỉnh**:

```
IoT Devices (simulated)
  → Kafka raw_telemetry (100K messages, 70/30 valid/error)
  → Redpanda Connect (vendor normalization → NGSI-LD)
  → Flink StatementSet (cleansing + classification + aggregation)
  → TimescaleDB (clean metrics + 1-min windows + error records)
  → FastAPI (10 REST endpoints)
```

**Các quyết định kỹ thuật đúng đắn của POC:**

| Quyết định | Lý do đúng |
|---|---|
| Kafka KRaft (no Zookeeper) | Đúng hướng — đây là tương lai của Kafka |
| Redpanda Connect làm ETL layer | Tách biệt normalization khỏi business logic — broker-agnostic |
| NGSI-LD canonical schema | Vendor-agnostic data model — chuẩn của smart city |
| Idempotent upsert với `dedup_key` (MD5) | Giải quyết at-least-once đúng cách không cần XA transactions |
| Event-time watermarking (30s) | Chính xác — dùng sensor time, không phải processing time |
| Flink StatementSet (3 INSERTs song song) | Hiệu quả — single job graph thay vì 3 job riêng biệt |
| Schema isolation (`esg` vs `error_mgmt`) | Clean separation of concerns trong cùng 1 DB |
| Flink sink DDL không include operator columns | Clever pattern — bảo vệ workflow state khỏi checkpoint replay |
| Hypertable partition trên `event_ts` (sensor time) | Đúng — queries by sensor time sẽ sử dụng index scans |
| 23 integration tests | Good discipline cho POC |

### 1.2 Throughput thực tế của POC

- **Batch**: 100K messages / ~60–90 giây → ~1,100–1,700 msg/s
- **Target production**: Cần đánh giá dựa trên số lượng thiết bị IoT thực tế
- **Rough estimate cho smart city**: 10,000 devices × 1 msg/5s = **2,000 msg/s baseline**, peak 3–5× = **10,000 msg/s**
- **POC bottleneck hiện tại**: Single Kafka broker + Single Flink TaskManager + 4 Kafka partitions

---

## 2. Đánh giá chi tiết — Điểm yếu & Gap

### 2.1 🔴 CRITICAL — Reliability & Fault Tolerance

**Vấn đề 1: Kafka không có replication**
```yaml
# docker-compose.yml
KAFKA_DEFAULT_REPLICATION_FACTOR=1  # ← single point of failure
KAFKA_NUM_PARTITIONS=4              # ← giới hạn max 4 consumers
KAFKA_LOG_RETENTION_HOURS=24        # ← 24h raw data, sau đó mất
```
- Khi Kafka broker chết → toàn bộ pipeline dừng, raw data mất
- Production cần RF=3, min.ISR=2, topic-level config khác nhau per topic

**Vấn đề 2: Flink không có High Availability**
```yaml
# flink-jobmanager: single container, no HA
# Khi JobManager chết → tất cả streaming jobs bị kill
# Không có checkpoint storage (in-memory, không phải S3/HDFS)
```
- `flink-job` là one-shot container — nếu fail sau khi submit, không tự resubmit
- Checkpoint chỉ lưu in-memory → restart mất toàn bộ state, đọc lại từ `earliest-offset`

**Vấn đề 3: TimescaleDB single node**
- Không có standby replica
- Không có backup/restore procedures
- Single point of failure cho cả read và write path

**Vấn đề 4: Redpanda Connect không có persistent state**
```yaml
restart: on-failure  # restart nhưng không có checkpoint
# Khi restart: sẽ re-consume lại từ last committed offset
# auto_replay_nacks: true là đúng nhưng thiếu configurable retry limits
```

---

### 2.2 🔴 CRITICAL — Security

**Vấn đề 1: Không có authentication/encryption trên Kafka**
```yaml
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
# Tất cả traffic Kafka đều unencrypted, unauthenticated
```

**Vấn đề 2: Plaintext credentials trong docker-compose**
```yaml
POSTGRES_PASSWORD: esg_pass       # ← trong code repository
TIMESCALE_PASS=esg_pass           # ← trong code repository
```
Và trong `esg-api/main.py`:
```python
f"password=esg_pass"  # ← hardcoded fallback
```

**Vấn đề 3: CORS wildcard trong FastAPI**
```python
allow_origins=["*"]   # ← tất cả origins đều được accept
allow_methods=["*"]
allow_headers=["*"]
```

**Vấn đề 4: Không có API Authentication**
- Không có JWT, API key, hay OAuth2
- Endpoint `/esg/data-quality/errors/{id}/review` (POST) không được bảo vệ
- Bất kỳ ai cũng có thể thay đổi operator review state

**Vấn đề 5: Admin UIs exposed không có auth**
- Flink Dashboard `:8081` — có thể cancel/modify streaming jobs
- Kafka UI `:8080` — có thể xóa topics, consumer groups

---

### 2.3 🟡 HIGH — Scalability Ceiling

**Vấn đề 1: 4 Kafka partitions = hard ceiling**
```
Max concurrent Flink operators = 4 (partition count)
Max Redpanda Connect consumers = 4
→ Không thể scale horizontally vượt qua 4 consumers
```

**Vấn đề 2: PyFlink performance overhead**
```
Flink Java → pemja bridge → Python interpreter
Overhead: ~2-3x so với native Java/Kotlin Flink job
Không sử dụng được Flink native serialization
```

**Vấn đề 3: Parallelism hardcoded**
```python
t_env.get_config().set("parallelism.default", "4")
# Không có dynamic resource allocation
# Không có TaskManager auto-scaling
```

**Vấn đề 4: TimescaleDB write bottleneck**
```python
'sink.buffer-flush.max-rows' = '1000'  # clean_metrics
'sink.buffer-flush.max-rows' = '200'   # aggregates  
'sink.buffer-flush.max-rows' = '500'   # error_records
# Flush mỗi 2s hoặc khi đủ rows
# Nhưng chỉ có 1 JDBC connection per sink operator
```

**Vấn đề 5: FastAPI connection pool không đủ**
```python
POOL = pool.ThreadedConnectionPool(minconn=2, maxconn=20, dsn=TSDB_DSN)
# psycopg2 là synchronous driver trong async FastAPI
# Mỗi request block một thread cho đến khi DB trả về
# maxconn=20 là giới hạn cứng cho concurrent reads
```

---

### 2.4 🟡 HIGH — Observability & Monitoring

**Những gì đang thiếu hoàn toàn:**
- Không có Prometheus metrics export
- Không có Grafana dashboards
- Không có distributed tracing (OpenTelemetry / Jaeger)
- Không có alerting rules (PagerDuty / Opsgenie / Slack)
- Không có log aggregation (ELK / Loki + Grafana)
- Không có end-to-end latency SLA monitoring

**Consumer lag monitoring chỉ qua CLI:**
```bash
make lag         # Flink consumer lag
make lag-connect # Redpanda Connect lag
# Không có alerting khi lag vượt ngưỡng
```

**Flink metrics chỉ qua Dashboard UI:**
- Không có `numRecordsInPerSecond` / `numRecordsOutPerSecond` alerts
- Không có backpressure alerting
- Không có checkpoint duration / failure alerting

---

### 2.5 🟡 HIGH — Data Governance

**Vấn đề 1: Không có Schema Registry**
```
Raw Kafka messages: plain JSON, no schema enforcement
→ Breaking changes (rename field, change type) không được detect
→ Benthos / Flink parse silently null nếu field bị rename
```

**Vấn đề 2: `esg_error_stream` topic là orphan**
```yaml
# Benthos routes completely broken messages → esg_error_stream
# Nhưng không có consumer nào đọc topic này trong production flow
# Error records đi qua con đường: ngsi_ld_telemetry → Flink → error_mgmt.error_records
# → esg_error_stream topic chỉ là dead letter nhưng không được xử lý
```

**Vấn đề 3: Retention policies không đủ**
```
KAFKA_LOG_RETENTION_HOURS=24
→ Raw telemetry data chỉ giữ 24h
→ Không có tiered storage (hot/warm/cold)
→ Không có data archival strategy
```

**Vấn đề 4: Không có data lineage**
- Không track được: message này từ sensor nào → qua Benthos job nào → Flink operator nào → table nào
- Debug khi có issue rất khó

**Vấn đề 5: Event timestamp parsing dễ bị lỗi**
```python
REPLACE(event_timestamp, 'Z', '')  # chỉ handle UTC 'Z' suffix
# Không handle '+07:00', '-05:00' timezone offsets
# Devices ở timezone khác nhau sẽ bị classify sai
```

**Vấn đề 6: `MAX_VALUE_THRESHOLD=10000` dùng chung cho tất cả measure types**
```python
# electric_kwh max realistic: ~450 kWh/h
# co2_ppm max realistic: ~2000 ppm  
# water_m3 max realistic: ~80 m³
# → Threshold 10000 quá cao cho co2, quá thấp cho power plants
# → Cần per-measure-type thresholds với configurable rules
```

---

### 2.6 🟢 MEDIUM — Operational Maturity

**Vấn đề 1: `latest` Docker image tags**
```yaml
timescale/timescaledb:latest-pg16   # non-deterministic
provectuslabs/kafka-ui:latest       # non-deterministic  
ghcr.io/redpanda-data/connect:latest # non-deterministic
```

**Vấn đề 2: Không có Kubernetes deployment**
- Docker Compose only — không deploy được lên cloud
- Không có Helm charts
- Không có resource limits/requests
- Không có pod disruption budgets

**Vấn đề 3: Không có CI/CD pipeline**
- Không có automated testing trong pipeline
- Không có image build / push automation
- Không có environment promotion (dev → staging → prod)

**Vấn đề 4: Secrets management**
```
Passwords trong docker-compose.yml → Git history
Cần: Vault + Kubernetes Secrets + SOPS/External Secrets Operator
```

**Vấn đề 5: postgres-errors.sql (legacy/obsolete)**
```
init-db/postgres-errors.sql → marked obsolete nhưng vẫn trong repo
→ Gây confusion về schema source of truth
→ Cần xóa hoặc archive
```

---

## 3. Production Architecture Blueprint

### 3.1 Target Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                   UIP ESG Telemetry — Production Architecture           │
│                                                                         │
│  IoT Devices / Edge Gateways                                            │
│  ┌─────────────────────────┐                                            │
│  │  MQTT Broker (EMQ X)    │──┐                                         │
│  │  HTTP Gateway           │──┼──► Kafka Cluster (3 brokers, KRaft)    │
│  │  gRPC Collector         │──┘    ├─ raw_telemetry  (RF=3, 24 parts)  │
│  └─────────────────────────┘       ├─ ngsi_ld_telemetry (RF=3, 24p)   │
│                                    ├─ esg_error_stream  (RF=3, 12p)   │
│  Schema Registry (Apicurio)        └─ esg_dlq           (RF=3, 6p)   │
│  └── Avro schemas for all topics                                        │
│                                                                         │
│  Redpanda Connect Cluster (3+ nodes)                                    │
│  ├─ Horizontal scaling via Kubernetes deployment                        │
│  ├─ Per-vendor normalizer pipelines                                     │
│  └─ DLQ routing for unparseable messages                               │
│                                                                         │
│  Flink Kubernetes Operator (Application Mode)                           │
│  ├─ Java/Kotlin job (không phải PyFlink)                                │
│  ├─ JobManager HA (Zookeeper / k8s leader election)                    │
│  ├─ TaskManagers: auto-scale 2–20 pods                                  │
│  ├─ Checkpoint: S3/MinIO (incremental, every 30s)                      │
│  ├─ State backend: RocksDB (incremental checkpoints)                   │
│  └─ Per-measure-type validation rules (configurable via ConfigMap)      │
│                                                                         │
│  TimescaleDB HA Cluster (Patroni)          Analytics Layer             │
│  ├─ Primary (write)                        ├─ ClickHouse (OLAP)        │
│  ├─ 2× Read Replicas                       └─ Metabase / Grafana       │
│  ├─ PgBouncer (connection pooling)                                      │
│  └─ Continuous backup (WAL-E / pgBackRest)                             │
│                                                                         │
│  Serving Layer                              Observability               │
│  ├─ ESG API (FastAPI + asyncpg)            ├─ Prometheus                │
│  ├─ API Gateway (Kong / AWS API GW)        ├─ Grafana                   │
│  ├─ OAuth2/JWT (Keycloak)                  ├─ Loki (logs)               │
│  └─ Rate limiting, caching (Redis)         ├─ Jaeger (tracing)         │
│                                            └─ PagerDuty (alerts)       │
│                                                                         │
│  Infrastructure: Kubernetes + Helm + ArgoCD (GitOps)                  │
│  Secrets: HashiCorp Vault + External Secrets Operator                  │
└─────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Kafka Production Configuration

```yaml
# Minimum viable production Kafka cluster
brokers: 3
replication_factor: 3
min_insync_replicas: 2

topics:
  raw_telemetry:
    partitions: 24          # scale dựa trên expected device count
    retention.ms: 604800000 # 7 days
    compression: lz4        # ~40% size reduction
    
  ngsi_ld_telemetry:
    partitions: 24
    retention.ms: 604800000
    
  esg_error_stream:
    partitions: 12
    retention.ms: 2592000000  # 30 days (errors cần giữ lâu hơn)
    
  esg_dlq:                    # NEW: dead letter queue
    partitions: 6
    retention.ms: 7776000000  # 90 days

security:
  protocol: SASL_SSL
  sasl_mechanism: SCRAM-SHA-256
  tls: mutual TLS với client certificates

monitoring:
  consumer_lag_alert: > 10,000 messages
  disk_usage_alert: > 70%
  under_replicated_alert: > 0
```

### 3.3 Flink Production Configuration

**Thay PyFlink bằng Java/Kotlin** hoặc upgrade lên **Flink 1.19+ với Python API improvements**:

```java
// Trước (PyFlink): JVM → pemja → Python → kết quả
// Sau (Java): JVM native, ~2-3x throughput improvement

// Job configuration cho production
StreamExecutionEnvironment env = ...;
env.setParallelism(24);                      // match Kafka partitions
env.enableCheckpointing(30_000);             // 30s checkpoint interval
env.getCheckpointConfig().setCheckpointingMode(EXACTLY_ONCE);  // XA JDBC nếu cần
env.setStateBackend(new EmbeddedRocksDBStateBackend(true));    // incremental

// Per-measure-type validation (thay vì hardcoded MAX_VALUE_THRESHOLD=10000)
// Đọc từ Kafka compacted topic hoặc JDBC catalog
```

Kubernetes deployment:
```yaml
apiVersion: flink.apache.org/v1beta1
kind: FlinkDeployment
metadata:
  name: esg-processing-job
spec:
  flinkVersion: v1_19
  flinkConfiguration:
    taskmanager.numberOfTaskSlots: "4"
    state.backend: rocksdb
    state.checkpoints.dir: s3://esg-flink-checkpoints/
    high-availability: kubernetes
    high-availability.storageDir: s3://esg-flink-ha/
  taskManager:
    resource:
      memory: "2048m"
      cpu: 1
    # Auto-scaling
  job:
    jarURI: s3://esg-artifacts/esg-processing-job.jar
    parallelism: 24
    upgradeMode: stateful
```

### 3.4 TimescaleDB Production Configuration

```sql
-- Thay đổi chunk interval dựa trên data volume
-- POC: 1 day (100K records)
-- Production (10K devices × 1msg/5s = ~86M records/day):
SELECT set_chunk_time_interval('esg.clean_metrics', INTERVAL '6 hours');

-- Retention policies
SELECT add_retention_policy('esg.clean_metrics', INTERVAL '90 days');
SELECT add_retention_policy('esg.aggregate_metrics', INTERVAL '2 years');

-- Continuous aggregate (thay thế Flink aggregate sink cho historical backfill)
CREATE MATERIALIZED VIEW esg_hourly_metrics
WITH (timescaledb.continuous) AS
SELECT 
    meter_id, site_id, measure_type,
    time_bucket('1 hour', event_ts) AS bucket,
    avg(value) AS avg_value,
    sum(value) AS total_value
FROM esg.clean_metrics
GROUP BY meter_id, site_id, measure_type, bucket;

-- Compression policy (tiered storage)
SELECT add_compression_policy('esg.clean_metrics', INTERVAL '7 days');
-- Compressed: typically 90%+ size reduction for time-series data

-- Connection pooling với PgBouncer
-- maxconn per TimescaleDB: 200 (PostgreSQL default 100)
-- PgBouncer pool: 1000 → 200
```

### 3.5 Schema Registry & Data Contract

```protobuf
// thay JSON bằng Avro hoặc Protobuf
syntax = "proto3";
message NgsiLdTelemetry {
  string meter_id = 1;
  string site_id = 2;
  string building_id = 3;
  string floor_id = 4;
  string zone_id = 5;
  google.protobuf.Timestamp event_timestamp = 6;
  string measure_type = 7;
  double value = 8;           // đã parse, không còn raw_value string
  string unit = 9;
  string source_id = 10;
  int64 normalized_at_ms = 11;
  DataQuality quality = 12;
}

enum DataQuality {
  UNKNOWN = 0;
  VALID = 1;
  ERROR = 2;
}
```

Lợi ích:
- Schema evolution control (backward/forward compatibility)
- ~60% smaller message size vs JSON
- Compile-time type safety
- Automatic schema documentation

### 3.6 Per-Measure-Type Validation Rules

```yaml
# ConfigMap → inject vào Flink job
# Thay thế hardcoded MAX_VALUE_THRESHOLD=10000
validation_rules:
  electric_kwh:
    min: 0.0
    max: 5000.0       # large industrial building
    unit_allowed: [kWh, MWh]
    
  water_m3:
    min: 0.0
    max: 500.0
    unit_allowed: [m³, L]
    
  temp_celsius:
    min: -20.0
    max: 80.0
    unit_allowed: [°C, °F, K]
    
  co2_ppm:
    min: 300.0
    max: 5000.0       # dangerous level, still valid sensor reading
    unit_allowed: [ppm]
    
  humidity_pct:
    min: 0.0
    max: 100.0
    unit_allowed: [%]
```

### 3.7 Security Architecture

```
Authentication / Authorization:
├─ Kafka: SASL_SSL + SCRAM-SHA-256
│   ├─ Service accounts per component (connect-sa, flink-sa, api-sa)
│   └─ ACLs: producer topics ≠ consumer topics
│
├─ API Gateway (Kong):
│   ├─ OAuth2/JWT (Keycloak IdP)
│   ├─ Rate limiting: 1000 req/min per tenant
│   ├─ API key cho machine-to-machine
│   └─ mTLS cho internal service communication
│
├─ Database:
│   ├─ Separate service accounts (flink_writer, api_reader, admin)
│   ├─ Column-level encryption cho PII fields (nếu có)
│   └─ SSL/TLS kết nối mandatory
│
├─ Secrets Management:
│   ├─ HashiCorp Vault (dynamic secrets cho DB)
│   └─ External Secrets Operator → Kubernetes Secrets
│
└─ Network:
    ├─ Kubernetes NetworkPolicy (deny all ingress by default)
    ├─ Flink: không expose UI ra internet (internal only)
    ├─ Kafka: không expose ra internet (only via API)
    └─ TimescaleDB: chỉ accessible từ trong cluster
```

---

## 4. Observability Stack

### 4.1 Metrics

```yaml
# Prometheus scraping targets
- job: kafka
  metrics: 
    - kafka_server_brokertopicmetrics_messagesin_total
    - kafka_consumer_consumer_fetch_manager_metrics_records_lag
    - kafka_controller_controllerstate_activecontrollercount

- job: flink
  metrics:
    - flink_taskmanager_job_task_numRecordsIn
    - flink_taskmanager_job_task_numRecordsOut
    - flink_taskmanager_job_task_currentInputWatermark
    - flink_jobmanager_job_numRestarts
    - flink_taskmanager_job_task_buffers_outPoolUsage  # backpressure indicator

- job: timescaledb
  metrics:
    - pg_stat_bgwriter_buffers_written
    - pg_stat_replication_pg_wal_lsn_diff  # replication lag
    - pg_stat_activity_count               # active connections

- job: esg-api
  metrics:
    - http_request_duration_seconds_bucket
    - http_requests_total
    - db_pool_connections_in_use
```

### 4.2 Key Alerts

| Alert | Condition | Severity |
|---|---|---|
| Kafka Consumer Lag | `lag > 50,000 messages` sustained 5m | CRITICAL |
| Flink Job Restart | `numRestarts > 3` trong 10m | HIGH |
| Flink Backpressure | `buffers.outPoolUsage > 0.8` sustained 2m | HIGH |
| TimescaleDB Replication Lag | `wal_lag > 60s` | HIGH |
| Error Rate Spike | Error records > 50% của total trong 5m | HIGH |
| API p99 Latency | `p99 > 2000ms` sustained 5m | MEDIUM |
| Kafka Under-replicated | `under_replicated_partitions > 0` | HIGH |
| Disk Usage | `> 70%` | MEDIUM |

### 4.3 Dashboard Setup

```
Grafana Dashboards:
1. Pipeline Health Overview
   - Messages in/out per second (realtime)
   - Consumer group lags (Flink + Redpanda Connect)
   - End-to-end latency (event_timestamp → ingested_at)
   - Error rate percentage

2. Flink Job Details
   - Per-operator throughput
   - Watermark progress
   - Checkpoint duration + size
   - Backpressure heatmap

3. Data Quality Dashboard
   - Error type breakdown (per hour/day)
   - Error sources (per site/building)
   - Re-ingestion success rate
   - Error review backlog

4. TimescaleDB Performance
   - Query duration heatmap
   - Chunk fill rate
   - Compression ratio over time
   - Connection pool utilization
```

---

## 5. Lộ trình nâng cấp (Phased Approach)

### Phase 1 — Foundation (4–6 tuần)
*Mục tiêu: Hardening POC để có thể demo với stakeholders an toàn*

- [ ] Pin tất cả Docker image tags (bỏ `:latest`)
- [ ] Move secrets vào `.env` file (không commit) + document `.env.example`
- [ ] Fix CORS: thay `allow_origins=["*"]` bằng explicit whitelist
- [ ] Thêm basic API key authentication (header `X-API-Key`)
- [ ] Tăng Kafka partitions lên 12 (test limit scaling)
- [ ] Thêm 1 Flink TaskManager nữa (test HA scenario)
- [ ] Viết thêm validation rules: per-measure-type thresholds
- [ ] Fix timezone handling trong event_timestamp parsing
- [ ] Cleanup: xóa `postgres-errors.sql` obsolete
- [ ] Thêm basic Prometheus endpoints (Kafka JMX exporter + Flink metrics reporter)
- [ ] Tạo runbook cho common failure scenarios

**Deliverable**: Staging environment, demo-ready với basic security

---

### Phase 2 — Production Infrastructure (8–12 tuần)
*Mục tiêu: Đủ điều kiện deploy production với low-traffic pilot*

- [ ] Kubernetes deployment (Minikube → EKS/GKE)
  - [ ] Helm charts cho tất cả components
  - [ ] ArgoCD GitOps pipeline
  - [ ] Resource limits + PodDisruptionBudgets
- [ ] Kafka cluster: 3 brokers + ZooKeeper-less KRaft HA
  - [ ] SASL_SSL authentication
  - [ ] Per-topic retention policies
- [ ] Flink Kubernetes Operator (Application Mode)
  - [ ] S3 checkpoints (30s interval, RocksDB backend)
  - [ ] JobManager HA
  - [ ] Rewrite job từ PyFlink → Java (hoặc Flink 1.19+ với Python improvements)
- [ ] TimescaleDB HA (Patroni + 2 read replicas)
  - [ ] PgBouncer connection pooling
  - [ ] Automated backups (pgBackRest → S3)
  - [ ] Retention + compression policies
- [ ] Apicurio Schema Registry + Avro/Protobuf migration
- [ ] HashiCorp Vault + External Secrets Operator
- [ ] Observability stack (Prometheus + Grafana + Loki + Alertmanager)
- [ ] Keycloak OAuth2/JWT cho API authentication
- [ ] API Gateway (Kong) với rate limiting

**Deliverable**: Production-ready cho pilot deployment (1–2 sites, ~500 devices)

---

### Phase 3 — Scale & Governance (12–20 tuần)
*Mục tiêu: Scale to full production, 100+ sites, 10K+ devices*

- [ ] Schema Registry: multi-version compatibility policies
- [ ] ClickHouse cho OLAP analytics (thay thế TimescaleDB aggregates queries)
- [ ] Flink SQL Catalog với Hive Metastore
- [ ] Tiered storage: Kafka → MinIO (cold storage) sau 7 ngày
- [ ] Multi-tenancy: per-tenant Kafka topics hoặc partition keys
- [ ] Data lineage tracking (Apache Atlas hoặc OpenLineage)
- [ ] Automated error correction workflows (approval gates)
- [ ] Edge computing: pre-processing tại Edge Gateway (reduce bandwidth)
- [ ] ML Anomaly Detection: Flink PMML model scoring trong pipeline
- [ ] Disaster Recovery: multi-region setup, RTO < 15min, RPO < 5min
- [ ] Load testing: JMeter/Gatling cho 50K msg/s sustained

**Deliverable**: Full production, 10K+ devices, SLA 99.5%

---

## 6. Capacity Planning

### 6.1 Ước tính cho Smart City deployment (100 tòa nhà)

```
Assumptions:
- 100 buildings × avg 200 meters/building = 20,000 meters
- 1 message per meter per 5 seconds = 4,000 msg/s baseline
- Peak (morning startup): 3× = 12,000 msg/s
- Message size (Avro): ~200 bytes
- Error rate (production): ~5% target

Kafka:
- Throughput peak: 12,000 × 200B = 2.4 MB/s ingress
- 7-day retention: 2.4 MB/s × 604,800s = ~1.4 TB
- Brokers: 3 (RF=3 → effective storage 3× = 4.2 TB per broker)
- Partitions: 24–48 (target ~500 msg/s per partition)

Flink:
- Throughput: 12,000 msg/s → 4 TaskManagers × 4 slots = 16 operators
- State size (windowed aggregates): ~2 GB working set → 8 GB with safety
- Checkpoint size (RocksDB incremental): ~500 MB per checkpoint
- Memory: 4 GB per TaskManager × 4 TMs = 16 GB

TimescaleDB:
- clean_metrics: 4,000 × 0.95 (valid) × 86,400s = ~330M rows/day
- Compressed (TimescaleDB ~10× compression): ~3 GB/day → 270 GB/90 days
- aggregate_metrics (1-min windows): 20K meters × 1,440 min × 5 types = 144M rows/day
- Total storage (90 days clean + 2 years aggregates): ~1 TB
- IOPS needed: ~10,000 write IOPS peak

API:
- Expected: 50 concurrent users × dashboards refreshing every 10s
- Rate: ~500 req/s
- Latency target: p99 < 500ms for timeseries queries
```

### 6.2 Kubernetes Node Sizing (Starting Point)

```
Node Pool: kafka
- 3 nodes × m5.2xlarge (8 vCPU, 32 GB RAM, gp3 SSD 2TB)

Node Pool: flink
- 4–16 nodes × m5.xlarge (4 vCPU, 16 GB RAM) [auto-scaling]

Node Pool: timescaledb
- 1 primary + 2 replicas × r5.2xlarge (8 vCPU, 64 GB RAM, io2 2TB)
  (Memory-optimized vì TimescaleDB shared_buffers cần ~25% RAM)

Node Pool: apps
- 3+ nodes × m5.large (2 vCPU, 8 GB RAM)
  (Redpanda Connect, ESG API, Observability stack)
```

---

## 7. Quyết định Kiến trúc Cần Review

### ADR-001: PyFlink vs Java/Kotlin Flink

| | PyFlink (hiện tại) | Java/Kotlin |
|---|---|---|
| Dev velocity | Cao (Python quen thuộc) | Thấp hơn ban đầu |
| Runtime performance | ~50-70% Java throughput | Baseline |
| JVM overhead | JVM + Python + pemja bridge | JVM only |
| Debugging | Khó hơn (mixed stack traces) | Standard Java tooling |
| State management | Hạn chế (không access full Flink state API) | Full API access |
| **Recommendation** | POC OK | **Production: Java/Kotlin** |

### ADR-002: TimescaleDB vs ClickHouse cho analytics

| | TimescaleDB (hiện tại) | ClickHouse |
|---|---|---|
| Use case | Operational + time-series | OLAP analytics |
| Write throughput | ~100K rows/s | ~1M rows/s |
| Query speed (aggregations) | Good với continuous aggregates | 10-100× faster |
| SQL compatibility | PostgreSQL-compatible | ClickHouse SQL dialect |
| Ecosystem | Rich (Patroni HA, pgBackRest) | ClickHouse Cloud |
| **Recommendation** | **Keep cho operational data** | **Add cho analytics** |

**Hybrid approach**: TimescaleDB làm operational store (real-time queries), ClickHouse làm analytics store (historical trends, reports). Dùng Flink để write cả hai từ cùng một pipeline.

### ADR-003: Schema Registry Technology

| | Confluent Schema Registry | Apicurio Registry |
|---|---|---|
| License | Community (free) | Apache 2.0 |
| Avro/Protobuf/JSON Schema | ✅ | ✅ |
| Kafka integration | Native | Native |
| UI | Limited | Good |
| Managed option | Confluent Cloud | Red Hat |
| **Recommendation** | OK nếu dùng Confluent Cloud | **Prefer nếu self-hosted** |

### ADR-004: MQTT vs Kafka cho Edge ingestion

Hiện tại POC assume devices gửi thẳng HTTP/Kafka. Thực tế:
- IoT devices thường dùng **MQTT** (lightweight, QoS levels, retain messages)
- Cần MQTT Broker (EMQ X / HiveMQ) → Kafka bridge
- Hoặc dùng **Kafka MQTT connector** (Confluent / open source)

---

## 8. Checklist Production Readiness

### Security Checklist
- [ ] Không có hardcoded passwords trong code/config
- [ ] Kafka SASL_SSL enabled
- [ ] API authentication (JWT/API key)
- [ ] CORS configured với explicit origins
- [ ] Admin UIs không exposed publicly
- [ ] TLS trên tất cả service-to-service communication
- [ ] Secret rotation procedure documented
- [ ] Penetration testing hoàn thành

### Reliability Checklist
- [ ] Kafka RF=3, min.ISR=2
- [ ] Flink JobManager HA enabled
- [ ] Flink checkpoint → external storage (S3/MinIO)
- [ ] TimescaleDB HA (Patroni + standby)
- [ ] Database backups automated + tested
- [ ] Disaster recovery runbook
- [ ] RTO/RPO targets defined và tested

### Observability Checklist
- [ ] Prometheus metrics cho tất cả components
- [ ] Grafana dashboards (Pipeline Health, Data Quality, DB Performance)
- [ ] Alerting rules với PagerDuty/Slack integration
- [ ] Log aggregation (Loki/ELK)
- [ ] Distributed tracing (Jaeger/Zipkin)
- [ ] Consumer lag alerting
- [ ] End-to-end latency SLA monitoring

### Operational Checklist
- [ ] Kubernetes deployment (Helm charts)
- [ ] CI/CD pipeline (build → test → staging → prod)
- [ ] All image tags pinned (no `:latest`)
- [ ] Resource limits defined cho tất cả pods
- [ ] PodDisruptionBudgets
- [ ] Horizontal pod autoscaling
- [ ] Runbooks cho common operations
- [ ] On-call rotation defined
- [ ] Incident response playbooks

### Data Quality Checklist
- [ ] Schema Registry với compatibility policies
- [ ] Per-measure-type validation thresholds
- [ ] Timezone-aware timestamp parsing
- [ ] DLQ consumer + automated alerting
- [ ] Data lineage tracking
- [ ] Retention + archival policies documented
- [ ] Error re-ingestion automated workflow
- [ ] Data quality SLA defined (target: < 1% error rate)

---

## 9. Những gì POC chứng minh tốt — Giữ nguyên

Không phải mọi thứ đều cần thay đổi. Những patterns sau **đã tốt và cần giữ**:

1. **NGSI-LD canonical schema** — vendor-agnostic, đúng cho smart city
2. **Flink StatementSet** — efficient single job graph, giữ pattern này
3. **Idempotent upsert với dedup_key** — đúng cách handle at-least-once
4. **Event-time watermarking** — không thay đổi, chỉ tune delay nếu cần
5. **Operator workflow isolation** (không include reviewed columns trong Flink DDL) — clever, giữ
6. **Schema separation `esg` vs `error_mgmt`** — clean, giữ
7. **Hypertable trên sensor event_ts** — đúng, giữ
8. **Flink JDBC upsert mode** — không thay đổi mechanism, chỉ cần XA nếu cần EXACTLY_ONCE guarantee mạnh hơn

---

## 10. Tóm tắt Executive Summary

| Dimension | POC Score | Production Target |
|---|---|---|
| Functional Completeness | ✅ 9/10 | 10/10 |
| Reliability | ❌ 2/10 | 9/10 |
| Security | ❌ 1/10 | 9/10 |
| Scalability | ⚠️ 4/10 | 9/10 |
| Observability | ❌ 2/10 | 9/10 |
| Operational Maturity | ⚠️ 3/10 | 8/10 |
| Data Governance | ⚠️ 4/10 | 8/10 |

**Bottom line:**  
POC này có **architecture core tốt** — data flow đúng, normalization sạch, idempotency được suy nghĩ cẩn thận. Đây không phải POC cần redesign từ đầu. Nó cần được **hardened và scaled** theo Phase 1–3 roadmap ở trên.

> **Thời gian ước tính để production-ready (pilot scale):** 6–8 tháng với team 4–6 engineers  
> **Chi phí infra (50 sites, 5K devices):** ước tính $3,000–5,000/tháng AWS/GCP  
> **Biggest risk:** Security gaps (Phase 1 phải address trước khi bất kỳ data thật nào được xử lý)

---

*Document này là living document — cập nhật khi các quyết định kiến trúc được xác nhận hoặc thay đổi.*
