# MVP5 Sprint M5-3 — Kickoff Plan (NL→BPMN Production + ROI Dashboard + G3 UAT)

| Field | Value |
|---|---|
| **Sprint** | M5-3 (2026-10-19 → 2026-11-01) |
| **Author** | PM |
| **Date** | 2026-06-26 (early-start planning) |
| **Audience** | PO, SA, BA, toàn team UIP + contractors |
| **Status** | PLANNING — for team review before M5-3 window (2026-10-19) |
| **Predecessor** | `mvp5-sprint2-kickoff.md`, `mvp5-sprint2-gate-g2-scorecard.md` |
| **SP committed** | 43 |
| **Gate** | M5-G3 (NL→BPMN UAT 5 operator × 20 workflow ≥ 98% valid) |

---

## §1. Sprint goal

**M5-3 goal:** NL→BPMN production-ready (BPMN synthesis service + validator hardening + operator review UI) + ROI dashboard (BA vertical 1) + UAT 5 operator × 20 workflow ≥ 98% valid (Gate M5-G3).

**Critical path:** T01 BPMN synthesis service → T02 BPMN validator hardening → T03 operator review UI production → T04 BPMN simulator → T14 NL UAT prep → T15 Gate M5-G3.

---

## §2. Dependency readiness from M5-2

| M5-2 output | M5-3 consumer | Status |
|---|---|---|
| T02 NL parser POC (100% hit rate) | T01 BPMN synthesis service | ✅ READY |
| T03 NL template grounding (10 templates) | T01 BPMN synthesis service | ✅ READY |
| T04 Operator review UI stub | T03 Operator review UI production | ✅ READY |
| T10 BPMN schema draft | T02 BPMN validator hardening | ✅ READY |
| T07 Billing skeleton (metering ledger) | T06 ROI dashboard backend | ✅ READY |
| T08 Billing unit spec (D4 hybrid) | T06 ROI dashboard backend | ✅ READY |
| T13 LOTUS prep + ROI AC stub | T08 ROI AC validation | ✅ READY |

**Dependency verdict:** All M5-3 critical-path dependencies **READY** (T01/T02/T03/T06/T08 inputs from M5-2 complete).

---

## §3. M5-3 task breakdown (16 tasks)

| Task ID | Task name | Owner | SP | Dependency | Deliverable |
|---|---|---|---|---|---|
| M5-3-T01 | BPMN synthesis service: NL intent + template grounding → BPMN XML output (D2 hybrid routing) | Backend-2 | 4 | M5-2-T02,T03 | Synthesis service + integration test |
| M5-3-T02 | BPMN validator hardening (R2 mitigation): schema + semantic rule (sprinkler/flood safety), fail-before-review-UI | SA + Backend-2 | 3 | M5-2-T10, T01 | Validator + 20 invalid-BPMN regression tests |
| M5-3-T03 | Operator review UI production: list/detail/approve/reject (BR-010), React Query useMutation | Frontend-1 | 4 | M5-2-T04, T01 | React page + approve-flow E2E test |
| M5-3-T04 | BPMN simulator: dry-run generated workflow against test digital-twin (no real actuation) | Backend-2 | 3 | T01 | Simulator + 5-scenario test |
| M5-3-T05 | NL latency optimization p95 ≤ 4s (Claude) / ≤ 8s (local fallback) — KR2.4 | Backend-2 | 2 | T01 | APM trace + latency report |
| M5-3-T06 | ROI dashboard backend: cost-breakdown per-building API (sensor cost + AI token cost + base fee) | Data Eng | 3 | M5-2-T07 | `/api/v1/roi/building/{id}` endpoint + test |
| M5-3-T07 | ROI dashboard frontend: cost-breakdown per-building chart + useBuildingROI hook | Frontend-1 | 3 | T06 | React component + recharts chart |
| M5-3-T08 | ROI acceptance criteria validation (BA) + 2-3 pilot bldg real cost data ingest | BA (vertical) | 2 | T06 | ROI AC sign-off + data load log |
| M5-3-T09 | Schema registry governance (ADR-051) — protobuf/Avro contract enforcement CI | SA + DevOps | 3 | M5-1-T13 | Schema registry + CI gate |
| M5-3-T10 | Observability OTel: trace all 25 bounded-context, sampling 10%, Grafana Tempo | DevOps | 3 | M5-1-T02 | OTel collector + Tempo dashboard |
| M5-3-T11 | Mobile v3.1 stub: offline-mode architecture (LWW + version vector design) | Frontend-2 | 2 | — | Design doc + 1 offline-write POC |
| M5-3-T12 | Sec contractor onboarding + Decree 13 DPIA review (GAP-2) — M5-4 prep | Sec | 2 | M5-2-T01 | DPIA v1 + Decree 13 checklist |
| M5-3-T13 | Synthetic 50-tenant test — billing quota + NL routing race (R16) | QA (synthetic) | 2 | M5-2-T06 | Synthetic report — NL routing under 50 tenant |
| M5-3-T14 | NL UAT prep: 5 operator × 20 workflow script + Vietnamese phrasebook | UX + Tester | 2 | T03,T04 | UAT script + phrasebook |
| M5-3-T15 | **Gate M5-G3**: NL→BPMN UAT 5 operator × 20 workflow ≥ 98% valid, ≥ 80% first-gen operator-approve | Tester + UX | 3 | T03,T04,T14 | UAT sign-off + 100-workflow audit log |
| M5-3-T16 | Sprint planning M5-4 | PM | 1 | — | M5-4 plan |

---

## §4. Risk review post-M5-2

| Risk ID | M5-2 status | M5-3 mitigation |
|---|---|---|
| **R2** (NL hallucination) | Mitigated (template grounding, 100% intent hit rate) | T02 BPMN validator hardening (20 invalid-BPMN regression tests) + T03 operator review UI production (BR-010 approve/reject flow) → **TARGET CLOSE M5-3** |
| **R5** (GAP-2 compliance) | Mitigated (ADR-049 authored, DPIA skeleton) | T12 Sec contractor onboarding + DPIA v1 + Decree 13 checklist → **TARGET CLOSE M5-4** |
| **R16** (build-for-50) | Progressing (INV-4 integrated, 5-tenant synthetic PASS) | T13 INV-5 NL routing race (50 tenant) → **PROGRESS M5-3, CLOSE M5-5 G7** |

---

## §5. Week 1 / Week 2 sequencing

### Week 1 (2026-10-19 → 2026-10-25) — Critical path

**Blocker-first principle:** T01 BPMN synthesis service **MUST complete Week 1** — blocks T02/T03/T04/T05.

| Day | Task | Owner | Blocker status |
|---|---|---|---|
| Mon–Wed | T01 BPMN synthesis service | Backend-2 | **WEEK 1 BLOCKER** — must complete by Wed EOD |
| Mon–Tue | T06 ROI backend | Data Eng | Parallel |
| Mon–Tue | T09 Schema registry | SA + DevOps | Parallel |
| Wed–Fri | T02 BPMN validator hardening | SA + Backend-2 | Depends T01 |
| Thu–Fri | T03 Operator review UI start | Frontend-1 | Depends T01 |
| Thu–Fri | T04 BPMN simulator | Backend-2 | Depends T01 |
| Fri | T10 OTel observability | DevOps | Parallel |

**M5-2 carry-over (parallel track, NOT blocking critical path):**
- **T05+T12 execution logs** (M5-2-T05 tenant fuzz + M5-2-T12 Flink multi-tenant IT) — DevOps/Backend execute locally, provide logs by Wed EOD

### Week 2 (2026-10-26 → 2026-11-01) — UAT prep + ROI dashboard

| Day | Task | Owner | Blocker status |
|---|---|---|---|
| Mon–Tue | T03 Operator review UI finish | Frontend-1 | Depends T01 (Week 1 done) |
| Mon–Tue | T07 ROI frontend | Frontend-1 | Depends T06 (Week 1 done) |
| Mon–Tue | T05 NL latency optimization | Backend-2 | Depends T01 (Week 1 done) |
| Tue–Wed | T11 Mobile v3.1 stub | Frontend-2 | Parallel |
| Wed–Thu | T14 NL UAT prep | UX + Tester | Depends T03 (Tue EOD), T04 (Week 1 done) |
| Thu | T08 ROI AC validation | BA (vertical) | Depends T06 (Week 1 done) |
| Thu | T12 DPIA review | Sec | Parallel |
| Thu | T13 Synthetic NL routing | QA (synthetic) | Parallel |
| Fri | **T15 Gate M5-G3 prep** | Tester + UX | Depends T14 (Thu EOD) |
| Fri | T16 M5-4 planning | PM | Parallel |

**Critical dependency chain:** T01 (Week 1 blocker) → T02/T03/T04 → T14 → T15 Gate M5-G3.

---

## §6. DoD (Definition of Done)

### Code quality gate
- [ ] Backend: `./gradlew test` 0 failure (all unit tests PASS)
- [ ] Flink: `mvn test` 0 failure (all unit tests PASS)
- [ ] Frontend: `npx tsc --noEmit` 0 errors
- [ ] ArchTest: ModuleBoundaryArchTest 73/73 PASS (M5-1 baseline)
- [ ] Contract test: Pact provider verify PASS (M5-1 gate)

### Functional gate (M5-G3 specific)
- [ ] NL→BPMN UAT 5 operator × 20 workflow ≥ 98% valid BPMN (G3 criterion 1)
- [ ] NL→BPMN UAT ≥ 80% first-gen operator-approve (G3 criterion 2)
- [ ] BPMN validator fail-before-review: 20 invalid-BPMN regression tests PASS (G3 criterion 3)
- [ ] BPMN simulator: 5-scenario dry-run PASS (no real actuation) (G3 criterion 4)

### Documentation gate
- [ ] UAT script + Vietnamese phrasebook (T14)
- [ ] ROI AC sign-off + data load log (T08)
- [ ] DPIA v1 + Decree 13 checklist (T12)
- [ ] M5-G3 gate scorecard (T15)
- [ ] M5-4 sprint plan (T16)

---

## §7. Go/No-Go — CONDITIONAL on M5-2 G2 close

### Go criteria
- [x] M5-2 G2 criteria 2/4 PASS (cache namespace ✅, CH RowPolicy synthetic ✅)
- [x] M5-2 G2 criteria 4/4 PASS (NL parser 100% hit rate ✅)
- [x] M5-2 G2 criteria 1/4 CONDITIONAL (fuzz test code ✅, execution pending → M5-3 Week 1 DevOps/Backend)
- [x] NL→BPMN dependencies ready (T02 parser ✅, T03 grounding ✅, T04 operator UI stub ✅, T10 BPMN schema ✅)
- [x] No P0/P1 blockers

### No-Go risks
- **M5-2 T05+T12 execution pending** — Mitigated: code quality verified, execution mechanical, DevOps/Backend execute M5-3 Week 1 parallel track (not blocking NL→BPMN critical path)
- **PO billing questions (5 open)** — Mitigated: defer M5-3 T08 + M5-4 T04, not blocking NL→BPMN critical path
- **Sec DPIA review not started** — Mitigated: Sec contractor onboard M5-3 Week 1, DPIA skeleton ready (ADR-049 416 lines)

### Verdict
🟡 **CONDITIONAL GO** — M5-3 can start Week 1 tasks (T01 BPMN synthesis service, T02 BPMN validator hardening, T03 operator review UI production). M5-2 T05+T12 execution by DevOps/Backend M5-3 Week 1 (parallel track, not blocking critical path).

**Gate M5-G2 verdict:** 🟡 CONDITIONAL PASS (14/16 task artifacts DONE, T05+T12 execution pending).

---

## §8. Carry-over from M5-2

### M5-2 T05+T12 execution logs (DevOps/Backend Week 1)
- **M5-2-T05:** `TenantIsolationFuzzTest.java` (9 tests) — execute `./gradlew test -Ptag=fuzz`, provide logs by Wed EOD
- **M5-2-T12:** `FlinkMultiTenantConcurrentIT.java` (6 tests) — execute `mvn test`, provide logs by Wed EOD
- **Owner:** DevOps or Backend-1 (need dev environment with Docker daemon for Testcontainers)
- **Deliverable:** Execution logs + test result summary (PASS/FAIL) → update `mvp5-sprint2-gate-g2-scorecard.md`

### PO billing questions (5 open → M5-3 T08 + M5-4 T04)
From `mvp5-billing-unit-spec.md` §6:
1. AI overage billing unit: per-token or per-request? → **M5-3 T08 ROI AC validation**
2. Base flat fee: per-building or per-tenant? → **M5-3 T08 ROI AC validation**
3. Invoice cadence: monthly or quarterly? → **M5-3 T08 ROI AC validation**
4. Credit note workflow: auto or manual review? → **M5-4 T04 Billing dispute workflow**
5. Dispute SLA: 7-day or 30-day? → **M5-4 T04 Billing dispute workflow**

### Sec DPIA review (M5-3 T12)
- **Input:** ADR-049 (416 lines, 3 decisions, DPIA skeleton §8.3)
- **Deliverable:** DPIA v1 + Decree 13 checklist
- **Owner:** Sec contractor (onboard M5-3 Week 1)
- **Timeline:** T12 scheduled Week 2 Thu, Sec contractor onboard Mon

---

## §9. Sprint capacity check

| Role | M5-3 FTE | Task load | Status |
|---|---|---|---|
| Backend-2 | 1.0 | T01 (4 SP) + T02 (3 SP, 50% SA) + T04 (3 SP) + T05 (2 SP) = **10.5 SP** | ✅ Within capacity (10-12 SP/sprint) |
| Frontend-1 | 1.0 | T03 (4 SP) + T07 (3 SP) = **7 SP** | ✅ Within capacity (8-10 SP/sprint) |
| Data Eng | 1.0 | T06 (3 SP) = **3 SP** | ✅ Light load (can absorb M5-2 carry-over billing questions) |
| SA | 1.0 | T02 (3 SP, 50% Backend-2) + T09 (3 SP, 50% DevOps) = **3 SP** | ✅ Within capacity |
| DevOps | 1.0 + 0.5 contractor | T09 (3 SP, 50% SA) + T10 (3 SP) + M5-2 T05+T12 execution = **4.5 SP + carry-over** | 🟡 Tight — prioritize T05+T12 execution Week 1 |
| UX | 1.0 | T14 (2 SP, 50% Tester) = **1 SP** | ✅ Light load |
| Tester | 0.5 | T14 (2 SP, 50% UX) + T15 (3 SP, 50% UX) = **2.5 SP** | ✅ Within capacity |
| BA (vertical) | 0.5 + 0.5 contractor | T08 (2 SP) = **2 SP** | ✅ Within capacity |
| Frontend-2 | 1.0 | T11 (2 SP) = **2 SP** | ✅ Light load |
| QA (synthetic) | 1.0 + 0.5 overlay | T13 (2 SP) = **2 SP** | ✅ Within capacity |
| Sec | 0.5 contractor | T12 (2 SP) = **2 SP** | ✅ Within capacity |
| PM | 1.0 | T16 (1 SP) = **1 SP** | ✅ Light load |

**Total SP committed: 43** (all tasks T01–T16).  
**Capacity verdict:** ✅ All roles within capacity. DevOps 🟡 tight — prioritize M5-2 T05+T12 execution Week 1 before T09/T10.

---

## §10. Decision

- **Sprint M5-3 go/no-go:** 🟡 **CONDITIONAL GO** — start Week 1 tasks (T01/T02/T03/T06/T09/T10), parallel track M5-2 T05+T12 execution by DevOps/Backend.
- **Critical path:** T01 BPMN synthesis service (Week 1 blocker) → T02/T03/T04 → T14 → T15 Gate M5-G3.
- **Carry-over from M5-2:** (1) T05+T12 execution logs (Week 1 DevOps/Backend), (2) PO billing questions (M5-3 T08 + M5-4 T04), (3) Sec DPIA review (T12 Week 2 Thu).
- **Gate M5-G3 target:** NL→BPMN UAT 5 operator × 20 workflow ≥ 98% valid + ≥ 80% first-gen operator-approve.

---

**Sprint M5-3 = source of truth:** `mvp5-sprint-plan.md` §Sprint M5-3 + §5 DoD.  
**Authored 2026-06-26** — PM

