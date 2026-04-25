# MVP1 — Platform Foundation + Core Modules + AI Workflow

**Trạng thái:** ✅ COMPLETED  
**Thời gian:** 28/03/2026 → 24/04/2026 (5 sprints)  
**Git tag:** `mvp1` (commit `03a23bfc`)  
**Story Points:** ~221 SP

---

## KPIs đạt được

| KPI | Target | Đạt được |
|-----|--------|---------|
| Alert latency (sensor → SSE) | <30s | <30s ✅ |
| API p95 latency | <200ms | ~120ms ✅ |
| Sensor ingestion (Flink) | ≥2,000 msg/s | 2,500 msg/s ✅ |
| ESG report generation | <10 phút | <1 phút ✅ |
| Test coverage (backend) | ≥75% | 78.9% ✅ |
| AI workflows | 7/7 | 7/7 ✅ |

---

## Modules đã hoàn thành

| Module | Mô tả |
|--------|--------|
| IoT Foundation | EMQX + ThingsBoard + Kafka KRaft + Redpanda Connect |
| Environment | AQI (EPA), sensor readings, trend charts |
| ESG | Energy/Water/Carbon KPIs, XLSX report |
| Traffic | HTTP adapter, vehicle counts, incidents |
| Alert System | Threshold detection, SSE <30s, acknowledge/escalate |
| City Ops Center | Leaflet real-time map, sensor overlay, alert feed |
| AI Workflow | Camunda 7 embedded + 7 Claude AI scenarios |
| Citizen Portal | Registration wizard, bills, notifications |
| Auth/RBAC | JWT, 3 roles: Admin/Operator/Citizen |

---

## Sprint History

| Sprint | Ngày | SP | Nội dung |
|--------|------|----|---------|
| Sprint 1 | 28/03 | 52 | Platform Foundation: EMQX, Kafka, Flink, Spring Boot, JWT |
| Sprint 2 | 31/03 | 53 | Environment + ESG + Alert + SSE |
| Sprint 3 | 06/04 | 60 | City Ops Center + Traffic + Citizen Portal |
| Sprint 4 | ~23/04 | 56 | AI Workflow (7 scenarios) + Camunda 7 |
| Sprint 5 | 24/04 | — | Tech Debt: Circuit Breaker, Audit Log, Cache |

---

## Tài liệu trong thư mục này

| Thư mục | Nội dung |
|---------|---------|
| `project/` | Master plan, sprint reviews, PO demo scripts |
| `architecture/` | Architecture overview, ADRs |
| `deployment/` | UAT-GUIDE, environment variables, Kafka topic registry |
| `qa/` | Manual test reports Sprint 3/4/5, E2E test suite |
| `prompts/` | AI agent prompts dùng trong Sprint 4-5 |
| `reports/performance/` | Load test results (API, Kafka, MQTT) |
| `reports/bugs/` | Bug reports theo sprint |
| `testing/` | Manual test sessions, frontend test dashboards |

---

## Open Gaps (chuyển sang MVP2)

Từ QA Sprint 5 — 12 test gaps cần xử lý trong MVP2:

| ID | Mô tả | Severity |
|----|--------|---------|
| GAP-01/02 | `escalateAlert()` / `getPublicNotifications()` zero unit tests | P0 |
| GAP-04 | `TriggerConfigCacheService` zero tests | P1 |
| GAP-05 | Kafka publish trong controller chưa verify | P1 |
| GAP-06 | PROPAGATION_REQUIRES_NEW isolation chưa test | P1 |
| GAP-10 | `getPublicNotifications` status filter vs BA spec | P1 |
| GAP-03,07,08,09,11,12 | Các gaps P2 còn lại | P2 |

Xem chi tiết: [Sprint 5 QA Report](../.claude/../../../.claude/workdir/qa-sprint5-report.md)
