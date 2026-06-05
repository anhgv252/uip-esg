# Sprint 10 — Gate Review Document

**Date:** 2026-07-15 15:00 SGT
**Sprint:** MVP3-10 — API Contract Completion + Pilot Security + Readiness Gate
**Decision:** DECLARE MVP3 DONE → Pilot Phase
**PO:** anhgv

---

## 1. Gate Status Summary

### Hard Gates (14) — ALL MUST PASS

| Gate | Criterion | Verifier | Status | Evidence |
|------|-----------|----------|--------|----------|
| G1 | OpenAPI spec: 110/110 endpoints documented, 0 undocumented | SA audit + CI | 📋 | `docs/api/openapi.json` — path count |
| G2 | CI contract drift check: `npm run gen-api-types && git diff --exit-code` PASS | CI pipeline | 📋 | GitHub Actions run #xxx |
| G3 | Frontend/Mobile: `npx tsc --noEmit` 0 errors with regenerated types | CI pipeline | 📋 | GitHub Actions run #xxx |
| G4 | Production profile: `curl POST /api/v1/test/inject-reading` → 404 | DevOps verify | 📋 | ProductionProfileSecurityTest PASS |
| G5 | Keycloak: uip-api secret rotation verified; old secret rejected | SA audit | 📋 | Rotation procedure executed |
| G6 | Error responses: ≥15 critical endpoints có 401/403/404/400 documented | SA audit | 📋 | OpenAPI spec `responses` section |
| G7 | iOS cert: submitted to Apple (or documented blocker with Android fallback) | PM verify | 📋 | Apple Developer portal screenshot |
| G8 | Pilot Runbook: 6 incident scenarios documented, Backend Lead + DevOps sign off | PM verify | 📋 | `docs/mvp3/ops/pilot-runbook.md` |
| G9 | Regression: ≥1,300 tests PASS, 0 FAIL on HA staging | QA report | 📋 | `docs/mvp3/qa/sprint10-regression-report.md` |
| G10 | OWASP: 0 new high+ CVEs | SonarQube + manual | 📋 | `./gradlew dependencyCheckAnalyze` output |
| G11 | SA Code Review: APPROVED (sa-fix-backlog = 0 carryover) | SA | 📋 | `docs/mvp3/reports/sprint10-code-review.md` |
| G12 | Demo dry-run: PO approves 5-min demo script | PO sign-off | 📋 | Demo executed without P0 blockers |
| G13 | Total tests ≥1,300; 0 failures | CI | 📋 | Test report |
| G14 | Debug endpoints: 0 reachable khi `spring.profiles.active=production` | Integration test | 📋 | ProductionProfileSecurityTest |

### Soft Gates (4) — Best Effort

| Gate | Criterion | Status | Evidence |
|------|-----------|--------|----------|
| GS1 | BPMN Workflow Designer UX improved (PO review) | 📋 | Frontend screenshot |
| GS2 | Mobile offline UX spike documented (wireframes + SP estimate) | 📋 | UX doc in v3.1 backlog |
| GS3 | ESG PDF Export functional (sync endpoint) | 📋 | `POST /api/v1/esg/reports/pdf` |
| GS4 | Android APK build pipeline configured (Expo EAS) | 📋 | `eas build` output |

---

## 2. Sprint Metrics

| Metric | Planned | Actual |
|--------|---------|--------|
| Sprint Duration | 10 calendar days | _to fill_ |
| Team Size | 5 FTE + SA | 5 FTE + SA |
| Committed SP (Tier 1) | 28 | _to fill_ |
| Committed SP (Tier 2) | 12 | _to fill_ |
| Delivered SP (Tier 1) | 28 | _to fill_ |
| Delivered SP (Tier 2) | _best effort_ | _to fill_ |
| Carry-over to v3.1 | TBD | _to fill_ |
| Bugs found in regression | TBD | _to fill_ |
| Bugs fixed same-sprint | TBD | _to fill_ |

### Story Completion

| Story ID | Description | SP | Owner | Status |
|----------|-------------|-----|-------|--------|
| S10-CONTRACT-01 | WorkflowDefinitionController path alignment | 2 | Backend-1 | 📋 |
| S10-CONTRACT-02 | Alert resolve documented | 1 | Backend-1 | 📋 |
| S10-CONTRACT-03 | Admin/sensors documented or gated | 1 | Backend-1 | 📋 |
| S10-CONTRACT-04 | TenantAdminController documented | 3 | Backend-2 | 📋 |
| S10-CONTRACT-05 | BMS module documented | 2 | Backend-2 | 📋 |
| S10-CONTRACT-06 | 9 remaining modules documented | 4 | Backend-1+2 | 📋 |
| S10-CONTRACT-07 | Error response codes for 15 endpoints | 2 | Backend-1 | 📋 |
| S10-CONTRACT-08 | Debug endpoints gated | 1 | Backend-2 | 📋 |
| S10-CONTRACT-09 | Dual SSE stream resolved | 1 | Backend-1 | 📋 |
| S10-CONTRACT-10 | API types regenerated + verified | 1 | Frontend | 📋 |
| S10-SEC-01 | Keycloak live rotation verified | 1 | DevOps | 📋 |
| S10-SEC-02 | iOS cert submitted | 1 | DevOps | 📋 |
| S10-SEC-03 | Production profile verified | 1 | Backend-2 | 📋 |
| S10-SEC-04 | OWASP scan clean | 2 | DevOps+QA | 📋 |
| S10-PILOT-01 | Pilot Runbook updated | 2 | DevOps | 📋 |
| S10-PILOT-02 | Regression ≥1,300 tests PASS | 2 | QA+Tester | 📋 |
| S10-PILOT-03 | Demo dry-run approved | 1 | PM+All | 📋 |
| S10-TD-01 | Mobile offline UX spike | 2 | Frontend+BA | 📋 |
| S10-TD-02 | BPMN UX polish | 3 | Frontend | 📋 |
| S10-TD-03 | ESG PDF Export | 3 | Backend-1 | 📋 |
| S10-TD-04 | Android APK build | 2 | DevOps+Frontend | 📋 |
| S10-TD-05 | Mobile Control Panel UX spike | 2 | Frontend+BA | 📋 |

---

## 3. MVP3 Declaration Checklist

- [ ] 110/110 API endpoints documented trong OpenAPI spec
- [ ] 0 P0/P1 bugs open
- [ ] ≥1,300 regression tests PASS
- [ ] OWASP 0 Critical findings
- [ ] Pilot Runbook reviewed + signed off
- [ ] PO demo approved
- [ ] SA Code Review APPROVED
- [ ] Ready for Pilot Phase (soft launch 2026-08-04)

---

## 4. Decision Matrix

| Scenario | Condition | Action | Timeline |
|----------|-----------|--------|----------|
| ✅ **PASS** | All 14 hard gates green | DECLARE MVP3 DONE | 2026-07-15 15:30 |
| ⚠️ **MINOR FAIL** | 1-2 hard gates fail (non-critical) | 2-day hotfix sprint | Re-gate 2026-07-17 |
| ❌ **MAJOR FAIL** | 3+ hard gates fail | Escalate to PO | Pilot delay to 2026-08-11 |

---

## 5. Post-Gate Actions

### If PASS — MVP3 DONE

| Date | Action | Owner |
|------|--------|-------|
| 2026-07-16 | Begin Pilot Preparation (2 weeks) | DevOps |
| 2026-07-16 → 07/31 | HA staging → production environment setup | DevOps |
| 2026-07-16 → 07/31 | City Authority user onboarding + training | BA + PM |
| 2026-07-16 → 07/31 | Data migration / seeding for pilot buildings | Backend |
| 2026-08-01 → 08/03 | Pilot Soft Launch (internal team testing) | All |
| **2026-08-04** | **🎯 PILOT SOFT LAUNCH** (5 buildings, 2 tenants) | All |
| 2026-08-04 → 08/10 | Pilot Week 1 — monitor + fix | All |
| **2026-08-10** | **🎯 TIER 2 PILOT SIGNED** | PM + PO |

### If FAIL — Hotfix Sprint

| Date | Action | Owner |
|------|--------|-------|
| 2026-07-16 → 07/17 | Hotfix sprint — resolve blocking gates | Relevant team |
| 2026-07-17 15:00 | Re-gate review | All |
| If re-gate PASS | Proceed to Pilot Preparation (1 week delay) | All |
| If re-gate FAIL | Escalate — pilot delay to 2026-08-11 | PO |

---

## 6. Tech Debt Carried Forward (v3.1)

| ID | Item | SP | Priority | Target |
|----|------|-----|----------|--------|
| TD-S10-01 | Avro dual-publish → Avro-only migration | 8 | P1 | Q3 2026 |
| TD-S10-02 | gRPC internal transport (ADR-012) | 13 | P1 | Q3 2026 |
| TD-S10-03 | Mobile offline mode | 8 | P2 | Q3 2026 |
| TD-S10-04 | Mobile Control Panel | 5 | P2 | Q3 2026 |
| TD-S10-05 | Automated chaos engineering | 5 | P3 | Q4 2026 |
| TD-S10-06 | Pact contract testing | 5 | P3 | Q4 2026 |
| TD-S10-07 | Error codes — complete all endpoints | 3 | P1 | Q3 2026 |
| TD-S10-08 | CH Keeper memory monitoring | 2 | P2 | Q3 2026 |
| TD-S10-09 | Pilot host RAM profile (16GB ceiling) | 2 | P2 | Q3 2026 |

---

## 7. Stakeholder Communication

### Pre-Gate Status Email (Day 9 — Jul 14)

**Subject:** Sprint 10 Pre-Gate Status — MVP3 Declaration Tomorrow

> Dear PO,
>
> Sprint 10 is nearing completion. Here is the pre-gate status:
>
> - **Hard Gates:** X/14 PASS (list any open items)
> - **Regression:** ≥1,300 tests PASS
> - **Demo dry-run:** Completed successfully
>
> Gate review is scheduled for tomorrow at 15:00 SGT.
>
> Best regards,
> PM

### MVP3 Done Announcement (If PASS)

**Subject:** 🎉 MVP3 DECLARED DONE — Pilot Phase Begins

> Dear Stakeholders,
>
> I'm pleased to announce that **MVP3 has been declared DONE** following successful Sprint 10 gate review on 2026-07-15.
>
> **Key achievements:**
> - 110/110 API endpoints fully documented
> - 1,300+ regression tests passing
> - High-availability infrastructure verified
> - Pilot Runbook ready with 6 incident scenarios
>
> **Next steps:**
> - Pilot Preparation: Jul 16 — Jul 31
> - Soft Launch: Aug 4, 2026 (5 buildings, 2 tenants)
> - Tier 2 Pilot Signed: Aug 10, 2026
>
> Best regards,
> PM

### Gate Fail Notification (If FAIL)

**Subject:** ⚠️ Sprint 10 Gate Review — Hotfix Required

> Dear PO,
>
> Sprint 10 gate review identified X blocking items. A 2-day hotfix sprint has been initiated.
>
> **Blocking items:** (list)
> **Re-gate scheduled:** 2026-07-17 15:00 SGT
> **Pilot impact:** If re-gate passes, pilot proceeds with 1-week delay (Aug 11)
>
> Best regards,
> PM

---

## Sign-Off

| Role | Name | Signature | Date |
|------|------|-----------|------|
| PO | anhgv | __________ | ____/____/____ |
| SA | _______ | __________ | ____/____/____ |
| Backend Lead | _______ | __________ | ____/____/____ |
| DevOps | _______ | __________ | ____/____/____ |
| QA Lead | _______ | __________ | ____/____/____ |

---

*Document: Sprint 10 Gate Review v1.0 | Created 2026-06-05*
*Based on: [Sprint 10 Plan](sprint10-plan.md) | [Sprint 10 Assignments](sprint10-assignments.md)*
