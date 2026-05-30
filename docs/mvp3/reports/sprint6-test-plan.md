# Sprint 6 — QA Test Plan

**Created:** 2026-05-30 | **QA Engineer**
**Scope:** AI Workflow Designer + Flood Alert Pipeline + Infrastructure

---

## 1. Test Strategy

| Dimension | Approach |
|-----------|----------|
| **Unit Tests** | JUnit5 + Mockito — already implemented (58 tests PASS) |
| **Integration Tests** | Testcontainers (PostgreSQL + Kafka + Redis) |
| **API Contract Tests** | REST Assured + MockMvc |
| **Manual Tests** | UI walkthrough + E2E demo scenarios |
| **Regression** | Full suite 891+ tests baseline |

---

## 2. Test Matrix

| Feature | Unit | IT | API Contract | Manual | E2E |
|---------|------|----|--------------|--------|-----|
| WorkflowDefinition CRUD | 12 ✅ | 10 planned | 7 ✅ | 5 | - |
| DecisionRouter | 5 ✅ | - | - | 2 | - |
| FloodAlertConsumer | 5 ✅ | 4 planned | - | 3 | - |
| Flink FloodAlertJob | 24 ✅ | - | - | - | 1 |
| BPMN Visual Editor | - | - | - | 5 | - |
| Flood Alert UI | - | - | - | 5 | - |
| Blue-green Deploy | - | - | - | 2 | - |

---

## 3. AI Workflow IT Scenarios (10 tests)

### WorkflowDefinition CRUD ITs

| TC# | Scenario | Expected |
|-----|----------|----------|
| WF-IT-01 | Create workflow with valid BPMN XML | 201, version=1, isActive=true |
| WF-IT-02 | Create workflow with empty BPMN XML | 400, validation error |
| WF-IT-03 | List workflows — tenant isolation | Only see own tenant's workflows |
| WF-IT-04 | Get workflow by ID | 200, full BPMN XML returned |
| WF-IT-05 | Update workflow — bump version | 200, version incremented, deploymentId=null |
| WF-IT-06 | Delete workflow — soft delete | 204, isActive=false, not in list |
| WF-IT-07 | Deploy workflow to Camunda | 200, camundaDeploymentId set |
| WF-IT-08 | Deploy with invalid BPMN XML | 500 or 400, no deployment created |
| WF-IT-09 | Execute without deploy | 409, "must be deployed first" |
| WF-IT-10 | Concurrent update — optimistic lock | Last write wins, no data corruption |

---

## 4. Flood Alert Test Cases (15 scenarios)

### Threshold Boundary Tests (Flink unit — already PASS)

| TC# | Sensor Type | Value | Expected Severity |
|-----|------------|-------|-------------------|
| FL-TC-01 | RAINFALL | 49.9 | No alert |
| FL-TC-02 | RAINFALL | 50.0 | P2_ADVISORY |
| FL-TC-03 | RAINFALL | 80.0 | P1_WARNING |
| FL-TC-04 | RAINFALL | 120.0 | P0_EMERGENCY |
| FL-TC-05 | WATER_LEVEL | 1.9 | No alert |
| FL-TC-06 | WATER_LEVEL | 3.5 | P1_WARNING |
| FL-TC-07 | SOIL_MOISTURE | 87.0 | P1_WARNING |

### Consumer IT Scenarios

| TC# | Scenario | Expected |
|-----|----------|----------|
| FL-IT-01 | Valid flood event → persists alert | Alert saved with module=FLOOD |
| FL-IT-02 | Duplicate within 5 min → suppressed | No second alert |
| FL-IT-03 | After 5 min → new alert allowed | Second alert saved |
| FL-IT-04 | Invalid payload → DLQ | Message sent to DLQ topic |

### Severity Mapping

| Flink Severity | Alert Severity |
|---------------|---------------|
| P0_EMERGENCY | CRITICAL |
| P1_WARNING | HIGH |
| P2_ADVISORY | WARNING |

### False Positive Prevention

| TC# | Scenario | Expected |
|-----|----------|----------|
| FP-01 | Only 2 consecutive readings | No alert (need ≥3) |
| FP-02 | 3 readings > P2 but < 10 min gap | Alert generated (within window) |
| FP-03 | Different sensors, same zone | Independent tracking |

---

## 5. Manual Test Cases for Tester (20 scenarios)

### AI Workflow Designer (5 tests)

| TC# | Scenario | Steps | Expected |
|-----|----------|-------|----------|
| MT-01 | Create new workflow | Click "New Workflow" | Canvas shows Start Event |
| MT-02 | Save workflow | Modify canvas → click Save | "Saved (v1)" confirmation |
| MT-03 | Deploy workflow | Click "Deploy" | "Deployed to Camunda!" |
| MT-04 | Delete with confirm | Click Delete → confirm | Workflow removed from list |
| MT-05 | Load existing workflow | Click workflow in list | BPMN loads in modeler |

### Flood Alert UI (5 tests)

| TC# | Scenario | Steps | Expected |
|-----|----------|-------|----------|
| MT-06 | Flood alert card display | Open Alerts page → filter FLOOD | Card shows severity badge + value |
| MT-07 | Water level gauge | View flood alert detail | Vertical gauge with P0/P1/P2 markers |
| MT-08 | Map overlay | Switch to map view | Colored circles at sensor locations |
| MT-09 | Alert severity colors | Check P0, P1, P2 alerts | Red, orange, blue respectively |
| MT-10 | Alert acknowledge | Click "Acknowledge" on flood alert | Status changes to ACKNOWLEDGED |

### Flood Alert E2E (5 tests)

| TC# | Scenario | Steps | Expected |
|-----|----------|-------|----------|
| E2E-01 | Inject 3 readings | Run demo-flood-alert.sh | Alert appears <30s |
| E2E-02 | Verify alert in DB | GET /alerts?module=FLOOD | Alert persisted with correct severity |
| MT-11 | Alert in Operations Center | Open dashboard during inject | Real-time alert notification |
| MT-12 | Map overlay updates | During flood alert | Circle marker appears on map |
| MT-13 | Dedup prevention | Inject same sensor 2x within 5 min | Only 1 alert |

### Infrastructure (5 tests)

| TC# | Scenario | Steps | Expected |
|-----|----------|-------|----------|
| MT-14 | EMQX health check | curl emqx:18083/status | 200 OK |
| MT-15 | Blue-green status | ./blue-green-switch.sh status | Shows active slot |
| MT-16 | Blue-green switch | ./blue-green-switch.sh switch | Switch <30s |
| MT-17 | Blue-green rollback | ./blue-green-switch.sh rollback | Returns to original |
| MT-18 | Kafka topics verified | kafka-topics --list | Flood + BMS topics present |

---

## 6. Quality Gates

| Gate | Criterion | Threshold | Status |
|------|-----------|-----------|--------|
| G1 | Unit tests | 58 new tests PASS | ✅ PASS |
| G2 | IT tests | All Testcontainers tests PASS | ⏳ Pending |
| G3 | Regression | 0 new failures (baseline: 99 pre-existing) | ⏳ Pending |
| G4 | JaCoCo LINE | ≥77% | ⏳ Pending |
| G5 | JaCoCo BRANCH | ≥62% | ⏳ Pending |
| G6 | TypeScript | 0 errors | ✅ PASS |
| G7 | Flood alert latency | <30s E2E | ⏳ Manual |
| G8 | No false P0 alerts | Zero in test suite | ✅ PASS (unit) |

---

## 7. Risk Assessment — GO/NO-GO

| Risk | Impact | Mitigation | GO/NO-GO |
|------|--------|------------|----------|
| SA 3 CRITICAL bugs | Block deploy | ✅ FIXED (migration, renumber, DTO) | ✅ GO |
| 99 pre-existing test failures | Noise in regression | Known from Sprint 5 — Camunda + IT infra | ⚠️ Conditional |
| Flood E2E needs Flink running | Demo dependency | Fallback: inject-flood-alert bypass | ✅ GO |
| BPMN Modeler untested in browser | Unknown edge cases | Manual tester verifies | ⚠️ Need manual |
| Blue-green script untested on Docker | Deploy risk | Script has dry-run mode | ⚠️ Need Docker |

### GO/NO-GO Recommendation

**CONDITIONAL GO for staging deploy:**
- ✅ 3 CRITICAL SA findings FIXED
- ⏳ IT tests must be written + PASS
- ⏳ Manual tester must verify 20 scenarios
- ⏳ 99 pre-existing failures documented as known baseline (not Sprint 6 regression)

**NO-GO for production:**
- All GO factors + PO demo approval required
