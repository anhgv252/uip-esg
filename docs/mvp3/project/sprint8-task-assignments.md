# Sprint 8 — Task Assignments (All Roles)

**Date:** 2026-06-03 | **Updated:** 2026-06-04 | **Sprint:** MVP3-8
**Based on:** [Sprint 8 Master Plan](sprint8-plan.md) + [User Stories](sprint8-stories.md) + [Architecture](sprint8-architecture.md)

> **Status update 2026-06-04:** Tất cả dev tasks DONE. SA Code Review APPROVED (post-fix).
> QA execution reports complete. **Chuyển sang Tester để manual verification.**
> Xem: [Tester Handoff →](../qa/sprint8-tester-handoff.md)

---

## Backend-1 — Task Assignment

**Total SP:** 7 SP | **Status: DEV DONE ✅**

| # | Task ID | Task | SP | Done |
|---|---------|------|-----|------|
| 1 | S8-C01 | Fix SLA-001: Flink Kafka listener config | 1 | ✅ DEV DONE |
| 2 | S8-C01-TEST | Verify VibrationAnomalyJob consumes from Kafka | — | ✅ DEV DONE |
| 3 | S8-OPS03 | Flink CI/CD: `flink-deploy.sh` + Makefile targets | 3 | ✅ DEV DONE |
| 4 | S8-OPS03-CFG | `@ConditionalOnProperty` CH cluster vs single-node | — | ✅ DEV DONE |
| 5 | S8-OPS06 | Avro Auto-registration: `register-avro-schemas.sh` | 3 | ✅ DEV DONE |
| 6 | S8-OPS06-TEST | Idempotent run twice → no error | — | ✅ DEV DONE |

**Commits:** `a6e16383` (S8-C01), `810aa591` (S8-OPS03), `76005b08` (S8-OPS06)

---

## Backend-2 — Task Assignment

**Total SP:** 5 SP | **Status: DEV DONE ✅**

| # | Task ID | Task | SP | Done |
|---|---------|------|-----|------|
| 1 | S8-OPS01-SUPPORT | Validate CH migration — data integrity check | — | ✅ DEV DONE |
| 2 | S8-OPS02-SUPPORT | Validate Kafka topic rebalancing | — | ✅ DEV DONE |
| 3 | S8-QA03 | BMS Hardware Simulator: j2mod slave + 12 IT tests | 5 | ✅ DEV DONE |
| 4 | S8-QA03-MODBUS | Modbus simulator → BMS → Kafka verified | — | ✅ DEV DONE |
| 5 | S8-QA03-BACNET | BACnet adapter tests (via unit tests) | — | ✅ DEV DONE |
| 6 | S8-QA03-CB | Circuit breaker fault scenario tested | — | ✅ DEV DONE |

**Commits:** `233dd644` (S8-QA03)

---

## Frontend — Task Assignment

**Total SP:** 13 SP | **Status: DEV DONE ✅**

| # | Task ID | Task | SP | Done |
|---|---------|------|-----|------|
| 1 | S8-M01-HOOKS | Shared hooks: `useDashboard`, `useAlerts`, `useSensors` | 2 | ✅ DEV DONE |
| 2 | S8-M01-TABS | Bottom tab navigation (4 tabs) | 1 | ✅ DEV DONE |
| 3 | S8-M01-KPI | 4 KPI cards (Energy, Safety, AQI, Alerts) | 3 | ✅ DEV DONE |
| 4 | S8-M01-DETAIL | Card → detail screen navigation | 1 | ✅ DEV DONE |
| 5 | S8-M01-PULL | Pull-to-refresh + auto-refresh 30s | 1 | ✅ DEV DONE |
| 6 | S8-M02-ALERTS | Alert list: severity sort + filter chips | 3 | ✅ DEV DONE |
| 7 | S8-M02-SAFETY | Safety score gauge (0-100, color zones) | 1 | ✅ DEV DONE |
| 8 | S8-M02-PUSH | Push deep-link: `uipmobile://alerts/{id}` | 1 | ✅ DEV DONE |

**SA fix applied:** `GOOD` → `SAFE` enum alignment (commit `46859cb3`)
**Commits:** `d3e7f0e3` (S8-M01), `29c755a7` (S8-M02), `46859cb3` (SA fixes)

---

## DevOps — Task Assignment

**Total SP:** 18 SP | **Status: DEV DONE ✅**

| # | Task ID | Task | SP | Done |
|---|---------|------|-----|------|
| 1 | S8-OPS01-KEEPER | ClickHouse Keeper deployed + keeper-config.xml fix | 2 | ✅ DEV DONE |
| 2 | S8-OPS01-NODES | ClickHouse 2 nodes: ReplicatedReplacingMergeTree | 4 | ✅ DEV DONE |
| 3 | S8-OPS01-MIGRATE | CH data migration + BACKWARD compat | 2 | ✅ DEV DONE |
| 4 | S8-OPS01-TEST | CH HA validation: failover + replication tests | — | ✅ DEV DONE |
| 5 | S8-OPS02-KRAFT | Kafka 3-broker KRaft: kafka-1/2/3 quorum | 3 | ✅ DEV DONE |
| 6 | S8-OPS02-REBALANCE | Kafka partition rebalancing + RF=3 verified | 2 | ✅ DEV DONE |
| 7 | S8-OPS02-TEST | Kafka HA: broker failover + rolling restart | — | ✅ DEV DONE |
| 8 | S8-OPS04 | PG Streaming Replication + replicator role fix | 3 | ✅ DEV DONE |
| 9 | S8-OPS05 | Keycloak Pilot Realm + uip-mobile PKCE client | 2 | ✅ DEV DONE |

**SA fixes applied:** keeper `<server_id>`, replicator role, kafka-rebalance parsing (commit `46859cb3`)
**Commits:** `ea8a8cc4` (S8-OPS01), `33196902` (S8-OPS02), `fc178b36` (S8-OPS04), `d426c078` (S8-OPS05)

---

## QA — Task Assignment

**Total SP:** 7 SP | **Status: QA DONE ✅**

| # | Task ID | Task | SP | Done |
|---|---------|------|-----|------|
| 1 | S8-QA-ENV | Staging environment prep + data seeding | — | ✅ QA DONE |
| 2 | S8-QA-CHAOS | Infrastructure chaos testing (CHA + KFT + PGR) | — | ✅ QA DONE |
| 3 | S8-QA-FCD | Flink CI/CD validation | — | ✅ QA DONE |
| 4 | S8-QA01 | Pilot regression 285/285 TCs PASS | 5 | ✅ QA DONE |
| 5 | S8-QA02 | k6 SLA gate: 500VU/200VU 30 min — 5/5 thresholds | 2 | ✅ QA DONE |
| 6 | S8-QA-REPORT | Test execution reports compiled | — | ✅ QA DONE |

**Reports:**
- [`sprint8-regression-report.md`](../qa/sprint8-regression-report.md) — 285/285 PASS
- [`sprint8-k6-report.md`](../qa/sprint8-k6-report.md) — all SLA gates PASS
- [`sprint8-bms-simulator-report.md`](../qa/sprint8-bms-simulator-report.md) — 12/12 IT PASS

---

## SA — Task Assignment

**Status: SA DONE ✅**

| # | Task | Done |
|---|------|------|
| 1 | Review ADR-036 (ClickHouse HA) | ✅ APPROVED |
| 2 | Review ADR-037 (Kafka 3-broker KRaft) | ✅ APPROVED |
| 3 | Review ADR-038 (Flink CI/CD) | ✅ APPROVED |
| 4 | Review mobile shared hooks architecture | ✅ APPROVED |
| 5 | **SA Code Review** — 2 CRITICAL + 3 MAJOR fixed | ✅ G12 PASS |

**Report:** [`sprint8-code-review.md`](../reports/sprint8-code-review.md)

---

## Tester — Task Assignment

**Status: 🔄 IN PROGRESS — ASSIGNED TO TESTER**

> Xem full handoff tại: [sprint8-tester-handoff.md](../qa/sprint8-tester-handoff.md)

| # | Task ID | Task | TC Count | Status |
|---|---------|------|----------|--------|
| 1 | S8-TEST-PREP | Verify staging env + mobile device setup | — | 🔄 TODO |
| 2 | S8-TEST-MOBILE | Mobile testing: Dashboard + Alerts + Safety | 21 TCs | 🔄 TODO |
| 3 | S8-TEST-INFRA | Infrastructure manual tests: CH + Kafka + PG | 20 TCs | 🔄 TODO |
| 4 | S8-TEST-FLINK | Flink CI/CD manual verification | 6 TCs | 🔄 TODO |
| 5 | S8-TEST-KEYCLOAK | Keycloak pilot realm: 3 users login | 5 TCs | 🔄 TODO |
| 6 | S8-TEST-SMOKE | Post-deploy smoke test (10 endpoints) | 10 TCs | 🔄 TODO |
| 7 | S8-TEST-REPORT | Manual test execution report | — | 🔄 TODO |

**Deliverables expected:**
- Screenshots: Dashboard + Alerts iOS + Android
- `docs/mvp3/qa/sprint8-manual-test-execution-report.md`
- Bug reports (nếu có P0/P1)
- Sign-off cho Gate Review 2026-06-17 15:00 SGT

---

## Summary — Sprint 8 Status

| Role | SP | Status |
|------|-----|--------|
| Backend-1 | 7 SP | ✅ DEV DONE |
| Backend-2 | 5 SP | ✅ DEV DONE |
| Frontend | 13 SP | ✅ DEV DONE |
| DevOps | 18 SP | ✅ DEV DONE |
| QA | 7 SP | ✅ QA DONE |
| SA | — | ✅ APPROVED |
| **Tester** | **~5 SP** | **🔄 IN PROGRESS** |

**Gate status:** 12/12 Hard Gates PASS | 4/4 Soft Gates PASS
**Remaining:** Tester manual verification → Gate Review 2026-06-17

---

*Document: Sprint 8 Task Assignments v2.0 | Updated 2026-06-04*
