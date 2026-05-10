# UIP Smart City — Wiki Index

> **Platform:** Urban Intelligence Platform (Smart City)
> **Stack:** Spring Boot · Apache Flink · TimescaleDB · Kafka · React · Claude AI

---

## Quick Navigation

| Chủ đề | File | Mô tả |
|--------|------|-------|
| **🆕 MVP3 Master Plan** | [mvp3/README.md](mvp3/README.md) | Building Cluster v3.0 — 6 sprints, Tier 2 pilot 2026-08-10 |
| **Roadmap tổng thể** | [mvp2/project/demo-and-roadmap-2026-04-25.md](mvp2/project/demo-and-roadmap-2026-04-25.md) | 4 Tier strategy, ROI, 17 user stories, KPIs |
| **Executive Demo Script** | [mvp2/project/executive-demo-script.md](mvp2/project/executive-demo-script.md) | 10 phút cho lãnh đạo/đối tác |
| **Demo Setup Script** | [scripts/demo-setup.sh](../scripts/demo-setup.sh) | Tự động kiểm tra trước demo |
| **UAT Sign-off** | [mvp2/reports/mvp2-uat-signoff.md](mvp2/reports/mvp2-uat-signoff.md) | 15/15 PASS, đã ký |
| **Runbook** | [mvp2/deployment/runbook.md](mvp2/deployment/runbook.md) | Deploy, Rollback, Restore, 6 incident procedures |
| **Oncall Playbook** | [mvp2/deployment/oncall-playbook.md](mvp2/deployment/oncall-playbook.md) | P0/P1/P2 severity, alert scenarios |

---

## Architecture & Decisions

| ADR | Title | Status | Scope |
|-----|-------|--------|-------|
| [ADR-010](mvp2/architecture/ADR-010-multi-tenant-strategy.md) | Multi-Tenant Isolation: tenant_id + RLS + HikariCP SET LOCAL | Accepted | MVP2 |
| [ADR-011](mvp2/architecture/ADR-011-monorepo-module-extraction.md) | Monorepo + Capability Flags + Module Extraction | Accepted | MVP2 |
| [ADR-014](mvp2/architecture/ADR-014-telemetry-enrichment-pattern.md) | Telemetry Enrichment: inject tenant_id vào Kafka stream | Accepted | MVP2 |
| [ADR-015](mvp2/architecture/ADR-015-caching-read-heavy-performance.md) | Caching: Redis TTL + TimescaleDB Continuous Aggregates | Accepted | MVP2 |
| [ADR-019](mvp2/architecture/ADR-019-partner-customization-architecture.md) | Partner Customization: 3-layer extension | Accepted | MVP2 |
| [ADR-020](mvp2/architecture/ADR-020-non-http-tenant-propagation.md) | Non-HTTP Tenant ID Propagation (Kafka/Flink) | Accepted | MVP2 |
| [ADR-021](mvp2/architecture/ADR-021-t1-force-rls-compat.md) | T1 Single-Tenant + FORCE RLS Compatibility | Accepted | MVP2 |
| [ADR-022](mvp2/architecture/ADR-022-cache-warming-strategy.md) | Cache Warming Strategy After Batch Write | Accepted | MVP2 |
| [ADR-023](mvp2/architecture/ADR-023-rls-migration-strategy.md) | RLS Migration: Zero-Downtime Strategy | Accepted | MVP2 |
| [ADR-025](mvp2/architecture/ADR-025-tenant-admin-authorization.md) | Tenant Admin Authorization | Accepted | MVP2 |

**Deferred ADRs (v3.0+):**
| ADR | Title | Trigger |
|-----|-------|---------|
| [ADR-012](mvp2/architecture/ADR-012-clickhouse-adoption-trigger.md) | ClickHouse Adoption | ESG query >5min p95 hoặc >10K sensors |
| [ADR-013](mvp2/architecture/ADR-013-edge-computing-strategy.md) | Edge Computing | Site T2 với WAN >70% |
| [ADR-016](mvp2/architecture/ADR-016-data-lakehouse-strategy.md) | Data Lakehouse | Historical >2 năm |
| [ADR-017](mvp2/architecture/ADR-017-multi-region-strategy.md) | Multi-Region | SLA 99.95%+ |

---

## Multi-Tenancy Knowledge Base

| Tài liệu | Mô tả |
|----------|-------|
| [Training Playbook](mvp2/project/multi-tenant-training-playbook.md) | 90 phút training: architecture, RLS, JWT claims, checklist |
| [ADR-010: Multi-Tenant Strategy](mvp2/architecture/ADR-010-multi-tenant-strategy.md) | T1/T2/T3 isolation, JWT claims, HikariCP |
| [ADR-020: Non-HTTP Propagation](mvp2/architecture/ADR-020-non-http-tenant-propagation.md) | Kafka/Flink/@Async tenant context |
| [ADR-021: T1 + FORCE RLS](mvp2/architecture/ADR-021-t1-force-rls-compat.md) | Single-tenant backward compat |

---

## Modules

| Module | Backend | Frontend | Test Report |
|--------|---------|----------|-------------|
| Dashboard | — | `pages/DashboardPage.tsx` | Sprint 6 UAT |
| Environment Monitoring | `EnvironmentController` + `EnvironmentService` | `pages/EnvironmentPage.tsx` | Sprint 6 UAT |
| ESG Tracking | `EsgController` + `EsgService` + `@Cacheable` | `pages/EsgPage.tsx` | k6 cache benchmark |
| Alert Management | `AlertController` + `AlertService` + `@Cacheable` | `pages/AlertsPage.tsx` | Sprint 6 UAT |
| City Operations | — | `pages/CityOpsPage.tsx` + Leaflet map | Sprint 6 UAT |
| Traffic | `TrafficController` | `pages/TrafficPage.tsx` | Sprint 6 UAT |
| AI Workflow | Camunda 7 + Claude API | `pages/AiWorkflowPage.tsx` | Sprint 5 demo |
| Citizen Portal (PWA) | Push API | `pages/citizen/Mobile*` | Lighthouse 95 |
| Tenant Admin | 6 API endpoints (Sprint 4) | `pages/tenant-admin/*` | Sprint 6 UAT |

---

## Testing & Performance

| Tài liệu | Mô tả |
|----------|-------|
| [Sprint 6 Test Report](mvp2/reports/sprint6-test-report-2026-05-08.md) | 18/18 E2E PASS, k6 cache 11x, load test 1000 VU |
| [Sprint 5 Demo + Retro](mvp2/reports/sprint5-po-demo-and-retro-2026-05-07.md) | Multi-tenant demo, lessons learned |
| [Security Audit](mvp2/reports/security-audit-sprint1.md) | OWASP Top 10, 0 Critical, 16 fixed |
| [OWASP Dependency Check](security/owasp-dependency-check-report-2026-05-06.md) | 16 CVE fixed, 0 Critical open |

### Performance Baselines (2.45M rows, 1000 VU k6 test 2026-05-10)

| Endpoint | Cold p95 | Warm p95 | Cache Hit | k6 1000 VU p95 |
|----------|----------|----------|-----------|----------------|
| ESG Summary | 70ms (cagg) | 10ms | 100% | 1361ms |
| Sensors (cached) | ~10ms | ~10ms | ✅ New | 1364ms |
| Alerts (cached) | ~10ms | ~10ms | ✅ New | 2714ms |
| Health check | — | — | — | 2012ms |

> **Ghi chú:** p95 cao ở 1000 VU là do chạy trên single dev machine. Production K8s 3 replicas theo Helm values (HPA min 3 / max 8) sẽ scale tuyến tính.

### Capacity

| Scenario | Max VUs | Throughput | Error Rate |
|----------|---------|------------|------------|
| Dev small data | ~150 VUs | ~150 RPS | <0.1% |
| Dev 2.4M rows (trước optimize) | ~80 VUs | ~100 RPS | ~89% errors |
| Dev 2.4M rows (sau optimize) | 1000 VUs | ~549 RPS | 0.009% |
| Production (3 replicas K8s) | 500+ VUs | 500+ RPS (est.) | <0.1% |

---

## Project Management

| Tài liệu | Mô tả |
|----------|-------|
| [Detail Plan](mvp2/project/mvp2-detail-plan.md) | Full Sprint 1-6 plan, all tasks, dependency chain |
| [Implementation Backlog](mvp2/project/implementation-backlog.md) | ADR-driven task list |
| [Team Presentation](mvp2/project/team-presentation-architecture.md) | 60 phút kiến trúc cho team |
| [Sprint Retro Checklist](mvp2/project/sprint-retrospective-checklist.md) | Template cho retro |

---

## Lessons Learned (cross-sprint)

1. **Test với dữ liệu thực**: 105 rows cho kết quả sai hoàn toàn — luôn test ≥2M rows
2. **Pre-demo dry-run bắt buộc**: Sprint 5 smoke 0/8 pass vì backend không chạy
3. **Multi-tenant correctness > feature breadth**: 1 data leak = mất niềm tin
4. **Cache key phải chứa tenant_id từ ngày 1**: Cross-tenant leak qua cache
5. **Tomcat/HikariCP config cho load**: Default 200 threads / 20 connections = bottleneck
6. **Sync FE/BE types trước implement**: OpenAPI gate prevents contract drift
7. **Runbook drill quarterly**: 3 drills PASS nhưng cần lặp lại

---

## API Spec

File [api/openapi.json](api/openapi.json) là **live file** — cập nhật từ backend:
```bash
bash scripts/update-openapi-spec.sh
cd frontend && npm run generate:types
```

---

---

## MVP3 — Building Cluster v3.0 (Planning)

| Tài liệu | Mô tả |
|----------|-------|
| [MVP3 Master Plan](mvp3/README.md) | 6 sprints, milestone map, risk register, budget |
| `mvp3/architecture/` | ADR-026 đến ADR-034 (cần viết) |
| `mvp3/qa/` | Test strategy Tier 2, ClickHouse consistency, BMS tests |

*Tổng hợp từ 79 tài liệu — cập nhật 2026-05-10*
