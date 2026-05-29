# Sprint MVP3-6 — Master Plan

**Status:** DRAFT — PO Planning 2026-05-29
**Document Date:** 2026-05-29
**Sprint Start:** 2026-06-02 (Mon)
**Sprint End:** 2026-06-13 (Fri EOD)
**Gate Review:** 2026-06-13 15:00 SGT
**Sprint trước:** MVP3-5 — GATE PASS 21/21 | PO DEMO 16/16 | ZERO CARRY-OVER
**PO:** anhgv

---

## Context

Sprint 5 hoàn thành BMS Full Integration + Alerts SSE + Forecast Fallback (21/21 DONE, 1,224 tests, PO demo 16/16 PASS). Zero carry-over — lần đầu tiên trong MVP3.

**PO quyết định (planning 2026-05-29):**
- **Sprint 6 focus:** AI Innovation — AI Workflow Designer + Flood Alert Pipeline E2E
- **Mobile Foundation:** React Native scaffold + Keycloak PKCE + Push Backend song song
- **Building Safety:** defer Sprint 7 (trước pilot)
- **Avro/Schema Registry:** defer post-pilot
- **EMQX MQTT Production:** carry-over từ Sprint 5 (OPS-2)
- **Blue-green deploy:** validate trong Sprint 6 cho pilot readiness Sprint 7
- **Target:** City Authority pilot readiness tháng 8 — AI Workflow là differentiator cho demo

**Carry-over từ Sprint 5:**
- **EMQX MQTT production (OPS-2):** EMQX container healthy nhưng chưa có BMS commands qua MQTT thật. Cần production config Sprint 6 Day 1.
- **BMS ITs (QA-1):** 10/10 Testcontainers ITs PASS nhưng một số edge case chưa cover. Sprint 6 bổ sung thêm.
- **Push Subscription FE page (KI-07):** Backend API complete + tested, thiếu frontend page.

---

## 1. Sprint Overview

| Dimension | Value |
|---|---|
| **Sprint Name** | MVP3-6: AI Innovation + Mobile Foundation |
| **Duration** | 2026-06-02 (Mon) → 2026-06-13 (Fri) — 10 calendar days |
| **Team** | 5 FTE (Backend 2, Frontend 1, QA 1, DevOps 1) + SA spike |
| **Net Capacity** | ~47 SP (59 SP - 20% buffer) |
| **Committed** | ~74 SP (Tier 1 + Tier 2) |
| **Buffer** | ~0 SP — aggressive, nhưng Sprint 5 đã proof 50/47 delivered |

> **74 SP vs 47 SP capacity (+27 SP).** Aggressive commit dựa trên Sprint 5 precedent. Tier 1 (45 SP) = PHẢI DONE. Tier 2 (29 SP) = best-effort, nếu không kịp → Mobile defer Sprint 7.

---

## 2. Sprint Goal (SMART)

Team sẽ đạt **HARD PASS** by 2026-06-13 15:00 SGT bằng cách:

1. AI Workflow Designer — BPMN visual editor (`bpmn-js`) + drag & drop AI decision nodes + flood alert workflow demo-ready
2. Flood Alert Pipeline E2E — Sensor → Kafka → Flink CEP → Alert → SSE push, latency <30 giây
3. EMQX MQTT Production — BMS commands qua MQTT broker thật, heartbeat status ONLINE
4. Blue-green deploy — Docker Compose validation, rollback <30s
5. React Native scaffold — Expo + shared hooks + navigation (Tier 2 best-effort)
6. Push Notification backend — FCM/APNs multi-channel (Tier 2 best-effort)
7. Regression maintain: 1,500+ tests PASS, 0 failures

---

## 3. Backlog Committed

### Tier 1 — PHẢI DONE (45 SP)

#### Epic 0: Carry-over từ Sprint 5 [7 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S6-C01 | EMQX MQTT Production — BMS commands qua MQTT broker, device heartbeat status ONLINE | 5 | DevOps | P1 | `POST /bms/devices/{id}/commands` → EMQX publish → device ACK; heartbeat updates status ONLINE/UNKNOWN |
| S6-C02 | Push Subscription FE page — `/settings/notifications` subscribe/unsubscribe push | 2 | Frontend | P2 | Subscribe button → `POST /api/v1/push/subscribe`; unsubscribe → `DELETE`; list active subscriptions |

**Ghi chú kỹ thuật:**
- S6-C01: EMQX config đã healthy (Sprint 5). Cần: BmsCommandAckConsumer listen Kafka `bms.command.ack`, update device status via `BmsDeviceService`. Test với MQTT client mock.
- S6-C02: Backend API đã có (`PushSubscriptionController`). Frontend chỉ cần form + React Query mutation.

#### Epic 1: AI Workflow Designer [13 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S6-AI01 | ADR-030 AI Workflow Architecture — bpmn-js + Spring Boot backend + AI decision node | 2 | SA | P0 | ADR merged Day 2, bpmn-js evaluation complete |
| S6-AI02 | BPMN Backend — WorkflowDefinition CRUD API + WorkflowEngine (Port/Adapter) | 5 | Backend-1 | P0 | `POST /api/v1/workflows` create, `GET /api/v1/workflows` list, `POST /api/v1/workflows/{id}/execute` trigger |
| S6-AI03 | AI Decision Node — `AiDecisionGateway` integrate Claude API cho urban decision making | 3 | Backend-1 | P1 | Decision node nhận context → gọi AI → trả confidence score + recommendation |
| S6-AI04 | BPMN Frontend — visual editor page `/workflows` với bpmn-js modeler + AI node palette | 5 | Frontend | P0 | Drag & drop nodes, connect edges, save/load workflow JSON, AI node có config panel (prompt, threshold) |

**Ghi chú kỹ thuật:**
- bpmn-js (`bpmn-js@17.x`, MIT) — library chính cho visual editor
- Backend: `WorkflowDefinition` entity (JSONB cho BPMN XML), `WorkflowExecution` entity (status, variables)
- AI Decision Node: confidence > 0.85 → auto-execute; 0.6-0.85 → operator queue; < 0.6 → escalate
- Port/Adapter: `WorkflowEnginePort` interface — Camunda/Flowable adapter cho Phase 2, in-process adapter cho MVP
- Python coexistence: AI Workflow backend = Java (Camunda/Flowable fallback cho Tier 1), Python AI service = Tier 2 opt-in enhancement

#### Epic 2: Flood Alert Pipeline [13 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S6-FL01 | Flink CEP Flood Alert Job — pattern detection: rainfall > threshold + water level rising | 5 | Backend-2 | P0 | 3 consecutive readings vượt threshold trong 10 min → alert event published to Kafka |
| S6-FL02 | Alert ingestion — Kafka consumer → AlertService → DB + SSE push + push notification | 3 | Backend-2 | P0 | Flood alert <30s từ sensor reading → SSE event + push notification |
| S6-FL03 | Flood Alert Frontend — `/alerts` flood-specific cards + map overlay | 3 | Frontend | P0 | Flood alert card với severity + location + affected area map; escalation action |
| S6-FL04 | Flood Alert Demo Scenario — seed sensor data + trigger flood event end-to-end | 2 | Backend-2 | P0 | Demo script: inject 3 rainfall readings → Flink detects → alert appears in UI <30s |

**Ghi chú kỹ thuật:**
- Flink CEP job deploy trên existing Flink infrastructure (Sprint 1)
- Sensor types: `RAINFALL` (mm/h), `WATER_LEVEL` (m), `SOIL_MOISTURE` (%)
- Alert severity: P0 EMERGENCY (water level > critical) → broadcast all channels
- Existing AlertService reused — chỉ thêm flood-specific alert type
- Kafka topic mới: `UIP.flink.alert.flood.v1` (register trong kafka-topic-registry.xlsx)

#### Epic 3: Infrastructure + QA [12 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S6-OPS1 | Blue-green deploy — Docker Compose validation (2 backend instances, nginx upstream switch) | 3 | DevOps | P0 | Deploy backend v2 → switch nginx → verify; rollback <30s; zero downtime |
| S6-OPS2 | Python forecast auto-retry — scheduled retry mọi 5 min khi Python DOWN | 2 | Backend-2 | P2 | `@Scheduled(fixedDelay=300000)` check Python health → retry → auto-recover `isFallback=false` |
| S6-QA1 | Sprint 6 regression gate — maintain 1,500+ tests, 0 failures | 3 | QA | P0 | Gate metrics PASS, JaCoCo report attached |
| S6-QA2 | BMS ITs supplement — 5 additional Testcontainers scenarios (carry-over QA-1) | 2 | QA | P1 | 5/5 new ITs PASS — MQTT ACK, concurrent commands, DLQ overflow |
| S6-PM1 | Demo script + PO dry-run | 2 | PM | P0 | Script ready Day 9, dry-run pass |

---

### Tier 2 — BEST EFFORT (29 SP)

#### Epic 4: Mobile Foundation [21 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S6-M01 | React Native + Expo scaffold — `applications/operator-mobile/`, shared hooks, navigation | 8 | Frontend | P1 | `npx expo start` runs, 4 tabs (Dashboard/Alerts/Controls/Profile), shared `useAlerts` hook |
| S6-M02 | Keycloak PKCE login + tenant selection — `expo-auth-session` + SecureStore | 5 | Frontend + Backend | P1 | Login → JWT issued → tenant list → select → dashboard loads |
| S6-M03 | Mobile API endpoint — `/api/v1/mobile/auth/config` return Keycloak endpoints + PKCE params | 2 | Backend-1 | P1 | GET returns `{issuer, clientId, scopes}` cho mobile app |
| S6-M04 | FCM + APNs push notification backend — `firebase-admin` + `pushy`, multi-channel | 5 | Backend-2 | P1 | Alert → NotificationRouter → FCM/APNs → device receive; V29 migration `device_push_tokens` |
| S6-M05 | SA: ADR-030 Mobile Stack + ADR-034 Structural Monitoring prep | 1 | SA | P1 | ADR-030 merged, ADR-034 drafted |

**Ghi chú kỹ thuật Mobile:**
- Expo SDK 51, React Native 0.74
- Shared hooks: `useAlerts`, `useSensors`, `useBuildingList` — 60% reuse từ web
- Mobile-specific: `useAuthMobile` (SecureStore), `usePushNotifications` (expo-notifications)
- npm workspaces: `packages/api-types/` — shared TypeScript types

---

## 4. Story Point Summary

| Epic | SP | Owner | Tier |
|---|---|---|---|
| E0: Carry-over (EMQX + Push FE) | 7 | DevOps + Frontend | 1 |
| E1: AI Workflow Designer | 13 | Backend-1 + Frontend + SA | 1 |
| E2: Flood Alert Pipeline | 13 | Backend-2 + Frontend | 1 |
| E3: Infrastructure + QA | 12 | DevOps + Backend + QA + PM | 1 |
| E4: Mobile Foundation | 21 | Frontend + Backend + SA | 2 |
| **Total Committed** | **66 SP** | | |

> **66 SP vs 47 SP capacity (+19 SP).** Sprint 5 precedent: 50/47 delivered. Tier 1 (45 SP) fit capacity. Tier 2 (21 SP) best-effort — nếu Frontend overloaded → Mobile defer Sprint 7.

---

## 5. Capacity Gap Analysis

### Team Roster & Capacity

| Role | Member | Capacity | Tier 1 | Tier 2 | Total Load |
|------|--------|----------|--------|--------|------------|
| **Backend-1** (AI Workflow) | Backend Engineer A | ~12 SP | 10 SP | 2 SP | 100% |
| **Backend-2** (Flood Alert + IoT) | Backend Engineer B | ~12 SP | 10 SP | 5 SP | 125% ⚠️ |
| **Frontend** | Frontend Engineer | ~10 SP | 10 SP | 13 SP | 230% ⚠️⚠️ |
| **QA** | QA Engineer | ~8 SP | 5 SP | 0 SP | 63% |
| **DevOps** | DevOps Engineer | ~5 SP | 8 SP | 0 SP | 160% ⚠️ |
| **SA** (Day 1-2 only) | Solution Architect | ~2 SP | 2 SP | 1 SP | spike only |
| **PM** | Project Manager | — | 2 SP | 0 SP | continuous |

### ⚠️ Bottleneck Analysis

**Frontend Engineer — CRITICAL BOTTLENECK (23 SP vs 10 SP capacity)**

Frontend cần làm: AI Workflow Designer FE (5 SP) + Flood Alert FE (3 SP) + Push Sub FE (2 SP) + RN scaffold (8 SP) + PKCE login (5 SP) = 23 SP.

**Mitigation:**
1. **Priority order:** AI Workflow FE → Flood Alert FE → Push Sub FE → RN scaffold → PKCE login
2. **RN scaffold có thể defer Sprint 7** nếu Frontend overloaded — Backend Push backend vẫn ship
3. **SA spike Day 1-2:** Frontend engineer pair với SA trên bpmn-js evaluation → giảm learning curve

**Backend-2 — overloaded (15 SP vs 12 SP capacity)**
- Flood Alert Pipeline (10 SP) + FCM/Push backend (5 SP)
- Mitigation: Push backend chỉ cần `firebase-admin` integration, ít logic — có thể Day 8-10

### Fallback cắt giảm (nếu sprint chạy chậm)

| Option | SP tiết kiệm | Trade-off |
|---|---|---|
| **F1) Mobile scaffold → Sprint 7** | −8 | Mobile app delay 2 tuần, Push backend vẫn ship |
| **F2) PKCE login → Sprint 7** | −5 | Mobile app chỉ scaffold, không login được |
| **F3) Push backend → Sprint 7** | −5 | Alert notification chỉ SSE (web), không mobile push |
| **F4) Blue-green → Sprint 7** | −3 | Pilot deploy rollback manual, không zero-downtime |
| **F5) BMS ITs supplement → Sprint 7** | −2 | IT coverage giảm nhẹ |

**Recommended cut order (nếu cần):** F2 → F4 → F5 → F3 → F1

---

## 6. Definition of Done

- [ ] AI Workflow Designer: BPMN editor load, drag & drop nodes, save workflow, execute workflow
- [ ] AI Decision Node: Claude API integration, confidence score, operator queue
- [ ] Flood Alert Pipeline: Flink CEP job RUNNING, sensor → alert <30s E2E
- [ ] EMQX MQTT: BMS commands qua MQTT, device status heartbeat
- [ ] Blue-green deploy: Docker Compose validation, rollback <30s
- [ ] Python auto-retry: scheduled every 5 min, auto-recover `isFallback=false`
- [ ] Push Subscription FE page: subscribe/unsubscribe work
- [ ] 1,500+ tests PASS, 0 failures
- [ ] JaCoCo LINE ≥77%, BRANCH ≥62%
- [ ] ADR-030 merged (Mobile Stack)
- [ ] Sprint 5 regression: zero new failures
- [ ] SA code review: 10/10 Backend + 10/10 Frontend APPROVED

### Tier 2 DoD (best-effort)
- [ ] React Native scaffold: `npx expo start` runs, navigation works
- [ ] Keycloak PKCE login: JWT issued, tenant selection works
- [ ] FCM push: alert → Android notification received
- [ ] V29 migration: `device_push_tokens` table created

---

## 7. Acceptance Criteria Gate (Sprint Gate 2026-06-13)

### Hard Pass (tất cả phải PASS)

| Gate | Criterion | Verifier | Status |
|---|---|---|---|
| G1 | AI Workflow: BPMN editor load + drag & drop + save + execute workflow | Manual QA | |
| G2 | AI Decision Node: Claude API call → confidence score returned | IT automated | |
| G3 | Flood Alert: Flink CEP detects pattern → Kafka event published | IT automated | |
| G4 | Flood Alert E2E: sensor reading → alert in UI <30s | Manual QA + timer | |
| G5 | EMQX MQTT: BMS command → MQTT publish → device ACK → status update | QA curl + EMQX log | |
| G6 | Blue-green deploy: rollback <30s validated | DevOps run | |
| G7 | Python auto-retry: DOWN → retry → UP → `isFallback=false` | QA curl | |
| G8 | 1,500+ tests PASS, 0 failures | CI | |
| G9 | JaCoCo LINE ≥77%, BRANCH ≥62% | JaCoCo report | |
| G10 | ADR-030 merged | Git | |
| G11 | SA code review APPROVED | SA | |

### Soft Pass (WARN không block)

| Gate | Criterion | Status |
|---|---|---|
| GS1 | React Native scaffold runs + navigation works | |
| GS2 | Keycloak PKCE login works on mobile | |
| GS3 | FCM push notification received on Android | |
| GS4 | BMS ITs supplement (5 new scenarios) | |

---

## 8. AI Workflow Designer — Technical Spec

### Architecture

```
┌─────────────────── Frontend (React) ───────────────────┐
│  /workflows                                             │
│  ┌─────────────────────────────────────────────┐       │
│  │  bpmn-js Modeler                             │       │
│  │  ┌───┐   ┌───┐   ┌───────┐   ┌───┐         │       │
│  │  │Start│→ │AI │ → │Notify │ → │End│         │       │
│  │  │Event│  │Gate│   │Task  │   │   │         │       │
│  │  └───┘   └───┘   └───────┘   └───┘         │       │
│  │         ▲ config panel:                       │       │
│  │         │  prompt, threshold, model           │       │
│  │         └────────────────────────             │       │
│  └─────────────────────────────────────────────┘       │
│         │ REST API (JSON)                               │
└─────────┼───────────────────────────────────────────────┘
          │
┌─────────▼──────── Backend (Spring Boot) ───────────────┐
│  WorkflowController                                     │
│    POST /api/v1/workflows         → create              │
│    GET  /api/v1/workflows         → list                │
│    GET  /api/v1/workflows/{id}    → get definition      │
│    POST /api/v1/workflows/{id}/execute → trigger run    │
│                                                         │
│  WorkflowEnginePort (interface)                         │
│    ├── InProcessWorkflowAdapter (Tier 1 — Java)        │
│    │     └── parse BPMN → walk nodes → execute         │
│    └── CamundaWorkflowAdapter (Tier 2 — future)        │
│                                                         │
│  AiDecisionGateway                                      │
│    ├── input: context (sensor data, rules, history)     │
│    ├── call: Claude API (claude-sonnet)                 │
│    └── output: {decision, confidence, reasoning}        │
│          confidence > 0.85 → auto-execute               │
│          0.6-0.85 → operator queue                      │
│          < 0.6 → escalate                               │
│                                                         │
│  WorkflowExecution entity                               │
│    id, definitionId, status, variables, startedAt       │
└─────────────────────────────────────────────────────────┘
```

### Database

```sql
-- V28 migration
CREATE TABLE ai_workflow.workflow_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(50) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    bpmn_xml        TEXT NOT NULL,
    version         INT NOT NULL DEFAULT 1,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name, version)
);

CREATE TABLE ai_workflow.workflow_executions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    definition_id   UUID NOT NULL REFERENCES ai_workflow.workflow_definitions(id),
    tenant_id       VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    variables       JSONB,
    current_node    VARCHAR(100),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    error_message   TEXT
);
```

### Flood Alert Workflow (Demo Scenario)

```xml
<!-- Flood Alert Workflow — BPMN 2.0 -->
<definitions>
  <process id="flood-alert-pipeline">
    <startEvent id="sensor-trigger" name="Sensor Reading Received" />
    
    <serviceTask id="check-thresholds" name="Check Thresholds">
      <!-- Flink CEP already detects — this node validates -->
    </serviceTask>
    
    <serviceTask id="ai-assess" name="AI Risk Assessment">
      <!-- Call Claude API: analyze sensor data + weather + historical -->
      <!-- Input: {rainfall, waterLevel, soilMoisture, forecast, history} -->
      <!-- Output: {riskLevel, confidence, recommendation} -->
    </serviceTask>
    
    <exclusiveGateway id="risk-decision" name="Risk Level?" />
    
    <serviceTask id="alert-p0" name="Broadcast Emergency Alert">
      <!-- P0 EMERGENCY: SMS + push + SSE + email to all operators -->
    </serviceTask>
    
    <serviceTask id="alert-p1" name="Notify Operations Team">
      <!-- P1 WARNING: push + SSE to operations team -->
    </serviceTask>
    
    <serviceTask id="log-advisory" name="Log Advisory">
      <!-- P2 ADVISORY: dashboard notification only -->
    </serviceTask>
    
    <endEvent id="complete" />
  </process>
</definitions>
```

---

## 9. Flood Alert Pipeline — Technical Spec

### Data Flow

```
Sensor (rainfall, water_level, soil_moisture)
  → Kafka topic: UIP.iot.sensor.reading.v1
  → Flink CEP Job: FloodAlertJob
      Pattern: 3 readings > threshold within 10 min
      Output: FloodAlertEvent
  → Kafka topic: UIP.flink.alert.flood.v1
  → Monolith: FloodAlertConsumer
      → AlertService.createAlert()
      → SSE push via Redis PUBLISH
      → Push notification (FCM/APNs if available)
  → Frontend: Alert card + map overlay
```

### Flink CEP Pattern

```java
Pattern<SensorReading, ?> floodPattern = Pattern
    .<SensorReading>begin("reading1")
        .where(r -> r.getValue() > r.getThreshold())
    .timesOrMore(3)
    .within(Time.minutes(10));
```

### Thresholds (TCVN 9386:2012 + City Authority)

| Sensor Type | P2 Advisory | P1 Warning | P0 Emergency |
|------------|-------------|------------|--------------|
| RAINFALL (mm/h) | 50 | 80 | 120 |
| WATER_LEVEL (m) | 2.0 | 3.5 | 5.0 |
| SOIL_MOISTURE (%) | 70 | 85 | 95 |

---

## 10. Timeline (10 ngày)

```
Day 1   (06-02): SA spike bpmn-js + ADR-030 draft
                  EMQX MQTT production config (DevOps)
                  Flood Alert Flink CEP design (Backend-2)
                  Regression baseline (QA)

Day 2   (06-03): ADR-030 review + merge
                  BPMN Backend: WorkflowDefinition entity + CRUD API (Backend-1)
                  Flink CEP FloodAlertJob implement (Backend-2)
                  AI Workflow FE: bpmn-js integration start (Frontend)
                  Python auto-retry (Backend-2)

Day 3-4 (06-04→05): BPMN Backend: WorkflowEngine + execute endpoint
                  AI Decision Node: Claude API integration
                  Flood Alert: Kafka consumer + AlertService integration
                  AI Workflow FE: node palette + drag & drop
                  EMQX MQTT: BmsCommandAckConsumer

Day 5-6 (06-06→07): AI Workflow FE: AI node config panel
                  Flood Alert FE: alert cards + map overlay
                  Flood Alert demo scenario seed data
                  Blue-green deploy validation (DevOps)
                  Push Subscription FE page (Frontend)

Day 7   (06-08): AI Workflow E2E integration test
                  Flood Alert E2E test (<30s latency)
                  BMS ITs supplement (QA)
                  ─── Tier 1 complete checkpoint ───

Day 8-9 (06-09→10): ─── Tier 2 best-effort ───
                  React Native scaffold (Frontend)
                  PKCE login (Frontend + Backend-1)
                  FCM push backend (Backend-2)
                  ADR-034 Structural Monitoring draft (SA)

Day 10  (06-13): Gate review 15:00 SGT
                  Demo script dry-run
                  SA code review sign-off
```

---

## 11. Risks

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| bpmn-js learning curve — AI Workflow complex | 40% | Medium | SA spike Day 1-2; fallback: static workflow JSON template thay vì BPMN |
| Frontend overloaded (23 SP vs 10 SP) | 50% | High | Priority order AI Workflow → Flood Alert → Push Sub; Mobile defer Sprint 7 nếu cần |
| Claude API rate limit / latency cho AI Decision | 25% | Medium | Cache decisions cho similar context; timeout 5s → fallback rule-based |
| Flink CEP flood job deploy issue | 20% | Medium | Existing Flink infrastructure proven (Sprint 1); CEP pattern simple |
| Mobile RN scaffold conflict với web workspace | 30% | Low | npm workspaces isolated; RN deps riêng `applications/operator-mobile/` |
| 66 SP committed vs 47 SP capacity | 100% | Medium | Sprint 5 precedent; Tier 1 locked at 45 SP; clear cut order defined |
| Blue-green Docker Compose complexity | 15% | Low | Simple: 2 backend containers + nginx upstream switch script |

---

## 12. Dependencies & Prerequisites

| Prerequisite | Owner | Deadline | Status |
|---|---|---|---|
| bpmn-js evaluation (which version, custom palette) | SA + Frontend | Day 1 EOD | |
| Claude API key + rate limit confirmed | Backend-1 | Day 1 | |
| EMQX production config documented | DevOps | Day 1 | |
| Flink job submitter tested with CEP | Backend-2 | Day 2 | |
| Expo SDK 51 + React Native 0.74 compatibility | Frontend | Day 7 (Tier 2) | |
| FCM service account key | Backend-2 | Day 8 (Tier 2) | |

---

## 13. Sprint 7 Preview

| Feature | Priority | SP | Notes |
|---------|----------|-----|-------|
| Building Safety Backend (Flink CEP + Welford) | P1 | 13 | ADR-034 approved Sprint 6 |
| Building Safety UI | P1 | 8 | Sensor grid + alert banner |
| Mobile Dashboard + Alerts (React Native) | P1 | 13 | Full mobile features |
| Mobile Control Panel | P2 | 5 | Actuator commands + confirmation |
| BMS Command ACK + SSE feedback | P2 | 3 | Deferred from Sprint 5 |
| ESG PDF Export (GRI 302/305) | P2 | 5 | City Authority format |
| Pilot regression 100+ scenarios | P0 | 5 | Full regression |
| Pilot readiness gate + demo | P0 | 3 | ALL SLA gates |
| **Total** | | **~55 SP** | |

---

*Sprint 6 plan created: 2026-05-29 | Based on PO decisions: AI Innovation + Mobile Foundation | Sprint 5 precedent: over-commit + deliver*
*Next: PO review → finalize → Sprint 6 kickoff 2026-06-02*
