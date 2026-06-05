# Sprint MVP3-10 — Task Assignments by Role

**Generated:** 2026-06-05
**Sprint:** 2026-07-02 (Wed) → 2026-07-15 (Tue)
**Gate Review:** 2026-07-15 15:00 SGT
**Total Committed:** 40 SP (Tier 1: 28 SP + Tier 2: 12 SP)
**Team:** 5 FTE + SA spike support

---

## 📋 Assignment Summary

| Role | Tier 1 SP | Tier 2 SP | Total SP | Primary Focus |
|------|-----------|-----------|----------|---------------|
| **Backend-1** | 6 | 3 | 9 | API Contract P0 fixes + Error codes + ESG PDF |
| **Backend-2** | 5 | 0 | 5 | Tenant Admin + BMS docs + Security gates |
| **Frontend** | 1 | 5 | 6 | API type regeneration + UX spikes |
| **DevOps** | 5 | 2 | 7 | Security hardening + Pilot Runbook |
| **QA + Tester** | 2 | 0 | 2 | Regression suite + OWASP scan |
| **SA** | — | — | — | Review + Code Review (mandatory) |
| **PM** | — | — | — | Demo dry-run + Gate review |
| **BA** | — | — | — | UX spike input + Acceptance sign-off |

---

## 👨‍💻 Backend-1 — Primary Assignments

**Team Member:** Backend Lead
**Capacity:** 9 SP (6 Tier 1 + 3 Tier 2)
**Sprint Focus:** API Contract P0 Fixes + Error Response Codes

### Task List

| # | Task ID | Story | SP | Sprint Day | Priority | Dependencies |
|---|---------|-------|-----|-----------|----------|--------------|
| 1 | **S10-CONTRACT-01** | Fix P0-1: Align WorkflowDefinitionController paths — 7 endpoints documented trong OpenAPI spec với đúng request/response schemas | 2 | Day 1 | **P0** | None — start immediately |
| 2 | **S10-CONTRACT-02** | Fix P0-2: Document `PUT /api/v1/alerts/{id}/resolve` trong OpenAPI spec + add to generated types | 1 | Day 1-2 | **P0** | None |
| 3 | **S10-CONTRACT-03** | Fix P0-3: Document hoặc gate `POST /api/v1/admin/sensors` — add to spec với `@PreAuthorize` documented | 1 | Day 2 | **P0** | None |
| 4 | **S10-CONTRACT-06** | Document Buildings (6) + Push (5) + Forecast (2) + Dashboard (2) + Analytics (1) + Mobile Auth (1) + Invite (1) + SSE Stream (1) | 4 | Day 4-6 | **P1** | After CONTRACT-04/05 done |
| 5 | **S10-CONTRACT-07** | Add error response codes (401, 403, 404, 400) cho auth, alert, sensor, ESG report, tenant admin — minimum 15 critical endpoints | 2 | Day 3-4 | **P0** | None |
| 6 | **S10-CONTRACT-09** | Resolve dual SSE stream: canonical URL `/api/v1/alerts/stream`, deprecate hoặc redirect `/api/v1/notifications/stream` | 1 | Day 5 | **P1** | None |
| 7 | **S10-TD-03** | ESG PDF Export — `POST /api/v1/esg/reports/pdf` sync endpoint với iText/OpenPDF | 3 | Day 7-8 | P2 | After Tier 1 tasks |
| 8 | — | SA Code Review support + buffer | — | Day 9-10 | — | — |

### Acceptance Criteria Checklist

- [ ] `npm run gen-api-types` includes workflow endpoints
- [ ] Alert state machine complete in OpenAPI spec
- [ ] `POST /api/v1/admin/sensors` có security schema hoặc bị gated
- [ ] 19 endpoints documented (Buildings + Push + Forecast + Dashboard + remaining)
- [ ] ≥15 critical endpoints có error response codes
- [ ] Một canonical SSE URL hoạt động
- [ ] PDF generates with GRI 302/305 tables

### Key Risk Items
- **R-01:** WorkflowDefinitionController path fix có thể break frontend BPMN calls → Frontend verify Day 2
- **R-05:** 49 endpoints doc effort có thể overrun → prioritize P0 + security-sensitive P1

### Handoff Protocol

**→ Frontend (Day 3-4):**
```
DECIDED: WorkflowDefinitionController paths aligned, alert resolve documented
DONE: openapi.json updated with 9 P0 endpoints + error codes
NEXT: S10-CONTRACT-10 — Regenerate packages/api-types/ + verify compile
OPEN: Verify frontend BPMN workflow calls work with new paths
```

---

## 👨‍💻 Backend-2 — Primary Assignments

**Team Member:** Backend Engineer 2
**Capacity:** 5 SP (5 Tier 1 + 0 Tier 2)
**Sprint Focus:** Tenant Admin + BMS Documentation + Security Gates

### Task List

| # | Task ID | Story | SP | Sprint Day | Priority | Dependencies |
|---|---------|-------|-----|-----------|----------|--------------|
| 1 | **S10-CONTRACT-04** | Document TenantAdminController — 10 endpoints, highest-privilege module | 3 | Day 1-3 | **P0** | None — start immediately |
| 2 | **S10-CONTRACT-05** | Document BMS module — BmsDeviceController + BmsDeviceCommandController (7 endpoints) | 2 | Day 3-4 | **P1** | After CONTRACT-04 |
| 3 | **S10-CONTRACT-08** | Gate debug endpoints với `@Profile("!production")` — FloodTestController (2) + FakeTrafficDataController (1) | 1 | Day 5 | **P0** | None (shared) |
| 4 | **S10-SEC-03** | Production profile review — verify all `@Profile("!production")` gates work; no test/debug endpoints leak | 1 | Day 5-6 | **P0** | After CONTRACT-08 |
| 5 | **S10-CONTRACT-06** | Support remaining module documentation | — | Day 7-8 | **P1** | After CONTRACT-04/05 |
| 6 | — | SA Code Review support + regression support | — | Day 8-10 | — | — |

### Acceptance Criteria Checklist

- [ ] 10/10 tenant admin endpoints documented với auth requirements, request/response DTOs
- [ ] BMS endpoints documented; command endpoint có security schema cho actuator control
- [ ] `curl POST /api/v1/test/inject-reading` → 404 trên production profile
- [ ] `curl POST /api/v1/test/inject-flood-alert` → 404 trên production profile
- [ ] `curl GET /api/v1/internal/fake-traffic` → 404 trên production profile
- [ ] Spring Boot `production` profile active → ALL 3 debug endpoints return 404

### Key Risk Items
- **R-07:** `@Profile("!production")` có thể break test controller trong CI → CI runs `dev` profile; add `@ActiveProfiles("dev")` to test classes

### Handoff Protocol

**→ DevOps (Day 5):**
```
DECIDED: Debug endpoints gated with @Profile("!production")
DONE: 3 debug controllers annotated; integration test added
NEXT: S10-SEC-03 — Verify production profile in staging deployment
OPEN: None — ready to verify
```

---

## 🎨 Frontend — Primary Assignments

**Team Member:** Frontend Engineer
**Capacity:** 6 SP (1 Tier 1 + 5 Tier 2)
**Sprint Focus:** API Type Regeneration + UX Polish + Mobile Spikes

### Task List

| # | Task ID | Story | SP | Sprint Day | Priority | Dependencies |
|---|---------|-------|-----|-----------|----------|--------------|
| 1 | **S10-CONTRACT-10** | Regenerate `packages/api-types/` + verify frontend/mobile compile; update CI contract drift check | 1 | Day 3-4 | **P0** | ⚠️ Blocked by S10-CONTRACT-01/02/03 |
| 2 | **S10-TD-02** | BPMN Workflow Designer UX polish — improve node styles, toolbar, properties panel | 3 | Day 5-6 | P2 | None |
| 3 | **S10-TD-01** | Mobile offline UX spike — document UX flows, create wireframes, estimate implementation SP cho v3.1 | 2 | Day 7-8 | P2 | BA input needed |
| 4 | **S10-TD-04** | Support Android APK build config — Expo EAS build setup | — | Day 9-10 | P2 | DevOps lead |
| 5 | **S10-TD-05** | Mobile Control Panel UX spike — actuator command confirmation flow wireframes | 2 | Tier 2 | P2 | BA input needed |

### Acceptance Criteria Checklist

- [ ] `npx tsc --noEmit` → 0 errors after type regeneration
- [ ] CI contract drift check PASS: `npm run gen-api-types && git diff --exit-code`
- [ ] Frontend BPMN workflow calls verified with new WorkflowDefinitionController paths
- [ ] PO reviews improved BPMN Designer UI — no blocking UX issues
- [ ] Mobile offline UX doc exists; implementation SP estimate in v3.1 backlog
- [ ] `eas build --platform android` produces downloadable APK (with DevOps)

### Key Risk Items
- **R-01:** New WorkflowDefinitionController paths break existing frontend BPMN calls → verify Day 2 (after Backend-1 delivers)
- **R-02:** OpenAPI spec regeneration produces breaking types → pin openapi-generator version; commit current types as baseline

### Handoff Protocol

**From Backend-1 (expected Day 3):**
```
DECIDED: openapi.json updated, 9 P0 endpoints documented
NEXT: Regenerate packages/api-types/ + run tsc --noEmit
OPEN: Verify BPMN workflow integration with new paths
```

---

## 🛠️ DevOps — Primary Assignments

**Team Member:** DevOps Engineer
**Capacity:** 7 SP (5 Tier 1 + 2 Tier 2)
**Sprint Focus:** Pilot Security Hardening + Runbook + Build Pipeline

### Task List

| # | Task ID | Story | SP | Sprint Day | Priority | Dependencies |
|---|---------|-------|-----|-----------|----------|--------------|
| 1 | **S10-SEC-01** | Keycloak live secret rotation trên staging — verify uip-api secret rotation + backend reconnect | 1 | Day 1 | **P0** | None — start immediately |
| 2 | **S10-SEC-02** | iOS Developer Certificate submission — Apple Developer account + cert request | 1 | Day 2-3 | **P1** | Apple account access |
| 3 | **S10-PILOT-01** | Pilot Runbook update — HA instructions, Keycloak rotation, CH failover, Kafka broker recovery, rollback procedures (6 scenarios) | 2 | Day 4-5 | **P0** | After SEC-01 (rotation procedure feeds runbook) |
| 4 | **S10-SEC-04** | OWASP dependency check update — fix any new CVEs high+; verify SonarQube clean | 2 | Day 6-7 | **P1** | None (shared with QA) |
| 5 | **S10-TD-04** | Android APK build pipeline — Expo EAS build config cho APK distribution | 2 | Day 8-9 | P2 | Expo project setup |
| 6 | — | Buffer + SA review support | — | Day 10 | — | — |

### Pilot Runbook — 6 Incident Scenarios Required

1. **Keycloak secret rotation failure** — rollback procedure
2. **ClickHouse node failure** — HA failover steps
3. **Kafka broker down** — recovery procedure
4. **Application deployment rollback** — blue-green rollback
5. **Database connection pool exhaustion** — HikariCP tuning emergency
6. **SSE/WebSocket connection storm** — rate limiting activation

### Acceptance Criteria Checklist

- [ ] Login flow succeeds sau Keycloak secret rotation; old secret rejected
- [ ] iOS cert submitted to Apple; review started (48-72h turnaround)
- [ ] Pilot Runbook covers 6 incident scenarios; Backend Lead + DevOps sign off
- [ ] 0 new high+ CVEs from OWASP scan; SonarQube quality gate PASS
- [ ] `eas build --platform android` produces downloadable APK

### Key Risk Items
- **R-03:** Keycloak live rotation breaks backend login → test on staging first; have rollback (re-import old realm)
- **R-04:** iOS Apple review rejects cert → start Day 2; Android APK as fallback
- **R-08:** OWASP finds new high CVE → check early Day 6; have 4 days buffer

### Handoff Protocol

**→ SA (Day 5):**
```
DECIDED: Keycloak rotation verified on staging
DONE: Rotation procedure documented → feeds into Pilot Runbook
NEXT: SA review Pilot Runbook draft
OPEN: iOS cert status depends on Apple review timeline
```

---

## 🧪 QA + Tester — Primary Assignments

**Team Member:** QA Engineer + Manual Tester
**Capacity:** 2 SP (2 Tier 1 + 0 Tier 2)
**Sprint Focus:** Regression Suite Preparation + Full Regression Run

### Task List

| # | Task ID | Story | SP | Sprint Day | Priority | Dependencies |
|---|---------|-------|-----|-----------|----------|--------------|
| 1 | — | Prepare regression suite — add S10 contract test cases | — | Day 1-3 | — | None — start immediately |
| 2 | **S10-SEC-04** | OWASP scan (pair DevOps) — verify 0 high+ CVEs | — | Day 5-6 | **P1** | None (shared) |
| 3 | **S10-PILOT-02** | Full regression on HA staging — target ≥1,300 tests, 100% PASS | 2 | Day 8-10 | **P0** | ⚠️ Blocked by all Tier 1 contract work |
| 4 | — | Regression report + Sprint 10 test report | — | Day 10 | — | After regression run |

### Regression Test Coverage Required

| Category | Target Tests | Focus Areas |
|----------|-------------|-------------|
| API Contract | 110 endpoints | All documented endpoints respond per spec |
| Auth + Security | ≥50 tests | JWT, Keycloak, @PreAuthorize, production profile |
| Alert Pipeline | ≥30 tests | Flood alert, AQI alert, resolve, acknowledge |
| ESG Reports | ≥20 tests | GRI 302/305, PDF generation |
| Sensor Data | ≥40 tests | Ingestion, aggregation, forecast |
| Integration | ≥100 tests | Kafka → Flink → ClickHouse → API |
| Existing Regression | ~950 tests | Sprint 1-9 baseline |
| **Total Target** | **≥1,300 tests** | **0 FAIL** |

### Acceptance Criteria Checklist

- [ ] ≥1,300 tests PASS on HA staging environment
- [ ] 0 FAIL in regression run
- [ ] 0 new high+ CVEs from OWASP dependency check
- [ ] SonarQube quality gate PASS
- [ ] Regression report attached to Sprint 10 close-out document
- [ ] All S10 contract test cases added to regression suite

### Key Risk Items
- **R-06:** Regression reveals new bugs → fix cycle eats into sprint buffer → 2-day buffer allocated; Sprint 8 precedent (13 bugs fixed same-day)

### Handoff Protocol

**→ PM (Day 10):**
```
DECIDED: Regression baseline ≥1,300 tests
DONE: Full regression report; 0 FAIL
NEXT: PM prepare demo dry-run + gate review
OPEN: Any bugs found during regression → Backend fix same-day
```

---

## 🏗️ SA (Solution Architect) — Spike Support

**Team Member:** Solution Architect
**Capacity:** Review support (no SP allocation)
**Sprint Focus:** Architecture Review + Mandatory Code Review

### Task List

| # | Task | Sprint Day | Output |
|---|------|-----------|--------|
| 1 | Review P0 contract fix approach (WorkflowDefinitionController path alignment) | Day 1 | Verbal/written approval |
| 2 | Review error response code patterns — consistent với project standards | Day 3 | Pattern approval |
| 3 | Review Pilot Runbook draft — verify incident scenarios complete | Day 5 | Written feedback |
| 4 | **SA Code Review (MANDATORY)** — Full sprint code review per CLAUDE.md checklist | **Day 9** | `docs/mvp3/reports/sprint10-code-review.md` |
| 5 | Gate Review participation | Day 10 | Gate assessment |

### SA Code Review Checklist (MANDATORY — per CLAUDE.md)

**Backend (10 items):**
1. Unused imports / dead code
2. Spring bean registration (`@Component`, auto-wire)
3. Null safety (nullable fields, Optional)
4. Exception handling consistent với existing pattern
5. JWT claims đúng (`iss`, `sub`, `tenant_id`)
6. Resource leak (try-with-resources, stream close)
7. Thread safety (volatile, synchronized, ConcurrentHashMap)
8. Config env vars có default (`@Value("${x:default}")`)
9. Dependency license compatible (KHÔNG AGPL)
10. API contract match frontend (path, method, DTO)

**Frontend (10 items):**
1. `npx tsc --noEmit` → 0 errors
2. API call signature match backend
3. React Query patterns đúng
4. Null/undefined safety
5. Accessibility (aria-label, form labels)
6. Memory leak prevention
7. Bundle size impact
8. Responsive breakpoints
9. Error states (loading, error, empty)
10. Auth guard

### Output
- `docs/mvp3/reports/sprint10-code-review.md` — Mandatory before deploy

---

## 📊 PM (Project Manager) — Coordination Assignments

**Team Member:** Project Manager
**Capacity:** Coordination (no SP allocation)
**Sprint Focus:** Demo Dry-Run + Gate Review + Stakeholder Communication

### Task List

| # | Task | Sprint Day | Output |
|---|------|-----------|--------|
| 1 | Sprint 10 Kickoff — distribute assignments, confirm capacity | Day 1 | Kickoff meeting minutes |
| 2 | Day 2 Status check — P0 contract fixes done? | Day 2 | Status update to PO |
| 3 | Day 5 Status check — API contract 100%? | Day 5 | Status update to PO + SA |
| 4 | Day 8 Status check — Security hardening verified? | Day 8 | Status update to PO + SA |
| 5 | **S10-PILOT-03** Demo dry-run — 5-min executive demo script; PO + PM verify | **Day 10** | Approved demo script |
| 6 | Sprint 10 Gate Review — 15:00 SGT | Day 10 | Gate review minutes |
| 7 | **DECLARE MVP3 DONE** (if gate pass) | Day 10 | MVP3 completion announcement |

### Stakeholder Communication Schedule

| Date | Format | Owner | Audience |
|------|--------|-------|----------|
| Day 2 (Jul 3) | Status: P0 contract fixes done | Backend Lead | PO |
| Day 5 (Jul 7) | Status: API contract 100% | SA | PO + PM |
| Day 8 (Jul 10) | Security hardening verified | DevOps | PO + SA |
| Day 9 (Jul 11) | Regression results | QA | PM + PO |
| **Jul 15 15:00** | **Gate Review — DECLARE MVP3 DONE** | **All** | **PO + City Authority** |

### Demo Dry-Run Requirements (5-min Executive Demo Script)

1. **Dashboard Overview** (60s) — City Operations Center live view
2. **Alert System** (60s) — Trigger flood alert → notification delivered
3. **ESG Report** (60s) — Generate GRI 302/305 report
4. **Mobile App** (60s) — Push notification on mobile device
5. **HA Demo** (60s) — Kill a node → failover → no downtime

---

## 📝 BA (Business Analyst) — Spike Input Assignments

**Team Member:** Business Analyst
**Capacity:** Spike input (no SP allocation)
**Sprint Focus:** UX Spike Input + Acceptance Sign-Off

### Task List

| # | Task | Sprint Day | Output |
|---|------|-----------|--------|
| 1 | Provide mobile offline UX spike input — citizen use cases, offline scenarios, data priorities | Day 6-7 | UX requirements doc |
| 2 | Provide Mobile Control Panel UX spike input — actuator command safety, confirmation patterns | Day 7-8 | Safety requirements doc |
| 3 | Review + sign off acceptance criteria for S10 deliverables | Day 9-10 | AC sign-off |
| 4 | Pilot Phase preparation — citizen onboarding scenarios, training material outline | Day 10 | Pilot onboarding doc outline |

### UX Spike Input Requirements

**Mobile Offline (S10-TD-01):**
- Citizen offline scenarios: elevator, basement parking, tunnel
- Priority data: alerts (always show), sensor readings (cached), ESG reports (deferred)
- Sync strategy: background sync when connectivity returns
- Conflict resolution: server-wins for sensor data, user-chooses for form data

**Mobile Control Panel (S10-TD-05):**
- HIGH danger commands: HVAC shutdown, fire suppression, elevator stop
- Confirmation pattern: 2-step confirm + countdown + undo window
- Audit trail: every command logged with user, timestamp, reason

---

## 🎯 Critical Path & Dependencies

```
Day 1-2: P0 contract fixes (Backend-1) ──→ Day 3-4: Frontend type regen
Day 1-3: Tenant Admin docs (Backend-2) ──→ Day 4-6: All 110 endpoints
Day 4-6: All endpoints documented ──→ Day 7: Error codes + type regen verify
Day 5: Debug gates (Backend-2) ──→ Day 5-6: Production profile verify
Day 1: Keycloak rotation (DevOps) ──→ Day 4-5: Pilot Runbook
Day 6-7: OWASP scan (QA+DevOps) ──→ Day 8-10: Full regression
Day 8-10: Regression PASS ──→ Day 10: Demo dry-run
Day 9: SA Code Review ──→ Day 10: FINAL GATE
```

**⚠️ Blocking Dependencies:**
- Frontend (CONTRACT-10) blocked until Backend-1 delivers CONTRACT-01/02/03
- QA regression (PILOT-02) blocked until ALL Tier 1 contract work done
- Demo dry-run (PILOT-03) blocked until regression PASS + Runbook done

---

## 📊 Sprint Progress Tracking

### Tier 1 Progress (28 SP — ALL MUST DONE)

| Epic | SP | Tasks | Status |
|------|-----|-------|--------|
| Epic 1: API Contract Completion | 13 | 6 tasks | 📋 |
| Epic 2: API Contract Quality | 5 | 4 tasks | 📋 |
| Epic 3: Pilot Security Hardening | 5 | 4 tasks | 📋 |
| Epic 4: Pilot Readiness Gate | 5 | 3 tasks | 📋 |

### Tier 2 Progress (12 SP — BEST EFFORT)

| Epic | SP | Tasks | Status |
|------|-----|-------|--------|
| TD-01: Mobile offline UX spike | 2 | Frontend + BA | 📋 |
| TD-02: BPMN UX polish | 3 | Frontend | 📋 |
| TD-03: ESG PDF Export | 3 | Backend-1 | 📋 |
| TD-04: Android APK pipeline | 2 | DevOps + Frontend | 📋 |
| TD-05: Mobile Control Panel UX | 2 | Frontend + BA | 📋 |

---

## ⚡ Quick Reference — Who Does What

| "Tôi cần..." | Hỏi ai |
|---------------|--------|
| WorkflowDefinitionController path fix | **Backend-1** |
| Alert resolve endpoint documented | **Backend-1** |
| Tenant Admin endpoints documented | **Backend-2** |
| BMS endpoints documented | **Backend-2** |
| Debug endpoints gated | **Backend-2** |
| API types regenerated | **Frontend** |
| BPMN Designer improved | **Frontend** |
| Keycloak rotation verified | **DevOps** |
| iOS cert submitted | **DevOps** |
| Pilot Runbook updated | **DevOps** |
| Android APK build | **DevOps** |
| Regression suite run | **QA + Tester** |
| OWASP scan | **QA + DevOps** |
| Code Review | **SA** (mandatory Day 9) |
| Demo dry-run | **PM + All** |
| UX spike input | **BA** |
| Gate Review | **PM** |

---

*Document: Sprint 10 Task Assignments v1.0 | Created 2026-06-05*
*Based on: [Sprint 10 Master Plan](sprint10-plan.md)*
