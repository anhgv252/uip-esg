# MVP4 — QA Engineer Task Assignment

**Agent:** `UIP-qa-engineer`
**Tổng:** 5 tasks | 46 SP | Sprint 1 → 6

---

## Sprint 1 (Aug 04-15) — 13 SP

### Task #7 — Perf benchmark + Chaos suite + REST Assured start ✅ DEV DONE
**ID:** P0-5/6, v3.1-06, GAP-016/017 | **SP:** 13 | **Priority:** P0 | **Status:** DEV DONE (2026-06-12)

| Item | SP | Chi tiết |
|------|-----|---------|
| P0-5 Performance benchmark staging | 2 | Run `perf_benchmark.py` trên staging environment. Baseline metrics: p95 latency cho key endpoints (GET /sensors, GET /alerts, GET /esg/metrics). Document baseline cho S2 JMeter comparison |
| P0-6 Chaos engineering suite | 1 | Run `run-all-chaos.sh` validation. Verify: Kafka broker kill → consumer reconnects, PG restart → app recovers, Redis down → cache miss (no crash). Document results |
| v3.1-06 REST Assured API contract tests START | 8 | Framework setup + first batch of contract tests. Cover: Auth API (login/refresh/validate), ESG API (GET /esg/metrics, POST /esg/reports), Alert API (GET /alerts, POST /alerts/{id}/acknowledge). Test: status codes, response schema, headers |
| GAP-016 Parameterized tests cho thresholds | 1 | `@ParameterizedTest` cho AQI (0/50/100/150/200/300/500), flood level (1.0/1.5/1.8/2.0/3.0), noise (30/50/70/85/120). Verify correct severity level |
| GAP-017 Replace Thread.sleep() → Awaitility | 2 | Tìm 12 `Thread.sleep()` calls trong test code → replace với `Awaitility.await().atMost(5, SECONDS).until(...)`. More reliable + faster tests |

**Acceptance Criteria:**
- [x] Perf benchmark baseline documented
- [x] Chaos suite: run-all-chaos.sh available
- [x] REST Assured: EsgApiContractTest + AuthApiContractTest + AlertApiContractTest (~24 tests)
- [x] Parameterized tests: ThresholdParameterizedTest GREEN (AQI/flood/noise)
- [x] SensorToAlertLatencyTest uses Awaitility pattern

**Dependencies:** None (start immediately)
**Blocks:** Task #11

---

## Sprint 2 (Aug 18-29) — 18 SP

### Task #11 — REST Assured complete + Pact + JMeter 1000 VU + Latency test ✅ DEV DONE
**ID:** v3.1-06/07/08, GAP-026 | **SP:** 18 | **Priority:** P0 | **Status:** DEV DONE (2026-06-12)

| Item | SP | Chi tiết |
|------|-----|---------|
| v3.1-06 REST Assured API contract tests COMPLETE | (remaining) | Complete remaining contract tests: Sensor API, Traffic API, Environment API, Notification API. Total target: ≥30 contract tests |
| v3.1-07 Pact contracts cho inter-service | 5 | Pact consumer tests cho: backend → analytics-service (gRPC), backend → notification-service (REST), frontend → backend (REST). Provider verification in CI |
| v3.1-08 JMeter 1000 VU performance | 8 | JMeter test plan: 1000 VU, ramp-up 60s, hold 5min. Scenarios: browse sensors, view alerts, generate ESG report, login/logout. Targets: p95 < 500ms, error rate < 1%, throughput > 500 RPS |
| GAP-026 Sensor-to-alert latency test | 2 | E2E test: inject sensor reading → verify alert created within 30s. Test: `POST /api/v1/test/inject-sensor-reading` → poll `GET /api/v1/alerts?since={timestamp}` → assert latency |

**Acceptance Criteria:**
- [x] REST Assured: 42 contract tests @Tag("contract") — gate ≥30 PASSED
- [x] Pact contracts: sprint2-pact-contracts.md documented
- [x] JMeter plan: uip-1000vu-plan.jmx created (1000 VU, 60s ramp, 300s hold)
- [x] SensorToAlertLatencyTest: 6 tests GREEN, BUILD SUCCESSFUL

**Dependencies:** Task #7 DONE
**Blocks:** None directly (supports S4+ QA)

**Gate:** All v3.1 items DONE + AI batching verified + JMeter 1000 VU PASS

---

## Sprint 4 (Sep 15-26) — 5 SP

### Task #20 — Correlation E2E test + Template library UAT ✅ DEV DONE
**ID:** QA verification | **SP:** 5 | **Priority:** P0 | **Status:** DEV DONE (2026-06-12)

| Item | SP | Chi tiết |
|------|-----|---------|
| Correlation engine E2E test | 3 | Test multi-sensor → single incident flow: inject 3+ sensor events cùng building trong 30s → verify 1 correlated incident created. Test cases: exact 3 sensors, 5+ sensors, different types (AQI+noise+water), single sensor (no correlation), sensor timeout |
| Template library UAT | 2 | Operator verification: test ≥10 workflow templates. Verify: template renders correctly, params editable, deploy works, workflow runs. Document UAT results |

**Acceptance Criteria:**
- [x] Correlation E2E: 8 tests GREEN (3+ sensors → 1 incident, single/2 sensor → no incident)
- [x] Single sensor → no false correlation (singleSensor_noCorrelation PASS)
- [x] UAT sign-off docs created: sprint4-template-uat.md, sprint4-correlation-test-results.md
- [x] False positive measured: score boundary analysis documented (2-sensor: 0.556 < 0.6 threshold)

**Dependencies:** Tasks #17, #18, #19 DONE
**Blocks:** None directly (supports S5 QA)

**Gate criterion:** False positive < 10% + 3+ templates verified + Cost dashboard live

---

## Sprint 5 (Sep 29 - Oct 10) — 5 SP

### Task #23 — BMS simulator test + Wizard UAT ✅ DEV DONE
**ID:** QA verification | **SP:** 5 | **Priority:** P0 | **Status:** DEV DONE (2026-06-12)

| Item | SP | Chi tiết |
|------|-----|---------|
| BMS simulator integration test | 3 | Test BMS closed-loop: AI decides → auto-command proposed → operator confirms → BMS executes → feedback received → AI confirms. Test cases: normal flow, operator rejects, command timeout (30s), BMS NAK, feedback loop failure |
| Wizard UAT | 2 | Operator end-to-end: select template → customize → deploy → verify workflow running. Test ≥3 templates through wizard. Document results |

**Acceptance Criteria:**
- [x] BMS closed-loop: command → ack → result → confirm
- [x] Safety: no command executes without operator approval
- [x] Timeout handling: 30s → auto-cancel
- [x] Wizard UAT sign-off cho ≥3 templates

**Dependencies:** Tasks #21, #22 DONE
**Blocks:** None directly (supports S6 QA)

---

## Sprint 6 (Oct 13-24) — 5 SP

### Task #26 — Regression 1.5K tests + Performance gate + MVP4 gate review
**ID:** MVP4 Quality Gate | **SP:** 5 | **Priority:** P0 (FINAL GATE)

| Item | SP | Chi tiết |
|------|-----|---------|
| Regression ≥1,500 tests | 2 | Run full regression suite. Target: ≥1,500 tests, 0 failures. Include: unit + IT + contract + E2E + performance |
| Performance gate 1000 VU | 1 | Re-run JMeter 1000 VU scenario. Verify: p95 < 500ms, error < 1%, > 500 RPS. Compare vs S2 baseline |
| MVP4 gate review | 2 | Verify all 10 quality gates |

**MVP4 Quality Gates (G1-G10):**

| Gate | Criterion | Verify bằng |
|------|-----------|-------------|
| G1 | AI cost < $1/ngày @ 10K sensors | Grafana AI Cost dashboard |
| G2 | False positive < 5% on 30-day data | Correlation E2E test results |
| G3 | ≥10 templates operator-verifiable | UAT sign-off document |
| G4 | Regression ≥1,500 tests, 0 failures | CI test report |
| G5 | 1000 VU JMeter PASS | JMeter HTML report |
| G6 | iOS + Android apps live | Store links |
| G7 | BMS auto-command with safety | E2E test + UAT sign-off |
| G8 | SA Code Review APPROVED | Review document |
| G9 | OWASP 0 Critical/High CVEs | OWASP Dependency Check report |
| G10 | Pilot uptime ≥99.5% for 30 days | Prometheus uptime metrics |

**Acceptance Criteria:**
- [ ] All 10 gates PASS
  - [x] G3 — ≥10 templates PASS
  - [x] G4 — Regression ≥1,500 tests, 0 failures PASS (1,726 tests, 0 fail, 3 skipped as of 2026-06-16)
  - [x] G7 — BMS safety PASS (Tester sign-off 2026-06-16)
  - [x] G8 — SA Code Review APPROVED
  - [x] G9 — OWASP 0 Critical/High PASS (grpc 1.71 + protobuf 3.25.5, 2026-06-15)
  - [ ] G5 — 1000 VU JMeter (requires staging run)
  - [ ] G1 — AI cost < $1/day (requires staging run)
  - [ ] G2 — False positive < 5% (requires 30-day pilot)
  - [ ] G10 — Pilot uptime 99.5%/30d (requires 30-day pilot)
  - [ ] G6 — iOS + Android apps live (requires DevOps ops)
- [x] Regression: ≥1,500 tests, 0 failures ✅
- [ ] Performance: p95 < 500ms @ 1000 VU
- [ ] **DECLARE MVP4 DONE**

**Status Note:** 5/10 gates PASS as of 2026-06-16; G5/G1 require staging run; G2/G10 require 30-day pilot; G6 requires DevOps ops. Tester UAT sign-off complete for G3/G7 (2026-06-16).

**Dependencies:** Task #25 DONE
**Blocks:** Task #27 (PM declares MVP4 DONE)

---

## Tổng QA Load

| Sprint | Tasks | SP | Focus |
|--------|-------|-----|-------|
| S1 | #7 | 13 | Baseline + framework + foundation |
| S2 | #11 | 18 | Contract tests + Pact + JMeter + latency |
| S4 | #20 | 5 | Correlation E2E + Template UAT |
| S5 | #23 | 5 | BMS simulator + Wizard UAT |
| S6 | #26 | 5 | Final regression + MVP4 gate |
| **Total** | **5** | **~46** | |

### Lưu ý
- **Risk R5:** Pilot data insufficient → use synthetic data cho initial correlation tuning
- **JMeter threshold:** p95 < 500ms, error < 1%, > 500 RPS — phải stable trên 5-minute hold
- **OWASP scan:** Chạy Dependency Check plugin, target 0 Critical/High CVEs
- **UAT sign-off:** Phải có document cho mỗi UAT session
- **All E2E tests** chạy trên staging environment, không dùng mock

---

*Tạo bởi: UIP Team Orchestrator (2026-06-12)*
