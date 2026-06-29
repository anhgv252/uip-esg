# MVP5 Sprint M5-4 — Kickoff Plan

**Sprint:** MVP5 Sprint M5-4  
**Duration:** 2026-06-30 to 2026-07-27 (4 weeks)  
**Gate:** M5-G4 (Billing GA + LOTUS VN + Compliance Audit Prep)  
**Team Capacity:** 60 SP (15 SP/week × 4 weeks, 80% velocity)  
**Status:** 🟢 GO

---

## Sprint Goal

Ship **Billing GA** (invoice auto-generation, reconciliation 99.5%, dispute workflow) + **LOTUS VN Certification Engine** + **Compliance Audit Prep** (audit-log lite, ISO 37120, OWASP scan) to enable HCMC city revenue operations and regulatory compliance for 2026-Q3 investor demo.

**Success Criteria:**
- ✅ Billing auto-invoicing processes 1000+ buildings/month with 99.5% accuracy
- ✅ LOTUS VN certification engine validates 5 building types (residential, commercial, industrial, mixed-use, public)
- ✅ ISO 37120 indicator engine calculates 15 urban sustainability KPIs
- ✅ Audit-log lite provides immutable append-only trail for 3 critical workflows (billing, LOTUS cert, ESG report)
- ✅ OWASP dependency-check scan passes with 0 critical/high CVEs
- ✅ **Gate G3 re-validation:** NL→BPMN UAT 5 operators × 20 workflows ≥ 98% valid + ≥ 80% first-gen approve

---

## Task Breakdown (23 tasks = 17 new + 6 carry-over)

### New M5-4 Tasks (17 tasks, 44 SP)

| ID | Title | SP | Owner | Dependencies | Week Target |
|---|---|---|---|---|---|
| **M5-4-T01** | Billing aggregation job | 5 | Data Eng | T02 | Week 1-2 |
| **M5-4-T02** | Invoice auto-generation (Stripe) | 5 | Backend | T01 | Week 2 |
| **M5-4-T03** | Billing reconciliation 99.5% | 3 | Backend + QA | T02 | Week 2-3 |
| **M5-4-T04** | Billing dispute workflow | 3 | Backend + Frontend | T02 | Week 3 |
| **M5-4-T05** | Billing dashboard frontend | 3 | Frontend | T04 | Week 3 |
| **M5-4-T06** | LOTUS VN certification engine | 5 | SA + Backend | — | Week 1-2 (BLOCKER) |
| **M5-4-T07** | LOTUS VN frontend | 3 | Frontend | T06 | Week 2-3 |
| **M5-4-T08** | LOTUS VN AC validation | 2 | BA + QA | T06 | Week 3-4 |
| **M5-4-T09** | Audit-log lite (immutable append) | 3 | Backend | — | Week 1-2 |
| **M5-4-T10** | ISO 37120 indicator engine | 5 | SA + Backend | — | Week 1-2 |
| **M5-4-T11** | ISO 37120 dashboard | 3 | Frontend | T10 | Week 3 |
| **M5-4-T12** | ISO 37120 AC validation | 2 | BA + QA | T10 | Week 3-4 |
| **M5-4-T13** | OWASP dependency-check scan | 2 | DevOps + QA | — | Week 1 |
| **M5-4-T14** | Gate M5-G4 pre-check | 2 | PM + QA | All tasks | Week 4 |
| **M5-4-T15** | Sprint M5-5 planning | 1 | PM | T14 | Week 4 |
| **M5-4-T16** | Billing integration tests | 2 | QA | T03 | Week 3 |
| **M5-4-T17** | LOTUS VN integration tests | 2 | QA | T08 | Week 4 |

**Subtotal:** 44 SP (new tasks)

### Carry-Over from M5-3 (6 tasks, 16 SP)

| ID | Title | SP | Owner | M5-3 Blocker | M5-4 Resolution | Week Target |
|---|---|---|---|---|---|---|
| **M5-3-T05** | NL latency optimization (p95 ≤ 4s) | 3 | Backend | ViT5 endpoint not provisioned | DevOps provisions ViT5 endpoint Week 1 | Week 2 |
| **M5-3-T08** | ROI AC sign-off | 2 | BA | Pilot building data unavailable | City provides pilot data Week 2 | Week 3 |
| **M5-3-T11** | Mobile v3.1 stub | 2 | Frontend-2 | Capacity unavailable in M5-3 | Frontend-2 reallocated from M5-5 | Week 2 |
| **M5-3-T12** | Sec DPIA review | 1 | Security | Contractor not onboarded | **DEFER TO M5-5** (non-blocking) | — |
| **M5-3-T13** | Synthetic NL routing race | 2 | QA | No live NL traffic in M5-3 | QA uses staging stack with synthetic | Week 3 |
| **M5-3-T14** | NL UAT prep | 3 | QA + PM | Operator availability | PM coordinates HCMC operators Week 1 | Week 1 |
| **M5-3-T15** | Gate G3 UAT execution | 3 | PM + QA + Ops | T14 not started | Execute UAT Week 2-3 | Week 2-3 |

**Subtotal:** 16 SP (carry-over), minus 1 SP deferred (T12) = **15 SP active**

**Total M5-4 Committed:** 44 + 15 = **59 SP** (98% of 60 SP capacity)

---

## Critical Path & Dependencies

### Week 1 — Foundation & Blockers

**Blocker resolution:**
- **T06 LOTUS VN engine** (SA + Backend, 5 SP) — blocks T07 frontend + T08 AC validation
- **M5-3-T14 NL UAT prep** (QA + PM, 3 SP) — blocks M5-3-T15 Gate G3 UAT execution
- **M5-4-T13 OWASP scan** (DevOps + QA, 2 SP) — compliance prerequisite for Gate G4

**Parallel work:**
- T01 Billing aggregation job (Data Eng)
- T09 Audit-log lite (Backend)
- T10 ISO 37120 indicator engine (SA + Backend)
- DevOps provisions ViT5 endpoint for M5-3-T05

**Week 1 Deliverables:**
- LOTUS VN engine 50% complete (domain model, 2/5 building types)
- NL UAT prep 100% (scenarios, training materials, HCMC operators scheduled for Week 2)
- OWASP scan baseline (0 critical/high CVEs, action plan for medium/low)
- Billing aggregation job 50% (ETL pipeline, monthly building usage rollup)

### Week 2 — Billing Core + LOTUS Completion + Gate G3 UAT

**Critical deliverables:**
- **T02 Invoice auto-generation** (Backend, 5 SP) — Stripe integration, invoice PDF generation
- **T06 LOTUS VN engine 100%** (all 5 building types validated)
- **M5-3-T15 Gate G3 UAT execution** (5 operators × 20 workflows = 100 workflows, ≥ 98% valid, ≥ 80% approve)
- M5-3-T05 NL latency optimization (p95 ≤ 4s with local ViT5)
- M5-3-T11 Mobile v3.1 stub (Frontend-2)

**Dependencies:**
- T03 Billing reconciliation ← T02 invoice generation
- T07 LOTUS VN frontend ← T06 engine API
- M5-3-T15 UAT ← M5-3-T14 prep complete + operators available

**Week 2 Risks:**
- HCMC operator no-show for UAT → **Mitigation:** PM backup slot Week 3 Day 1-2
- Stripe API rate limit during invoice generation testing → **Mitigation:** Use Stripe test mode with higher limits

### Week 3 — Billing Reconciliation + Frontend Delivery + ISO 37120

**Deliverables:**
- T03 Billing reconciliation 99.5% (Backend + QA integration tests)
- T04 Billing dispute workflow (Backend + Frontend)
- T05 Billing dashboard frontend
- T07 LOTUS VN frontend (certification request form, status dashboard)
- T11 ISO 37120 dashboard (15 urban KPIs)
- M5-3-T08 ROI AC sign-off (BA with pilot building data)
- M5-3-T13 Synthetic NL routing race (QA load test)

**Quality gates:**
- T03 billing reconciliation ≥ 99.5% accuracy (1000-invoice test batch)
- T16 Billing integration tests (100% E2E coverage: aggregation → invoice → reconciliation → dispute)

### Week 4 — Gate M5-G4 Pre-Check + Compliance Wrap-Up

**Deliverables:**
- T08 LOTUS VN AC validation (BA + QA sign-off)
- T12 ISO 37120 AC validation (BA + QA sign-off)
- T14 Gate M5-G4 pre-check (PM + QA scorecard)
- T17 LOTUS VN integration tests (QA)

**Gate M5-G4 criteria:**
1. Billing auto-invoicing 1000+ buildings with 99.5% reconciliation accuracy ✅
2. LOTUS VN engine validates 5 building types with BA AC sign-off ✅
3. ISO 37120 calculates 15 KPIs with BA AC sign-off ✅
4. Audit-log lite provides immutable trail for 3 workflows ✅
5. OWASP scan 0 critical/high CVEs ✅
6. **Gate G3 re-validation:** NL→BPMN UAT ≥ 98% valid + ≥ 80% approve ✅

---

## Team Allocation

| Role | Week 1 | Week 2 | Week 3 | Week 4 | Total SP |
|---|---|---|---|---|---|
| **SA** | T06 LOTUS, T10 ISO | T06 LOTUS | — | T14 Gate review | 8 |
| **Backend** | T01 Billing, T09 Audit, T06 LOTUS, T10 ISO | T02 Invoice, M5-3-T05 NL | T03 Reconcile, T04 Dispute | — | 26 |
| **Frontend** | — | M5-3-T11 Mobile, T07 LOTUS | T05 Billing UI, T11 ISO UI | — | 11 |
| **Frontend-2** | — | M5-3-T11 Mobile | — | — | 2 |
| **Data Eng** | T01 Billing | T01 Billing | — | — | 5 |
| **DevOps** | T13 OWASP, ViT5 provision | — | — | — | 2 |
| **QA** | M5-3-T14 UAT prep, T13 OWASP | M5-3-T15 UAT, M5-3-T13 Synthetic | T03 Reconcile, T16 Billing tests, M5-3-T13 Synthetic | T08 LOTUS AC, T12 ISO AC, T17 LOTUS tests | 14 |
| **BA** | — | — | M5-3-T08 ROI AC, T08 LOTUS AC, T12 ISO AC | T08 LOTUS AC, T12 ISO AC | 6 |
| **PM** | M5-3-T14 UAT prep | M5-3-T15 UAT | — | T14 Gate G4, T15 M5-5 plan | 10 |

**Total allocated:** 84 SP-person (includes parallel work, actual committed 59 SP)

---

## Risks & Mitigations

### R1: Gate G3 UAT Operator No-Show (CRITICAL)
- **Impact:** Cannot re-validate Gate G3 → M5-G4 blocks on unresolved G3
- **Likelihood:** MEDIUM (HCMC operator schedules unpredictable)
- **Mitigation:**
  - PM coordinates with Urban Planning Dept by 2026-07-01 for confirmed Week 2 slot
  - Backup UAT slot: Week 3 Day 1-2 (2 operators minimum if 5 unavailable)
  - Fallback: Defer NL→BPMN GA to M5-5, ship Billing + LOTUS in M5-4 as planned

### R2: ViT5 Endpoint Provisioning Delay (HIGH)
- **Impact:** M5-3-T05 NL latency optimization cannot complete → p95 latency stays at 6-8s
- **Likelihood:** MEDIUM (DevOps capacity stretched with T13 OWASP + monitoring)
- **Mitigation:**
  - DevOps priority: ViT5 endpoint by Week 1 Day 3 (blocks Backend Week 2)
  - Fallback: Ship M5-4 with SaaS ViT5 (current state), defer on-prem to M5-5

### R3: Billing Reconciliation Accuracy < 99.5% (HIGH)
- **Impact:** Cannot ship Billing GA → revenue operations blocked
- **Likelihood:** LOW (logic straightforward, but edge cases unknown)
- **Mitigation:**
  - Week 2: Backend + QA run 1000-invoice test batch from real building usage data
  - Week 3: Fix edge cases (prorated charges, mid-month cancellations, dispute adjustments)
  - Buffer: 2 SP reserved for reconciliation edge-case fixes

### R4: M5-4 Capacity Crunch (MEDIUM)
- **Impact:** 59 SP committed vs 60 SP capacity = 98% utilization, no buffer
- **Likelihood:** HIGH (carry-over + new work + Gate G3 re-validation)
- **Mitigation:**
  - Defer M5-3-T12 DPIA to M5-5 (1 SP freed, non-blocking)
  - If Week 2 slips: cut T11 ISO 37120 dashboard to M5-5 (3 SP), keep backend engine in M5-4
  - PM daily standup tracking: flag blockers within 24h

### R5: LOTUS VN Certification Engine Complexity (MEDIUM)
- **Impact:** 5 building types × domain-specific rules = high complexity, may slip Week 2 deadline
- **Likelihood:** MEDIUM (SA + Backend new to Vietnamese green building standards)
- **Mitigation:**
  - SA Week 1: Study LOTUS VN official criteria, create decision matrix for 5 building types
  - Backend Week 1: Implement 2 simplest types (residential, commercial) first
  - Week 2: Remaining 3 types (industrial, mixed-use, public) + integration tests

---

## Sprint Ceremonies

| Ceremony | Schedule | Attendees | Agenda |
|---|---|---|---|
| **Sprint Kickoff** | 2026-06-30 10:00 | All team | Review this plan, clarify dependencies, assign Week 1 tasks |
| **Daily Standup** | Daily 09:30 | All team | Blockers, progress, risk escalation (15 min max) |
| **Mid-Sprint Review** | 2026-07-14 14:00 | PM + SA + QA | Gate G3 UAT results, M5-4 progress (50% checkpoint) |
| **Sprint Demo** | 2026-07-26 15:00 | All team + stakeholders | Demo Billing GA + LOTUS VN + ISO 37120 |
| **Sprint Retro** | 2026-07-27 10:00 | All team | Lessons learned, M5-5 improvements |

---

## Carry-Over from M5-3 — Resolution Plan

| Task | M5-3 Blocker | M5-4 Resolution | Owner | Week |
|---|---|---|---|---|
| **M5-3-T05** NL latency | ViT5 endpoint | DevOps provisions endpoint Week 1 Day 3 | Backend | 2 |
| **M5-3-T08** ROI AC | Pilot data | City provides pilot building data Week 2 | BA | 3 |
| **M5-3-T11** Mobile stub | Frontend-2 capacity | Frontend-2 reallocated from M5-5 backlog | Frontend-2 | 2 |
| **M5-3-T12** DPIA | Contractor onboard | **DEFER TO M5-5** (non-blocking compliance) | Security | — |
| **M5-3-T13** Synthetic NL | No live traffic | QA uses staging stack with synthetic load | QA | 3 |
| **M5-3-T14** UAT prep | Operator availability | PM coordinates HCMC Week 1, UAT Week 2-3 | PM + QA | 1 |
| **M5-3-T15** Gate G3 UAT | T14 not started | Execute UAT Week 2-3, 5 operators × 20 workflows | PM + QA | 2-3 |

**Action items for Sprint Kickoff:**
1. PM confirms HCMC operator availability by 2026-07-01 EOD (R1 mitigation)
2. DevOps provisions ViT5 endpoint by 2026-07-03 EOD (R2 mitigation)
3. SA completes LOTUS VN decision matrix by 2026-07-05 EOD (R5 mitigation)
4. Backend + QA define 1000-invoice test batch by 2026-07-02 EOD (R3 mitigation)

---

## Gate M5-G4 Preview

**Target Date:** 2026-07-27 (Sprint M5-4 end)  
**Criteria:**
1. ✅ Billing auto-invoicing 1000+ buildings with 99.5% reconciliation accuracy
2. ✅ LOTUS VN engine validates 5 building types with BA AC sign-off
3. ✅ ISO 37120 calculates 15 urban sustainability KPIs
4. ✅ Audit-log lite provides immutable append-only trail for 3 critical workflows
5. ✅ OWASP dependency-check scan passes with 0 critical/high CVEs
6. ✅ **Gate G3 re-validation:** NL→BPMN UAT ≥ 98% valid + ≥ 80% first-gen approve

**Gate G4 pass conditions:**
- All 6 criteria met + Sprint Demo successful + Zero production incidents during M5-4
- If Gate G3 re-validation fails: Escalate to city authority, defer NL→BPMN GA to M5-5

**Next Sprint:** M5-5 (GIS integration + Advanced Analytics + NL GA) — Target: 2026-08-24

---

## Go/No-Go Decision: 🟢 GO

**Rationale:**
- ✅ M5-3 delivered 72% SP (31/43), critical path features production-ready
- ✅ Team capacity 60 SP vs 59 SP committed (98% utilization, manageable)
- ✅ No production incidents in M5-3, infrastructure stable
- ✅ Billing + LOTUS + ISO 37120 requirements well-defined, no scope ambiguity
- ⚠️ Gate G3 UAT deferred to M5-4 Week 2-3 (operator availability confirmed by PM)
- ⚠️ 6 carry-over tasks = 27% M5-4 capacity, but 1 task deferred (T12 DPIA) → 15 SP active

**Sprint M5-4 is GO for execution starting 2026-06-30.**

---

**Next Actions:**
1. **PM:** Confirm HCMC operator availability by 2026-07-01 EOD
2. **DevOps:** Provision ViT5 endpoint by 2026-07-03 EOD
3. **SA:** LOTUS VN decision matrix by 2026-07-05 EOD
4. **All team:** Sprint Kickoff 2026-06-30 10:00 — review this plan, assign Week 1 tasks

**Prepared by:** PM  
**Date:** 2026-06-29  
**Status:** APPROVED — Sprint M5-4 execution begins 2026-06-30
