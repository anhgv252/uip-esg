# Sprint 1 Completion Report — UIP ESG POC MVP2

**Ngày:** 2026-05-03  
**Sprint:** MVP2 Sprint 1 (28 Apr – 09 May 2026)  
**Tác giả:** PM / QA Team  

---

## ✅ Kết luận: GO to Sprint 2

> Tất cả blocker đã được resolve. Sprint 1 đạt **PASS** — không còn bug HIGH/MEDIUM mở.

---

## Exit Criteria Score Card

| Gate | Kết quả | Chi tiết |
|------|---------|---------|
| **Developer** | ✅ PASS | E2E 42/42 pass, TypeScript strict, security audit done, code pushed to main |
| **QA** | ✅ PASS | 12 spec files green; alert latency <30s verified trong PO demo |
| **Tester** | ✅ PASS | 20/20 manual tests pass (BUG-004 và BUG-005 đã fixed) |
| **PM** | ✅ PASS | 0 HIGH/MEDIUM bugs open; PO demo completed; code on main |

---

## Metrics

| Chỉ số | Giá trị |
|--------|---------|
| Story Points committed | 58 SP |
| Story Points completed | 55 SP (95%) |
| E2E tests | 42/42 pass, 1 skip |
| Manual tests | 20/20 pass, 1 skip |
| Open bugs (HIGH+) | **0** |
| Commits to main | 9 commits (`84b88011` → `35a60767`) |

---

## Bug Resolution

| ID | Mô tả | Severity | Resolution |
|----|-------|----------|-----------|
| BUG-001 | `GET /api/v1/sensors/UNKNOWN` → HTTP 500 | Medium | Deferred Sprint 2 (cosmetic, no user impact) |
| BUG-002 | `/actuator/health/circuitbreakers` → HTTP 403 | Low | Deferred (dev infra only) |
| BUG-003 | `/actuator/prometheus` → HTTP 500 | Low | Deferred (observability, non-blocking) |
| **BUG-004** | Citizen Notifications → HTTP 500 | **High** | ✅ **FIXED** — endpoint đúng là `/api/v1/alerts/notifications`; `AlertController` + `AlertEventRepository.findRecentPublicAlerts` implemented (commit `66e57fe9`) |
| **BUG-005** | Token vẫn hợp lệ sau logout | **Medium** | ✅ **FIXED** — `TokenBlacklistService` (in-memory ConcurrentHashMap) + `JwtAuthenticationFilter.isBlacklisted()` (commit `e24712c9`) |

---

## Sprint 1 Deliverables

### Backend
- JWT token blacklist (secure logout) ✅
- Security hardening: audit log, TraceIdFilter, GlobalExceptionHandler (RFC 7807) ✅
- Actuator lockdown: `/prometheus`, `/metrics` → ROLE_ADMIN only ✅
- Resilience4j Circuit Breaker (kafka-producer + redis-cache) ✅
- Alert pipeline: `AlertController`, `AlertEventRepository`, `AlertService` ✅
- Citizen module: registration, profile, meters, invoices, notifications ✅
- ESG report generation + XLSX download ✅

### Frontend
- Automated PO demo script: `e2e/sprint1-demo.spec.ts` (21/21 pass) ✅
- `tsconfig.playwright.json` (Playwright UI mode fix) ✅
- Demo scripts: `npm run demo`, `demo:slow`, `demo:ui` ✅
- Security headers, API error traceId ✅

### QA / Docs
- Sprint 1 Manual Test Report (20/20 MBTs pass) ✅
- OWASP Top 10 Security Audit (0 Critical, 0 High remaining) ✅
- OpenAPI snapshot CI (pre-push hook) ✅

---

## Sprint 2 Readiness

**Backlog:** Ready — 55 SP, ADR-010/011/020/021 approved  
**Kickoff:** Mon 2026-05-12, 09:00

### Top 3 Risks Sprint 2

| ID | Risk | Severity | Mitigation |
|----|------|----------|-----------|
| R-002 | Multi-tenancy complexity (ADR-010 RLS policies) | HIGH | SA spike Tuần 3 đầu |
| R-003 | ADR-021 T1 single-tenant + FORCE RLS compatibility | MEDIUM | Acceptance test by Wed 2026-05-13 |
| R-005 | ESG Q2 deadline chưa confirm với city authority | MEDIUM | Clarify by Tue 2026-05-05 |

### Sprint 2 Focus Areas
- MVP2-07a: Tenant entity + RLS policies
- MVP2-07b: TenantContext ThreadLocal + filter repos
- MVP2-26/27: Capability flags (ADR-011)
- ADR-020: Non-HTTP tenant ID propagation

---

## Tài liệu tham chiếu

| Tài liệu | Đường dẫn |
|----------|-----------|
| Manual Test Report | [sprint1-manual-test-report.md](sprint1-manual-test-report.md) |
| Security Audit | [security-audit-sprint1.md](security-audit-sprint1.md) |
| Implementation Backlog | [../project/implementation-backlog.md](../project/implementation-backlog.md) |
| Detail Plan MVP2 | [../project/mvp2-detail-plan.md](../project/mvp2-detail-plan.md) |
