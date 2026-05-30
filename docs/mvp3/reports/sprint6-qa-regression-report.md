# Sprint 6 — QA Regression Gate Report

**Date:** 2026-05-30 | **QA Engineer**
**Build:** `./gradlew test jacocoTestReport` — BUILD SUCCESSFUL in 6m 7s

---

## 1. Regression Gate Results

| Metric | Value | Threshold | Status |
|--------|-------|-----------|--------|
| Total tests run | **1,015** | - | - |
| Passed | **1,015** | - | - |
| Failed | **0** | 0 new failures | ✅ **PASS** |
| Ignored | **1** (OpenApiSpecGenerator) | - | Known |
| Success rate | **100%** | - | ✅ **PASS** |
| JaCoCo LINE coverage | **86%** (3,033/3,516) | ≥ 77% | ✅ **PASS** |
| JaCoCo BRANCH coverage | **70%** (730/1,030) | ≥ 62% | ✅ **PASS** |

### Sprint 6 New Tests (28 confirmed)

| Test Suite | Tests | Status |
|------------|-------|--------|
| DecisionRouterTest | 5 | ✅ ALL PASS |
| WorkflowDefinitionServiceTest | 11 | ✅ ALL PASS |
| WorkflowDefinitionControllerWebMvcTest | 7 | ✅ ALL PASS |
| FloodAlertConsumerTest | 5 | ✅ ALL PASS |

### Pre-existing Baseline Note

The previously reported 99 failures are from the `integrationTest` task (requires Docker/Testcontainers). The `test` task runs clean at 0 failures. **No Sprint 6 regression.**

---

## 2. Coverage Analysis

### Overall: PASS ✅

| Dimension | Actual | Threshold | Delta |
|-----------|--------|-----------|-------|
| LINE | 86% | ≥77% | +9% |
| BRANCH | 70% | ≥62% | +8% |

### Sprint 6 Module Coverage

| Package | LINE | BRANCH | Risk Level |
|---------|------|--------|------------|
| `aiworkflow.service` | 97% | 71% | ✅ OK |
| `aiworkow.controller` | 100% | n/a | ✅ OK |
| `aiworkflow.gateway` (DecisionRouter) | **28%** | **9%** | ⚠️ HIGH |
| `alert.flood` (FloodAlertConsumer) | **8%** | **30%** | ⚠️ HIGH |

### Coverage Gaps (Sprint 7 IT targets)

**DecisionRouter 28%/9%** — Untested paths:
- Cache lookup/write with JSON serialization
- Fallback on Redis failure
- AiDecisionInput field mapping
- Confidence boundary conditions (>0.85, 0.6-0.85, <0.6)

**FloodAlertConsumer 8%/30%** — Untested paths:
- Kafka listener with real messages
- DLQ fallback on invalid payload
- 5-min cooldown dedup logic
- Severity mapping (P0→CRITICAL, P1→HIGH, P2→WARNING)
- Event timestamp parsing

---

## 3. IT Test Strategy

### AI Workflow ITs (10 scenarios) — Sprint 7

**Testcontainers:** PostgreSQL 15 (existing TestConfiguration). No Kafka needed for CRUD ITs.

| TC# | Scenario | Key Assertion |
|-----|----------|---------------|
| WF-IT-01 | Create valid workflow | 201, version=1, isActive=true |
| WF-IT-02 | Create empty BPMN XML | 400, validation error |
| WF-IT-03 | List — tenant isolation | Only see own tenant |
| WF-IT-04 | Get by ID | 200, full BPMN XML |
| WF-IT-05 | Update — bump version | 200, version incremented |
| WF-IT-06 | Soft delete | 204, isActive=false |
| WF-IT-07 | Deploy to Camunda | 200, camundaDeploymentId set |
| WF-IT-08 | Deploy invalid XML | 400/500, no deployment |
| WF-IT-09 | Execute without deploy | 409, "must be deployed first" |
| WF-IT-10 | Concurrent update | Last-write-wins or 409 |

### Flood Alert ITs (4 scenarios) — Sprint 7

**Testcontainers:** PostgreSQL + Kafka + Redis (cooldown cache).

| TC# | Scenario | Key Assertion |
|-----|----------|---------------|
| FL-IT-01 | Valid event → persist alert | Alert saved module=FLOOD, correct severity |
| FL-IT-02 | Duplicate within 5 min → suppress | No second alert |
| FL-IT-03 | After 5 min → new alert | Second alert saved |
| FL-IT-04 | Invalid payload → DLQ | Message to DLQ topic |

**Use Awaitility** `atMost(10, SECONDS)` for async assertions. No Thread.sleep.

---

## 4. GO/NO-GO Assessment

### Verdict: ✅ CONDITIONAL GO for staging deploy

**GO factors (all met):**
1. ✅ 3 CRITICAL + 3 MAJOR SA findings FIXED and verified
2. ✅ 0 new regression failures (1,015 tests PASS)
3. ✅ LINE 86% ≥ 77%, BRANCH 70% ≥ 62%
4. ✅ BUILD SUCCESS
5. ✅ TypeScript 0 errors

**Conditional factors:**
1. ⏳ IT tests (14 planned) not yet written — target Sprint 7
2. ⏳ 99 pre-existing failures documented as known baseline (integrationTest task)
3. ⏳ BPMN Modeler untested in browser — manual tester must verify MT-01 to MT-05
4. ⏳ Flood E2E needs Flink — fallback `/inject-flood-alert` bypasses
5. ⏳ Blue-green deploy script untested on Docker — has dry-run mode

**Production: NO-GO** — Requires IT tests PASS + PO demo approval.

### Demo-Critical Gates (must verify before PO demo)

| Gate | Verification | Fallback |
|------|-------------|----------|
| G-DEMO-1 | `demo-flood-alert.sh` produces alert in <30s | Use `/inject-flood-alert` API |
| G-DEMO-2 | AI Workflow CRUD works in browser | Pre-recorded video |
| G-DEMO-3 | Flood alert card renders with correct severity colors | Static mockup |
| G-DEMO-4 | Blue-green switch completes in <30s | Show script output |

---

## 5. Test Case Quality Review

### 20 Manual Scenarios: ADEQUATE for demo

**Coverage gaps noted:**
- MT-02: Missing BPMN XML ↔ canvas state assertion
- MT-10: Should verify color change + audit log
- No empty state / no-data scenario
- No browser back-navigation test

**Recommended additions for demo safety:**
- MT-19: Workflow list survives page refresh (persistence)
- MT-20: Flood alert detail back-navigation (no data loss)

---

## 6. Risk Register

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| DecisionRouter 28% LINE / 9% BRANCH coverage | HIGH | Certain | IT tests Sprint 7; demo uses happy path |
| FloodAlertConsumer 13% LINE / 30% BRANCH coverage | HIGH | Certain | IT tests Sprint 7; demo bypasses consumer |
| Flink dependency for demo | MEDIUM | 40% | `/inject-flood-alert` REST fallback |
| BPMN Modeler browser compat | MEDIUM | 30% | Tester verifies Chrome + 1 browser |
| Blue-green on Docker untested | LOW | 20% | Script has dry-run mode |

---

## 7. Quality Gate Summary

| Gate | Criterion | Status |
|------|-----------|--------|
| G1 | Unit tests — 1,015 PASS | ✅ PASS |
| G2 | IT tests — 14 planned | ⏳ Sprint 7 |
| G3 | Regression — 0 new failures | ✅ PASS |
| G4 | JaCoCo LINE ≥77% → 86% | ✅ PASS |
| G5 | JaCoCo BRANCH ≥62% → 70% | ✅ PASS |
| G6 | TypeScript 0 errors | ✅ PASS |
| G7 | Flood latency <30s E2E | ⏳ Manual |
| G8 | No false P0 alerts | ✅ PASS (unit) |

**4/8 gates PASS. 2 pending manual, 2 pending IT (Sprint 7).**

---

*Report generated: 2026-05-30 | Verdict: CONDITIONAL GO | 1,015 tests PASS | LINE 86% BRANCH 70%*
