# Sprint MVP3-4 — Planning Document

**Status:** DRAFT — Pending Gate Review Sprint 3 (2026-05-30)  
**Document Date:** 2026-05-24  
**Sprint Start:** 2026-06-02 (Mon)  
**Sprint End:** 2026-06-13 (Fri EOD)  
**Gate Review:** 2026-06-13 15:00 SGT  
**Sprint trước:** MVP3-3 (CONDITIONAL PASS → Gate 2026-05-30)  
**PO:** anhgv

---

## 1. Sprint Overview

| Dimension | Value |
|---|---|
| **Sprint Name** | MVP3-4: ClickHouse HA + Test Isolation + CI Hardening |
| **Sprint Number** | 4 of MVP3 |
| **Duration** | 10 calendar days (2 weeks) |
| **Team Size** | 5 FTE (Backend 2, Frontend 1, QA 1, DevOps 1) |
| **City Authority Deadline** | **2026-06-15 NON-NEGOTIABLE** — GRI 302/305 must be production-ready |
| **Sprint End Buffer** | 2 days before deadline (June 13 → June 15) |

---

## 2. Sprint Goal

**Gate-Based SMART Goal:**  
Team will achieve HARD PASS by 2026-06-13 15:00 SGT by delivering ClickHouse 2-node HA (S3-09/S3-10 carry-over), full integration test isolation with CI `@Tag("integration")` profile, and clean Gradle test suite (112/112 PASS, 0 infra-dep failures in PR build) — ensuring City Authority ESG delivery readiness by 2026-06-15 deadline.

---

## 3. Capacity Planning

| Role | % Sprint | Capacity (SP) |
|---|---|---|
| Backend Lead | 100% | 13 SP |
| Backend Dev | 100% | 11 SP |
| Frontend Dev | 80% | 10 SP |
| QA Lead | 100% | 12 SP |
| DevOps Eng | 100% | 13 SP |
| **Total** | | **59 SP** |
| **Buffer (20%)** | | **−12 SP** |
| **Net Available** | | **47 SP** |

---

## 4. Sprint Backlog

### 4.1 Carry-Over from Sprint 3 (C20 — MANDATORY)

| Story ID | Title | SP | Owner | Priority | Source |
|---|---|---|---|---|---|
| **S4-01** | ClickHouse 2-node HA cluster (ReplicatedMergeTree + Keeper) | 8 | DevOps | **P0** | C20 deferred |
| **S4-02** | ClickHouse HA failover test (kill node-1, verify analytics) | 3 | DevOps + QA | **P0** | C20 deferred |
| **S4-03** | Integration test isolation: `@Tag("integration")` profile + CI unit-only build | 3 | Backend Lead | **P0** | Retro A1 |
| **S4-04** | Fix + re-tag 15 infra-dep. IT tests (mock or `@DisabledIf`) | 3 | Backend Eng | **P0** | Retro A2 |
| **S4-05** | JaCoCo clean run → confirm branch coverage ≥65% | 1 | Backend Lead | P1 | Retro A3/A8 |
| **S4-06** | Caffeine cache eviction report (Kafka listener + cache stats endpoint) | 2 | Backend Eng | P1 | GAP-4 |
| **S4-07** | HPA config: analytics-service (v3-EXT-05) | 2 | DevOps | P1 | GAP-1 |
| **S4-08** | ISO 37120 waterIntensityM3PerPerson metric | 1 | Backend Eng | P2 | GAP-3 |
| **S4-09** | OpenAPI spec update (S3-01 DoD) | 0.5 | Backend Eng | P3 | Minor DoD |

**Carry-over total: ~23.5 SP**

### 4.2 New Stories (Sprint 4)

| Story ID | Title | SP | Owner | Priority | Notes |
|---|---|---|---|---|---|
| **S4-10** | iot-ingestion-service shadow mode (v3-EXT-06) | 5 | Backend Eng 2 | P1 | Detail plan Sprint 4 |
| **S4-11** | Prometheus + Grafana dashboard: Kong + analytics-service SLIs | 3 | DevOps | P1 | Observability gap |
| **S4-12** | G11 clarification: GRI generation SLA (5s query vs 30s full) | 1 | Backend Lead + PM | P1 | Amber gate from Sprint 3 |

**New stories total: ~9 SP**

### 4.3 Sprint 4 Total Committed

| Category | SP |
|---|---|
| Carry-over P0 (mandatory) | 14 SP |
| Carry-over P1/P2/P3 | ~9.5 SP |
| New stories | 9 SP |
| **Total Committed** | **~32.5 SP** |
| **Buffer remaining** | ~14.5 SP (stretch: Predictive AI spike if time permits) |

---

## 5. Acceptance Criteria

### AC-01 (P0): ClickHouse 2-Node HA Live
> DevOps có thể kill 1 ClickHouse node, analytics dashboard vẫn phục vụ queries, không có data loss.

**PASS criteria:**
- [ ] 2 ClickHouse nodes UP + Keeper healthy trong `docker compose ps`
- [ ] ReplicatedReplacingMergeTree schema deployed ON CLUSTER
- [ ] Data count match giữa node-1 và node-2 sau replication
- [ ] Kill node-1 → analytics queries vẫn trả data từ node-2 (< 5s failover)
- [ ] Named volumes configured (`ch-node1-data`, `ch-node2-data`, `keeper-data`)

### AC-02 (P0): Clean CI Test Suite
> PR build chỉ chạy unit tests. Integration tests chạy riêng (manual / nightly). 0 infra-dep. failures trong `./gradlew unitTest`.

**PASS criteria:**
- [ ] `@Tag("integration")` trên tất cả IT / `*IntegrationTest` classes
- [ ] Gradle task `unitTest` exclude `@Tag("integration")` — 0 failures
- [ ] Gradle task `integrationTest` run IT suite với docker infra
- [ ] 15 previously-failing tests: fixed (mock/disabled) hoặc re-tagged
- [ ] JaCoCo branch coverage ≥65% sau clean `unitTest` run

### AC-03 (P1): Observability Baseline
> Kong + analytics-service SLIs visible trong Grafana (p95 latency, error rate, throughput).

**PASS criteria:**
- [ ] Prometheus scraping Kong metrics (prometheus plugin active)
- [ ] Grafana dashboard: request rate, p95 latency, error rate cho analytics-service
- [ ] HPA analytics-service configured (min 2 / max 6, CPU 70%)

---

## 6. Definition of Done (Sprint 4)

- [ ] All P0 stories DONE (AC-01 + AC-02)
- [ ] CI pipeline: `unitTest` = 0 failures in PR build
- [ ] ClickHouse HA: failover test PASS in QA environment
- [ ] No new P0/P1 bugs introduced
- [ ] SA Code Review checklist complete (per CLAUDE.md)
- [ ] Gate Review demo ready by 2026-06-12 17:00 (day before gate)

---

## 7. Week-by-Week Plan

### Week 1 (2026-06-02 → 2026-06-06)
**Theme:** ClickHouse HA Deploy + Test Isolation Foundation

| Day | Milestone | Owner |
|---|---|---|
| Mon 06-02 | Sprint kickoff. DevOps: ClickHouse cluster design + docker-compose. Backend Lead: `@Tag("integration")` PR | DevOps + Backend |
| Tue 06-03 | ClickHouse Keeper + node-1 up. Backend: 15 test fixes batch 1 | DevOps + Backend |
| Wed 06-04 | ClickHouse node-2 join cluster. Replication verify. Unit test suite clean run | DevOps + Backend |
| Thu 06-05 | Schema migration: ReplicatedReplacingMergeTree ON CLUSTER. Analytics service multi-host URL | DevOps + Backend |
| Fri 06-06 | **Week 1 checkpoint:** ClickHouse 2-node UP ✓, `unitTest` 0 failures ✓ | All |

### Week 2 (2026-06-09 → 2026-06-13)
**Theme:** Failover Test + Observability + City Authority Readiness

| Day | Milestone | Owner |
|---|---|---|
| Mon 06-09 | ClickHouse failover test (S4-02). HPA config (S4-07). JaCoCo confirm | QA + DevOps + Backend |
| Tue 06-10 | Prometheus/Grafana dashboard (S4-11). G11 SLA clarification (S4-12) | DevOps + PM |
| Wed 06-11 | Integration regression: `integrationTest` full suite. Bug triage | QA + All |
| Thu 06-12 | Demo dry-run. SA code review. Remaining P2/P3 items | All |
| Fri 06-13 | **Gate Review 15:00 SGT** — PO Demo Live | All |

---

## 8. Risks

| ID | Risk | Severity | Probability | Mitigation |
|---|---|---|---|---|
| R1 | ClickHouse Keeper setup complex → slips Week 1 | HIGH | 40% | Start Mon morning; DevOps Day 1 full focus. Use ClickHouse Keeper (built-in, simpler than ZooKeeper) |
| R2 | 15 test fixes take longer than 3 SP | MED | 30% | Tag-and-skip first; fix properly in Week 2. Unit tests must be clean by Wed 06-04 |
| R3 | City Authority deadline (06-15) buffer only 2 days | HIGH | 20% | Gate is Fri 06-13. Zero scope creep in Week 2. AC-01 + AC-02 absolute priority |
| R4 | GRI generation p95 >5s (G11 amber) | MED | 50% | S4-12 clarification with PO/City Authority — likely "5s = query only" |

---

## 9. City Authority Deadline Checklist

> **Deadline: 2026-06-15 — NON-NEGOTIABLE**

Pre-conditions for City Authority delivery (all must be ✅ by 2026-06-13 Gate):

- [x] GRI 302/305 export API live (AC-01 Sprint 3 ✅)
- [x] Keycloak RSA auth (AC-02 Sprint 3 ✅)
- [ ] ClickHouse HA — analytics không bị downtime khi 1 node fail (Sprint 4 P0)
- [ ] 112/112 regression PASS (Sprint 4 P0 — clean test suite)
- [ ] Staging deployment verified (DevOps smoke test)
- [ ] City Authority demo rehearsal (≥1 dry-run trước deadline)

---

## 10. Carry-Over Summary (C20 Reference)

> See [Change Order C20](../changes/C20-clickhouse-ha-descoped.md) for full context.

**S3-09/S3-10 ClickHouse HA descoped per PO approval 2026-05-22.** These are Sprint 4 mandatory P0 items — City Authority resilience requirement. Single-node ClickHouse has been stable but HA is required for production confidence before 2026-06-15 delivery.

---

*Document created: 2026-05-24 | Owner: UIP PM*  
*Status: DRAFT — pending official Sprint 3 Gate Review (2026-05-30) for formal Sprint 4 start*
