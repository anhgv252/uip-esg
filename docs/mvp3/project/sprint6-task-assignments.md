# Sprint MVP3-6 — Task Assignments

**Created:** 2026-05-29
**Sprint:** 2026-06-02 → 2026-06-13
**Total Committed:** 66 SP across 6 roles (7 people)

---

## Verification Summary

| Role | Tasks | Tier 1 SP | Tier 2 SP | Total SP |
|------|-------|-----------|-----------|----------|
| **Backend-1** (AI Workflow) | 4 | 9 | 2 | 11 |
| **Backend-2** (Flood Alert + Push) | 5 | 12 | 5 | 17 |
| **Frontend** | 5 | 10 | 13 | 23 |
| **DevOps** | 3 | 8 | 0 | 8 |
| **QA** | 5 | 7 | 1 | 8 |
| **SA** (Day 1-2 spike) | 1 | 2 | 0 | 2 |
| **PM** | 1 | 2 | 0 | 2 |
| **Total** | **24** | **50** | **21** | **71** |

> **Note:** Total 71 SP includes SA spike + PM demo script. Net implementation = 66 SP.

---

## Team Roster & Capacity

| Role | Member | Capacity | Tier 1 | Tier 2 | Load |
|------|--------|----------|--------|--------|------|
| **Backend-1** (AI Workflow) | Backend Engineer A | ~12 SP | 9 SP | 2 SP | 92% |
| **Backend-2** (Flood Alert + Push) | Backend Engineer B | ~12 SP | 12 SP | 5 SP | 142% ⚠️ |
| **Frontend** | Frontend Engineer | ~10 SP | 10 SP | 13 SP | 230% ⚠️⚠️ |
| **QA** | QA Engineer | ~8 SP | 7 SP | 1 SP | 100% |
| **DevOps** | DevOps Engineer | ~5 SP | 8 SP | 0 SP | 160% ⚠️ |
| **SA** (Day 1-2 only) | Solution Architect | ~2 SP | 2 SP | 0 SP | spike |
| **PM** | Project Manager | — | 2 SP | 0 SP | continuous |

> Backend-1 split: AI Workflow (9 SP) + Mobile Auth (2 SP Tier 2). Frontend là bottleneck — priority order nghiêm ngặt.

---

## SA — Architecture Spikes (Day 1-2)

### SA-1: ADR-030 AI Workflow Architecture [2 SP]

**Status:** ADR APPROVED — key findings:

| Finding | Impact |
|---------|--------|
| Camunda 7 embedded đã có (39 files, 7 processes) | KHÔNG cần InProcessWorkflowAdapter mới |
| `ClaudeApiService` + `RuleBasedFallbackDecisionService` đã có | B1-3 giảm từ 3 SP → 1.5 SP |
| `bpmn-js@18.15.0` đã trong package.json | FE-1 giảm effort |
| API path conflict: `/api/v1/workflow` (existing) vs `/api/v1/workflows` (plan) | Resolve: dùng existing `/api/v1/workflow` endpoints + thêm mới |
| `AiWorkflowPage.tsx` + `BpmnViewer.tsx` + workflow hooks đã có | FE-1 base layer done |

**Decision:** Tiếp tục Camunda 7 embedded. Không viết lại engine. Focus effort vào visual designer + metadata CRUD.

**ADR file:** `docs/mvp3/architecture/ADR-030-ai-workflow-architecture.md`

### SA-2: ADR-031 Mobile Stack (draft) [Tier 2]
**ADR file:** `docs/mvp3/architecture/ADR-031-mobile-stack.md`

### SA-3: ADR-034 Structural Monitoring (draft for Sprint 7)
**ADR file:** `docs/mvp3/architecture/ADR-034-structural-monitoring.md`

---

## BACKEND-1 — AI Workflow + Mobile Auth (Backend Engineer A)

**Focus:** AI Workflow CRUD + AI Decision Gateway + Mobile Auth endpoint
**Package base:** `com.uip.backend.aiworkflow.*` (new) + reuse `com.uip.backend.workflow.*` (existing Camunda)

### Task B1-1: ADR-030 Review [part of SA-1, 1 SP] — DONE
- Pair với SA review bpmn-js + Camunda integration
- **Result:** ADR approved. Camunda retained. No engine rewrite needed.

### Task B1-2: WorkflowDefinition CRUD + Deploy to Camunda [S6-AI02, 5 SP]
Priority: P0 | Sprint Day 2-5 | Dependencies: SA-1 (ADR approved ✅)

**AC:**
| # | AC | Notes |
|---|---|---|
| 1 | `POST /api/v1/workflows` — create workflow (name, description, BPMN XML) | New entity `WorkflowDefinition` |
| 2 | `GET /api/v1/workflows` — list (tenant-isolated, paginated) | RLS per tenant_id |
| 3 | `GET /api/v1/workflows/{id}` — get definition + BPMN XML | Return full XML for frontend modeler |
| 4 | `PUT /api/v1/workflows/{id}` — update name/description/XML | Bump version |
| 5 | `DELETE /api/v1/workflows/{id}` — soft delete | Set is_active=false |
| 6 | `POST /api/v1/workflows/{id}/deploy` — deploy to Camunda | Camunda RepositoryService.createDeployment() |
| 7 | `POST /api/v1/workflows/{id}/execute` — start process instance | Reuse existing WorkflowService.startProcess() |

**Technical:**
- V28 migration: `ai_workflow.workflow_definitions` (schema new, không đụng ACT_* của Camunda)
- **Không cần** `workflow_executions` table — Camunda `ACT_HI_PROCINST` handles execution history
- Entity: UUID PK, tenant_id, name, description, bpmnXml (TEXT), version, isActive, camundaDeploymentId
- Controller at `/api/v1/workflows` (new, không conflict với existing `/api/v1/workflow`)
- Deploy: parse BPMN XML → Camunda `RepositoryService.createDeployment().addString().deploy()`
- Execute: lookup Camunda process key from deployment → `RuntimeService.startProcessInstanceByKey()`

**Files to create:**
- `backend/src/main/resources/db/migration/V28__ai_workflow.sql`
- `backend/src/main/java/com/uip/backend/aiworkflow/model/WorkflowDefinition.java`
- `backend/src/main/java/com/uip/backend/aiworkflow/repository/WorkflowDefinitionRepository.java`
- `backend/src/main/java/com/uip/backend/aiworkflow/service/WorkflowDefinitionService.java`
- `backend/src/main/java/com/uip/backend/aiworkflow/controller/WorkflowDefinitionController.java`
- `backend/src/test/.../WorkflowDefinitionServiceTest.java`
- `backend/src/test/.../WorkflowDefinitionControllerWebMvcTest.java`

**Test strategy:**
- Unit: WorkflowDefinitionServiceTest (CRUD logic + deploy to Camunda mock)
- WebMvc: WorkflowDefinitionControllerWebMvcTest (API contract)
- IT: Defer to QA regression (Sprint 6 Day 10)

### Task B1-3: AI Decision Gateway — Confidence Routing [S6-AI03, 1.5 SP — REDUCED]
Priority: P1 | Sprint Day 4-5 | Dependencies: B1-2

**What already exists (SA discovery):**
- `ClaudeApiService` — Claude API call + CB + fallback ✅
- `RuleBasedFallbackDecisionService` — rule-based fallback ✅
- `AIDecision` DTO — decision, reasoning, confidence, severity ✅

**What is NEW (gaps):**
- `DecisionRouter` — confidence-based routing: >0.85 auto-execute, 0.6-0.85 operator queue, <0.6 escalate
- Redis cache for similar decisions (TTL 15 min, key = scenarioKey + hash(context))
- Wire routing logic into `AIAnalysisDelegate` (existing Camunda delegate)

**Files to create/modify:**
- `backend/src/main/java/com/uip/backend/aiworkflow/gateway/DecisionRouter.java` (NEW)
- Modify: `backend/src/main/java/com/uip/backend/workflow/delegate/AIAnalysisDelegate.java` (add routing)
- `backend/src/test/.../DecisionRouterTest.java` (NEW — 5 tests for confidence thresholds)

### Task B1-4: Mobile Auth Config Endpoint [S6-M03, 2 SP] — Tier 2
Priority: P1 | Sprint Day 8 | Dependencies: None

**AC:**
- `GET /api/v1/mobile/auth/config` → `{issuer, clientId, scopes, redirectUri}`
- Public endpoint (no auth) — add to SecurityConfig permitAll
- Tenant via query param `?tenantId=xxx` (no JWT before login)

**Files:**
- `backend/src/main/java/com/uip/backend/auth/api/MobileAuthConfigController.java` (NEW)
- `backend/src/main/java/com/uip/backend/auth/api/dto/MobileAuthConfigResponse.java` (NEW)
- Modify: SecurityConfig — add `/api/v1/mobile/auth/config` to permitAll
- `backend/src/test/.../MobileAuthConfigControllerTest.java` (NEW)

---

## BACKEND-2 — Flood Alert + Python Retry + Push (Backend Engineer B)

**Focus:** Flink CEP Flood Alert + Kafka Consumer + Demo + Python Auto-retry + FCM/APNs
**Package base:** `com.uip.backend.alert.flood.*`, `com.uip.backend.forecast.*`, `com.uip.backend.notification.*`

### Task B2-1: Flink CEP Flood Alert Job [S6-FL01, 5 SP]
Priority: P0 | Sprint Day 1-4 | Dependencies: None

**AC:**
- Flink CEP job consumes `ngsi_ld_environment`, filters RAINFALL/WATER_LEVEL/SOIL_MOISTURE
- Pattern: 3 consecutive readings > threshold within 10 min → `FloodAlertEvent`
- Output to Kafka `UIP.flink.alert.flood.v1`
- Severity: P0/P1/P2 per TCVN 9386:2012 thresholds

**Technical:**
- Add `flink-cep` dependency to `flink-jobs/pom.xml`
- Reuse boilerplate from `AlertDetectionJob.java` (KafkaSource, RocksDB, checkpoint)
- KeyBy sensorId for independent tracking per sensor

**Files to create:**
- `flink-jobs/src/main/java/com/uip/flink/flood/FloodAlertEvent.java`
- `flink-jobs/src/main/java/com/uip/flink/flood/ThresholdCondition.java`
- `flink-jobs/src/main/java/com/uip/flink/flood/FloodAlertJob.java`
- `flink-jobs/src/test/java/com/uip/flink/flood/FloodAlertJobTest.java` (9 threshold + 6 pattern tests)

### Task B2-2: Flood Alert Kafka Consumer [S6-FL02, 3 SP]
Priority: P0 | Sprint Day 3-5 | Dependencies: B2-1

**AC:**
- `FloodAlertConsumer` listens `UIP.flink.alert.flood.v1` → persist AlertEvent module=FLOOD
- Severity mapping: P0→CRITICAL, P1→HIGH, P2→WARNING (compatible với existing queries)
- Redis PUBLISH for SSE push (reuse pattern từ `AlertEventKafkaConsumer`)
- Dedup 5-min window, DLQ fallback

**Files to create:**
- `backend/src/main/java/com/uip/backend/alert/flood/FloodAlertConsumer.java`
- `backend/src/test/java/com/uip/backend/alert/flood/FloodAlertConsumerTest.java` (4 tests)

**Minimal existing file changes:**
- `AlertEvent` entity: add `location` field (optional, for map overlay)

### Task B2-3: Flood Alert Demo Scenario [S6-FL04, 2 SP]
Priority: P0 | Sprint Day 5-6 | Dependencies: B2-1 + B2-2

**AC:**
- `POST /api/v1/test/inject-reading` (test-only, `@Profile("test")`)
- Demo script: inject 3 RAINFALL >80mm/h readings → alert appears <30s
- Seed 3 flood sensors pre-registered

**Files:**
- `backend/src/main/java/com/uip/backend/alert/flood/FloodTestController.java`
- `scripts/demo-flood-alert.sh`

### Task B2-4: Python Forecast Auto-retry [S6-OPS2, 2 SP]
Priority: P2 | Sprint Day 2 | Dependencies: None

**AC:**
- `@Scheduled(fixedDelay=300000)` health check Python `/actuator/health`
- Python DOWN → log WARN (naive already serving)
- Python UP after DOWN → clear Redis `forecasts::*` cache → auto-recover

**Files:**
- `backend/src/main/java/com/uip/backend/forecast/ForecastHealthChecker.java`
- `backend/src/test/java/com/uip/backend/forecast/ForecastHealthCheckerTest.java` (5 tests)

### Task B2-5: FCM + APNs Push Backend [S6-M04, 5 SP] — Tier 2
Priority: P1 | Sprint Day 8-10 | Dependencies: None

**AC:**
- `FcmAdapter` implements `NotificationChannel` — `firebase-admin:9.3.0`
- `ApnsAdapter` implements `NotificationChannel` — `pushy:0.15.x`
- Both `@ConditionalOnProperty` (no-op when not configured)
- Invalid token auto-cleanup
- Reuse existing `PushSubscription` table (platform="fcm"/"apns") — **no V29 migration needed**

**Files to create:**
- `backend/src/main/java/com/uip/backend/notification/service/FcmAdapter.java`
- `backend/src/main/java/com/uip/backend/notification/service/ApnsAdapter.java`
- `backend/src/test/java/com/uip/backend/notification/service/FcmAdapterTest.java` (4 tests)
- `backend/src/test/java/com/uip/backend/notification/service/ApnsAdapterTest.java` (4 tests)

---

## FRONTEND — BPMN Editor + Flood Alert + Push Page + Mobile (Frontend Engineer)

**Focus:** AI Workflow visual editor + Flood Alert UI + Push Settings + React Native scaffold
**⚠️ BOTTLENECK: 23 SP vs 10 SP capacity — strict priority order**

**Priority order (KHÔNG đổi):**
```
FE-1 (BPMN Modeler, 5 SP) → FE-2 (Flood Alert, 3 SP) → FE-3 (Push Page, 1 SP)
→ FE-4 (RN Scaffold, 8 SP) → FE-5 (PKCE Login, 5 SP)
```

### Task FE-1: BPMN Visual Editor [S6-AI04, 5 SP] — ACTUAL: 3-4 SP
Priority: P0 | Sprint Day 2-6

**What ALREADY EXISTS (SA discovery):**
- `bpmn-js@18.15.0` in package.json ✅
- `BpmnViewer.tsx` — read-only viewer ✅
- `AiWorkflowPage.tsx` — 3 tabs dashboard ✅
- `useWorkflowData.ts` — React Query hooks ✅
- `api/workflow.ts` — API client types ✅

**What to BUILD:**
1. `WorkflowModeler.tsx` — bpmn-js **Modeler** (not Viewer) with containerRef, importXML/saveXML
2. `NodePalette.tsx` — Custom palette: Start Event, Service Task, AI Decision Gateway, Notification Task, End Event
3. `AiNodeConfigPanel.tsx` — Config for AI nodes: prompt (TextField), confidenceThreshold (Slider 0-1), model (Select)
4. BPMN Moddle extension JSON — `ai:prompt`, `ai:confidenceThreshold`, `ai:model`
5. Integrate into existing `AiWorkflowPage.tsx` — add "Designer" tab

**No new npm deps** — bpmn-js already installed. Custom panel via MUI (no bpmn-js-properties-panel).

**Files to create:**
- `frontend/src/components/workflow/WorkflowModeler.tsx`
- `frontend/src/components/workflow/NodePalette.tsx`
- `frontend/src/components/workflow/AiNodeConfigPanel.tsx`
- `frontend/src/components/workflow/bpmn-moddle.json`

### Task FE-2: Flood Alert Cards + Map Overlay [S6-FL03, 3 SP] — ACTUAL: 2-3 SP
Priority: P0 | Sprint Day 5-6

**What ALREADY EXISTS:**
- `AlertsPage.tsx` — full alert page with filters, SSE, ack/escalate ✅
- `useAlertManagement.ts` + `useAlertStream.ts` ✅
- `SensorMap.tsx` + `SensorMarker.tsx` — Leaflet map ✅

**What to BUILD:**
1. `FloodAlertCard.tsx` — severity badge, location, water level gauge, mini-chart
2. `FloodRiskMapOverlay.tsx` — Leaflet overlay, color-coded severity zones
3. `WaterLevelGauge.tsx` — vertical gauge with P0/P1/P2 threshold markers
4. Extend `useAlerts` — add `module=FLOOD` filter
5. Add "Alert Type" filter to AlertsPage + map view mode

**No new npm deps** — react-leaflet + recharts already installed.

### Task FE-3: Push Subscription Settings Page [S6-C02, 1 SP] — ACTUAL: 0.5-1 SP
Priority: P2 | Sprint Day 5

**What ALREADY EXISTS:**
- `api/pushSubscription.ts` — full API client ✅
- `hooks/usePushSubscription.ts` — React Query hooks ✅
- `pwa/vapid.ts` + `sw-register.ts` — Web Push setup ✅

**What to BUILD (only UI):**
1. `NotificationSettingsPage.tsx` — permission status, subscribe/unsubscribe buttons
2. `PushSubscriptionList.tsx` — list active subscriptions
3. Add route `/settings/notifications` to router

### Task FE-4: React Native + Expo Scaffold [S6-M01, 8 SP] — Tier 2
Priority: P1 | Sprint Day 7-10 | **DEFER to Sprint 7 if FE-1/2/3 not done by Day 7**

**AC:**
- `applications/operator-mobile/` — Expo SDK 51 + React Native 0.74
- 4 tabs: Dashboard / Alerts / Controls / Profile
- Shared hooks: `useAlerts`, `useSensors`, `useBuildingList`
- `npx expo start` runs

**New npm deps (in operator-mobile/package.json):**
- `expo@51.x`, `react-native@0.74.x`, `@react-navigation/native`, `@react-navigation/bottom-tabs`
- `@tanstack/react-query`, `expo-secure-store`, `react-native-safe-area-context`, `react-native-screens`

### Task FE-5: Keycloak PKCE Login [S6-M02, 5 SP] — Tier 2
Priority: P1 | Sprint Day 9-10 | Dependencies: FE-4

**AC:**
- Login screen → `expo-auth-session` PKCE → Keycloak → JWT → SecureStore
- Tenant selection → dashboard loads

**Files:**
- `src/screens/auth/LoginScreen.tsx`
- `src/hooks/useAuthMobile.ts`
- `src/context/AuthContext.tsx`

---

## DEVOPS — EMQX + Blue-green + Grafana (DevOps Engineer)

### Task OPS-1: EMQX MQTT Production [S6-C01, 5 SP]
Priority: P1 | Sprint Day 1-3

**AC:**
- EMQX rule engine: `bms/commands/#` → Kafka
- MQTT auth: username/password per tenant
- Device heartbeat: `bms/heartbeat/{deviceId}` → status ONLINE/UNKNOWN

**Files to modify:**
- `infrastructure/docker-compose.yml` — EMQX env vars
- `infrastructure/emqx/emqx.conf` — auth + rule engine
- `infrastructure/kafka/create-topics.sh` — add `UIP.bms.command.ack.v1`

### Task OPS-2: Blue-green Deploy Validation [S6-OPS1, 3 SP]
Priority: P0 | Sprint Day 5-6

**AC:**
- 2 backend instances (blue/green) in docker-compose
- `scripts/blue-green-switch.sh` — switch nginx upstream
- Rollback <30s verified

**Files to create/modify:**
- `infrastructure/docker-compose.yml` — add `uip-backend-green`
- `frontend/nginx.conf` — upstream switch support
- `scripts/blue-green-switch.sh` — deploy + rollback script

### Task OPS-3: Grafana AI Workflow + Flood Alert Panels
Priority: P1 | Sprint Day 6-7

**Files to modify:**
- `infra/monitoring/grafana/dashboards/uip-services.json` — add 3 panels
- `infra/monitoring/prometheus.yml` — Flink scrape target

---

## QA — Regression + AI Workflow Tests + Flood Alert Tests (QA Engineer)

### Task QA-1: Sprint 6 Regression Gate [S6-QA1, 3 SP]
Priority: P0 | Day 1 baseline + Day 10 gate

**Scope:**
- Day 1: Run full regression (1,686+ tests) — baseline PASS
- Day 10: Full regression — 0 new failures
- JaCoCo LINE ≥77%, BRANCH ≥62%
- Manual regression: 30 carry-over TCs from Sprint 5

### Task QA-2: AI Workflow Test Plan [2 SP]
Priority: P0 | Day 3-5

**New automated tests: 30 TCs**
- Workflow CRUD IT: 10 scenarios (create, list, get, invalid XML, auth, tenant isolation, execute, concurrent)
- AI Decision Node: 9 unit tests (confidence routing, Claude fallback, timeout, CB)
- BPMN Parsing: 8 unit tests (valid, invalid, missing nodes, large XML, AI node)
- Execution status: 3 unit tests (PENDING→RUNNING→COMPLETED/FAILED)

### Task QA-3: Flood Alert Test Plan [2 SP]
Priority: P0 | Day 4-6

**New automated tests: 33 TCs**
- Flink CEP pattern: 15 unit tests (threshold boundaries, false positive, dedup, severity mapping)
- Alert latency: 3 IT tests (<10s DB, <30s SSE)
- Alert regression: 5 IT tests (acknowledge, escalate, SSE include flood, GET filter)
- Demo scenario: 6 manual steps + 1 E2E

### Task QA-4: BMS ITs Supplement [S6-QA2, 2 SP]
Priority: P1 | Day 7

**New Testcontainers ITs: 5 TCs**
- BMS-TC-11: MQTT ACK → status update
- BMS-TC-12: Concurrent 5 commands
- BMS-TC-13: DLQ overflow
- BMS-TC-14: Device offline → UNKNOWN
- BMS-TC-15: Cross-tenant command → 403

### Task QA-5: Mobile + Push Test Plan [1 SP] — Tier 2
Priority: P1 | Day 8-9

**New tests: 10 TCs**
- Push delivery: 5 unit tests (FCM mock, invalid token, multi-platform, SSE degradation)
- Mobile PKCE: 5 IT tests (config endpoint, JWT claims, refresh, 401, public access)

### Sprint 6 New Test Count

| Task | Unit | IT | E2E | Manual | Total |
|------|------|----|-----|--------|-------|
| QA-2 AI Workflow | 20 | 10 | 0 | 0 | 30 |
| QA-3 Flood Alert | 18 | 8 | 1 | 6 | 33 |
| QA-4 BMS ITs | 0 | 5 | 0 | 0 | 5 |
| QA-5 Mobile/Push | 5 | 5 | 0 | 0 | 10 |
| QA-1 Regression | 0 | 0 | 0 | 30 | 30 |
| **Total** | **43** | **28** | **1** | **36** | **108** |

**Projected end-of-sprint: 1,686 + 108 = ~1,794 tests**

---

## PM — Demo Script + Close-out (Project Manager)

### Task PM-1: Sprint 6 Demo Script + PO Dry-run [S6-PM1, 2 SP]
Priority: P0 | Day 9-10

**Demo scenarios:**
1. AI Workflow Designer: Create flood workflow → AI Decision node → execute → Claude response
2. Flood Alert E2E: Inject sensor data → Flink detects → alert <30s → SSE push → ack
3. EMQX MQTT: BMS command → MQTT → ACK → status ONLINE
4. Blue-green deploy: Deploy v2 → switch → rollback <30s
5. (Tier 2) Mobile: Login → alerts → push notification

**Files:**
- `docs/mvp3/project/sprint6-po-demo-script.md`
- Pre-demo checklist (12 items)
- Q&A guide for PO
- Backup plan

### Task PM-2: Sprint 6 Close-out Report Template
Priority: P1 | Day 10

**Sections:** Gate summary, Deliverables, Task completion, QA sign-off, SA sign-off, Known limitations, Demo readiness
**File:** `docs/mvp3/reports/sprint6-closeout-po-report.md`

---

## Timeline Summary (10 ngày)

```
Day 1   (06-02): SA spike ADR-030 + EMQX config (OPS-1) + Flink CEP design (B2-1) + Regression baseline (QA-1)
Day 2   (06-03): ADR-030 merge + BPMN Backend CRUD (B1-2) + Flink CEP implement (B2-1) + Python retry (B2-4)
Day 3-4 (06-04→05): BPMN Backend deploy+execute (B1-2) + AI Decision routing (B1-3)
                  + Flood Consumer (B2-2) + bpmn-js Modeler start (FE-1)
Day 5-6 (06-06→07): AI Workflow FE modeler+palette (FE-1) + Flood Alert FE cards (FE-2)
                  + Push Settings FE (FE-3) + Blue-green (OPS-2) + Flood Demo (B2-3)
Day 7   (06-08): AI Workflow E2E test + Flood Alert E2E + BMS ITs (QA-4) + Grafana panels (OPS-3)
                  ─── Tier 1 COMPLETE checkpoint ───
Day 8-9 (06-09→10): ─── Tier 2 best-effort ───
                  Mobile Auth config (B1-4) + FCM/Push backend (B2-5)
                  RN scaffold (FE-4) + PKCE login (FE-5) + Mobile tests (QA-5)
Day 10  (06-13): Gate review 15:00 SGT + Demo dry-run (PM-1) + SA code review
```

---

## Open Questions (cần PO/SA resolve)

| # | Question | Owner | Deadline |
|---|----------|-------|----------|
| 1 | API path: `/api/v1/workflows` (new) or extend existing `/api/v1/workflow`? | SA + Backend-1 | Day 2 |
| 2 | Flood dedup window: same as CEP 10min or separate cooldown? | Backend-2 | Day 4 |
| 3 | FCM/APNs: reuse existing `push_subscriptions` table or new `device_push_tokens`? | Backend-2 recommends reuse | Day 8 |
| 4 | Flood risk zones: polygon or CircleMarker for demo? | Frontend recommends CircleMarker | Day 5 |
| 5 | BPMN "Save" endpoint: new POST or existing Camunda deploy? | Backend-1 + SA | Day 2 |

---

*Sprint 6 task assignments created: 2026-05-29 | Based on: 7 agent reports (Backend-1, Backend-2, Frontend, DevOps, QA, SA, PM)*
*Key discovery: Camunda 7 + bpmn-js already embedded → significant effort savings for AI Workflow*
*Frontend bottleneck acknowledged: Tier 2 Mobile defer Sprint 7 if overloaded*
