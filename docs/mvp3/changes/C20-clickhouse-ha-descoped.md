# Change Order C20 — ClickHouse HA Descoped to Sprint 4

**Change Order ID:** C20  
**Date:** 2026-05-24  
**Sprint Affected:** MVP3-3 (Sprint 3)  
**Author:** UIP PM  
**Approved By:** PO (anhgv) — verbal approval 2026-05-22  
**Status:** APPROVED — Formal write-up (per retro action A4)

---

## 1. Summary

Stories **S3-09** (ClickHouse 2-node HA cluster) and **S3-10** (ClickHouse HA failover test) are **descoped from Sprint 3** and moved to Sprint 4 as carry-over with P0 priority.

---

## 2. Affected Stories

| Story ID | Title | SP | Original Priority |
|---|---|---|---|
| S3-09 | ClickHouse 2-node HA cluster | 8 | P1 |
| S3-10 | ClickHouse HA failover test | 3 | P1 |
| **Total** | | **11 SP** | |

---

## 3. Reason for Descope

| Reason | Detail |
|---|---|
| **DevOps capacity** | Sprint 3 DevOps focus was Keycloak RSA migration (S3-07 — P0) + Kong analytics cutover (S3-16). Parallel ClickHouse HA setup would have caused context switching on critical auth work. |
| **Non-critical for City Authority deadline** | GRI 302/305 export (AC-01) and Keycloak RSA (AC-02) are the deliverables required for City Authority deadline 2026-06-15. ClickHouse HA improves resilience but does not block reporting. |
| **Single-node stable** | Current ClickHouse single-node instance has zero downtime incidents in Sprint 2 and Sprint 3. Risk of deferring is LOW for the sprint period. |
| **No PO objection** | PO confirmed defer decision on 2026-05-22 during end-of-week-1 retro. |

---

## 4. Impact Assessment

| Area | Impact | Mitigation |
|---|---|---|
| City Authority ESG deadline (2026-06-15) | **NONE** — GRI export works on single-node ClickHouse | No action needed |
| Analytics availability | LOW risk — single-node stable, no incidents last 2 sprints | Sprint 4 HA delivery adds redundancy |
| Sprint 3 AC satisfaction | AC-03 moved to N/A (not FAIL) per PO approval | Reflected in Gate Review verdict |
| Sprint 4 scope | +11 SP carry-over → Sprint 4 must account for this | Sprint 4 backlog updated |

---

## 5. Sprint 4 Action Required

| Action | Owner | Priority | Target |
|---|---|---|---|
| Add S3-09 (ClickHouse 2-node HA) to Sprint 4 backlog | PM | **P0** | Sprint 4 Week 1 |
| Add S3-10 (ClickHouse HA failover test) to Sprint 4 backlog | PM | **P0** | Sprint 4 Week 1 |
| DevOps plan: ClickHouse cluster setup + ZooKeeper config | DevOps | P0 | Sprint 4 Day 1 |
| QA: Write failover test scenarios (kill node 1, verify analytics) | QA | P0 | Sprint 4 Week 1 |

---

## 6. Approval Record

| Role | Name | Decision | Date |
|---|---|---|---|
| Product Owner | anhgv | ✅ APPROVED (verbal) | 2026-05-22 |
| Tech Lead | — | ✅ Acknowledged | 2026-05-22 |
| PM (formal doc) | UIP PM | ✅ Documented | 2026-05-24 |

---

## 7. Reference

- Sprint 3 retro: [sprint3-summary-retro.md](../project/sprint3-summary-retro.md) — Section 7, Carry-forward
- Sprint 3 readiness assessment: [sprint3-readiness-assessment-2026-05-24.md](../reports/sprint3-readiness-assessment-2026-05-24.md) — Section 4
- Original stories: [sprint3-task-assignments.md](../project/sprint3-task-assignments.md) — S3-09, S3-10

---

*Document created: 2026-05-24 (per retro action A4 — deadline Mon 2026-05-25)*  
*Change Order type: Scope Reduction — Defer to Next Sprint*
