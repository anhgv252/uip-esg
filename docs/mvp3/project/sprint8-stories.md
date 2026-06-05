# Sprint 8 — User Stories & Acceptance Criteria

**Sprint:** MVP3-8 | Hybrid: Pilot Prep + Mobile + Infrastructure HA
**Timeline:** 2026-06-04 → 2026-06-17 (10 ngày)
**BA:** UIP Business Analyst | **Date:** 2026-06-03

---

## Tier 1 — PHẢI DONE (35 SP)

---

### Epic 0: Carry-over P0 Fix (1 SP)

#### S8-C01: Fix SLA-001 — Flink Kafka Listener Config

**User Story:** As a city operator, I want the VibrationAnomalyJob to consume sensor data from Kafka correctly, so that structural alerts are detected in real-time.

**Context:** Logic đúng (41/41 unit tests PASS). Vấn đề chỉ là `bootstrap.servers` config trong Flink job không match với Kafka service name trong Docker Compose.

**Acceptance Criteria:**
- **AC-1:** Given VibrationAnomalyJob deployed, When Kafka topic `UIP.iot.sensor.reading.v1` receives messages, Then Flink consumes within 5 seconds
- **AC-2:** Given 3 vibration spikes >50mm/s within 10s, When Flink processes them, Then structural alert emitted to `UIP.structural.alert.critical.v1` within 15 seconds
- **AC-3:** Given Flink restart, When checkpoint exists, Then job resumes from last checkpoint (no data gap >5 minutes)

**Business Rules:**
- BR-010 still enforced: P0 alert = operator review ONLY
- Config must use env vars with defaults: `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`

**Edge Cases:**
- Kafka topic empty → no alert (correct behavior)
- Kafka unavailable → Flink retry with exponential backoff
- Multiple consumers same group → partition rebalance

---

### Epic 1: Mobile App Foundation (13 SP)

#### S8-M01: Mobile Dashboard — KPI Cards + Shared Hooks + Bottom Tabs

**User Story:** As a building operator, I want to see a mobile dashboard with key building metrics (energy, safety, AQI, active alerts) so that I can monitor my buildings from anywhere.

**Acceptance Criteria:**
- **AC-1:** Given operator logged in via PKCE, When dashboard loads, Then 4 KPI cards display: Energy (kWh), Safety Score (0-100), AQI (0-500), Active Alerts count
- **AC-2:** Given KPI data available, When user taps a card, Then navigates to detail screen (Energy → Building Analytics, Safety → Building Safety, AQI → Environment, Alerts → Alert List)
- **AC-3:** Given bottom tab navigation, Then 4 tabs visible: Dashboard, Alerts, Buildings, Profile
- **AC-4:** Given shared hooks loaded, Then `useAlerts`, `useBuildingList`, `useSensors` reuse from web with zero modification
- **AC-5:** Given mobile responsive layout, Then renders correctly on iPhone SE (375px) and iPad (1024px)

**Business Rules:**
- Dashboard auto-refresh every 30 seconds (React Query `refetchInterval`)
- Pull-to-refresh triggers immediate refetch
- Offline: show last cached data with "Offline" badge
- Tenant-scoped: only data for operator's assigned buildings

**Edge Cases:**
- No buildings assigned → "No buildings available. Contact your admin."
- API timeout → skeleton loading state, retry after 10s
- Token expired → auto-refresh via refresh_token, then retry

**Shared Hooks Reuse Strategy:**
```
packages/api-types/     ← shared types (Building, Alert, SensorReading)
frontend/src/hooks/     ← web hooks (use in both web + mobile via npm workspaces)
applications/operator-mobile/src/hooks/  ← mobile-specific overrides only
```

---

#### S8-M02: Mobile Alerts + Building Safety — Alert List + Safety Score + Push Deep-link

**User Story:** As a building operator, I want to view and filter alerts on mobile, see building safety scores, and tap push notifications to go directly to alert details, so that I can respond quickly to critical situations.

**Acceptance Criteria:**
- **AC-1:** Given alerts exist, When operator opens Alerts tab, Then list shows all alerts sorted by severity (P0 first, then P1, P2)
- **AC-2:** Given alert list loaded, When operator pulls to refresh, Then new alerts appear within 2 seconds
- **AC-3:** Given alert filters available, When operator selects severity filter, Then only matching alerts shown
- **AC-4:** Given building safety score available, When operator views building card, Then SafetyScoreGauge (0-100) displays with color: green (≥80), amber (50-79), red (<50), gray (offline)
- **AC-5:** Given push notification received, When operator taps notification, Then app opens to alert detail screen with full alert context

**Business Rules:**
- P0 alerts: red background, non-dismissible badge until acknowledged
- Alert cooldown: same alert type for same building → max 1 per 5 minutes
- Safety score cached 5 minutes (mobile-side cache via React Query)
- Push deep-link format: `uip-operator://alerts/{alertId}`

**Edge Cases:**
- 100+ alerts → virtualized list (FlashList)
- Push while app in foreground → show in-app banner, not system notification
- Push while app killed → cold start → navigate to alert after auth
- Safety score unavailable (sensor offline) → show "N/A" with gray gauge

---

### Epic 2: Infrastructure HA (13 SP)

#### S8-OPS01: ClickHouse 2-node HA — ReplicatedMergeTree + Keeper

**User Story:** As a DevOps engineer, I want ClickHouse running in a 2-node cluster with ReplicatedMergeTree, so that analytics queries remain available when one node fails.

**Acceptance Criteria:**
- **AC-1:** Given 2 ClickHouse nodes running, When node-1 goes down, Then queries route to node-2 with <1 second failover
- **AC-2:** Given ReplicatedMergeTree configured, When data inserted to node-1, Then node-2 replicates within 5 seconds
- **AC-3:** Given Docker Compose up, When `clickhouse-01` container restarts, Then it rejoins cluster automatically
- **AC-4:** Given Tier 1 staging, When `CLICKHOUSE_CLUSTER_ENABLED=false`, Then single-node mode works unchanged (BACKWARD compat)

**Business Rules:**
- Keeper (not ZooKeeper) for coordination — lighter footprint
- ZooKeeper migration path documented for future scale
- ReplicatedMergeTree on `sensor_reading_hourly` table
- Docker service names: `clickhouse-01`, `clickhouse-02`, `clickhouse-keeper`
- Data retention unchanged: 2 year hot, 5 year cold

**Edge Cases:**
- Both nodes down → analytics queries return empty (graceful degradation, not 500)
- Keeper down → writes continue locally, replicate when Keeper recovers
- Schema migration on cluster → `ON CLUSTER '{cluster}'` syntax
- Existing data migration: INSERT INTO new Replicated table FROM old MergeTree table

---

#### S8-OPS02: Kafka 3-broker KRaft — Scale + Replication

**User Story:** As a DevOps engineer, I want Kafka running with 3 brokers in KRaft mode, so that message durability is guaranteed even if one broker fails.

**Acceptance Criteria:**
- **AC-1:** Given 3 Kafka brokers running, When broker-1 goes down, Then producers and consumers continue without interruption
- **AC-2:** Given replication.factor=3, min.insync.replicas=2, When message acknowledged, Then at least 2 brokers have the message
- **AC-3:** Given existing topics, When migrating to 3 brokers, Then partition rebalancing completes without downtime
- **AC-4:** Given KRaft mode (no Zookeeper), When all 3 controllers form quorum, Then metadata replication works correctly

**Business Rules:**
- KRaft quorum: 3 controllers (co-located with brokers)
- Topic defaults: replication.factor=3, min.insync.replicas=2
- Partition count unchanged per topic
- Retention policy unchanged
- Docker services: `kafka-1`, `kafka-2`, `kafka-3`

**Edge Cases:**
- 2 brokers down → producers get NOT_ENOUGH_REPLICAS, consumers stop (expected — need quorum)
- Network partition → minority partition rejects writes
- Rolling upgrade: stop broker-1 → wait rebalance → start → repeat for 2, 3
- Existing consumer groups: rebalance triggers on broker join

---

### Epic 3: Automation + Pilot Prep (8 SP)

#### S8-OPS03: Flink Job CI/CD — Automated Submission

**User Story:** As a backend engineer, I want Flink jobs automatically submitted on CI deploy, so that I don't need manual curl commands after each deployment.

**Acceptance Criteria:**
- **AC-1:** Given CI pipeline runs, When `./gradlew :flink-jobs:build`, Then JAR built with name `flink-jobs-{git-hash}.jar`
- **AC-2:** Given JAR built, When CI submits to Flink, Then old job cancelled (with savepoint) and new job submitted
- **AC-3:** Given job submission, When state exists from previous version, Then job restores from savepoint
- **AC-4:** Given CI failure, When job submission fails, Then pipeline reports failure with Flink error logs

**Business Rules:**
- Savepoint before cancel: `flink stop --savepointPath /savepoints/{job-name}`
- Job name = class name (e.g., `VibrationAnomalyJob`)
- Cancel old job before submit new (not parallel — avoid dual consumption)
- Makefile targets: `make flink-submit`, `make flink-list`, `make flink-cancel`

**Edge Cases:**
- No existing job → skip cancel, just submit
- Savepoint fails → cancel without savepoint (log warning)
- Multiple jobs → submit each sequentially
- Flink cluster unavailable → CI fails with clear error

---

#### S8-QA01: Pilot Regression trên Staging — 243 TCs

**User Story:** As a QA engineer, I want to execute the full 243-TC regression suite on a dedicated staging environment, so that we can confirm pilot readiness.

**Acceptance Criteria:**
- **AC-1:** Given staging environment deployed, When regression suite runs, Then 243/243 test cases PASS
- **AC-2:** Given automated TCs (91.4%), When CI runs, Then all automated tests execute and report pass/fail
- **AC-3:** Given manual TCs, When tester executes, Then results documented in test execution report
- **AC-4:** Given SLA gates, When k6 runs, Then all performance thresholds met

**Business Rules:**
- Staging = dedicated Docker Compose on pilot server (not localhost)
- 3 pilot users: admin, operator, viewer
- ALL SLA gates from Sprint 7 must pass again
- New: Kafka 3-broker + ClickHouse 2-node must not break existing tests

**Edge Cases:**
- Test failure → investigate, fix, re-run (within sprint)
- Environment issue → DevOps fixes, QA re-runs
- Flaky test → stabilize before marking PASS

---

## Tier 2 — BEST EFFORT (15 SP)

---

#### S8-OPS04: PG Streaming Replication (3 SP)

**User Story:** As a DevOps engineer, I want PostgreSQL running with streaming replication, so that we have a hot standby for failover.

**Acceptance Criteria:**
- AC-1: Primary + standby running, replication lag <1 second
- AC-2: Standby promotes to primary within 30 seconds if primary fails
- AC-3: Read queries can route to standby (optional — needs app config)

---

#### S8-OPS05: Keycloak Pilot Realm (2 SP)

**User Story:** As a city authority admin, I want 3 pilot user accounts (admin, operator, viewer) in Keycloak, so that we can start pilot testing.

**Acceptance Criteria:**
- AC-1: Realm `uip` created with 3 users
- AC-2: Admin: full access all modules; Operator: building + alerts + safety; Viewer: read-only
- AC-3: PKCE login works on mobile with pilot realm

---

#### S8-QA02: Full k6 Load Test (2 SP)

**User Story:** As a QA engineer, I want to run the full k6 load test at scale, so that we confirm performance under realistic load.

**Acceptance Criteria:**
- AC-1: 500 VU cross-building dashboard sustained 30 minutes
- AC-2: 200 VU mobile operators sustained 20 minutes
- AC-3: All SLA thresholds met: dashboard p95 <3s, API error rate <0.01%, Kafka >1,667/s

---

#### S8-QA03: BMS Hardware Simulator Testing (5 SP)

**User Story:** As a QA engineer, I want to test BMS Modbus/BACnet adapters with a specialized simulator, so that we confirm protocol compatibility before real hardware.

**Acceptance Criteria:**
- AC-1: Modbus TCP simulator reads correct register values
- AC-2: BACnet/IP simulator responds to COV subscriptions
- AC-3: Circuit breaker opens after 5 consecutive failures, STALE flag emitted
- AC-4: Data flows: simulator → BMS adapter → Kafka → Flink → ClickHouse

---

#### S8-OPS06: Avro Auto-registration Script (3 SP)

**User Story:** As a backend engineer, I want Avro schemas automatically registered on deploy, so that I don't need manual curl commands.

**Acceptance Criteria:**
- AC-1: Bootstrap script checks Apicurio health, then registers 4 schemas
- AC-2: Idempotent: re-running doesn't fail if schemas already exist
- AC-3: CI runs script after Apicurio container healthy

---

## Tier 3 — DESCOPE

#### S8-SA01: SA Minor Findings Consolidated (5 SP)
- 8 findings từ Sprint 6-7 consolidated

#### S8-OPS07: Cache Warming Strategy (2 SP)
- Pre-warm Redis cache on startup for read-heavy endpoints

#### S8-OPS08: Open CVE Network Mitigation (3 SP)
- Network boundary rules for angus-activation, commons-fileupload CVEs

---

*Document: Sprint 8 User Stories v1.0 | BA | 2026-06-03*
