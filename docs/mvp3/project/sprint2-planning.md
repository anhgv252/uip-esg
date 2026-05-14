# Sprint MVP3-2 Planning Document
**Status:** APPROVED FOR EXECUTION  
**Document Date:** 2026-05-15  
**Sprint Start:** 2026-05-19 (Monday, Week 11)  
**Sprint End:** 2026-05-30 (Friday EOD)  
**Gate Review:** 2026-05-30 15:00 SGT

---

## 1. Sprint Overview

| Dimension | Value |
|-----------|-------|
| **Sprint Name** | MVP3-2: Analytics Foundation & ClickHouse Go-Live |
| **Sprint Number** | 2 of 4 (MVP3) |
| **Duration** | 10 calendar days (2 weeks) |
| **Team Size** | 5 FTE (Backend 2, Frontend 1, QA 1, DevOps 1) |
| **Total Backlog** | 56 SP (11 SP carry-over Week 1 + 45 SP new work) |
| **Objective** | Ship analytics-service cutover with live ClickHouse queries, deliver Analytics Dashboard MVP with Flink enrichment pipeline operational. Eliminate shadow 72h gap; achieve P0/P1 bug = 0. |

---

## 2. Sprint Goal

**Gate-Based SMART Goal:**  
Team will achieve CONDITIONAL PASS → HARD PASS by 2026-05-30 15:00 SGT by shipping analytics-service cutover, live ClickHouse deduplication (ReplacingMergeTree), full Analytics Dashboard with energy/emissions charts, and 103/103 tier-1 regression PASS with zero P0/P1 bugs, satisfying all 10 mandatory gate criteria for MVP3-2 hand-off to production cutover planning (Sprint 3).

---

## 3. Capacity Planning

### Team Allocation

| Role | Person | % Sprint | Type | Capacity (SP) |
|------|--------|---------|------|---------------|
| Backend Lead | TBD | 100% | Senior Engineer | 15 SP |
| Backend Dev | TBD | 100% | Mid Engineer | 12 SP |
| Frontend Dev | TBD | 100% | Mid Engineer | 12 SP |
| QA Lead | TBD | 100% | Senior QA | 14 SP |
| DevOps Eng | TBD | 100% | Mid/Senior | 12 SP |
| **Total Capacity** | 5 FTE | - | **65 SP** | **65 SP** |
| **Buffer** (20%) | - | - | **Reserved** | **-13 SP** |
| **Net Available** | - | - | **Committed** | **52 SP** |

### Week-by-Week Velocity

| Week | Committed SP | Capacity SP | Utilization | Notes |
|------|-------------|-------------|--------------|-------|
| **Week 1** (May 19-23) | 14 SP | 26 SP | 54% | Carry-over focus + v3-EXT-04 cutover prep |
| **Week 2** (May 26-30) | 42 SP | 26 SP | 162% | **RISK:** Over-committed; stretch goal, requires early Week 1 carry-over closure |

**Mitigation:** Week 1 must complete carry-over by EOW (Wed 2026-05-21 EOD) to free 13 SP capacity Wed-Fri for Week 2 launch stories. If carry-over slips, deprioritize v3-FE-04 (Filters) to Week 3.

---

## 4. Week 1 Plan (May 19–23)
**Theme:** Stabilization + Cutover Readiness

### Week 1 Daily Milestones

| Day | Date | Focus | Owner | Go/No-Go |
|-----|------|-------|-------|----------|
| **Mon** | 2026-05-19 | Sprint kickoff (10:00), carry-over tech review (11:00), ClickHouse ReplacingMergeTree design finalize | Backend Lead | — |
| **Tue** | 2026-05-20 | TD-01 MergeTree implementation, Testcontainers setup for TD-02, Flink checkpoint E2E skeleton | Backend, QA | — |
| **Wed** | 2026-05-21 | TD-01 code review + merge, TD-02 IT coverage build to ≥70%, v3-EXT-04 feature flag audit | All | **EOD GATE: Carry-over ≥80% complete** |
| **Thu** | 2026-05-22 | TD-03 Flink E2E test run (dry run checkpoint recovery), TD-05 Kong health check script, v3-EXT-04 cutover runbook finalize | QA, DevOps | — |
| **Fri** | 2026-05-23 | Carry-over final integration test, analytics-service shadow 72h validation readiness (data parity check), Week 2 readiness review | All | **EOW GATE: Carry-over DONE (≥90%)** |

### Week 1 Stories & Tasks

| Ticket | Title | SP | Owner | Acceptance Criteria | Status |
|--------|-------|-----|-------|--------------------|----|
| **TD-01** | ClickHouse ReplacingMergeTree + OffsetsInitializer | 5 | Backend | ✓ ReplacingMergeTree schema deployed to all envs (DEV/UAT/PROD-SHADOW) ✓ Dedup test suite PASS (dup scenario + offset logic) ✓ Backward compat with existing queries verified ✓ Rollback plan documented & tested | — |
| **TD-02** | CrossBuildingAggregationService Testcontainers IT | 2 | QA | ✓ Code coverage 39% → ≥85% ✓ All JDBC aggregation paths exercised in IT ✓ TimescaleDB Testcontainers container stable ✓ Performance baseline <500ms for 100 buildings | — |
| **TD-03** | Flink Kill/Restart E2E + Checkpoint Recovery | 3 | QA | ✓ E2E scenario: Flink job → checkpoint → force kill → restart ✓ Verify message offset catch-up (0 lost events) ✓ State recovery latency <2min ✓ Test automation in CI/CD pipeline | — |
| **TD-05** | Kong Health Check Automation | 1 | DevOps | ✓ Prometheus endpoint for Kong /health exposed ✓ Alerting rule: Kong down → Slack + PagerDuty ✓ Post-restart auto-recovery test PASS ✓ Runbook updated (1-page) | — |
| **v3-EXT-04** | analytics-service Cutover (Feature Flag Flip) | 3 | Backend | ✓ Feature flag "USE_ANALYTICS_SERVICE" hardened + tested ✓ Cutover runbook (DRI: PO) finalized & reviewed by CTO ✓ Rollback procedure validated in PROD-SHADOW ✓ Cutover scheduled EOW (Fri EOD or Mon morning) | — |

### Week 1 DoD Checklist
- [ ] All TD-* items merged to `develop` branch
- [ ] Shadow 72h data parity validation: delta <0.01%
- [ ] Carry-over regression: 103/103 tier-1 tests PASS
- [ ] Carry-over code coverage: ≥85% minimum across new IT code
- [ ] Zero P0/P1 bugs in carry-over stories
- [ ] analytics-service cutover runbook signed off by PO + CTO
- [ ] DevOps ready for Mon 2026-05-26 cutover execution (or Fri 2026-05-23 if accelerated)

---

## 5. Week 2 Plan (May 26–30)
**Theme:** Analytics Dashboard Ship + Enrichment Pipeline Go-Live

### Week 2 Daily Milestones

| Day | Date | Focus | Owner | Go/No-Go |
|-----|------|-------|-------|----------|
| **Mon** | 2026-05-26 | v3-EXT-04 Cutover Execution (or skip if done Fri EOW) + validation, v3-BE-03 ClickHouse client design review, v3-FE-03 chart skeleton + mock data | All | **Cutover health check:** Shadow 72h gap closed |
| **Tue** | 2026-05-27 | v3-BE-03 analytics queries build (getEnergyByBuilding, getEmissionsByTenant, getAQITrend), v3-FE-03 chart integration, v3-BE-04 Flink enrichment schema design | Backend, Frontend | — |
| **Wed** | 2026-05-28 | v3-BE-03 query tests + perf validation (<1s p99), v3-FE-04 filter component skeleton, Flink enrichment code review & merge | All | **Mid-Sprint Gate:** ≥50% v3-BE-03/v3-FE-03 functional |
| **Thu** | 2026-05-29 | v3-FE-03 chart refinement + responsive test, v3-FE-04 multi-select + date range integration, Flink enrichment deployment to UAT | Frontend, Backend | — |
| **Fri** | 2026-05-30 | Sprint regression suite (103 tier-1 tests), final integration tests, Analytics Dashboard UAT sign-off, Sprint 2 Gate Review (15:00 SGT) | All | **GATE REVIEW** |

### Week 2 Stories & Tasks

| Ticket | Title | SP | Owner | Acceptance Criteria | Status |
|--------|-------|-----|-------|--------------------|----|
| **v3-BE-03** | ClickHouse Client + Analytics Queries | 13 | Backend | ✓ Three queries live: getEnergyByBuilding, getEmissionsByTenant, getAQITrend ✓ Response time <1s p99 for standard date range ✓ Query tests (unit + integration) ≥90% coverage ✓ API v1 endpoints documented in OpenAPI ✓ Shadow 72h cutover validation ✓ Error handling & retry logic for network timeout | — |
| **v3-FE-03** | Analytics Dashboard — Energy + Emissions Charts | 13 | Frontend | ✓ Dashboard layout: Header (filters) + 4 chart panels (energy, emissions, AQI trend, building breakdown) ✓ Charts render live ClickHouse data (post v3-BE-03 ready) ✓ Responsive: Desktop (1920px) + Tablet (768px) + Mobile (375px) ✓ Lighthouse performance ≥90 on prod build ✓ Accessibility WCAG 2.1 AA ✓ E2E smoke test: navigate to dashboard → verify data load | — |
| **v3-FE-04** | Analytics Aggregation Filters | 8 | Frontend | ✓ Filter panel: Date range (pre-sets + custom), Building multi-select (max 10), Metric type (energy/emissions/AQI), GroupBy (hour/day/month) ✓ Filters persist in URL (shareable links) ✓ Apply/Reset buttons functional ✓ E2E test: filter combo → dashboard updates ✓ No state corruption on rapid filter clicks | — |
| **v3-BE-04** | Flink ClickHouse Enrichment (Building Metadata Join) | 8 | Backend | ✓ Flink stream job reads building metadata from JDBC source (on-demand or side input) ✓ Enrich sensor events with `building_name`, `district`, `category` ✓ Emit enriched events to ClickHouse sink ✓ Checkpoint-based recovery tested (via TD-03) ✓ Latency impact <100ms p99 ✓ Throughput ≥10k events/sec @ peak | — |

### Week 2 DoD Checklist
- [ ] v3-BE-03 merged; three ClickHouse queries in prod-shadow, p99 <1s confirmed
- [ ] v3-FE-03 merged; Analytics Dashboard live on prod-shadow with live data
- [ ] v3-FE-04 merged; filters functional & E2E test automated
- [ ] v3-BE-04 merged; Flink enrichment job deployed to UAT, checkpoint tested
- [ ] Sprint regression: 103/103 tier-1 PASS (tier-2: >95% tolerance)
- [ ] Integration test coverage: ≥25% (baseline from Sprint 1)
- [ ] Load test: verify throughput target (≥10k events/sec sustained)
- [ ] Code coverage: ≥82% (team baseline)
- [ ] Zero P0/P1 bugs at Sprint end; P2 bugs →backlog
- [ ] Analytics Dashboard UAT sign-off from PO
- [ ] All gate criteria checklist complete

---

## 6. Sprint 2 Backlog (Full Detail)

| Epic | Story ID | Title | SP | Priority | Owner | Week | Dependencies | Acceptance Criteria |
|------|----------|-------|-----|----------|-------|------|--------------|---------------------|
| **Stabilization** | TD-01 | ClickHouse ReplacingMergeTree + OffsetsInitializer | 5 | **CRITICAL** | Backend | 1 | None | ReplacingMergeTree deployed all envs; dedup test suite PASS; rollback plan tested |
| **Stabilization** | TD-02 | CrossBuildingAggregationService Testcontainers IT | 2 | **HIGH** | QA | 1 | None | Coverage 39%→≥85%; JDBC paths exercised; Testcontainers stable; <500ms perf |
| **Stabilization** | TD-03 | Flink Kill/Restart E2E + Checkpoint Recovery | 3 | **HIGH** | QA | 1 | None | E2E kill/restart scenario; offset recovery 0-loss; <2min recovery latency; CI/CD integrated |
| **Stabilization** | TD-05 | Kong Health Check Automation | 1 | **MEDIUM** | DevOps | 1 | None | Prometheus endpoint exposed; alerting rule live; auto-recovery test PASS; 1-page runbook |
| **Cutover** | v3-EXT-04 | analytics-service Cutover (Feature Flag Flip) | 3 | **CRITICAL** | Backend | 1 | TD-01 DONE | Feature flag hardened; cutover runbook approved by CTO; rollback validated in PROD-SHADOW |
| **Analytics Core** | v3-BE-03 | ClickHouse Client + Analytics Queries | 13 | **CRITICAL** | Backend | 2 | TD-01 merged | 3 queries live; <1s p99; ≥90% test coverage; OpenAPI updated; shadow 72h validation |
| **Analytics Core** | v3-BE-04 | Flink ClickHouse Enrichment (Building Metadata Join) | 8 | **HIGH** | Backend | 2 | TD-03 DONE; v3-BE-03 ready | JDBC metadata join; enriched events to CH; checkpoint recovery tested; ≥10k events/sec |
| **Analytics UI** | v3-FE-03 | Analytics Dashboard — Energy + Emissions Charts | 13 | **CRITICAL** | Frontend | 2 | v3-BE-03 live | 4-chart layout; live CH data; responsive (1920/768/375px); Lighthouse ≥90; WCAG 2.1 AA |
| **Analytics UI** | v3-FE-04 | Analytics Aggregation Filters | 8 | **HIGH** | Frontend | 2 | v3-FE-03 merged | Date range, Building multi-select, Metric type, GroupBy filters; URL persistence; E2E automated |

**Total Committed:** 56 SP (11 SP Week 1 carry-over + 3 SP v3-EXT-04 + 42 SP Week 2 stories)

---

## 7. Definition of Done — Sprint 2 Gate Criteria

### Mandatory Gate Checklist (10/10 Required for HARD PASS)

| Gate Item | Target | Verification | Owner | Status |
|-----------|--------|--------------|-------|--------|
| **G1: Analytics Dashboard Live** | Dashboard in prod-shadow with live ClickHouse data | PO UAT sign-off + 4h smoke test | Frontend | — |
| **G2: ClickHouse Deduplication** | ReplacingMergeTree deployed + verified no dups | Data consistency query in UAT/PROD-SHADOW | Backend | — |
| **G3: Flink Checkpoint Recovery** | E2E kill/restart test PASS (0 event loss) | Automated test in CI/CD | QA | — |
| **G4: CrossBuilding Coverage** | Code coverage ≥85% (39% → 85%) | SonarQube report | QA | — |
| **G5: Integration Test Coverage** | ≥25% integration test coverage | Coverage report by layer | QA | — |
| **G6: P0/P1 Bugs** | Zero P0/P1 bugs open at sprint end | JIRA query: open P0/P1 = 0 | All | — |
| **G7: Shadow 72h Delta** | Data difference <0.01% (shadow vs prod) | Analytics query comparison report | Backend | — |
| **G8: Tier-1 Regression** | 103/103 tier-1 tests PASS | CI/CD regression suite | QA | — |
| **G9: Load Test** | Verify throughput target (≥10k events/sec sustained) | JMeter / custom load test report | QA | — |
| **G10: Code Review** | All Sprint 2 stories reviewed + approved by Tech Lead | GitHub/GitLab review logs | Tech Lead | — |

### Conditional Pass Criteria (Escalation Required)

| Scenario | Threshold | Action | Escalation |
|----------|-----------|--------|-----------|
| G4 CrossBuilding coverage 82–84% | Borderline | Extend coverage in Week 2 Wed+Thu; if <82% → escalate to CTO | CTO approve or defer v3-BE-04 to Sprint 3 |
| G7 Shadow delta 0.01–0.05% | Acceptable (tight tolerance) | Root cause analysis + mitigation | CTO + PO sign-off required for HARD PASS |
| G9 Load test 8k–10k events/sec | Near-miss | Investigate Flink bottleneck; consider horizontal pod scaling | DevOps + Backend escalate if <8k |

### Sprint Hygiene (Non-Blocking)

- [ ] All commits squashed & follow conventional commit (feat/fix/docs/test)
- [ ] Code coverage ≥82% project-wide (team baseline)
- [ ] Zero SonarQube blocker issues at merge
- [ ] README updated for new features
- [ ] Runbooks updated: analytics-service cutover, Flink enrichment deployment
- [ ] Retrospective notes captured for retro session

---

## 8. Risk Register

### Risks Identified (Sprint 2)

| Risk ID | Description | Probability | Impact | Owner | Mitigation | Monitor |
|---------|-------------|-------------|--------|-------|-----------|---------|
| **R1** | Week 2 capacity over-committed (42 SP / 26 SP capacity) | HIGH | **CRITICAL** | PM | Complete carry-over by Wed 2026-05-21 EOD; if slip detected Tue, deprioritize v3-FE-04 (Filters) to Sprint 3 | Daily standup |
| **R2** | ClickHouse ReplacingMergeTree backfill causes PROD-SHADOW outage (2–4h) | MEDIUM | **HIGH** | Backend | Pre-create test environment replica; dry-run backfill script in QA env; schedule backfill during low-traffic window (3-7am SGT) | Backend Lead pre-sprint |
| **R3** | Flink enrichment JDBC metadata join latency exceeds <100ms p99 (hits 200-300ms) | MEDIUM | **HIGH** | Backend | Design review by DevOps for connection pooling tuning; load test early (Wed Week 2); have batch enrichment fallback plan | Load test Wed 2026-05-28 |
| **R4** | Shadow 72h delta detection reveals unexpected ESG calculation discrepancy | MEDIUM | **MEDIUM** | Backend + QA | Daily shadow data audit (Tue-Thu Week 1); if delta >0.01% detected, escalate to CTO Fri morning for investigation | Daily audit logs |
| **R5** | Kong post-restart health check automation fails in prod (alerting gap remains) | LOW | **MEDIUM** | DevOps | Test Kong restart manually in PROD-SHADOW before gatekeeping; have fallback: manual health check + Slack notification | TD-05 validation Fri Week 1 |
| **R6** | Frontend Lighthouse performance <85 on prod build (accessibility or bundle size) | MEDIUM | **MEDIUM** | Frontend | Run Lighthouse on mock data mid-week; code-split recharts bundles; identify bottleneck by Wed EOD | Wed 2026-05-28 Lighthouse run |
| **R7** | analytics-service cutover rollback triggers (e.g., ClickHouse query timeout in prod) | MEDIUM | **CRITICAL** | Backend + DevOps | Finalize rollback procedure by Wed Week 1; have ClickHouse rollback (revert ReplacingMergeTree) + feature flag rollback (flip back to legacy aggregation service) | Cutover runbook sign-off Wed |

### Risk Mitigation Timeline

| Date | Mitigation Action | Owner | Status |
|------|-------------------|-------|--------|
| 2026-05-19 Mon | Sprint kickoff risk review; Backend finalizes MergeTree backfill dry-run | Backend Lead | — |
| 2026-05-21 Wed | Carry-over delivery gate (R1 escalation trigger) | PM | — |
| 2026-05-22 Thu | TD-05 Kong restart test in PROD-SHADOW (R5 validation) | DevOps | — |
| 2026-05-23 Fri | Shadow 72h final validation (R4 sign-off) + analytics-service cutover runbook approval (R7 sign-off) | Backend + CTO | — |
| 2026-05-28 Wed | v3-FE-03 Lighthouse performance run (R6 detection) | Frontend | — |

---

## 9. Dependencies & Blockers

### Pre-Sprint Requirements (Must Resolve by 2026-05-19)

| Blocker | Dependency On | Owner | ETA | Impact if Blocked |
|---------|---------------|-------|-----|-------------------|
| **ClickHouse cluster resource allocation** | DevOps provisioning (ReplacingMergeTree table space +20%) | DevOps | 2026-05-18 | TD-01 blocked; cascade to v3-BE-03 delay |
| **analytics-service JAR readiness for cutover** | Backend integration + QA functional test | Backend | 2026-05-18 | v3-EXT-04 cannot start Mon 2026-05-26 |
| **PROD-SHADOW environment refresh** | DevOps data sync from prod cluster | DevOps | 2026-05-18 | Shadow 72h validation impossible |
| **Flink checkpoint recovery cluster setup** | DevOps Flink cluster + storage provisioning | DevOps | 2026-05-18 | TD-03 E2E test cannot run |
| **PO sign-off on cutover runbook** | PO availability for review (Wed 2026-05-21) | PO | 2026-05-21 | v3-EXT-04 execution delayed |

### External Dependencies

| Dependency | Owned By | Status | Risk to Sprint |
|-----------|----------|--------|---|
| City Authority ESG reporting deadline (2026-06-15) | PO / Client | Planned | MVP3-2 delivery on-time required for Sprint 3 prep |
| Third-party API monitoring (Grafana dashboards) | DevOps | On-track | Non-blocking (nice-to-have for cutover observability) |
| GRI 302/305 format compliance sign-off | BA | Pending (Sprint 1 carryover note) | Format locked in Sprint 1; verify no rework required |

### Internal Blockers to Monitor

| Blocker | Current Status | Escalation If Occurs |
|---------|---|---|
| Backend senior engineer unavailable mid-sprint | GREEN (confirmed) | Scale v3-BE-04 enrichment to DevOps; reduce scope |
| Frontend senior designer unavailable for v3-FE-03 reviews | YELLOW (monitor) | Escalate to UX for design review delegation |
| QA lab environment (Testcontainers setup) unstable | GREEN (IT foundation from Sprint 1 stable) | Escalate to DevOps for env reboot |

---

## 10. Ceremony Schedule

### Daily Standup
- **Time:** 09:30 SGT (Mon–Fri)
- **Duration:** 15 min
- **Attendees:** Backend (2), Frontend (1), QA (1), DevOps (1), PM (scribe)
- **Format:** Blockers → Progress → Plan
- **Location:** Zoom link (TBD in sprint channel)

### Mid-Sprint Review (Checkpoint Gate)
- **Date:** Wednesday 2026-05-28 14:00 SGT
- **Duration:** 30 min
- **Attendees:** Tech Lead, Backend, Frontend, QA, PM, CTO (optional)
- **Agenda:**
  - v3-BE-03 query progress (target: ≥50% functional)
  - v3-FE-03 chart rendering (target: ≥50% merged)
  - Risk escalation check (R1 capacity, R6 Lighthouse)
  - Week 2 readiness forecast
- **Go/No-Go:** Can team hit Friday gate?

### Sprint Review (Demo + PO Sign-Off)
- **Date:** Friday 2026-05-30 13:00 SGT
- **Duration:** 45 min
- **Attendees:** PO, Tech Lead, Backend, Frontend, QA, PM, City Authority stakeholder (optional)
- **Agenda:**
  - Analytics Dashboard live demo (v3-FE-03)
  - ClickHouse query performance showcase (v3-BE-03)
  - Filters demo (v3-FE-04)
  - Regression test results (103/103)
  - Cutover readiness sign-off
- **Gate Review:** Immediately follow (15:00 SGT, 15 min)
  - G1–G10 checklist review
  - HARD PASS / CONDITIONAL PASS / FAIL determination
  - Escalation matrix invoked if needed

### Sprint Retrospective
- **Date:** Friday 2026-05-30 16:00 SGT
- **Duration:** 45 min
- **Attendees:** Backend (2), Frontend (1), QA (1), DevOps (1), PM (facilitator)
- **Format:** Went well / Went wrong / Action items for Sprint 3
- **Key Topic:** Capacity planning lessons (Week 2 overcommit risk review)
- **Sync:** Lessons logged to `.claude/workdir/sprint2-retro-lessons-[date].md`

### Backlog Refinement (Sprint 3 Prep)
- **Date:** Thursday 2026-05-29 15:00 SGT
- **Duration:** 45 min
- **Attendees:** PM, Tech Lead, PO
- **Agenda:** Sprint 3 story intake (RoutingJwtDecoder + HA, if approved), story sizing, dependency mapping
- **Output:** Sprint 3 backlog ready for sprint planning (Mon 2026-06-02)

---

## 11. Escalation Matrix

### Decision Authority by Scope

| Issue | Threshold | Owner | Escalation | Authority |
|-------|-----------|-------|-----------|-----------|
| **Capacity Risk** | Any story at risk of miss by >2 days | PM | CTO + PO | Deprioritize scope vs. extend deadline |
| **Technical Design** | Architecture conflict between BE + FE | Tech Lead | CTO + BA | Design arbitration + ADR if needed |
| **Bug Severity** | Any P0 or P1 bug discovered | QA | PM + Backend Lead | Interrupt sprint work or hotfix? |
| **Gate Failure** | ≥2 gate criteria failing at sprint end | PM | CTO + PO | CONDITIONAL PASS + Sprint 3 replan |
| **External Blocker** | Dependency owner (DevOps/Client) unavailable | PM | CTO | Workaround or delay sprint? |
| **Code Quality** | SonarQube blocker issues at merge | Tech Lead | CTO | Refactor or accept debt? |
| **Performance** | Load test <8k events/sec | Backend | CTO + DevOps | Investigate or defer optimization? |

### Escalation Protocol

1. **PM detects issue** (daily standup or mid-sprint review)
2. **PM notifies owner + CTO via Slack** (within 2h)
3. **Owner has 24h to propose mitigation** (else auto-escalate to PO)
4. **CTO + PO meet within 48h** to decide scope/timeline
5. **Decision communicated to team** by EOD next day

### Out-of-Scope Issues (Auto-Defer to Sprint 3)
- New feature requests (unless P0 bug)
- Nice-to-have technical debt
- Optimization tasks if sprint overcommit detected

---

## 12. Sprint 2 → Sprint 3 Preview

**Sprint 3 Objective (DRAFT):** Production cutover + security hardening + foundation for city authority integration testing (UAT phase). Expected stories: RoutingJwtDecoder dual-issuer support (RSA key rotation), ClickHouse HA failover cluster setup, analytics-service production deployment, UAT data pipeline replication, and early ESG reporting module (GRI 302/305 export). Sprint 3 gate criteria will include production canary metrics (0.1% error rate, <500ms p99 latency) and city authority UAT readiness checkpoint. Team will also prepare sprint 4 (June 09–20) for citizen portal launch and public ESG data API.

---

## 13. Appendix: Reference Documents

- **Sprint 1 Close-out Report:** [docs/mvp3/reports/sprint1-closeout-po-report.md](../reports/sprint1-closeout-po-report.md)
- **Sprint 1 QA Assessment:** [docs/mvp3/qa/sprint1-qa-assessment-final.md](../qa/sprint1-qa-assessment-final.md)
- **Sprint 1 Risk Review:** [docs/mvp3/architecture/sprint1-risk-review.md](../architecture/sprint1-risk-review.md)
- **ADR-026 (ClickHouse Deduplication):** [docs/mvp3/architecture/adr-026-clickhouse-dedup.md](../architecture/adr-026-clickhouse-dedup.md) *(if exists)*
- **ADR-027 (Flink Enrichment):** [docs/mvp3/architecture/adr-027-flink-enrichment.md](../architecture/adr-027-flink-enrichment.md) *(if exists)*
- **Cutover Runbook (Template):** [docs/mvp3/project/analytics-service-cutover-runbook.md](./analytics-service-cutover-runbook.md) *(to be created Week 1)*
- **Load Test Scenarios:** [scripts/load-test.js](../../../scripts/load-test.js)
- **Regression Test Suite:** [tests/tier1-regression/](../../../tests/tier1-regression/)

---

## 14. Sign-Off & Approval

| Role | Name | Signature | Date |
|------|------|-----------|------|
| **Product Owner** | TBD | — | 2026-05-15 |
| **Tech Lead / CTO** | TBD | — | 2026-05-15 |
| **Project Manager** | UIP PM | ✓ APPROVED FOR EXECUTION | 2026-05-15 |
| **QA Lead** | TBD | — | 2026-05-15 |

---

**Document Version:** 1.0  
**Last Updated:** 2026-05-15 16:00 SGT  
**Next Review:** 2026-05-19 09:30 SGT (Sprint Kickoff)

---

*Sprint MVP3-2 is the critical bridge between MVP3-1 stabilization and MVP3-3 production cutover. Success criteria are non-negotiable: city authority ESG reporting deadline (2026-06-15) depends on MVP3-2 analytics foundation. All gate criteria must be satisfied to proceed to Sprint 3.*
