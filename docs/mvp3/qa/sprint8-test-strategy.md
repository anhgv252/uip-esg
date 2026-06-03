# Sprint 8 — QA Test Strategy

**QA Engineer:** UIP QA Team | **Date:** 2026-06-03
**Sprint:** MVP3-8 | Hybrid: Pilot Prep + Mobile + Infrastructure HA
**Regression Baseline:** 243 TCs (91.4% automated) from Sprint 7

---

## 1. Test Scope & Priorities

### Tier 1 — Critical Path (MUST PASS before gate)

| Area | Stories | Risk Level | Test Type |
|------|---------|------------|-----------|
| Flink Kafka Config Fix | S8-C01 | HIGH — blocks all structural alerts | Integration + Manual |
| ClickHouse 2-node HA | S8-OPS01 | HIGH — data loss risk | Chaos + Integration |
| Kafka 3-broker KRaft | S8-OPS02 | HIGH — message durability | Chaos + Integration |
| Mobile Dashboard | S8-M01 | MEDIUM — new UI | Manual + Visual |
| Mobile Alerts + Safety | S8-M02 | MEDIUM — new UI | Manual + Functional |
| Flink CI/CD | S8-OPS03 | LOW — automation | CI Pipeline Validation |
| Pilot Regression | S8-QA01 | CRITICAL — gate blocker | Full Regression |

### Tier 2 — Best Effort

| Area | Stories | Risk Level | Test Type |
|------|---------|------------|-----------|
| PG Streaming Replication | S8-OPS04 | MEDIUM | Chaos |
| Keycloak Pilot Realm | S8-OPS05 | LOW | Manual Auth |
| k6 Load Test | S8-QA02 | HIGH — SLA gate | Performance |
| BMS Hardware Simulator | S8-QA03 | MEDIUM — protocol compat | Integration |

---

## 2. Test Strategy by Area

### 2.1 Infrastructure HA Testing (S8-OPS01 + S8-OPS02)

**Goal:** Verify zero data loss and zero downtime during infrastructure changes.

#### ClickHouse HA Test Matrix

| Test ID | Scenario | Steps | Expected Result | Priority |
|---------|----------|-------|-----------------|----------|
| CHA-001 | Node failover | 1) Insert data 2) Stop clickhouse-01 3) Query data | Query succeeds via clickhouse-02 | P0 |
| CHA-002 | Replication lag | 1) Insert 1000 rows to node-1 2) Query node-2 | Rows appear within 5 seconds | P0 |
| CHA-003 | Node rejoin | 1) Stop clickhouse-01 2) Wait 30s 3) Start again | Node rejoins cluster, data consistent | P0 |
| CHA-004 | BACKWARD compat | 1) Set CLICKHOUSE_CLUSTER_ENABLED=false 2) Run existing analytics tests | All 82 analytics regression tests PASS | P0 |
| CHA-005 | Both nodes down | 1) Stop both nodes 2) Query analytics API | API returns 200 with empty data (graceful degradation) | P1 |
| CHA-006 | Keeper restart | 1) Stop keeper 2) Insert data 3) Start keeper | Data replicates after keeper recovery | P1 |
| CHA-007 | Data migration integrity | 1) Count rows before migration 2) Migrate 3) Count after | Row count matches exactly | P0 |

#### Kafka 3-broker Test Matrix

| Test ID | Scenario | Steps | Expected Result | Priority |
|---------|----------|-------|-----------------|----------|
| KHA-001 | Broker failover | 1) Produce messages 2) Stop kafka-1 3) Continue producing | No message loss, consumers continue | P0 |
| KHA-002 | Replication verify | 1) Produce 1000 messages 2) Check all 3 brokers | min.insync.replicas=2 verified | P0 |
| KHA-003 | Partition rebalance | 1) Add kafka-2, kafka-3 2) Check partition distribution | Partitions spread across all 3 brokers | P0 |
| KHA-004 | Rolling restart | 1) Stop kafka-1 2) Wait rebalance 3) Start 4) Repeat kafka-2, kafka-3 | Zero downtime, no data loss | P0 |
| KHA-005 | Producer ack=all | 1) Send message with acks=all 2) Kill broker before sync | Message still available on remaining brokers | P1 |
| KHA-006 | Consumer offset | 1) Consumer reading at offset X 2) Broker failover 3) Resume | Consumer resumes from offset X | P0 |

#### Chaos Test Procedure (Infrastructure)

```bash
# ClickHouse chaos
docker compose stop clickhouse-01
sleep 5
# Verify queries still work via clickhouse-02
curl -s "http://localhost:8124/?query=SELECT count() FROM analytics.sensor_reading_hourly"
docker compose start clickhouse-01

# Kafka chaos
docker compose stop kafka-1
sleep 10
# Verify producer/consumer still working
docker compose start kafka-1
```

### 2.2 Mobile App Testing (S8-M01 + S8-M02)

**Goal:** Verify mobile UI functional correctness on iOS + Android.

#### Mobile Dashboard Test Matrix

| Test ID | Scenario | Expected Result | Platform | Priority |
|---------|----------|-----------------|----------|----------|
| MOB-001 | Dashboard loads | 4 KPI cards visible with real data | iOS + Android | P0 |
| MOB-002 | KPI card tap | Navigate to correct detail screen | iOS + Android | P0 |
| MOB-003 | Bottom tabs | All 4 tabs navigate correctly | iOS + Android | P0 |
| MOB-004 | Pull-to-refresh | Data refreshes, loading indicator shows | iOS + Android | P1 |
| MOB-005 | Auto-refresh (30s) | KPI values update without user action | iOS + Android | P1 |
| MOB-006 | Offline mode | Cached data shown with "Offline" badge | iOS + Android | P2 |
| MOB-007 | iPhone SE layout | All elements visible, no overflow | iOS | P0 |
| MOB-008 | iPad layout | Responsive, fills screen properly | iOS | P1 |
| MOB-009 | No buildings assigned | "No buildings available" message | iOS + Android | P1 |
| MOB-010 | Token expired | Auto-refresh → data loads | iOS + Android | P0 |

#### Mobile Alerts + Safety Test Matrix

| Test ID | Scenario | Expected Result | Platform | Priority |
|---------|----------|-----------------|----------|----------|
| MAL-001 | Alert list loads | Alerts sorted P0→P1→P2 | iOS + Android | P0 |
| MAL-002 | Severity filter | Only matching alerts shown | iOS + Android | P0 |
| MAL-003 | Safety score gauge | 0-100, correct color zone | iOS + Android | P0 |
| MAL-004 | Push notification → tap | Opens alert detail screen | iOS + Android | P0 |
| MAL-005 | Push foreground | In-app banner, not system notification | iOS + Android | P1 |
| MAL-006 | Push cold start | Login → navigate to alert detail | iOS + Android | P1 |
| MAL-007 | Safety score offline | "N/A" with gray gauge | iOS + Android | P2 |
| MAL-008 | P0 alert badge | Red, non-dismissible until acknowledged | iOS + Android | P0 |
| MAL-009 | 100+ alerts list | Virtualized (FlashList), smooth scroll | iOS + Android | P1 |
| MAL-010 | Alert pull-to-refresh | New alerts appear within 2 seconds | iOS + Android | P1 |

### 2.3 Flink CI/CD Testing (S8-OPS03)

| Test ID | Scenario | Expected Result | Priority |
|---------|----------|-----------------|----------|
| FCD-001 | `make flink-build` | JAR built with git hash in filename | P0 |
| FCD-002 | `make flink-submit` | Job submitted, appears in Flink dashboard | P0 |
| FCD-003 | Re-deploy | Old job cancelled (with savepoint), new job submitted | P0 |
| FCD-004 | Savepoint restore | New job restores state from savepoint | P0 |
| FCD-005 | No existing job | Submit succeeds (skip cancel) | P1 |
| FCD-006 | Flink unavailable | CI fails with clear error message | P1 |

### 2.4 SLA Gate Verification (S8-QA02)

| SLA ID | Metric | Target | Tool | Priority |
|--------|--------|--------|------|----------|
| SLA-001 | Structural alert P0 latency | <15s | Manual inject + timer | P0 |
| SLA-002 | Dashboard API p95 | <3s | k6 | P0 |
| SLA-003 | ESG PDF generation | <30s | k6 | P0 |
| SLA-004 | Kafka throughput | >1,667 msg/s | k6 | P0 |
| SLA-005 | API error rate | <0.01% over 20 min | k6 | P0 |
| SLA-006 | ClickHouse query p95 | <1,000ms | k6 | P1 |
| SLA-007 | Cross-building query p95 | <2s | k6 | P1 |
| SLA-008 | Mobile API p95 | <100ms | k6 | P1 |

---

## 3. Regression Strategy

### 3.1 Existing Regression Suite (243 TCs from Sprint 7)

| Module | TCs | Automated | New Impact Areas |
|--------|-----|-----------|------------------|
| Auth + JWT | 15 | 15 | Keycloak pilot realm |
| ESG Metrics | 25 | 25 | No change expected |
| Alert Engine | 20 | 20 | Flink config fix may affect |
| Environment | 15 | 15 | No change expected |
| Traffic | 10 | 10 | No change expected |
| Building Cluster | 18 | 18 | CH HA migration impact |
| BMS Devices | 15 | 12 | Simulator testing |
| Citizen Portal | 12 | 12 | No change expected |
| AI Workflow | 20 | 20 | No change expected |
| Forecast | 15 | 15 | No change expected |
| Notification | 15 | 13 | Push deep-link |
| Mobile | 20 | 8 | New mobile screens |
| Structural Safety | 18 | 17 | Flink config fix |
| Cross-module Integration | 25 | 20 | Infrastructure HA impact |
| **Total** | **243** | **220 (91.4%)** | |

### 3.2 New Test Cases for Sprint 8

| Area | New TCs | Automated | Manual |
|------|---------|-----------|--------|
| ClickHouse HA | 7 | 5 | 2 |
| Kafka 3-broker | 6 | 5 | 1 |
| Mobile Dashboard | 10 | 0 | 10 |
| Mobile Alerts + Safety | 10 | 0 | 10 |
| Flink CI/CD | 6 | 6 | 0 |
| PG Replication | 3 | 2 | 1 |
| **Total New** | **42** | **18** | **24** |

**Sprint 8 Total: 285 TCs** (243 existing + 42 new)

### 3.3 High-Risk Regression Areas

Sprint 8 infrastructure changes (CH HA + Kafka 3-broker) ảnh hưởng:

1. **Flink dual-sink** → verify EsgDualSinkJob still writes to both TS + CH after CH migration
2. **Analytics service** → verify ClickHouse queries work with cluster endpoint
3. **Kafka consumers** → verify partition rebalance doesn't break consumer groups
4. **Tenant isolation** → re-verify ISO-001 through ISO-010 on new infrastructure

---

## 4. Quality Gates

### Gate Checklist — Sprint 8

| # | Gate | Criterion | Hard/Soft | Verifier |
|---|------|-----------|-----------|----------|
| G1 | SLA-001 Fix | VibrationAnomalyJob consumes from Kafka | HARD | QA |
| G2 | CH HA | Node failover <1s, replication <5s | HARD | QA + DevOps |
| G3 | Kafka 3-broker | Broker failover, no data loss | HARD | QA + DevOps |
| G4 | Mobile Dashboard | 4 KPI cards, bottom tabs | HARD | Tester |
| G5 | Mobile Alerts | Alert list sorted, safety score | HARD | Tester |
| G6 | Flink CI/CD | `make flink-submit` works | HARD | Backend |
| G7 | Regression | 243/243 existing TCs PASS on staging | HARD | QA |
| G8 | SLA Gate | All 8 SLA thresholds met | HARD | QA |
| G9 | Tests | 1,200+ total, 0 failures | HARD | CI |
| G10 | Coverage | LINE ≥86%, BRANCH ≥70% | HARD | CI |
| G11 | TypeScript | 0 errors | HARD | CI |
| G12 | SA Code Review | APPROVED | HARD | SA |
| GS1 | PG Replication | Lag <1s | SOFT | DevOps |
| GS2 | Keycloak Realm | 3 users login | SOFT | Tester |
| GS3 | BMS Simulator | E2E data flow | SOFT | QA |
| GS4 | Avro Auto-reg | 4 schemas registered | SOFT | Backend |

---

## 5. Test Execution Schedule

| Day | Activity | Owner | Deliverable |
|-----|----------|-------|-------------|
| Day 1-3 | Prepare staging env + seed data | QA + DevOps | Staging ready |
| Day 3-4 | CH HA + Kafka chaos testing | QA + DevOps | CHA/KHA test results |
| Day 4-5 | Flink CI/CD pipeline validation | QA + Backend | FCD test results |
| Day 6-7 | Mobile testing (Dashboard + Alerts) | Tester | MOB/MAL test results |
| Day 6-8 | Pilot regression (243 TCs) on staging | QA | Regression report |
| Day 8-9 | k6 SLA gate + load test | QA | SLA report |
| Day 9-10 | BMS simulator + final bug triage | QA + Tester | Execution report |
| Day 10 | Sign-off + gate review | All | Gate checklist |

---

## 6. Test Environments

| Environment | Purpose | Config |
|-------------|---------|--------|
| **Dev (local)** | Developer unit tests, integration tests | Single-node, docker-compose.yml |
| **Staging (pilot)** | Regression, SLA gate, chaos testing | HA mode, docker-compose.ha.yml overlay |
| **Mobile** | Mobile UI testing | Expo Go on iOS simulator + Android emulator |

---

## 7. Defect Management

### Severity Classification

| Severity | Definition | SLA Fix | Example |
|----------|------------|---------|---------|
| P0 - Blocker | Blocks sprint gate, no workaround | Same day | CH data migration fails, Kafka message loss |
| P1 - Critical | Feature broken, workaround exists | Next day | Mobile KPI card shows wrong data |
| P2 - Major | Feature partially broken | Within sprint | Push deep-link navigates wrong screen |
| P3 - Minor | Cosmetic, minor inconvenience | Backlog | Mobile loading animation too fast |

### Bug Report Template
```
**Test Case ID:** [TC-xxx]
**Severity:** [P0/P1/P2/P3]
**Environment:** [staging/mobile/dev]
**Steps:**
1. ...
**Expected:** ...
**Actual:** ...
**Screenshot/Log:** [attach]
```

---

*Document: Sprint 8 Test Strategy v1.0 | QA Engineer | 2026-06-03*
