# Sprint MVP3-8 — Master Plan

**Status:** DRAFT — PO Brainstorming 2026-06-03
**Document Date:** 2026-06-03
**Sprint Start:** 2026-06-04 (Wed)
**Sprint End:** 2026-06-17 (Tue EOD)
**Gate Review:** 2026-06-17 15:00 SGT
**Sprint trước:** MVP3-7 — GATE PASS (1,178 tests, 86% coverage, 4.73/5.0 EA score, 0 P0 bugs)
**PO:** anhgv

---

## Context

Sprint 7 hoàn thành Building Safety + Avro + Pilot Readiness (38/38 DEV DONE, 1,178 tests, 4.73/5.0 EA score). Enterprise Architecture Assessment khuyến nghị 2 P0 items: **Infrastructure HA** + **Mobile App investment**.

**PO quyết định (brainstorming 2026-06-03):**
- **Sprint 8 focus:** Hybrid — Pilot Preparation + Mobile App Foundation + Infrastructure HA
- **Hướng D:** Kết hợp EA Assessment khuyến nghị với pilot readiness
- **Over-commit:** 1.06× capacity (50 SP vs 47 SP) — acceptable theo Tier system

---

## 1. Sprint Overview

| Dimension | Value |
|---|---|
| **Sprint Name** | MVP3-8: Pilot Prep + Mobile + Infrastructure HA |
| **Duration** | 2026-06-04 (Wed) → 2026-06-17 (Tue) — 10 calendar days |
| **Team** | 5 FTE (Backend 2, Frontend 1, QA 1, DevOps 1) + SA spike |
| **Net Capacity** | ~47 SP |
| **Committed** | ~50 SP (Tier 1: 35 SP + Tier 2: 15 SP) |
| **Over-commit** | +3 SP (6%) — manageable với Tier triage |

---

## 2. Sprint Goal (SMART)

Team sẽ đạt **HARD PASS** by 2026-06-17 15:00 SGT bằng cách:

1. **SLA-001 Fix** — Flink Kafka listener config corrected, VibrationAnomalyJob consumes from Kafka
2. **Mobile Dashboard** — React Native dashboard với 4 KPI cards + bottom tab navigation + shared hooks reuse
3. **Mobile Alerts + Safety** — Alert list, safety score gauge, push notification deep-link
4. **ClickHouse 2-node HA** — ReplicatedMergeTree + Keeper, automatic failover, BACKWARD compat
5. **Kafka 3-broker KRaft** — Scale lên 3 brokers, replication.factor=3, zero-downtime migration
6. **Flink CI/CD** — Automated job submission (build → savepoint → cancel → submit)
7. **Pilot Regression** — 243 TCs executed on staging, ALL SLA gates verified
8. **Tier 2:** PG replication, Keycloak pilot realm, k6 load test, BMS simulator, Avro auto-registration

---

## 3. Backlog Committed

### Tier 1 — PHẢI DONE (35 SP)

#### Epic 0: Carry-over P0 Fix [1 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S8-C01 | Fix SLA-001: Flink Kafka listener config — `bootstrap.servers` không match Docker Compose service name | 1 | Backend-1 | P0 | VibrationAnomalyJob consumes from Kafka, alert <15s |

**Deadline:** 2026-06-04 (Day 1)

#### Epic 1: Mobile App Foundation [13 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S8-M01 | Mobile Dashboard — KPI cards (Energy, Safety, AQI, Alerts), shared hooks reuse, bottom tab navigation | 8 | Frontend | P0 | Dashboard renders with real API, 4 KPI cards, bottom tabs, shared hooks |
| S8-M02 | Mobile Alerts + Safety — Alert list with pull-to-refresh, severity filter, safety score gauge, push deep-link | 5 | Frontend | P0 | Alerts sorted by severity, safety score 0-100, push → alert detail |

#### Epic 2: Infrastructure HA [13 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S8-OPS01 | ClickHouse 2-node HA — ReplicatedMergeTree + Keeper + BACKWARD compat | 8 | DevOps | P0 | Node-1 down → queries route node-2, replication <5s, Tier 1 single-node unchanged |
| S8-OPS02 | Kafka 3-broker KRaft — Scale + replication.factor=3 + rolling migration | 5 | DevOps | P0 | Broker down → no interruption, min.insync.replicas=2 |

#### Epic 3: Automation + Pilot Prep [8 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S8-OPS03 | Flink job CI/CD — Automated submission (savepoint → cancel → submit) | 3 | Backend-1 + DevOps | P0 | CI pipeline: JAR build → Flink submit, Makefile targets |
| S8-QA01 | Pilot regression trên staging — 243 TCs + ALL SLA gates | 5 | QA + Tester | P0 | 243/243 PASS on staging, SLA report attached |

### Tier 2 — BEST EFFORT (15 SP)

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S8-OPS04 | PG Streaming Replication — primary + hot standby | 3 | DevOps | P1 | Replication lag <1s, failover <30s |
| S8-OPS05 | Keycloak Pilot Realm — import + 3 users (admin/operator/viewer) | 2 | DevOps | P1 | 3 users login successfully |
| S8-QA02 | Full k6 Load Test — 500 VU / 200 VU sustained 30 phút | 2 | QA | P1 | All SLA thresholds met |
| S8-QA03 | BMS Hardware Simulator — Modbus/BACnet protocol testing | 5 | QA + Backend-2 | P1 | Simulator → BMS → Kafka → Flink verified |
| S8-OPS06 | Avro Auto-registration — Bootstrap script cho Apicurio | 3 | Backend-1 | P1 | 4 schemas registered on deploy, idempotent |

### Tier 3 — DESCOPE

| ID | Story | SP | Priority |
|---|---|---|---|
| S8-SA01 | SA minor findings consolidated (8 items) | 5 | P2 |
| S8-OPS07 | Cache warming strategy | 2 | P2 |
| S8-OPS08 | Open CVE network mitigation | 3 | P2 |

---

## 4. Milestones

| Date | Milestone | Gate |
|------|-----------|------|
| **2026-06-04 (Wed)** | Sprint 8 Kickoff — SLA-001 fix bắt đầu | |
| **2026-06-05 (Thu)** | SLA-001 DONE — Flink consumes from Kafka | GATE-0 |
| **2026-06-06 (Fri)** | ClickHouse HA deployed + Kafka 3-broker running | |
| **2026-06-08 (Sun)** | CH HA failover test PASS + Kafka rolling migration done | GATE-1 |
| **2026-06-09 (Mon)** | Mobile Dashboard first draft + Flink CI/CD pipeline | |
| **2026-06-11 (Wed)** | Mobile Dashboard + Alerts DONE — Tier 1 Mobile complete | GATE-2 |
| **2026-06-12 (Thu)** | Pilot regression run bắt đầu (243 TCs) + k6 load test | |
| **2026-06-14 (Sat)** | Pilot regression PASS + SLA gate verified | GATE-3 |
| **2026-06-16 (Mon)** | SA Code Review + Tier 2 wrap-up | |
| **2026-06-17 (Tue)** | Sprint 8 Close — Gate Review 15:00 SGT | **FINAL GATE** |

---

## 5. Team Assignments

### Backend-1 (1 SP Tier 1 carry-over + 3 SP Tier 1 automation + 3 SP Tier 2)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1 | S8-C01: Fix SLA-001 Flink Kafka config | 1 |
| Day 2-4 | S8-OPS03: Flink job CI/CD pipeline + Makefile targets | 3 |
| Day 5-7 | S8-OPS06: Avro auto-registration script | 3 |
| Day 8-10 | Buffer / Tier 2 support / integration testing | — |

### Backend-2 (0 SP Tier 1 + 5 SP Tier 2)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1-3 | Support DevOps: ClickHouse migration validation + Kafka topic rebalancing | — |
| Day 4-7 | S8-QA03: BMS Hardware Simulator — Modbus/BACnet testing | 5 |
| Day 8-10 | Buffer / BMS ITs supplement / regression support | — |

### Frontend (13 SP Tier 1 Mobile)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1-4 | S8-M01: Mobile Dashboard — KPI cards + shared hooks + bottom tabs | 8 |
| Day 5-7 | S8-M02: Mobile Alerts + Safety Score + push deep-link | 5 |
| Day 8-10 | Polish + responsive testing + mobile E2E | — |

### DevOps (13 SP Tier 1 Infra + 5 SP Tier 2)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1-4 | S8-OPS01: ClickHouse 2-node HA + Keeper + migration | 8 |
| Day 4-6 | S8-OPS02: Kafka 3-broker KRaft + rolling migration | 5 |
| Day 7-8 | S8-OPS04: PG Streaming Replication | 3 |
| Day 8-9 | S8-OPS05: Keycloak Pilot Realm import | 2 |
| Day 9-10 | S8-OPS03 support: Flink CI/CD integration testing | — |

### QA + Tester (5 SP Tier 1 + 7 SP Tier 2)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1-3 | Prepare staging environment + test data seeding | — |
| Day 6-8 | S8-QA01: Pilot regression 243 TCs on staging | 5 |
| Day 8-9 | S8-QA02: Full k6 load test (500 VU / 200 VU) | 2 |
| Day 9-10 | S8-QA03: BMS hardware simulator (pair Backend-2) | 5 |

### SA (spike support)

| Sprint Day | Task | SP |
|-----------|------|-----|
| Day 1-2 | Review ADR-036 (CH HA) + ADR-037 (Kafka KRaft) | — |
| Day 4-5 | Review ADR-038 (Flink CI/CD) + Mobile arch review | — |
| Day 9-10 | SA Code Review (mandatory per CLAUDE.md) | — |

---

## 6. Dependencies

```
S8-C01 (SLA-001 fix) ────────→ unblocks QA regression (Day 1)
S8-OPS01 (CH HA) ─────────────→ S8-QA01 (staging needs HA)
S8-OPS02 (Kafka 3-broker) ────→ S8-QA01 (staging needs 3 brokers)
S8-OPS03 (Flink CI/CD) ───────→ S8-OPS01 (submit after CH HA ready)
S8-M01 (Mobile Dashboard) ────→ S8-M02 (Mobile Alerts builds on Dashboard)
S8-OPS05 (Keycloak realm) ────→ S8-QA01 (pilot users for regression)
S8-QA01 (Regression) ─────────→ FINAL GATE (2026-06-17)
```

**Critical Path:**
```
Day 1: SLA-001 fix (Backend-1)
Day 1-4: CH HA (DevOps) ──────→ Day 6-8: QA Regression
Day 1-6: Kafka 3-broker (DevOps) → Day 6-8: QA Regression
Day 1-7: Mobile (Frontend) ───→ Day 8-10: Mobile QA
```

---

## 7. Risk Register

| ID | Risk | Probability | Impact | Owner | Mitigation |
|----|------|------------|--------|-------|------------|
| R-01 | ClickHouse migration mất data khi chuyển từ MergeTree sang ReplicatedMergeTree | MEDIUM (30%) | HIGH | DevOps | Backup trước migration, test trên non-prod trước, rollback plan |
| R-02 | Kafka rolling restart gây consumer lag spike | MEDIUM (40%) | MEDIUM | DevOps | Rolling restart 1 broker tại thời điểm, wait rebalance xong rồi mới restart broker tiếp |
| R-03 | Mobile shared hooks không compatible với React Native | LOW (20%) | HIGH | Frontend + SA | SA review Day 1, test hooks trong RN early |
| R-04 | Frontend bottleneck — Mobile 13 SP cho 1 dev trong 7 ngày | MEDIUM (35%) | MEDIUM | PM | Prioritize Dashboard > Alerts. Cut push deep-link nếu cần |
| R-05 | Over-commit 1.06× — Tier 2 có thể miss | LOW (15%) | LOW | PM | Tier system nghiêm ngặt. Tier 1 = 35 SP < capacity |
| R-06 | BMS simulator không represent real hardware behavior | MEDIUM (40%) | LOW | QA | Document limitations, plan hardware-in-the-loop test Sprint 9 |
| R-07 | ClickHouse Keeper memory usage trên môi trường nhỏ | LOW (15%) | MEDIUM | DevOps | Monitor Keeper memory, document resource requirements |
| R-08 | Kafka partition rebalance trigger consumer restart → data gap | LOW (20%) | MEDIUM | DevOps | Flink checkpoint restores from last offset |

---

## 8. Quality Gates

### Hard Gates (12) — ALL MUST PASS

| Gate | Criterion | Verifier | Status |
|------|-----------|----------|--------|
| G1 | SLA-001 Fix: VibrationAnomalyJob consumes from Kafka, alert <15s | Unit + IT tests | ⬜ |
| G2 | Mobile Dashboard: 4 KPI cards render with real API, bottom tabs work | Manual + screenshot | ⬜ |
| G3 | Mobile Alerts: Alert list sorted by severity, safety score 0-100 | Manual + screenshot | ⬜ |
| G4 | ClickHouse 2-node: node-1 down → queries route to node-2 | Chaos test | ⬜ |
| G5 | ClickHouse replication: data inserted node-1 appears node-2 within 5s | Integration test | ⬜ |
| G6 | Kafka 3-broker: broker down → no interruption | Chaos test | ⬜ |
| G7 | Kafka replication: min.insync.replicas=2 verified | Config audit | ⬜ |
| G8 | Flink CI/CD: `make flink-submit` submits job automatically | CI pipeline | ⬜ |
| G9 | Pilot Regression: 243/243 TCs PASS on staging | QA report | ⬜ |
| G10 | SLA Gate: all performance thresholds met | k6 report | ⬜ |
| G11 | Tests: 1,200+ total, 0 failures | CI | ⬜ |
| G12 | SA Code Review: APPROVED | SA | ⬜ |

### Soft Gates (5) — Best Effort

| Gate | Criterion | Status |
|------|-----------|--------|
| GS1 | PG streaming replication: lag <1s | ⬜ |
| GS2 | Keycloak pilot realm: 3 users login | ⬜ |
| GS3 | BMS simulator: end-to-end data flow verified | ⬜ |
| GS4 | Avro auto-registration: 4 schemas on deploy | ⬜ |
| GS5 | Mobile push deep-link: tap notification → alert detail | ⬜ |

---

## 9. Cut Order (nếu chậm tiến độ)

```
1. S8-OPS06  Avro Auto-registration        (3 SP) — Tier 2 first
2. S8-QA03   BMS Hardware Simulator         (5 SP) — Tier 2
3. S8-OPS04  PG Streaming Replication       (3 SP) — Tier 2
4. S8-M02    Push deep-link                 (1 SP) — partial cut, keep alert list
5. S8-QA02   Full k6 Load Test              (2 SP) — Tier 2
```

**Mục tiêu tối thiểu:** Tier 1 (35 SP) = pilot viable + EA P0 addressed.

---

## 10. ADR Register (Sprint 8)

| ADR | Title | Owner | Status |
|-----|-------|-------|--------|
| ADR-036 | ClickHouse 2-node HA — ReplicatedMergeTree + Keeper | SA + DevOps | Draft |
| ADR-037 | Kafka 3-broker KRaft — Quorum Replication | SA + DevOps | Draft |
| ADR-038 | Flink Job CI/CD — Automated Submission Pipeline | SA + Backend | Draft |

---

## 11. Infrastructure Migration Strategy

### ClickHouse: Single-node → 2-node Cluster

```
Phase 1 (Day 1): Deploy clickhouse-keeper + clickhouse-02
Phase 2 (Day 2): Create ReplicatedMergeTree tables on cluster
Phase 3 (Day 3): Migrate data: INSERT INTO replicated FROM old table
Phase 4 (Day 3): Update application config to use cluster endpoint
Phase 5 (Day 4): Test failover + remove old single-node config
```

### Kafka: 1-broker → 3-broker KRaft

```
Phase 1 (Day 1): Update docker-compose: add kafka-2, kafka-3 with KRaft config
Phase 2 (Day 2): Rolling restart: kafka-1 gets new KRaft config
Phase 3 (Day 3): Start kafka-2, kafka-3 → quorum formed
Phase 4 (Day 3): Reassign partitions: kafka-reassign-partitions --replication-factor 3
Phase 5 (Day 4): Verify replication, test broker down scenario
```

### BACKWARD Compatibility

```yaml
# Tier 1 staging (single-node):
CLICKHOUSE_CLUSTER_ENABLED: "false"
KAFKA_BROKER_COUNT: "1"

# Tier 2 pilot (HA):
CLICKHOUSE_CLUSTER_ENABLED: "true"
KAFKA_BROKER_COUNT: "3"
```

---

## 12. Kafka Topic Registry Update

| Topic | Change | Impact |
|-------|--------|--------|
| `UIP.iot.sensor.reading.v1` | replication.factor 1→3 | Zero downtime |
| `UIP.iot.bms.reading.v1` | replication.factor 1→3 | Zero downtime |
| `UIP.flink.alert.detected.v1` | replication.factor 1→3 | Zero downtime |
| `UIP.flink.analytics.hourly-rollup.v1` | replication.factor 1→3 | Zero downtime |
| `UIP.structural.alert.critical.v1` | replication.factor 1→3 | Zero downtime |

---

## 13. Stakeholder Communication

| Frequency | Format | Owner | Audience |
|-----------|--------|-------|----------|
| Day 4 (2026-06-07) | Status update: Infra HA complete | PM | PO |
| Day 7 (2026-06-10) | Mobile demo (internal) | Frontend | PM + PO |
| Day 9 (2026-06-12) | Pilot regression results | QA | PM + PO |
| **2026-06-17 15:00** | **Sprint 8 Gate Review** | **All** | **PO + City Authority** |

---

*Document prepared by UIP PM — 2026-06-03*
*Previous: [Sprint 7 Plan](sprint7-plan.md) | Detail Plan: [detail-plan.md](detail-plan.md)*
