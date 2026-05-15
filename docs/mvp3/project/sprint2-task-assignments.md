# Sprint MVP3-2 — Task Assignments & Tracking

**Status:** ACTIVE
**Document Date:** 2026-05-15
**Last Updated:** 2026-05-15 (Pre-sprint session — regression + bug fix run)
**Sprint Start:** 2026-05-19 | **Sprint End:** 2026-05-30
**Source:** Sprint 2 Planning (`sprint2-planning.md`) + MVP3 Detail Plan (`detail-plan.md`)

---

## 1. Task Summary by Role

### SA (Solution Architect)

| Task ID | Title | Priority | SP | Week | Dependencies | Status |
|---------|-------|----------|-----|------|--------------|--------|
| SA-SPR2-01 | Flink Enrichment JDBC Metadata Join Design Review (ADR) | HIGH | - | W1 | TD-03 PASS | PENDING |

**Notes:** Nếu TD-03 (Flink checkpoint) fail, SA cần re-design enrichment architecture. ADR output block v3-BE-04.

---

### Backend (2 Engineers)

| Task ID | Title | SP | Priority | Week | Dependencies | Deadline | Status |
|---------|-------|-----|----------|------|--------------|----------|--------|
| TD-01 | ClickHouse ReplacingMergeTree + OffsetsInitializer | 5 | CRITICAL | W1 | None | Wed 05-21 review | PENDING |
| v3-EXT-04 | analytics-service Cutover (Feature Flag Flip) | 3 | CRITICAL | W1 | TD-01 DONE | Fri 05-23 ready | PENDING |
| v3-BE-03 | ClickHouse Client + Analytics Queries | 13 | CRITICAL | W2 | TD-01, v3-EXT-04 | Fri 05-30 | PENDING |
| v3-BE-04 | Flink ClickHouse Enrichment (Building Metadata Join) | 8 | HIGH | W2 | TD-03 PASS, v3-BE-03 ready | Fri 05-30 | PENDING |
| Carry-over | CRITICAL Items (C-2, C-5, OL-1, OL-2, R-RLS-1) | - | CRITICAL | W1 | None | Wed 05-21 EOD | PENDING |
| Carry-over | MEDIUM Items (M-1, M-2) | - | MEDIUM | W2 | None | Fri 05-30 | PENDING |

**Backend SP Total:** 29 SP (TD-01: 5 + v3-EXT-04: 3 + v3-BE-03: 13 + v3-BE-04: 8)

**Assignment Suggestion:**
- Backend Lead: TD-01, v3-EXT-04, v3-BE-03 (21 SP)
- Backend Dev: Carry-over items, v3-BE-04 (8 SP + carry-over)

---

### Frontend (1 Engineer)

| Task ID | Title | SP | Priority | Week | Dependencies | Deadline | Status |
|---------|-------|-----|----------|------|--------------|----------|--------|
| v3-FE-03 | Analytics Dashboard — Energy + Emissions Charts | 13 | CRITICAL | W2 | v3-BE-03 live | Fri 05-30 | PENDING |
| v3-FE-04 | Analytics Aggregation Filters | 8 | HIGH | W2 | v3-FE-03 merged | Fri 05-30 | PENDING |

**Frontend SP Total:** 21 SP

**Risk (R1):** Week 2 over-committed (42 SP / 26 SP capacity). If carry-over slips, deprioritize v3-FE-04 to Sprint 3.

---

### DevOps (1 Engineer)

| Task ID | Title | SP | Priority | Week | Dependencies | Deadline | Status |
|---------|-------|-----|----------|------|--------------|----------|--------|
| Pre-Sprint | Infrastructure Provisioning (CH, Flink, PROD-SHADOW) | - | CRITICAL | Pre | None | Sun 05-18 | PENDING |
| TD-05 | Kong Health Check Automation | 1 | MEDIUM | W1 | None | Thu 05-22 | PENDING |
| C-3 | Flink Checkpoint Remote Storage (MinIO/S3) | - | CRITICAL | W1 | Pre-Sprint done | Wed 05-21 | PENDING |
| v3-EXT-04/05 | Cutover Execution + HPA | 5 | CRITICAL | W1-2 | TD-01, runbook ready | Mon 05-26 | PENDING |
| W2 Ops | Monitoring (Flink Grafana) + CH Upgrade Assessment | - | MEDIUM | W2 | None | Fri 05-30 | PENDING |

**DevOps SP Total:** 6 SP (TD-05: 1 + v3-EXT-04/05: 5)

---

### QA (1 Engineer)

| Task ID | Title | SP | Priority | Week | Dependencies | Deadline | Status |
|---------|-------|-----|----------|------|--------------|----------|--------|
| **TD-02** | CrossBuildingAggregationService Testcontainers IT | 2 | HIGH | W1 | None | Fri 05-23 | **IN PROGRESS** — Pre-sprint: 2 test bugs fixed (AGG-IT-11, AGG-IT-14), 18/18 PASS. Coverage upgrade 39%→≥85% target: Week 1. |
| TD-03 | Flink Kill/Restart E2E + Checkpoint Recovery | 3 | HIGH | W1 | DevOps C-3 done | Fri 05-23 | PENDING |
| QA Strategy | Sprint 2 Test Strategy + Quality Gates + 15 Manual TC | 3 | HIGH | W1 | None | Fri 05-23 | PENDING |
| Regression | Sprint 2 Regression + Load Test + Shadow Validation | - | CRITICAL | W2 | Cutover done | Fri 05-30 | PENDING |

**QA SP Total:** 8 SP

---

### Tester (Manual)

| Task ID | Title | Priority | Week | Dependencies | Deadline | Status |
|---------|-------|----------|------|--------------|----------|--------|
| Manual TC | Sprint 2 Manual Test Execution (15 TC + Smoke + Exploratory) | HIGH | W2 | v3-FE-03/04 deployed | Fri 05-30 12:00 | PENDING |

**Tester SP:** Not estimated (manual execution)

---

## 2. Dependency Graph

```
Pre-Sprint (DevOps) ──────────────────────────────────────────────→ All Week 1 tasks

Week 1 Critical Path:
  DevOps: C-3 (Flink MinIO) ──→ QA: TD-03 (Flink E2E) ──→ SA: ADR Review ──→ v3-BE-04
  Backend: TD-01 (ReplacingMergeTree) ──→ v3-EXT-04 (Cutover) ──→ v3-BE-03 (CH Queries)
  QA: TD-02 (Coverage IT) ─── independent ───┐
  DevOps: TD-05 (Kong) ──── independent ──────┘

Week 2 Critical Path:
  v3-BE-03 (CH Queries) ──→ v3-FE-03 (Dashboard) ──→ v3-FE-04 (Filters) ──→ Tester: Manual TC
  v3-BE-04 (Flink Enrichment) ──→ QA: Load Test
  DevOps: Cutover Execution ──→ QA: Regression Suite
  QA: All gates ──→ Gate Review (Fri 15:00)
```

## 3. Gate Review Checklist Owner Assignment

| Gate | Criteria | Owner | Verify Date |
|------|----------|-------|-------------|
| G1 | Analytics Dashboard Live | Frontend + Tester | Fri 05-30 |
| G2 | ClickHouse Deduplication | Backend | Fri 05-30 |
| G3 | Flink Checkpoint Recovery | QA | Fri 05-23 |
| G4 | CrossBuilding Coverage ≥85% | QA | Fri 05-30 |
| G5 | Integration Test Coverage ≥25% | QA | Fri 05-30 |
| G6 | Zero P0/P1 Bugs | All | Fri 05-30 |
| G7 | Shadow 72h Delta <0.01% | Backend + QA | Daily audit |
| G8 | Tier-1 Regression 103/103 | QA | Fri 05-30 |
| G9 | Load Test ≥10k events/sec | QA | Fri 05-30 |
| G10 | Code Review all stories | Tech Lead | Fri 05-30 |

## 4. Risk Monitor for PM

| Risk | Trigger Date | Owner | PM Action if Triggered |
|------|-------------|-------|----------------------|
| R1: Week 2 over-commit | Tue 05-27 | PM | Deprioritize v3-FE-04 to Sprint 3 |
| R2: CH backfill outage | Mon 05-19 | Backend | Schedule during 3-7am SGT |
| R3: Flink enrichment latency | Wed 05-28 | Backend | Escalate to CTO, batch fallback |
| R4: Shadow delta >0.01% | Daily | QA | Escalate to CTO |
| R5: Kong health check fails | Thu 05-22 | DevOps | Manual fallback + Slack |
| R6: Lighthouse <85 | Wed 05-28 | Frontend | Code-split recharts |
| R7: Cutover rollback triggers | Fri 05-23 | Backend | Rollback procedure validated |

## 5. Week-by-Week PM Checkpoints

### Week 1 (May 19-23)

| Day | PM Checkpoint | Go/No-Go |
|-----|--------------|----------|
| Mon 05-19 10:00 | Sprint kickoff — review carry-over, confirm assignments | — |
| Tue 05-20 | Track TD-01 progress, verify DevOps C-3 setup | — |
| Wed 05-21 EOD | **CARRY-OVER GATE:** ≥80% carry-over complete | **YES/NO** |
| Thu 05-22 | TD-05 Kong validation, TD-03 dry run | — |
| Fri 05-23 EOW | **WEEK 1 GATE:** Carry-over DONE ≥90%, cutover runbook signed off | **YES/NO** |

### Week 2 (May 26-30)

| Day | PM Checkpoint | Go/No-Go |
|-----|--------------|----------|
| Mon 05-26 | Cutover execution + validation | **CUTOVER HEALTH CHECK** |
| Tue 05-27 | v3-BE-03 query progress, v3-FE-03 skeleton | — |
| Wed 05-28 14:00 | **MID-SPRINT REVIEW:** ≥50% v3-BE-03/v3-FE-03 functional | **YES/NO** |
| Thu 05-29 15:00 | Backlog refinement (Sprint 3 prep) | — |
| Fri 05-30 13:00 | Sprint Review (Demo + PO Sign-Off) | — |
| Fri 05-30 15:00 | **GATE REVIEW:** G1-G10 checklist | **HARD/CONDITIONAL/FAIL** |
| Fri 05-30 16:00 | Sprint Retrospective | — |

## 6. Escalation Protocol

| Issue | Threshold | First Response | Escalation |
|-------|-----------|----------------|------------|
| Carry-over slip | Not ≥80% by Wed 05-21 EOD | PM → Backend Lead | CTO + PO by Thu 05-22 |
| Cutover failure | Any P0 bug post-cutover | Backend Lead → rollback | CTO + PO within 2h |
| Load test <8k/sec | Near-miss threshold | Backend + DevOps investigate | CTO if <8k confirmed |
| Capacity risk | Any story at risk >2 days | PM → all-hands | CTO + PO: scope vs deadline |

---

## 7. Sprint 2 → Sprint 3 Transition

**Sprint 3 Prep (Thu 05-29 15:00):**
- Attendees: PM, Tech Lead, PO
- Agenda: Sprint 3 story intake, RoutingJwtDecoder + HA sizing, dependency mapping
- Output: Sprint 3 backlog ready for planning (Mon 06-02)

**Sprint 3 Preview:**
- RoutingJwtDecoder dual-issuer (8-10 SP, deferred from Sprint 1)
- ClickHouse HA failover cluster (deferred from Sprint 2)
- Production deployment + canary metrics
- Early ESG reporting module (GRI 302/305 export)

---

---

## 8. Pre-Sprint Activity Log (2026-05-15)

> Ghi nhận công việc hoàn thành trong ngày pre-sprint trước khi Sprint 2 chính thức bắt đầu (2026-05-19).

| Item | Kết quả | Ghi chú |
|------|---------|---------|
| **BUG-AGG-IT-11** — `emptyTimeRange_sameFromTo_returnsEmpty` | ✅ FIXED | Added early-return guard trong `CrossBuildingAggregationService.aggregate()` khi `from == to` |
| **BUG-AGG-IT-14** — `zeroValueMetrics_sumAndAvgCorrect` wrong assertions | ✅ FIXED | Corrected: `dataPoints=2→12`, `totalValue≈100→1100` (12 rows = 11 seeded + 1 zero-value) |
| **CrossBuildingAggregationServiceIT** 18/18 | ✅ PASS | Was 16/18 (2 failures); now all 18 pass |
| **Tier-1 API regression** 103/103 | ✅ PASS | Pre-sprint gate G8 pre-validated |
| **Demo walkthrough** | ✅ COMPLETE | Dashboard, Buildings, ESG, Traffic, Environment — all working with live ClickHouse data |
| **Architecture review: AnalyticsProxyController** | ✅ ACCEPTED | Tech debt confirmed: proxy bypasses Kong. Accepted per ADR-028 (Kong scope = extracted services only). Deferred deletion to Sprint MVP3-4 (v3-DevOps-04). |
| **Sprint roadmap review** (PM) | ✅ DONE | Không có story nào tường minh để xóa `AnalyticsProxyController`. Gap identified — cần thêm vào Sprint MVP3-4 backlog: [FE] Remove proxy + migrate frontend analytics → Kong (3 SP). |

### Tech Debt Item Mới — Thêm vào Sprint MVP3-4 Backlog

| ID | Title | SP | Sprint | Notes |
|----|-------|-----|--------|-------|
| **TD-PROXY-01** | [FE+BE] Remove `AnalyticsProxyController`, migrate frontend analytics calls → Kong (:8000) | 3 SP | **MVP3-4** (after v3-DevOps-04 Kong production-ready) | DoD: proxy deleted, frontend `VITE_ANALYTICS_URL` points to Kong, E2E test PASS |

---

**Document Version:** 1.1
**Created:** 2026-05-15
**Owner:** UIP PM
**Next Update:** 2026-05-19 (Sprint Kickoff)
