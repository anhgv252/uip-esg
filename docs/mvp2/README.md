# MVP2 — Production Hardening + Multi-Tenancy

**Trạng thái:** 🔄 IN PROGRESS  
**Thời gian dự kiến:** Q2 2026 (10–12 tuần)  
**Sprint start:** 2026-04-28

---

## MVP2 Goal

> "UIP sẵn sàng bán cho khách hàng Tier 1 (Single Building) thực tế với SLA-bearing deployment"

---

## Sprint MVP2-1 (Tuần 1–2): Security + QA Gaps

**Goal:** Loại bỏ P0 security risks và fill critical test gaps

| ID | Story | SP | Owner |
|----|-------|----|-------|
| MVP2-01 | HashiCorp Vault integration + secrets rotation | 8 | DevOps |
| MVP2-03a | Alert escalation tests (GAP-01,02,09,10) | 5 | Backend+QA |
| MVP2-03b | Cache service tests (GAP-04,05,06) | 5 | Backend+QA |
| MVP2-03c | CB + audit tests (GAP-03,07,08,11,12) | 3 | Backend+QA |
| MVP2-04 | EntityNotFoundException → 404 mapping | 3 | Backend |
| MVP2-05 | Circuit Breaker state persistence | 5 | Backend |
| MVP2-06 | Cache eviction retry + TTL giảm 60s | 3 | Backend |
| MVP2-16 | OpenAPI CI gate | 5 | QA |
| MVP2-18 | Security audit OWASP Top 10 | 8 | Security |

---

## Sprint MVP2-2 (Tuần 3–4): Multi-Tenancy + Monitoring

**Goal:** Tenant isolation sẵn sàng; production observability; Tier 1 UAT

| ID | Story | SP | Owner |
|----|-------|----|-------|
| MVP2-20 | tenant_id + LTREE location_path schema migration | 5 | Backend |
| MVP2-07a | Tenant entity + RLS policy | 8 | Backend |
| MVP2-07b | TenantContext ThreadLocal + filter tất cả repositories | 5 | Backend |
| MVP2-08 | Kubernetes Helm charts (backend, frontend, infra) | 8 | DevOps |
| MVP2-09 | GitHub Actions CI/CD pipeline | 5 | DevOps |
| MVP2-10 | Prometheus + Grafana + alerting rules | 8 | DevOps |
| MVP2-11 | PostgreSQL WAL backup + PITR | 5 | DevOps |
| MVP2-14 | API rate limiting per tenant | 5 | Backend |

---

## Roadmap tổng thể MVP2 → v4.0

Xem: [Demo & Roadmap 2026-04-25](project/demo-and-roadmap-2026-04-25.md)

## Implementation Backlog (ADR-driven)

Xem: [Implementation Backlog](project/implementation-backlog.md) — task cụ thể theo từng ADR, file cần tạo/sửa, dependency map

---

## Cấu trúc tài liệu MVP2

| Thư mục | Nội dung |
|---------|---------|
| `project/` | Sprint plans, PO demo scripts, roadmap |
| `architecture/` | ADRs mới: ADR-010 multi-tenant, ADR-011 module extraction, ... |
| `api/` | openapi.json snapshot khi release MVP2 |
| `deployment/` | K8s Helm values, CI/CD guide, Vault setup |
| `qa/` | Test plans, QA reports theo sprint |
| `reports/` | Performance tests, security audit reports |

---

## ADRs

### Tất cả ADRs — xem `architecture/`

| ADR | Title | Status |
|-----|-------|--------|
| ADR-010 | Multi-tenant strategy: tenant_id + LTREE (metadata only) + RLS + HikariCP SET LOCAL | ✅ Accepted |
| ADR-011 | Monorepo architecture + capability flags + module extraction order + strangler fig | ✅ Accepted |
| ADR-012 | ClickHouse adoption: chỉ khi ESG query >5 phút hoặc >10K sensors | ✅ Accepted |
| ADR-013 | Edge computing: EMQX Edge buffer (T2), Flink Edge agg (T3), Edge AI (T4) | ✅ Accepted |
| ADR-014 | Telemetry Enrichment Pattern: inject tenant_id vào stream | ✅ Accepted |
| ADR-015 | Caching & Read-Heavy Performance: Redis TTL + Continuous Aggregates | ✅ Accepted |
| ADR-016 | Data Lakehouse: Iceberg on MinIO + Trino (không Snowflake) | ✅ Accepted |
| ADR-017 | Multi-region: Warm DR trước → Active-Active khi ≥2 cities | ✅ Accepted |
| ADR-019 | Partner Customization: 3-layer extension (DB config → YAML → Spring module) | ✅ Accepted |
