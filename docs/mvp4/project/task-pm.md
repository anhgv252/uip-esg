# MVP4 — Project Manager Task Assignment

**Agent:** `UIP-project-manager`
**Tổng:** 2 tasks | 5 SP | Sprint 1 + Sprint 6

---

## Sprint 1 (Aug 04-15) — 1 SP

### Task #8 — Coverage fix + Pilot monitoring
**ID:** P0-7 | **SP:** 1 | **Priority:** P0

| Item | SP | Chi tiết |
|------|-----|---------|
| P0-7 Correct coverage claims | 0.5 | Fix 86% → 76.3% trong all materials: investor deck, stakeholder reports, wiki. Verify số từ JaCoCo XML report |
| Pilot monitoring + stakeholder comms | 0.5 | Daily check pilot health: uptime, error rate, alert delivery. Stakeholder email update đầu tuần. Track pilot incidents trong risk register |

**Acceptance Criteria:**
- [ ] All materials updated với accurate coverage 76.3%
- [ ] Pilot daily health check documented
- [ ] Stakeholder email sent Day 1 + Day 7

**Dependencies:** None (start immediately)
**Blocks:** None

---

## Sprint 6 (Oct 13-24) — 4 SP

### Task #27 — MVP4 Summary + MVP5 Roadmap + Stakeholder demo
**ID:** MVP4 Close-out | **SP:** 4 | **Priority:** P0 (FINAL) | **Status:** PARTIAL — 5/10 gates PASS (2026-06-16), awaiting G5+G1 staging run

| Item | SP | Chi tiết | Status |
|------|-----|---------|--------|
| MVP4 Summary report | 1.5 | Document: deliverables completed, KPIs achieved vs target, lessons learned, tech debt carried over, team velocity actual vs planned | ⏳ DRAFT done ([`reports/mvp4-summary-draft.md`](../reports/mvp4-summary-draft.md)) — gate status section updated; KPIs to be filled after G1/G5 staging run |
| MVP5 Roadmap draft | 1.5 | Plan next phase: K8s migration, NL→BPMN (Vietnamese natural language), scale >20 buildings, Series A trigger. Timeline: Q1 2027 | ✅ DRAFT done ([`reports/mvp5-roadmap-draft.md`](../reports/mvp5-roadmap-draft.md)) |
| Stakeholder demo + sign-off | 1 | City authority + investor demo: AI cost optimization demo, correlation engine demo, operator self-service demo. Collect sign-off | ⏳ Script done ([`reports/mvp4-stakeholder-demo-script.md`](../reports/mvp4-stakeholder-demo-script.md)) — ready to execute |

**Acceptance Criteria:**
- [ ] MVP4 Summary: all KPIs documented vs target *(pending G1/G5 measurement)*
- [x] MVP5 Roadmap: timeline + features + resource estimate
- [ ] Stakeholder demo done, sign-off obtained *(pending scheduling + G1/G5 PASS)*
- [ ] **DECLARE MVP4 DONE** (trigger: G5 + G1 PASS on staging + stakeholder demo) — currently 5/10 gates PASS (G3, G4, G7, G8, G9); G1/G5 ready to execute per [`qa/staging-gate-ticket.md`](../qa/staging-gate-ticket.md); G2/G10 run in parallel during 30-day pilot (Aug 2026)

**Dependencies:** Task #26 (QA Gate) DONE
**Blocks:** None (this is the final task)

---

## PM Responsibilities Across All Sprints

Ngoài 2 assigned tasks, PM có ongoing responsibilities:

| Sprint | Activity | Time Investment |
|--------|----------|-----------------|
| S1 | Pilot monitoring, stakeholder comms | 0.5 SP |
| S2 | Sprint review, risk register update | (included in ongoing) |
| S3 | Sprint review, KPI tracking | (included in ongoing) |
| S4 | Sprint review, template UAT coordination | (included in ongoing) |
| S5 | Sprint review, BMS safety protocol sign-off coordination | (included in ongoing) |
| S6 | Final gate review, stakeholder demo, close-out | 4 SP |

### Ongoing PM Deliverables per Sprint

| Deliverable | Frequency | Template |
|-------------|-----------|----------|
| Sprint Review document | End of each sprint | `docs/mvp4/reports/sprint{N}-review.md` |
| Risk register update | Weekly | Update R1-R7 status |
| Stakeholder email | Bi-weekly | City authority + investor update |
| Sprint plan review | Start of each sprint | Verify SP allocation matches capacity |
| Velocity tracking | End of each sprint | Actual vs planned SP |

---

## Tổng PM Load

| Sprint | Tasks | SP | Focus |
|--------|-------|-----|-------|
| S1 | #8 | 1 | Pilot monitoring + coverage fix |
| S2-S5 | Ongoing | — | Sprint reviews + stakeholder comms |
| S6 | #27 | 4 | MVP4 close-out + MVP5 roadmap + demo |
| **Total** | **2 tasks** | **~5 SP** | |

### Lưu ý
- PM hoạt động xuyên suốt 6 sprints nhưng formal task assignment chỉ ở S1 + S6
- Ongoing activities (sprint review, stakeholder comms) included trong sprint overhead
- **Non-negotiable:** City authority ESG reporting deadlines must never slip
- **Series A trigger:** $100K MRR target — PM tracks progress toward this milestone
- **Stakeholder demo (S6):** Executive-level, 30 min, focus on AI cost savings + false positive reduction

---

---

## Declare MVP4 DONE — Final Checklist

Trigger: **G5 + G1 PASS on staging + stakeholder demo** (G2/G10 can run in parallel during 30-day pilot, Aug 2026)

```
[ ] G5 JMeter 1000 VU PASS on staging
[ ] G1 AI cost < $1/day PASS on staging (ticket: docs/mvp4/qa/staging-gate-ticket.md)
[ ] Stakeholder demo done (script: docs/mvp4/reports/mvp4-stakeholder-demo-script.md)
[ ] City Authority sign-off obtained
[ ] mvp4-summary-draft.md KPIs filled with actual measurements (G1/G5 results)
[ ] mvp4-summary-draft.md updated with final gate review link
[ ] MVP4 DONE declared (final gate review document)
```

**Parallel gates (30-day pilot, Aug 2026):**
- G2: False positive rate < 5% ✅ boundary met (0.556 < 0.6)
- G10: Pilot uptime ≥ 99.5% → measured during live pilot

**Ops tasks (non-blocking):**
- G6: iOS/Android app store submission (guides done)

*Tạo bởi: UIP Team Orchestrator (2026-06-12); updated 2026-06-16*
