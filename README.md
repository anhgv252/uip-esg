# UIP — Urban Intelligence Platform

> **Nền tảng Đô thị Thông minh** (Smart City Platform)
> On-Premise | Java Spring Boot | Apache Flink | TimescaleDB | React

---

## Tổng quan

UIP là nền tảng quản lý đô thị thông minh tích hợp:
- **Giám sát môi trường** (AQI, nhiệt độ, độ ẩm, CO2)
- **Theo dõi ESG** (điện, nước, carbon)
- **Quản lý giao thông** (mật độ, sự cố)
- **Cổng cư dân** (hộ khẩu, hóa đơn tiện ích, thông báo)
- **AI Workflow** (7 kịch bản tự động hoá với Claude API)

## Tài liệu

| Tài liệu | Đường dẫn |
|---|---|
| Master Plan | [docs/project/master-plan.md](docs/project/master-plan.md) |
| Architecture Overview | [docs/architecture/overview.md](docs/architecture/overview.md) |
| API Documentation | `docs/api/` |

## Cấu trúc dự án

```
uip-esg-poc/
├── poc/               ← POC ban đầu (archived, đã validate pipeline)
├── backend/           ← Spring Boot (Java) — API + Business Logic
├── flink-jobs/        ← Apache Flink (Java) — Stream Processing
├── frontend/          ← React 18 + TypeScript — Dashboards + Citizen Portal
├── infrastructure/    ← Docker Compose + EMQX + ThingsBoard + Kafka configs
├── docs/              ← Architecture, Project docs, API specs
└── .claude/           ← AI Agent configuration
```

## Tech Stack

| Layer | Technology |
|---|---|
| IoT MQTT Broker | EMQX CE 5.x |
| IoT Platform | ThingsBoard CE 3.x |
| Message Broker | Apache Kafka 3.7 KRaft |
| ETL | Redpanda Connect (Benthos) |
| Stream Processing | Apache Flink 1.19 (Java) |
| Database | TimescaleDB (PostgreSQL 16) + PgBouncer |
| Cache | Redis 7.x |
| Backend | Spring Boot 3.2 (Java 21) |
| Frontend | React 18 + TypeScript + MUI + Leaflet |
| AI | Claude API (claude-sonnet-4-6) |

## Quick Start

```bash
# Khởi động full stack (UAT)
cd infrastructure
docker compose up -d

# Xem logs
docker compose logs -f

# Status
docker compose ps
```

## POC

Pipeline ban đầu (PyFlink + FastAPI) được lưu tại [`poc/`](poc/). Xem [`poc/README.md`](poc/README.md) để chạy POC.

---

> **Timeline MVP1:** 28/03/2026 → 28/05/2026
