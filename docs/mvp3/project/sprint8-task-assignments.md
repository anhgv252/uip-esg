# Sprint 8 — Task Assignments (All Roles)

**Date:** 2026-06-03 | **Sprint:** MVP3-8
**Based on:** [Sprint 8 Master Plan](sprint8-plan.md) + [User Stories](sprint8-stories.md) + [Architecture](sprint8-architecture.md)

---

## Backend-1 — Task Assignment

**Total SP:** 7 SP (Tier 1: 4 SP + Tier 2: 3 SP)
**Capacity:** ~10 SP (10 days × 1 SP/day × 1.0 FTE)

### Tasks (Priority Order)

| # | Task ID | Task | SP | Day | Dependencies | Done |
|---|---------|------|-----|-----|-------------|------|
| 1 | S8-C01 | **Fix SLA-001:** Flink Kafka listener config — change `bootstrap.servers` từ `localhost:9092` sang `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}` env var | 1 | Day 1 | None | ⬜ |
| 2 | S8-C01-TEST | Verify VibrationAnomalyJob consumes: inject 3 spikes >50mm/s via API, verify alert appears in Kafka topic within 15s | — | Day 1 | S8-C01 | ⬜ |
| 3 | S8-OPS03 | **Flink CI/CD:** Create `infrastructure/scripts/flink-deploy.sh` + Makefile targets (`make flink-build`, `make flink-submit`, `make flink-deploy`, `make flink-list`) | 3 | Day 2-4 | S8-C01 | ⬜ |
| 4 | S8-OPS03-CFG | Add `@ConditionalOnProperty` cho ClickHouse cluster vs single-node (BACKWARD compat per ADR-036) | — | Day 3 | S8-OPS01 progress | ⬜ |
| 5 | S8-OPS06 | **Avro Auto-registration:** Create `infrastructure/scripts/register-avro-schemas.sh` — check Apicurio health, register 4 schemas, idempotent | 3 | Day 5-7 | S8-OPS03 | ⬜ |
| 6 | S8-OPS06-TEST | Verify: run script twice → no error on second run | — | Day 7 | S8-OPS06 | ⬜ |
| 7 | — | Buffer / support QA regression / integration testing | — | Day 8-10 | — | ⬜ |

**Key Files to Modify:**
- `flink-jobs/src/.../VibrationAnomalyJob.java` — env var config
- `infrastructure/scripts/flink-deploy.sh` — NEW
- `infrastructure/Makefile` — add flink targets
- `infrastructure/scripts/register-avro-schemas.sh` — NEW
- `backend/src/.../config/ClickHouseConfig.java` — conditional config

**Acceptance:**
- [ ] `make flink-submit` submits job to Flink successfully
- [ ] `make flink-deploy` takes savepoint → cancels → submits
- [ ] Avro schemas registered on deploy, idempotent on re-run
- [ ] SLA-001 fix: VibrationAnomalyJob RUNNING + consuming

---

## Backend-2 — Task Assignment

**Total SP:** 5 SP (Tier 2)
**Capacity:** ~10 SP

### Tasks (Priority Order)

| # | Task ID | Task | SP | Day | Dependencies | Done |
|---|---------|------|-----|-----|-------------|------|
| 1 | S8-OPS01-SUPPORT | Support DevOps: validate ClickHouse migration — run data integrity check script, verify row counts match | — | Day 1-3 | S8-OPS01 | ⬜ |
| 2 | S8-OPS02-SUPPORT | Support DevOps: validate Kafka topic rebalancing — verify consumer groups intact after partition reassignment | — | Day 3-4 | S8-OPS02 | ⬜ |
| 3 | S8-QA03 | **BMS Hardware Simulator Testing:** Configure Modbus TCP simulator (j2mod test server), BACnet/IP simulator (BACnet4J test device), test adapters end-to-end | 5 | Day 4-7 | S8-OPS02 | ⬜ |
| 4 | S8-QA03-MODBUS | Modbus test: connect simulator → BmsModbusAdapter → Kafka → verify reading in topic | — | Day 5 | S8-QA03 | ⬜ |
| 5 | S8-QA03-BACNET | BACnet test: connect simulator → BmsBacnetAdapter → Kafka → verify reading in topic | — | Day 6 | S8-QA03 | ⬜ |
| 6 | S8-QA03-CB | Circuit breaker test: 5 consecutive failures → CB OPEN → STALE flag → Kafka event | — | Day 7 | S8-QA03 | ⬜ |
| 7 | — | Buffer / regression support / integration testing | — | Day 8-10 | — | ⬜ |

**Key Files to Test/Modify:**
- `backend/src/.../bms/BmsModbusAdapter.java` — verify with simulator
- `backend/src/.../bms/BmsBacnetAdapter.java` — verify with simulator
- Test containers/scripts for Modbus/BACnet simulators

**Acceptance:**
- [ ] Modbus simulator → BMS adapter → Kafka verified (10+ readings)
- [ ] BACnet simulator → BMS adapter → Kafka verified (10+ readings)
- [ ] Circuit breaker: 5 failures → OPEN → STALE flag emitted to Kafka

---

## Frontend — Task Assignment

**Total SP:** 13 SP (Tier 1)
**Capacity:** ~10 SP — **BOTTLENECK ALERT** (13 SP > 10 SP)

### Tasks (Priority Order)

| # | Task ID | Task | SP | Day | Dependencies | Done |
|---|---------|------|-----|-----|-------------|------|
| 1 | S8-M01-HOOKS | **Shared hooks setup:** Create `packages/api-types/` with shared types (Building, Alert, SensorReading, ForecastDataPoint). Verify `useAlerts`, `useBuildingList`, `useSensors` work in RN via npm workspaces | 2 | Day 1-2 | None | ⬜ |
| 2 | S8-M01-TABS | **Bottom tab navigation:** React Navigation `@react-navigation/bottom-tabs` — 4 tabs (Dashboard, Alerts, Buildings, Profile). Wire up navigation container | 1 | Day 2 | S8-M01-HOOKS | ⬜ |
| 3 | S8-M01-KPI | **KPI cards:** 4 cards (Energy kWh, Safety Score 0-100, AQI 0-500, Active Alerts count). Each card = React Query hook + responsive layout | 3 | Day 3-4 | S8-M01-HOOKS | ⬜ |
| 4 | S8-M01-DETAIL | **Card navigation:** Tap card → navigate to detail screen (Energy→Analytics, Safety→Building Safety, AQI→Environment, Alerts→Alert List) | 1 | Day 4 | S8-M01-KPI | ⬜ |
| 5 | S8-M01-PULL | **Pull-to-refresh + auto-refresh (30s):** RefreshControl + React Query refetchInterval. Skeleton loading state. Offline badge | 1 | Day 5 | S8-M01-KPI | ⬜ |
| 6 | S8-M02-ALERTS | **Alert list:** FlashList virtualized, sorted P0→P1→P2, severity filter chips, pull-to-refresh | 3 | Day 5-6 | S8-M01-HOOKS | ⬜ |
| 7 | S8-M02-SAFETY | **Safety score gauge:** 0-100 gauge component, color zones (green/amber/red/gray), cached 5 min React Query | 1 | Day 7 | S8-M01-KPI | ⬜ |
| 8 | S8-M02-PUSH | **Push deep-link:** `uipmobile://alerts/{id}` scheme, foreground notification banner, cold start handling | 1 | Day 7 | S8-M02-ALERTS | ⬜ |
| 9 | — | Polish + responsive testing + screenshots | — | Day 8-10 | All above | ⬜ |

**Key Files to Create/Modify:**
- `packages/api-types/` — NEW shared types package
- `applications/operator-mobile/src/screens/DashboardScreen.tsx` — KPI cards
- `applications/operator-mobile/src/screens/AlertListScreen.tsx` — Alert list
- `applications/operator-mobile/src/screens/AlertDetailScreen.tsx` — Alert detail
- `applications/operator-mobile/src/components/SafetyScoreGauge.tsx` — Gauge
- `applications/operator-mobile/src/components/KpiCard.tsx` — KPI card
- `applications/operator-mobile/src/navigation/MainTabNavigator.tsx` — Bottom tabs

**RISK MITIGATION (13 SP > 10 SP capacity):**
- Day 1-5: Dashboard MUST done (8 SP) — non-negotiable
- Day 5-7: Alerts + Safety (5 SP) — if behind, cut push deep-link (1 SP)
- Priority: Dashboard > Alerts > Safety Gauge > Push Deep-link

**Acceptance:**
- [ ] 4 KPI cards render with real API data on iOS + Android
- [ ] Bottom tab navigation works
- [ ] Shared hooks reuse 100% from web (no duplication)
- [ ] Alert list sorted P0→P1→P2, filter works
- [ ] Safety gauge 0-100, correct color zones
- [ ] `npx tsc --noEmit` → 0 errors

---

## DevOps — Task Assignment

**Total SP:** 18 SP (Tier 1: 13 SP + Tier 2: 5 SP)
**Capacity:** ~10 SP — **BOTTLENECK ALERT** (18 SP > 10 SP)

### Tasks (Priority Order)

| # | Task ID | Task | SP | Day | Dependencies | Done |
|---|---------|------|-----|-----|-------------|------|
| 1 | S8-OPS01-KEEPER | **ClickHouse Keeper:** Deploy `clickhouse-keeper` service + config in `docker-compose.ha.yml` overlay | 2 | Day 1 | None | ⬜ |
| 2 | S8-OPS01-NODES | **ClickHouse 2 nodes:** Deploy `clickhouse-01` + `clickhouse-02` with ReplicatedReplacingMergeTree engine + Keeper coordination | 4 | Day 2-3 | S8-OPS01-KEEPER | ⬜ |
| 3 | S8-OPS01-MIGRATE | **CH Data migration:** Flink savepoint → CREATE Replicated tables → INSERT INTO from old → atomic RENAME swap → resume Flink | 2 | Day 3-4 | S8-OPS01-NODES | ⬜ |
| 4 | S8-OPS01-TEST | **CH HA validation:** Node failover test, replication lag test, BACKWARD compat Tier 1 | — | Day 4 | S8-OPS01-MIGRATE | ⬜ |
| 5 | S8-OPS02-KRAFT | **Kafka 3-broker KRaft:** Update docker-compose overlay: kafka-1, kafka-2, kafka-3 with KRaft quorum config | 3 | Day 4-5 | S8-OPS01 (parallel OK) | ⬜ |
| 6 | S8-OPS02-REBALANCE | **Kafka partition rebalancing:** Reassign existing topic partitions to 3 brokers, verify replication.factor=3 | 2 | Day 5-6 | S8-OPS02-KRAFT | ⬜ |
| 7 | S8-OPS02-TEST | **Kafka HA validation:** Broker failover test, rolling restart, producer ack verification | — | Day 6 | S8-OPS02-REBALANCE | ⬜ |
| 8 | S8-OPS04 | **PG Streaming Replication:** TimescaleDB primary + hot standby in docker-compose.ha.yml | 3 | Day 7-8 | S8-OPS01 + S8-OPS02 | ⬜ |
| 9 | S8-OPS05 | **Keycloak Pilot Realm:** Export/import realm JSON with 3 users (pilot-admin, pilot-operator, pilot-viewer) | 2 | Day 8 | S8-OPS04 | ⬜ |
| 10 | — | Monitoring update + Grafana HA dashboards + buffer | — | Day 9-10 | — | ⬜ |

**Key Files to Create/Modify:**
- `infrastructure/docker-compose.ha.yml` — NEW overlay file
- `infrastructure/clickhouse/keeper-config.xml` — NEW
- `infrastructure/clickhouse/node-01-config.xml` — NEW
- `infrastructure/clickhouse/node-02-config.xml` — NEW
- `infrastructure/scripts/ch-migrate.sh` — NEW migration script
- `infrastructure/scripts/kafka-rebalance.sh` — NEW rebalancing script
- `infrastructure/keycloak/uip-pilot-realm.json` — NEW realm export

**RISK MITIGATION (18 SP > 10 SP capacity):**
- Day 1-4: ClickHouse HA (8 SP) — MUST done, highest EA P0 priority
- Day 4-6: Kafka 3-broker (5 SP) — parallel với CH testing
- Day 7-8: PG + Keycloak (5 SP) — Tier 2, cut if behind
- Priority: CH HA > Kafka > PG Replication > Keycloak

**Acceptance:**
- [ ] `docker compose -f docker-compose.yml -f docker-compose.ha.yml up -d` → all services healthy
- [ ] CH node-1 down → queries via node-2, no 500 error
- [ ] Kafka broker-1 down → producers/consumers continue
- [ ] PG replication lag <1s
- [ ] 3 pilot users login via Keycloak

---

## QA — Task Assignment

**Total SP:** 7 SP (Tier 1: 5 SP + Tier 2: 2 SP)
**Capacity:** ~10 SP

### Tasks (Priority Order)

| # | Task ID | Task | SP | Day | Dependencies | Done |
|---|---------|------|-----|-----|-------------|------|
| 1 | S8-QA-ENV | **Staging environment prep:** Deploy staging with HA config, seed test data (≥10M rows), verify all services UP | — | Day 1-3 | S8-OPS01 + S8-OPS02 | ⬜ |
| 2 | S8-QA-CHAOS | **Infrastructure chaos testing:** Execute CHA-001..007 + KHA-001..006 from test strategy | — | Day 3-4 | S8-QA-ENV | ⬜ |
| 3 | S8-QA-FCD | **Flink CI/CD validation:** Execute FCD-001..006 | — | Day 4-5 | S8-OPS03 | ⬜ |
| 4 | S8-QA01 | **Pilot regression (243 TCs):** Execute full regression on staging, document results | 5 | Day 6-8 | S8-QA-ENV + S8-QA-CHAOS | ⬜ |
| 5 | S8-QA02 | **k6 SLA gate:** Full load test 500 VU / 200 VU, verify all 8 SLA thresholds | 2 | Day 8-9 | S8-QA01 | ⬜ |
| 6 | S8-QA-REPORT | **Test execution report:** Compile all results, gate checklist, sign-off recommendation | — | Day 9-10 | S8-QA02 | ⬜ |

**Deliverables:**
- [ ] `docs/mvp3/qa/sprint8-test-execution-report.md` — full results
- [ ] Gate checklist: 12 HARD + 4 SOFT gates evaluated
- [ ] SLA report with k6 results
- [ ] Bug report (if any P0/P1 found)

---

## Tester — Task Assignment

**Total SP:** ~5 SP (supporting QA)
**Capacity:** ~10 SP

### Tasks (Priority Order)

| # | Task ID | Task | SP | Day | Dependencies | Done |
|---|---------|------|-----|-----|-------------|------|
| 1 | S8-TEST-PREP | **Test environment check:** Verify staging mobile access, Expo Go installed on simulators | — | Day 4-5 | S8-QA-ENV | ⬜ |
| 2 | S8-TEST-MOBILE | **Mobile testing (21 TCs):** Execute TC-S8-030..045 from manual test cases | — | Day 6-7 | S8-M01 + S8-M02 | ⬜ |
| 3 | S8-TEST-INFRA | **Infrastructure manual tests:** Execute TC-S8-001..002, TC-S8-010..012, TC-S8-020..021 | — | Day 5-6 | S8-QA-CHAOS | ⬜ |
| 4 | S8-TEST-FLINK | **Flink CI/CD manual tests:** Execute TC-S8-050..051 | — | Day 5 | S8-OPS03 | ⬜ |
| 5 | S8-TEST-KEYCLOAK | **Keycloak pilot realm tests:** Execute TC-S8-060 | — | Day 8 | S8-OPS05 | ⬜ |
| 6 | S8-TEST-SMOKE | **Post-deploy smoke test:** Execute TC-S8-070 (10 endpoints) | — | Day 9 | S8-QA01 | ⬜ |
| 7 | S8-TEST-REPORT | **Manual test execution report:** Screenshots, results, bug reports | — | Day 9-10 | All tests | ⬜ |

**Deliverables:**
- [ ] Screenshots: Dashboard + Alerts on iOS + Android
- [ ] Bug reports with reproduction steps
- [ ] Manual test execution report with PASS/FAIL status

---

## SA — Task Assignment (Support Role)

| # | Task | Day | Done |
|---|------|-----|------|
| 1 | Review ADR-036 (ClickHouse HA) + approve/reject | Day 1-2 | ⬜ |
| 2 | Review ADR-037 (Kafka 3-broker KRaft) + approve/reject | Day 2 | ⬜ |
| 3 | Review ADR-038 (Flink CI/CD) + approve/reject | Day 4 | ⬜ |
| 4 | Review mobile shared hooks architecture | Day 2-3 | ⬜ |
| 5 | **SA Code Review** (mandatory per CLAUDE.md) — review all Tier 1 changes before deploy | Day 9-10 | ⬜ |

---

## Summary — Bottleneck Analysis

| Role | SP Assigned | Capacity | Gap | Mitigation |
|------|------------|----------|-----|------------|
| Backend-1 | 7 SP | 10 SP | -3 SP ✅ | Room for buffer |
| Backend-2 | 5 SP | 10 SP | -5 SP ✅ | Support DevOps/QA |
| **Frontend** | **13 SP** | **10 SP** | **+3 SP ⚠️** | Cut push deep-link if behind |
| **DevOps** | **18 SP** | **10 SP** | **+8 SP ⚠️** | Cut PG + Keycloak if behind |
| QA | 7 SP | 10 SP | -3 SP ✅ | Room for re-runs |
| Tester | 5 SP | 10 SP | -5 SP ✅ | Support QA |

**Two bottlenecks identified:**
1. **Frontend (13 SP > 10 SP)** — Mobile là EA P0, phải deliver. Mitigation: cut push deep-link (1 SP) nếu cần.
2. **DevOps (18 SP > 10 SP)** — Infra HA là EA P0. Mitigation: cut PG replication + Keycloak (5 SP) to Tier 2.

**Cut order if behind schedule:**
```
DevOps:  S8-OPS05 (Keycloak) → S8-OPS04 (PG Repl) → S8-OPS02 (Kafka — partial cut to 2-broker)
Frontend: S8-M02-PUSH (deep-link) → S8-M01-PULL (auto-refresh) → S8-M01-DETAIL (card nav)
```

---

*Document: Sprint 8 Task Assignments v1.0 | All Roles | 2026-06-03*
