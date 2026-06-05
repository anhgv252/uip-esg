# Sprint MVP3-10 — Master Plan: Pilot Readiness Completion

**Status:** 📋 PLANNED
**Document Date:** 2026-06-05
**Sprint Start:** 2026-07-02 (Wed)
**Sprint End:** 2026-07-15 (Tue EOD)
**Gate Review:** 2026-07-15 15:00 SGT
**Sprint trước:** MVP3-9 — BUFFER COMPLETE + HA LIVE (all Tier 1+2 DONE, SA APPROVED)
**PO:** anhgv

> **Context:** Sprint 9 buffer window hoàn thành vượt schedule 14 ngày. Tất cả Tier 1+2 tasks DONE, SA APPROVED.
> Sprint 10 là sprint **duy nhất còn lại** để hoàn thiện remaining items trước khi declare MVP3 Done
> và chuyển sang **Pilot Phase** (soft launch target 2026-08-04, Tier 2 Pilot SIGNED 2026-08-10).

---

## Context

Sprint 9 kết thúc với **✅ BUFFER COMPLETE** — HA stack live 2026-06-05, API Contract discipline deployed, CI smoke tests passing, SA Code Review APPROVED. Tuy nhiên còn **3 nhóm remaining items** cần giải quyết trước khi production-ready:

1. **API Contract Debt** — 49 undocumented endpoints (P0: 3 path issues, P1: 9 modules, P2: 3 debug endpoints)
2. **Pilot Security Hardening** — Keycloak live secret rotation, debug endpoint gating, iOS cert submission
3. **Pilot Readiness Polish** — Runbook update, regression baseline update, demo dry-run

**PO quyết định (2026-06-05):**
- **Sprint 10 focus:** API Contract Completion + Pilot Security + Pilot Readiness Gate
- **Không có new feature** — sprint investment 100% hoàn thiện chất lượng
- **Goal:** Declare MVP3 DONE → chuyển sang Pilot Phase

---

## 1. Sprint Overview

| Dimension | Value |
|---|---|
| **Sprint Name** | MVP3-10: API Contract Completion + Pilot Security + Readiness Gate |
| **Duration** | 2026-07-02 (Wed) → 2026-07-15 (Tue) — 10 calendar days (2 weeks) |
| **Team** | 5 FTE (Backend 2, Frontend 1, QA 1, DevOps 1) + SA spike |
| **Net Capacity** | ~47 SP |
| **Committed Tier 1** | 28 SP |
| **Committed Tier 2** | 12 SP |
| **Over-commit** | 0% — conservative, quality-focused sprint |

---

## 2. Sprint Goal (SMART)

Team sẽ đạt **HARD PASS** by 2026-07-15 15:00 SGT bằng cách:

1. **API Contract 100%** — Toàn bộ 110 endpoints documented trong `openapi.json`, 0 undocumented, CI contract check passing
2. **P0 Path Fixes** — WorkflowDefinitionController path aligned, alert resolve documented, admin/sensors documented or gated
3. **Pilot Security** — Keycloak live secret rotation verified, debug endpoints `@Profile("!production")`, iOS cert submitted
4. **Error Response Codes** — Auth, alert, sensor, ESG endpoints có đầy đủ 401/403/404/400 responses
5. **Pilot Readiness** — Runbook updated với HA instructions, regression baseline ≥1,300 tests, demo dry-run approved
6. **Tech Debt Clear** — SA fix backlog = 0, mobile offline UX spike documented, dual SSE stream resolved

---

## 3. Backlog Committed

### Tier 1 — PHẢI DONE (28 SP)

#### Epic 1: API Contract Completion [13 SP] — P0 từ S9 Audit

| ID | Story | SP | Owner | Priority | AC | Status |
|---|---|---|---|---|---|---|
| S10-CONTRACT-01 | Fix P0-1: Align WorkflowDefinitionController paths — 7 endpoints documented trong OpenAPI spec với đúng request/response schemas | 2 | Backend-1 | **P0** | `npm run gen-api-types` includes workflow endpoints; CI contract check PASS | ✅ DEV DONE |
| S10-CONTRACT-02 | Fix P0-2: Document `PUT /api/v1/alerts/{id}/resolve` trong OpenAPI spec + add to generated types | 1 | Backend-1 | **P0** | Alert state machine complete in spec; frontend can discover resolve action | ✅ DEV DONE |
| S10-CONTRACT-03 | Fix P0-3: Document hoặc gate `POST /api/v1/admin/sensors` — add to spec với `@PreAuthorize` documented | 1 | Backend-1 | **P0** | Spec có endpoint với security schema; hoặc endpoint bị `@Profile("!production")` | ✅ DEV DONE |
| S10-CONTRACT-04 | Document TenantAdminController — 10 endpoints, highest-privilege module | 3 | Backend-2 | **P0** | 10/10 tenant admin endpoints documented với auth requirements, request/response DTOs | ✅ DEV DONE |
| S10-CONTRACT-05 | Document BMS module — 7 endpoints (BmsDeviceController + BmsDeviceCommandController) | 2 | Backend-2 | **P1** | BMS endpoints documented; command endpoint có security schema cho actuator control | ✅ DEV DONE |
| S10-CONTRACT-06 | Document 9 remaining modules: Buildings (6), Push (5), Forecast (2), Dashboard (2), Analytics (1), ESG PDF (1), Mobile Auth (1), Invite (1), SSE stream (1) | 4 | Backend-1 + Backend-2 | **P1** | Tất cả 110 endpoints documented; `npm run gen-api-types` generates complete types | ✅ DEV DONE |

#### Epic 2: API Contract Quality [5 SP]

| ID | Story | SP | Owner | Priority | AC | Status |
|---|---|---|---|---|---|---|
| S10-CONTRACT-07 | Add error response codes (401, 403, 404, 400) cho auth, alert, sensor, ESG report, tenant admin endpoints — minimum 15 critical endpoints | 2 | Backend-1 | **P0** | Spec có error responses; generated types include error DTOs | ✅ DEV DONE |
| S10-CONTRACT-08 | Gate debug/test endpoints với `@Profile("!production")` — FloodTestController (2) + FakeTrafficDataController (1) | 1 | Backend-2 | **P0** | `curl POST /api/v1/test/inject-reading` → 404 trên production profile | ✅ DEV DONE |
| S10-CONTRACT-09 | Resolve dual SSE stream: canonical URL `/api/v1/alerts/stream`, deprecate hoặc redirect `/api/v1/notifications/stream` | 1 | Backend-1 | **P1** | Một canonical SSE URL; frontend/mobile updated; spec documents đúng URL | ✅ DEV DONE |
| S10-CONTRACT-10 | Regenerate `packages/api-types/` + verify frontend/mobile compile; update CI contract drift check | 1 | Frontend | **P0** | `npx tsc --noEmit` 0 errors; CI contract check PASS with new spec | ✅ DEV DONE |

#### Epic 3: Pilot Security Hardening [5 SP]

| ID | Story | SP | Owner | Priority | AC | Status |
|---|---|---|---|---|---|---|
| S10-SEC-01 | Keycloak live secret rotation trên staging — verify uip-api secret rotation + backend reconnect | 1 | DevOps | **P0** | Login flow succeed sau rotation; old secret rejected | ✅ DEV DONE — procedure documented |
| S10-SEC-02 | iOS Developer Certificate submission — Apple Developer account + cert request | 1 | DevOps + Frontend | **P1** | Cert submitted; Apple review started (48-72h turnaround) | ⏳ PENDING — cần Apple Developer account (manual) |
| S10-SEC-03 | Production profile review — verify all `@Profile("!production")` gates work; no test/debug endpoints leak | 1 | Backend-2 | **P0** | Spring Boot `production` profile active → 3 debug endpoints return 404 | ✅ DEV DONE — ProductionProfileSecurityTest added |
| S10-SEC-04 | OWASP dependency check update — fix any new CVEs high+; verify SonarQube clean | 2 | DevOps + QA | **P1** | 0 new high+ CVEs; SonarQube quality gate PASS | ⏳ PENDING — cần chạy scan thực tế |

#### Epic 4: Pilot Readiness Gate [5 SP]

| ID | Story | SP | Owner | Priority | AC | Status |
|---|---|---|---|---|---|---|
| S10-PILOT-01 | Pilot Runbook update — HA instructions, Keycloak rotation, CH failover, Kafka broker recovery, rollback procedures | 2 | DevOps | **P0** | Runbook covers 6 incident scenarios; Backend Lead + DevOps sign off | ✅ DEV DONE |
| S10-PILOT-02 | Regression baseline update — chạy full regression on HA staging; target ≥1,300 tests, 100% PASS | 2 | QA + Tester | **P0** | ≥1,300 tests PASS; 0 FAIL; regression report attached | ⏳ PENDING — cần HA staging environment |
| S10-PILOT-03 | Demo dry-run — 5-min executive demo script; PO + PM verify | 1 | PM + All | **P0** | Demo script rehearsed; no P0 blockers found | ✅ DEV DONE — demo script + gate review template created |

---

### Tier 2 — BEST EFFORT (12 SP)

| ID | Story | SP | Owner | Priority | AC | Status |
|---|---|---|---|---|---|---|
| S10-TD-01 | Mobile offline UX spike — document UX flows, create wireframes, estimate implementation SP cho v3.1 | 2 | Frontend + BA | P2 | UX doc exists; implementation estimate in v3.1 backlog | 📋 NOT STARTED |
| S10-TD-02 | BPMN Workflow Designer UX polish — improve node styles, toolbar, properties panel | 3 | Frontend | P2 | PO reviews improved UI; no blocking UX issues | 📋 NOT STARTED |
| S10-TD-03 | ESG PDF Export — `POST /api/v1/esg/reports/pdf` sync endpoint với iText/OpenPDF | 3 | Backend-1 | P2 | PDF generates with GRI 302/305 tables; spec documented | ✅ DEV DONE — endpoint đã tồn tại từ Sprint 7 |
| S10-TD-04 | Android APK build pipeline — Expo EAS build config cho APK distribution | 2 | DevOps + Frontend | P2 | `eas build --platform android` produces downloadable APK | 📋 NOT STARTED |
| S10-TD-05 | Mobile Control Panel UX spike — actuator command confirmation flow wireframes | 2 | Frontend + BA | P2 | UX doc exists; HIGH danger confirmation pattern documented | 📋 NOT STARTED |

---

### Tier 3 — DESCOPE (v3.1 Backlog)

| ID | Story | SP | Lý do descope |
|---|---|---|---|
| S10-MOBILE-OFFLINE | Mobile offline mode full implementation | 8 | Cần UX spike trước (S10-TD-01) |
| S10-CHAOS | Automated chaos engineering (Chaos Mesh / Toxiproxy) | 5 | Manual chaos validated, không block pilot |
| S10-AVRO-MIGRATE | Avro full migration 4 topics (dual-publish → Avro-only) | 8 | Schema Registry deployed, dual-publish đủ cho pilot |
| S10-GRPC | gRPC internal service migration (ADR-012) | 13 | REST acceptable cho pilot; planned v3.1 |
| S10-PACT | Pact/Spring Cloud Contract framework | 5 | ADR-039 OpenAPI-first đủ cho pilot |
| S10-CVE-NET | Open CVE network mitigation | 3 | Không block pilot |

---

## 4. Milestones

| Date | Milestone | Gate |
|------|-----------|------|
| **2026-07-02 (Wed)** | Sprint 10 Kickoff — SA review OpenAPI gap priorities | |
| **2026-07-03 (Thu)** | P0 contract fixes DONE (S10-CONTRACT-01/02/03) — 3 critical paths resolved | GATE-0 |
| **2026-07-04 (Fri)** | Debug endpoints gated (S10-CONTRACT-08) + Production profile verified (S10-SEC-03) | |
| **2026-07-07 (Mon)** | Tenant admin + BMS endpoints documented (S10-CONTRACT-04/05) | |
| **2026-07-08 (Tue)** | All 110 endpoints documented (S10-CONTRACT-06) — CI contract check GREEN | GATE-1 |
| **2026-07-09 (Wed)** | Error response codes done (S10-CONTRACT-07) + Generated types verified (S10-CONTRACT-10) | |
| **2026-07-10 (Thu)** | Keycloak live rotation verified (S10-SEC-01) + iOS cert submitted (S10-SEC-02) | |
| **2026-07-11 (Fri)** | Pilot Runbook updated + OWASP scan clean | GATE-2 |
| **2026-07-12 (Sat)** | Full regression run on HA staging (≥1,300 tests) | |
| **2026-07-13 (Sun)** | SA Code Review (mandatory per CLAUDE.md) | |
| **2026-07-14 (Mon)** | Demo dry-run with PO + Tier 2 final push | |
| **2026-07-15 (Tue)** | Sprint 10 Close — Gate Review 15:00 SGT — **DECLARE MVP3 DONE** | **FINAL GATE** |

---

## 5. Team Assignments

### Backend-1 (6 SP Tier 1 + 3 SP Tier 2)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1 | S10-CONTRACT-01: Fix WorkflowDefinitionController path + document 7 endpoints | 2 |
| Day 1-2 | S10-CONTRACT-02: Document alert resolve endpoint | 1 |
| Day 2 | S10-CONTRACT-03: Document/gate admin/sensors endpoint | 1 |
| Day 4-6 | S10-CONTRACT-06: Document Buildings (6) + Push (5) + Forecast (2) + Dashboard (2) + remaining 4 | 4 (shared) |
| Day 3-4 | S10-CONTRACT-07: Add error response codes for 15 critical endpoints | 2 |
| Day 5 | S10-CONTRACT-09: Resolve dual SSE stream | 1 |
| Day 7-8 | S10-TD-03: ESG PDF Export (Tier 2) | 3 |
| Day 9-10 | SA Code Review support + buffer | — |

### Backend-2 (5 SP Tier 1 + 0 SP Tier 2)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1-3 | S10-CONTRACT-04: Document TenantAdminController — 10 endpoints | 3 |
| Day 3-4 | S10-CONTRACT-05: Document BMS module — 7 endpoints | 2 |
| Day 5 | S10-CONTRACT-08: Gate debug endpoints với `@Profile("!production")` | 1 (shared) |
| Day 5-6 | S10-SEC-03: Production profile review — verify gates work | 1 (shared) |
| Day 7-8 | S10-CONTRACT-06: Support remaining module documentation | — (shared) |
| Day 8-10 | SA Code Review support + regression support | — |

### Frontend (1 SP Tier 1 + 5 SP Tier 2)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 3-4 | S10-CONTRACT-10: Regenerate api-types + verify compile | 1 |
| Day 5-6 | S10-TD-02: BPMN Workflow Designer UX polish | 3 |
| Day 7-8 | S10-TD-01: Mobile offline UX spike (wireframes + estimate) | 2 |
| Day 9-10 | S10-TD-04: Support Android APK build config (Tier 2) | — |

### DevOps (5 SP Tier 1 + 2 SP Tier 2)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1 | S10-SEC-01: Keycloak live secret rotation trên staging | 1 |
| Day 2-3 | S10-SEC-02: iOS Developer Certificate submission | 1 |
| Day 4-5 | S10-PILOT-01: Pilot Runbook update — 6 incident scenarios | 2 |
| Day 6-7 | S10-SEC-04: OWASP dependency check + SonarQube | 2 (shared with QA) |
| Day 8-9 | S10-TD-04: Android APK build pipeline (Tier 2) | 2 |
| Day 10 | Buffer + SA review support | — |

### QA + Tester (2 SP Tier 1 + 0 SP Tier 2)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1-3 | Prepare regression suite — add S10 contract test cases | — |
| Day 5-6 | S10-SEC-04: OWASP scan (pair DevOps) | — (shared) |
| Day 8-10 | S10-PILOT-02: Full regression on HA staging (≥1,300 tests) | 2 |
| Day 10 | Regression report + Sprint 10 test report | — |

### SA (spike support)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1 | Review P0 contract fix approach (WorkflowDefinitionController path) | — |
| Day 3 | Review error response code patterns | — |
| Day 5 | Review Pilot Runbook draft | — |
| Day 9 | SA Code Review (mandatory — sprint10-code-review.md) | — |

---

## 6. Dependencies

```
S10-CONTRACT-01/02/03 (P0 fixes) ───→ S10-CONTRACT-10 (regenerate types)
S10-CONTRACT-04/05 (module docs) ────→ S10-CONTRACT-06 (remaining modules)
S10-CONTRACT-06 (all endpoints) ─────→ S10-CONTRACT-07 (error codes)
S10-CONTRACT-07 + S10-CONTRACT-10 ──→ S10-PILOT-02 (regression needs final spec)
S10-SEC-01 (Keycloak rotation) ──────→ unblocks external pilot invite
S10-SEC-02 (iOS cert) ───────────────→ unblocks IPA build (48-72h Apple review)
S10-SEC-03 (production profile) ─────→ S10-PILOT-01 (runbook references profile)
S10-PILOT-01 (runbook) + S10-PILOT-02 (regression) ──→ S10-PILOT-03 (demo dry-run)
```

**Critical Path:**
```
Day 1-2: P0 contract fixes (Backend-1) ──→ Day 3-4: Module docs (Backend-2)
Day 4-6: All 110 endpoints documented (pair) ──→ Day 7: Error codes + type regen
Day 8: Keycloak rotation + OWASP ──→ Day 9: SA Code Review
Day 10: Regression + Demo dry-run ──→ FINAL GATE (DECLARE MVP3 DONE)
```

---

## 7. API Contract Fix Priority Matrix

### P0 — Phải fix Day 1-2 (block pilot)

| # | Endpoint | Controller | Fix | Owner |
|---|----------|-----------|-----|-------|
| 1-7 | `/api/v1/workflows` (×7) | WorkflowDefinitionController | Align path + add to spec | Backend-1 |
| 8 | `PUT /api/v1/alerts/{id}/resolve` | AlertController | Add to spec | Backend-1 |
| 9 | `POST /api/v1/admin/sensors` | AdminController | Add to spec or gate | Backend-1 |

### P1 — Phải fix Day 3-6 (block production security)

| Module | Endpoints | Security Risk | Owner |
|--------|-----------|---------------|-------|
| Tenant Admin (10) | Full CRUD + user invite + settings | **HIGHEST** — multi-tenant admin | Backend-2 |
| BMS Devices (7) | Device CRUD + command injection | **HIGH** — actuator control | Backend-2 |
| Buildings (6) | Cluster + safety queries | MEDIUM — data access | Backend-1 |
| Push (5) | Subscribe + test push | MEDIUM — includes test endpoint | Backend-1 |
| Forecast (2) | Energy prediction | LOW | Backend-1 |
| Dashboard (2) | Stats aggregation | LOW | Backend-1 |
| ESG PDF (1) | Sync PDF generation | LOW | Backend-1 |
| Analytics (1) | Cross-building aggregate | MEDIUM | Backend-1 |
| Mobile Auth (1) | PKCE config | LOW | Backend-1 |
| Invite (1) | Accept invitation | LOW | Backend-1 |
| SSE Stream (1) | Alert real-time | LOW | Backend-1 |

### P2 — Best effort (Day 7-8)

| # | Endpoint | Fix |
|---|----------|-----|
| 47 | `POST /api/v1/test/inject-reading` | `@Profile("!production")` |
| 48 | `POST /api/v1/test/inject-flood-alert` | `@Profile("!production")` |
| 49 | `GET /api/v1/internal/fake-traffic` | `@Profile("!production")` |

### Error Response Codes — Minimum 15 critical endpoints

| Endpoint | Missing Codes | Priority |
|----------|---------------|----------|
| `POST /api/v1/auth/login` | 401 | P0 |
| `POST /api/v1/auth/invite/accept` | 400, 401, 404 | P0 |
| `PUT /api/v1/alerts/{id}/acknowledge` | 403, 404 | P0 |
| `PUT /api/v1/alerts/{id}/resolve` | 403, 404 | P0 |
| `POST /api/v1/esg/reports/generate` | 400, 403 | P0 |
| `GET /api/v1/environment/sensors/{id}/readings` | 404 | P1 |
| `POST /api/v1/admin/tenants` | 400, 401, 403 | P0 |
| `POST /api/v1/admin/tenants/{id}/users/invite` | 400, 401, 403 | P0 |
| `PUT /api/v1/admin/tenants/{id}/users/{uid}/role` | 400, 401, 403, 404 | P0 |
| `POST /api/v1/bms/devices` | 400, 401, 403 | P1 |
| `POST /api/v1/bms/devices/{id}/commands` | 400, 401, 403, 404 | P0 |
| `POST /api/v1/buildings` | 400, 401, 403 | P1 |
| `POST /api/v1/push/subscribe` | 400, 401 | P1 |
| `GET /api/v1/forecast/energy` | 401, 404 | P2 |
| `POST /api/v1/workflows` | 400, 401, 403 | P1 |

---

## 8. Risk Register

| ID | Risk | Probability | Impact | Owner | Mitigation |
|----|------|------------|--------|-------|------------|
| R-01 | WorkflowDefinitionController path fix breaks existing frontend BPMN calls | MEDIUM (30%) | HIGH | Backend-1 + Frontend | Frontend verify Day 2; test both old and new paths |
| R-02 | OpenAPI spec regeneration produces breaking types (enum renames, optional changes) | LOW (20%) | MEDIUM | Frontend | Pin openapi-generator version; commit current types as baseline |
| R-03 | Keycloak live rotation breaks backend login on staging | LOW (15%) | HIGH | DevOps | Test on staging first; have rollback (re-import old realm) |
| R-04 | iOS Apple review rejects cert (team/account issues) | MEDIUM (25%) | MEDIUM | DevOps | Start Day 2; Android APK as fallback |
| R-05 | 49 endpoints doc effort > estimated — sprint overrun | LOW (20%) | MEDIUM | Backend Lead | Prioritize P0 (Day 1-2) + P1 security-sensitive (Day 3-5); P1 low-risk can defer v3.1 |
| R-06 | Regression reveals new bugs — fix cycle eats into sprint buffer | MEDIUM (30%) | MEDIUM | QA + Backend | Sprint 8 precedent: 13 bugs fixed same-day; have 2-day buffer |
| R-07 | `@Profile("!production")` change breaks test controller in CI | LOW (15%) | LOW | Backend-2 | CI runs with `dev` profile; add `@ActiveProfiles("dev")` to test classes |
| R-08 | OWASP scan finds new high CVE in dependency — requires version bump | LOW (20%) | MEDIUM | DevOps | Check early Day 6; have 4 days buffer for fix |

---

## 9. Quality Gates

### Hard Gates (14) — ALL MUST PASS

| Gate | Criterion | Verifier | Status |
|------|-----------|----------|--------|
| G1 | OpenAPI spec: 110/110 endpoints documented, 0 undocumented | SA audit + CI | 📋 |
| G2 | CI contract drift check: `npm run gen-api-types && git diff --exit-code` PASS | CI pipeline | 📋 |
| G3 | Frontend/Mobile: `npx tsc --noEmit` 0 errors with regenerated types | CI pipeline | 📋 |
| G4 | Production profile: `curl POST /api/v1/test/inject-reading` → 404 | DevOps verify | 📋 |
| G5 | Keycloak: uip-api secret rotation verified; old secret rejected | SA audit | 📋 |
| G6 | Error responses: ≥15 critical endpoints có 401/403/404/400 documented | SA audit | 📋 |
| G7 | iOS cert: submitted to Apple (or documented blocker with Android fallback) | PM verify | 📋 |
| G8 | Pilot Runbook: 6 incident scenarios documented, Backend Lead + DevOps sign off | PM verify | 📋 |
| G9 | Regression: ≥1,300 tests PASS, 0 FAIL on HA staging | QA report | 📋 |
| G10 | OWASP: 0 new high+ CVEs | SonarQube + manual | 📋 |
| G11 | SA Code Review: APPROVED (sa-fix-backlog = 0 carryover) | SA | 📋 |
| G12 | Demo dry-run: PO approves 5-min demo script | PO sign-off | 📋 |
| G13 | Total tests ≥1,300 (285 S8 + 34 S9 + S10 additions); 0 failures | CI | 📋 |
| G14 | Debug endpoints: 0 reachable khi `spring.profiles.active=production` | Integration test | 📋 |

### Soft Gates (4) — Best Effort

| Gate | Criterion | Status |
|------|-----------|--------|
| GS1 | BPMN Workflow Designer UX improved (PO review) | 📋 |
| GS2 | Mobile offline UX spike documented (wireframes + SP estimate) | 📋 |
| GS3 | ESG PDF Export functional (sync endpoint) | 📋 |
| GS4 | Android APK build pipeline configured (Expo EAS) | 📋 |

---

## 10. Cut Order (nếu chậm tiến độ)

```
1. S10-TD-05  Mobile Control Panel UX spike     (2 SP) — Tier 2 first cut
2. S10-TD-04  Android APK build pipeline        (2 SP) — Tier 2
3. S10-TD-03  ESG PDF Export                    (3 SP) — Tier 2
4. S10-TD-02  BPMN UX Polish                    (3 SP) — Tier 2
5. S10-TD-01  Mobile offline UX spike           (2 SP) — Tier 2
6. S10-CONTRACT-06  Remaining P1 low-risk modules (1 SP subset) — document security-sensitive only
7. S10-CONTRACT-07  Error response codes         (2 SP) — reduce to P0 endpoints only
```

**Mục tiêu tối thiểu (G1-G12):** P0 contract fixes + Security hardening + Runbook + Regression = declare MVP3 DONE.

**Worst case:** Declare MVP3 DONE với P1 low-risk documentation deferred v3.1. Pilot proceeds.

---

## 11. ADR Register (Sprint 10)

Không có ADR mới. Sprint 10 là quality/completion sprint.

---

## 12. MVP3 Declaration Criteria

Sprint 10 gate pass = **DECLARE MVP3 DONE**. Cần đạt:

- [x] ~220 SP delivered qua Sprint 1-10 (target met)
- [ ] 110/110 API endpoints documented trong OpenAPI spec ← Sprint 10 deliverable
- [ ] 0 P0/P1 bugs open
- [ ] ≥1,300 regression tests PASS
- [ ] OWASP 0 Critical findings
- [ ] Pilot Runbook reviewed + sign-off
- [ ] PO demo approved
- [ ] SA Code Review APPROVED

**Post-MVP3 (v3.1 Backlog):**

| Feature | SP | Priority | Target |
|---------|-----|----------|--------|
| Avro full migration (4 topics) | 8 | P1 | Q3 2026 |
| gRPC internal service transport | 13 | P1 | Q3 2026 |
| Mobile offline mode | 8 | P2 | Q3 2026 |
| Mobile Control Panel | 5 | P2 | Q3 2026 |
| ESG PDF Export (if not done S10) | 5 | P2 | Q3 2026 |
| Automated chaos engineering | 5 | P3 | Q4 2026 |
| Pact contract testing | 5 | P3 | Q4 2026 |
| CVE network mitigation | 3 | P3 | Q3 2026 |

---

## 13. Definition of Done (Sprint 10)

Thêm vào DoD chuẩn:

- [ ] `openapi.json` documents 110/110 endpoints (CI enforced)
- [ ] Every `@RestController` endpoint either in spec or gated by `@Profile`
- [ ] Debug/test endpoints return 404 when `spring.profiles.active=production`
- [ ] Keycloak uip-api secret rotated on staging (old secret rejected)
- [ ] Pilot Runbook reviewed by Backend Lead + DevOps (sign-off in doc)
- [ ] SA Fix Backlog = 0 before sprint close
- [ ] Generated `packages/api-types/` matches `openapi.json` hash (CI enforced)

---

## 14. Tech Debt Carried Forward (v3.1)

| ID | Item | Priority | Sprint 10 Action | v3.1 Action |
|----|------|----------|------------------|-------------|
| TD-S10-01 | Avro dual-publish → Avro-only migration | P1 | N/A | Full migration 4 topics |
| TD-S10-02 | gRPC internal transport (ADR-012) | P1 | N/A | Implement protobuf + gRPC stubs |
| TD-S10-03 | Mobile offline mode | P2 | UX spike only (S10-TD-01) | Implement cached data + sync queue |
| TD-S10-04 | Mobile Control Panel | P2 | UX spike only (S10-TD-05) | Implement confirmation flow |
| TD-S10-05 | Automated chaos engineering | P3 | N/A | Chaos Mesh / Toxiproxy integration |
| TD-S10-06 | Pact contract testing | P3 | N/A | Provider/consumer pact verification |
| TD-S10-07 | OpenAPI spec quality — all endpoints get error codes | P1 | P0 endpoints only (15/49) | Complete all endpoints |
| TD-S10-08 | CH Keeper memory monitoring | P2 | Monitor | Alert if Keeper >2GB RSS |
| TD-S10-09 | Pilot host RAM profile (16GB ceiling) | P2 | Document in runbook | Scale vertically or split nodes |

---

## 15. Stakeholder Communication

| Frequency | Format | Owner | Audience |
|-----------|--------|-------|----------|
| Day 2 (2026-07-03) | Status: P0 contract fixes done | Backend Lead | PO |
| Day 5 (2026-07-07) | Status: API contract 100% | SA | PO + PM |
| Day 8 (2026-07-10) | Security hardening verified | DevOps | PO + SA |
| Day 9 (2026-07-11) | Regression results | QA | PM + PO |
| **2026-07-15 15:00** | **Sprint 10 Gate Review — DECLARE MVP3 DONE** | **All** | **PO + City Authority** |

---

## 16. Sprint 10 → Pilot Phase Transition

**Nếu Gate PASS (2026-07-15):**

```
2026-07-16 → 2026-07-31:  Pilot Preparation (2 weeks)
  - Pilot environment setup (HA staging → production)
  - City Authority onboarding (users, training)
  - Data migration / seeding for pilot buildings
  - Monitoring dashboards configured for pilot
  
2026-08-01 → 2026-08-03:  Pilot Soft Launch (internal team)
  - Internal team tests production environment
  - Final smoke tests on production
  - Runbook drill (simulate 2 incidents)

2026-08-04:               🎯 PILOT SOFT LAUNCH (5 buildings, 2 tenants)
  
2026-08-04 → 2026-08-10: Pilot Week 1 (monitor + fix)
  
2026-08-10:               🎯 TIER 2 PILOT SIGNED
```

**Nếu Gate FAIL:**
- Identify blocking items → 2-day hotfix sprint
- Re-gate 2026-07-17
- Worst case: pilot delay 1 week (2026-08-11 soft launch)

---

*Document: Sprint 10 Master Plan v1.0 | Created 2026-06-05*
*Based on: Sprint 9 close-out, API Contract Audit (49 endpoints), Pilot Readiness Assessment*
*Previous: [Sprint 9 Plan](sprint9-plan.md)*
