# UIP – ESG Telemetry Analytics  •  POC

> **Proof of Concept** cho pipeline:
> **Redpanda Connect → Apache Kafka → Flink → TimescaleDB + PostgreSQL**
>
> Kiểm thử với **100 000 bản tin** (70% valid / 30% lỗi phân loại 7 loại)

---

## 1. Stack lựa chọn

| Role | Image | Lý do |
|------|-------|-------|
| Message Broker | `bitnami/kafka:3.7` (KRaft) | Thuần Apache Kafka, không cần Zookeeper |
| Web Console | `provectuslabs/kafka-ui` | UI nhẹ, free, kết nối Kafka tiêu chuẩn |
| ETL / Normalisation | `ghcr.io/redpanda-data/connect` | Redpanda Connect = Benthos, broker-agnostic |
| Stream Processing | `flink:1.17` + PyFlink | Cleansing + Aggregation với SQL API |
| Time-series Store | `timescale/timescaledb:latest-pg16` | Hypertable cho clean metrics + aggregates |
| Error Store | `postgres:16-alpine` | Error records với operator review workflow |
| Serving API | FastAPI (custom build) | 10 REST endpoints cho dashboard + data quality |

> **Lưu ý:** Redpanda Connect **không** yêu cầu Redpanda broker.
> Nó là một ETL engine (fork của Benthos) kết nối được với bất kỳ
> Kafka-compatible cluster nào qua địa chỉ `kafka:9092`.

---

## 2. Kiến trúc tổng quan

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     UIP ESG Telemetry POC                               │
│                                                                         │
│  ┌─────────────┐   raw_telemetry    ┌────────────────────────────────┐  │
│  │  Producer   │──────────────────▶│   Apache Kafka 3.7 (KRaft)     │  │
│  │  100K msgs  │                   │   4 topics × 4 partitions       │  │
│  │  70% valid  │                   └──────────────┬─────────────────┘  │
│  │  30% errors │                                  │                    │
│  └─────────────┘                                  │ raw_telemetry      │
│                                                   │                    │
│  ┌────────────────────────────────◀───────────────┘                    │
│  │  Redpanda Connect (Benthos)                                          │
│  │  esg-normalize.yaml                                                  │
│  │  • Parses Format A (IoT) + Format B (BMS)                           │
│  │  • Maps vendor fields → NGSI-LD canonical schema                    │
│  │  • Routes bad messages → esg_error_stream                           │
│  └───────────────────────┬────────────────────────────────────────────┘  │
│                          │ ngsi_ld_telemetry                              │
│                          ▼                                                │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │   Apache Flink 1.17  (PyFlink SQL)  –  esg_processing.py        │    │
│  │                                                                   │    │
│  │   telemetry_classified VIEW (CASE WHEN … → error_type)           │    │
│  │                                                                   │    │
│  │   StatementSet (3 INSERTs chạy song song trong cùng 1 job graph) │    │
│  │   ├─ valid   → esg_clean_metrics      (TimescaleDB)              │    │
│  │   ├─ valid   → esg_aggregate_metrics  (TimescaleDB, 1-min window)│    │
│  │   └─ invalid → esg_error_records      (PostgreSQL)               │    │
│  └──────────────┬─────────────────────────┬──────────────────────────┘   │
│                 │                         │                               │
│                 ▼                         ▼                               │
│  ┌──────────────────────────┐  ┌─────────────────────────────────────┐   │
│  │  TimescaleDB  :5432      │  │  PostgreSQL  :5433                   │   │
│  │  esg_clean_metrics       │  │  esg_error_records                   │   │
│  │  esg_aggregate_metrics   │  │  ├─ error_type  (7 loại)            │   │
│  │  (hypertables, indexed)  │  │  ├─ reviewed / reviewed_by          │   │
│  │                          │  │  ├─ reingested / reingested_at      │   │
│  │                          │  │  └─ notes                           │   │
│  └───────────┬──────────────┘  └────────────┬────────────────────────┘   │
│              │                              │                             │
│              └──────────────┬──────────────┘                             │
│                             ▼                                            │
│              ┌──────────────────────────────┐                            │
│              │  ESG Service API (FastAPI)    │                            │
│              │  :8000  /docs  (Swagger UI)   │                            │
│              │  10 endpoints                 │                            │
│              └──────────────────────────────┘                            │
│                                                                          │
│  Kafka UI (Provectus)  :8080   ←  browse topics / messages / groups     │
│  Flink Dashboard       :8081   ←  monitor jobs / metrics / backpressure │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Cấu trúc thư mục

```
uip-esg-poc/
├── docker-compose.yml              # 11 services
├── Makefile                        # 30+ targets
├── README.md
│
├── init-db/
│   ├── timescaledb.sql             # hypertables + indexes + summary view
│   └── postgres-errors.sql         # error table + error_summary + error_by_source views
│
├── redpanda-connect/
│   ├── esg-normalize.yaml          # Benthos pipeline: raw → NGSI-LD (broker: kafka:9092)
│   └── console-config.yaml         # (obsolete – kept for reference, not mounted)
│
├── flink/
│   ├── Dockerfile                  # Flink 1.17 + PyFlink + Kafka/JDBC/PG JARs
│   └── jobs/
│       └── esg_processing.py       # PyFlink SQL StatementSet (3 concurrent INSERTs)
│
├── producer/
│   ├── Dockerfile
│   ├── requirements.txt
│   └── producer.py                 # 100K msgs, 7 error types, confluent-kafka
│
├── esg-api/
│   ├── Dockerfile
│   ├── requirements.txt
│   └── main.py                     # FastAPI, 10 endpoints
│
└── scripts/
    ├── check_results.py            # DB verification report
    ├── integration_tests.py        # 23 end-to-end test cases
    └── reingest_errors.py          # operator re-ingestion tool
```

---

## 4. Prerequisites

- Docker Desktop ≥ 24 (hoặc Docker Engine + Compose V2)
- RAM khuyến nghị: **≥ 8 GB** (Flink cần nhất ~2–3 GB)
- Disk trống: **≥ 5 GB**
- Python 3.9+ (chỉ cần để chạy scripts ngoài container)

---

## 5. Chạy POC

### Bước 1 – Khởi động stack

```bash
cd uip-esg-poc
make up
```

> **Lần đầu build** Flink Dockerfile download ~120 MB JARs → mất 3–5 phút.

### Bước 2 – Theo dõi tiến trình

```bash
make logs                 # tất cả services
make logs-producer        # chỉ xem producer gửi messages
make logs-flink-job       # chỉ xem Flink job submission
make logs-redpanda-connect # xem ETL normalisation pipeline
make status               # trạng thái containers
```

**Timeline dự kiến:**

| Thời điểm | Sự kiện |
|-----------|---------|
| 0 – 30 s  | Kafka KRaft khởi động (nhanh, không cần Zookeeper) |
| 30 – 60 s | Topics tạo xong, Redpanda Connect bắt đầu consume |
| 60 – 90 s | Flink cluster healthy, job được submit |
| 90 – 180 s | Producer gửi 100K messages (~60–90 s) |
| +60 s sau producer | Flink đóng cửa sổ aggregate đầu tiên |

### Bước 3 – Web UIs

| URL | Mô tả |
|-----|-------|
| http://localhost:8080 | **Kafka UI** – browse topics, messages, consumer groups |
| http://localhost:8081 | **Flink Dashboard** – jobs, metrics, backpressure |
| http://localhost:8000/docs | **ESG API** Swagger UI |
| http://localhost:8000/redoc | **ESG API** ReDoc |

### Bước 4 – Kiểm tra kết quả

```bash
make counts     # count nhanh cả 2 DB
make check      # báo cáo chi tiết
make test       # chạy 23 integration tests
```

---

## 6. Phân loại lỗi (Error Classification)

Flink `telemetry_classified` VIEW – priority order:

| # | error_type | Điều kiện | Tỉ lệ |
|---|-----------|-----------|--------|
| 1 | `MISSING_METER_ID` | `meter_id` null / empty | ~6% |
| 2 | `MISSING_VALUE` | `raw_value` null | ~6% |
| 3 | `INVALID_VALUE_FORMAT` | không parse được thành DOUBLE | ~4.5% |
| 4 | `OUT_OF_RANGE_NEGATIVE` | value < 0 | ~4.5% |
| 5 | `OUT_OF_RANGE_HIGH` | value > 10 000 | ~4.5% |
| 6 | `MISSING_UNIT` | `unit` null / empty | ~3% |
| 7 | `MISSING_TIMESTAMP` | `event_timestamp` null / empty | ~1.5% |

---

## 7. Kafka diagnostics

```bash
# List topics
make topics

# Describe một topic
make topic-desc T=ngsi_ld_telemetry

# Consumer group lag cho Flink processor
make lag

# Consumer group lag cho Redpanda Connect
make lag-connect

# Peek 5 messages từ ngsi_ld_telemetry
make peek

# Vào shell Kafka (dùng kafka-topics.sh, kafka-console-consumer.sh...)
make shell-kafka
```

---

## 8. Operator Workflow (xử lý lỗi)

```bash
# 1. Xem error breakdown
make shell-pg
#  SELECT * FROM error_summary;

# 2. Mark reviewed (psql)
make review ID=42 BY=admin
make review-type TYPE=MISSING_UNIT BY=admin

# 3. Mark reviewed (API)
curl -X POST http://localhost:8000/esg/data-quality/errors/42/review \
     -H 'Content-Type: application/json' \
     -d '{"reviewed_by":"admin","notes":"Sensor offline – expected"}'

# 4. Re-ingest (dry-run trước)
make reingest-dry
make reingest
make reingest-type TYPE=MISSING_UNIT
```

---

## 9. Useful Queries

```sql
-- TimescaleDB: tổng quan
SELECT * FROM esg_processing_summary;

-- Tiêu thụ điện theo site (1h gần nhất)
SELECT site_id, SUM(value) AS kwh
FROM esg_clean_metrics
WHERE measure_type='electric_kwh' AND ingested_at > NOW()-INTERVAL '1h'
GROUP BY site_id ORDER BY kwh DESC;

-- TimescaleDB time_bucket 5-min
SELECT time_bucket('5 minutes', ingested_at) AS bucket,
       meter_id, AVG(value) AS avg
FROM esg_clean_metrics
WHERE measure_type='co2_ppm'
GROUP BY bucket, meter_id ORDER BY bucket DESC LIMIT 20;

-- PostgreSQL: error breakdown
SELECT * FROM error_summary;
SELECT * FROM error_by_source;

-- Xem lỗi chưa review
SELECT id, error_type, meter_id, raw_value, error_detail
FROM esg_error_records WHERE reviewed=FALSE
ORDER BY received_at DESC LIMIT 20;
```

---

## 10. Customisation

| Env variable | Default | Mô tả |
|---|---|---|
| `TOTAL_MESSAGES` | `100000` | Số messages producer gửi |
| `VALID_RATIO` | `0.70` | Tỉ lệ valid messages |
| `BATCH_SIZE` | `500` | Flush interval producer |
| `MAX_VALUE_THRESHOLD` | `10000` | Ngưỡng OUT_OF_RANGE_HIGH |

---

## 11. Dọn dẹp

```bash
make down    # dừng, giữ volumes
make clean   # dừng + xóa tất cả volumes
```
