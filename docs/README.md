# UIP Smart City — Tài liệu dự án

> **Platform:** Urban Intelligence Platform (Smart City)  
> **Stack:** Spring Boot · Apache Flink · TimescaleDB · Kafka · React · Claude AI

---

## Cấu trúc thư mục

```
docs/
├── README.md              ← file này (index tổng thể)
├── api/
│   └── openapi.json       ← OpenAPI spec HIỆN TẠI (live, auto-updated)
├── mvp1/                  ← MVP1: Sprint 1-5 (28/03-24/04/2026) ✅ COMPLETED
│   ├── README.md
│   ├── project/           ← master plan, sprint reviews, demo scripts
│   ├── architecture/      ← architecture overview, ADRs
│   ├── deployment/        ← UAT guide, env vars, kafka topics
│   ├── qa/                ← test reports Sprint 3-5
│   ├── prompts/           ← AI agent prompts dùng trong Sprint 4-5
│   ├── reports/           ← performance reports, bug reports
│   └── testing/           ← manual test sessions, test dashboards
└── mvp2/                  ← MVP2: Q2 2026 (IN PROGRESS 🔄)
    ├── README.md
    ├── project/           ← roadmap, sprint plans
    ├── architecture/      ← ADRs mới (multi-tenant, scale, etc.)
    ├── api/               ← openapi.json snapshot khi release
    ├── deployment/        ← K8s Helm, CI/CD guides
    ├── qa/                ← test plans và reports
    └── reports/           ← performance, security audit
```

---

## Milestones & Tags

| Tag | Ngày | Mô tả |
|-----|------|--------|
| `mvp1` | 2026-04-25 | MVP1 complete: 5 sprints, ~221 SP, tất cả KPIs đạt |

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

### MVP1 (Completed)
- [Master Plan](mvp1/project/master-plan.md)
- [Sprint 3 Review Report](mvp1/project/sprint3-review-report.md)
- [Architecture Overview](mvp1/architecture/overview.md)
- [UAT Guide](mvp1/deployment/UAT-GUIDE.md)
- [Performance Report](mvp1/reports/performance/s4-05-full-report.md)

### MVP2 (In Progress)
- [Demo & Roadmap (2026-04-25)](mvp2/project/demo-and-roadmap-2026-04-25.md)
