# UIP Smart City — Tài liệu dự án

> **Platform:** Urban Intelligence Platform (Smart City)
> **Stack:** Spring Boot · Apache Flink · TimescaleDB · ClickHouse · Kafka · React · Claude AI
> **Trạng thái:** ✅ **MVP3 COMPLETED** — Sẵn sàng Pilot Deployment (2026-08-04)

---

## 📖 WIKI Tổng hợp

**👉 [WIKI.md](WIKI.md)** — Tài liệu tổng hợp toàn bộ dự án (MVP1 → MVP3), bao gồm:
- Tổng quan, Timeline & Milestones
- Kiến trúc Hệ thống (C4, deployment modes, 7 nguyên tắc)
- Modules & Tính năng (108 endpoints, 168 components)
- 40 Architecture Decision Records (ADRs)
- Testing & Chất lượng (~1,709 tests, 0 failures)
- Infrastructure & DevOps (HA stack, monitoring, runbooks)
- Lessons Learned, Pilot Plan, v3.1 Roadmap
- Quick Links đến tất cả tài liệu quan trọng

---

## Cấu trúc thư mục

```
docs/
├── README.md              ← file này (index)
├── WIKI.md                ← 📖 WIKI tổng hợp toàn bộ dự án (MVP1→MVP3)
├── api/
│   └── openapi.json       ← OpenAPI spec (live, auto-updated)
├── adr/                   ← Cross-MVP ADRs (ADR-039, ADR-040, ...)
├── architecture/          ← Kiến trúc độc lập sprint + SA Reviews
├── mvp1/                  ← MVP1: Foundation (28/03→24/04/2026) ✅ COMPLETED
│   ├── README.md
│   ├── project/           ← Master plan, sprint reviews, demo scripts
│   ├── architecture/      ← Architecture overview, ADRs
│   ├── deployment/        ← UAT guide, env vars, kafka topics
│   ├── qa/                ← Test reports Sprint 3-5
│   ├── prompts/           ← AI agent prompts (Sprint 4-5)
│   ├── reports/           ← Performance reports, bug reports
│   └── testing/           ← Manual test sessions
├── mvp2/                  ← MVP2: Multi-Tenancy (28/04→08/05/2026) ✅ COMPLETED
│   ├── README.md
│   ├── project/           ← Roadmap, sprint plans, demo scripts
│   ├── architecture/      ← ADR-010 to ADR-025 (multi-tenant, caching, etc.)
│   ├── deployment/        ← Runbook, oncall playbook, Kafka registry
│   ├── qa/                ← Test plans and reports
│   └── reports/           ← Performance, security audit, UAT sign-off
├── mvp3/                  ← MVP3: Building Cluster + HA + AI (12/05→11/06/2026) ✅ COMPLETED
│   ├── README.md          ← Master plan (10 sprints, ~347 SP)
│   ├── MVP3-SUMMARY.md    ← Complete summary + Gate review results
│   ├── project/           ← Sprint plans (S1-S11), demo scripts, investor brief
│   ├── architecture/      ← ADR-026 to ADR-038, system architecture, spike reports
│   ├── qa/                ← Test strategies, regression reports, bug tracker
│   ├── reports/           ← Sprint closeouts, code reviews, final assessments
│   ├── design/            ← Mobile UX specs
│   ├── infrastructure/    ← HA setup, Kong runbook
│   ├── ops/               ← Pilot runbook, APK build, Keycloak rotation
│   ├── test/              ← Test execution plans
│   ├── testing/           ← Integration guides, manual test sessions
│   ├── security/          ← OWASP templates and checklists
│   └── changes/           ← Change orders (CH HA descoped, etc.)
├── mvp4/                  ← MVP4: AI Scale + Correlation + Self-Service (08→10/2026) 📋 PLANNING
│   ├── README.md          ← Master plan (6 sprints, ~203 SP)
│   ├── project/           ← Sprint plans (S1-S6)
│   ├── architecture/      ← ADR-041 to ADR-046
│   ├── qa/                ← Test strategies
│   └── reports/           ← Sprint reviews, gate review
├── sa/                    ← SA skill + architecture review
├── security/              ← OWASP dependency check report
└── troubleshooting/       ← JaCoCo fixes, coverage reports
```

---

## Milestones & Tags

| Tag | Ngày | Mô tả |
|-----|------|--------|
| `mvp1` | 2026-04-25 | MVP1 complete: 5 sprints, ~221 SP, tất cả KPIs đạt |
| `mvp2` | 2026-05-08 | MVP2 complete: 6 sprints, ~168 SP, multi-tenant + UAT sign-off |
| `mvp3` | 2026-06-11 | MVP3 complete: 11 sprints, ~347 SP, HA + AI + Mobile |

---

## API Spec (luôn hiện tại)

File `docs/api/openapi.json` là **live file**, được cập nhật tự động:

```bash
# Cập nhật từ backend đang chạy
bash scripts/update-openapi-spec.sh

# Regenerate TypeScript types
cd frontend && npm run generate:types
```

---

## Quick Links

### Tổng quan
- **📖 [WIKI.md](WIKI.md)** — Tổng hợp toàn bộ dự án
- [Investor Q&A & Product Roadmap](investor-qa-product-roadmap-2026-06-06.md)
- [SA Architecture Review](sa/sa-architecture-review-uip-mvp3.md)

### MVP1 (Completed ✅)
- [Master Plan](mvp1/project/master-plan.md)
- [Architecture Overview](mvp1/architecture/overview.md)
- [UAT Guide](mvp1/deployment/UAT-GUIDE.md)
- [Performance Report](mvp1/reports/performance/s4-05-full-report.md)

### MVP2 (Completed ✅)
- [Demo & Roadmap](mvp2/project/demo-and-roadmap-2026-04-25.md)
- [Multi-Tenant Training Playbook](mvp2/project/multi-tenant-training-playbook.md)
- [UAT Sign-off](mvp2/reports/mvp2-uat-signoff.md)
- [Runbook](mvp2/deployment/runbook.md) · [Oncall Playbook](mvp2/deployment/oncall-playbook.md)
- [Security Audit](mvp2/reports/security-audit-sprint1.md)

### MVP3 (Completed ✅)
- [Master Plan](mvp3/README.md) · [Complete Summary](mvp3/MVP3-SUMMARY.md)
- [Close-out Assessment](mvp3/reports/mvp3-closeout-assessment-2026-06-11.md)
- [System Architecture](mvp3/architecture/system-architecture.md)
- [Pilot Runbook](mvp3/ops/pilot-runbook.md)
- [Sprint 10 Gate Review](mvp3/project/sprint10-gate-review.md)
- [Investor Brief](mvp3/project/investor-brief-2026-06-06.md)

### MVP4 (Planning 📋)
- [Master Plan](mvp4/README.md) — 6 sprints, ~203 SP, Aug→Oct 2026
