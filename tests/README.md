# tests/ — UIP Smart City Test Suites

## Cấu trúc

```
tests/
├── README.md
├── mvp1/
│   └── performance/     ← S4-05 performance KPI tests (Sprint 4, completed)
└── mvp2/
    └── performance/     ← Performance tests MVP2 (chờ implement)
```

## Chú ý: Smoke tests & Integration tests

**Smoke tests** (project-wide) nằm ở `scripts/uat_smoke_test.py`  
**Java unit + integration tests** nằm ở `backend/src/test/`  
**E2E tests (Playwright)** nằm ở `frontend/e2e/`

---

## MVP1 Performance Tests (`tests/mvp1/performance/`)

Sprint 4 — KPI validation: ≥2,000 msg/s throughput

| Script | Mục đích | Target KPI |
|--------|---------|-----------|
| `run_perf.sh` | Orchestration script chạy tất cả perf tests | — |
| `run_full_perf.sh` | 10-minute sustained MQTT load test (T-UAT-BE-02) | ≥2,000 msg/s |
| `mqtt_load_test.py` | MQTT load generator (N concurrent publishers) | Throughput |
| `kafka_producer.py` | Kafka direct producer (bypass Flink) | DB write throughput |
| `api_load_test.js` | k6 API load test (50 concurrent users) | p95 <200ms |
| `requirements.txt` | Python deps cho performance tests | — |

```bash
# Chạy full perf test suite (cần stack đang chạy)
cd tests/mvp1/performance
bash run_perf.sh
```

---

## MVP2 Performance Tests (`tests/mvp2/performance/`)

Sẽ được thêm vào trong Sprint MVP2-1/2:
- K8s load test (multi-tenant, nhiều tenant đồng thời)
- Multi-building aggregation latency
- Tenant isolation test (data không bị leak giữa tenant)
