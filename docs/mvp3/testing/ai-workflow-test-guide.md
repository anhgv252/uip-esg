# AI Workflow — Testing & Presentation Guide

**Version:** 1.0 | **Date:** 2026-06-01 | **Author:** Manual Tester  
**Sprint:** MVP3-6 | **Environment:** local / staging

---

## 1. Tại sao AI Workflow là Selling Point?

AI Workflow là **trái tim khác biệt** của UIP so với các nền tảng smart city truyền thống.

| Nền tảng thông thường | UIP Smart City |
|---|---|
| Rule-based cứng (if AQI > 150 → alert) | AI phân tích ngữ cảnh đa chiều |
| Hardcode từng trigger | Data-driven config, không cần code |
| Operator phải quyết định tất cả | Tự động hóa quyết định thông qua confidence score |
| Không có audit trail quyết định | Mọi AI decision có reasoning + confidence lưu lại |
| No-code impossible | Workflow Designer kéo thả BPMN 2.0 |

**3 giá trị cốt lõi cần trình bày:**
1. **Intelligent Decision Routing** — Claude AI phân tích → tự động / hàng đợi operator / escalate
2. **No-Code Workflow Builder** — City Admin tự tạo / sửa workflow bằng BPMN Designer, không cần developer
3. **Config-Driven Trigger Engine** — 7 scenarios, 3 loại trigger (Kafka / Scheduled / REST), thay đổi realtime không cần deploy

---

## 2. Kiến trúc Tổng quan

```
┌──────────────────── TRIGGER LAYER ─────────────────────────────┐
│  IoT Sensor → Flink CEP → Kafka  ─────→ GenericKafkaTrigger    │
│  Cron Schedule ─────────────────────→ GenericScheduledTrigger  │
│  REST API (Citizen Portal) ─────────→ GenericRestTrigger        │
└───────────────────────────────────┬────────────────────────────┘
                                    │  FilterEvaluator + VariableMapper
                                    ▼
┌──────────────────── CAMUNDA BPMN ENGINE ───────────────────────┐
│  WorkflowService.startProcess(processKey, variables)           │
│                                                                 │
│  Process Flow (BPMN 2.0):                                      │
│  StartEvent → AIAnalysisDelegate → [Domain Delegate] → End     │
└───────────────────────────────────┬────────────────────────────┘
                                    │  scenarioKey + context variables
                                    ▼
┌──────────────────── AI DECISION LAYER ─────────────────────────┐
│  ClaudeApiService.analyzeAsync(scenarioKey, context)           │
│   ├─ Load prompt template  (resources/prompts/{key}.txt)       │
│   ├─ Substitute variables  ({sensorId}, {aqiValue}, ...)       │
│   ├─ Call Claude API       (claude-sonnet-4-6)                 │
│   ├─ Circuit Breaker       (Resilience4j — auto fallback)      │
│   └─ Parse JSON response   {decision, confidence, reasoning}   │
│                                                                 │
│  DecisionRouter (Redis cache 15 min):                          │
│   confidence > 0.85  → AUTO_EXECUTE  ✅                        │
│   confidence 0.6–0.85 → OPERATOR_QUEUE ⚠️                     │
│   confidence < 0.6   → ESCALATE       🔴                       │
└───────────────────────────────────┬────────────────────────────┘
                                    │  aiDecision, aiConfidence, aiReasoning
                                    ▼
┌──────────────────── ACTION LAYER ──────────────────────────────┐
│  Domain Delegates: notifications, evacuation, traffic, ESG...  │
│  Variables stored in Camunda: aiDecision, aiReasoning,         │
│  aiConfidence, aiRecommendedActions, aiSeverity                │
└────────────────────────────────────────────────────────────────┘

┌──────────────────── BPMN DESIGNER (Frontend) ──────────────────┐
│  WorkflowModeler (bpmn-js) + NodePalette + AiNodeConfigPanel   │
│  WorkflowDefinitionService (CRUD + Camunda deploy)             │
│  WorkflowConfigPage (Trigger Config CRUD + Fire/Test)          │
└────────────────────────────────────────────────────────────────┘
```

---
### 2.1. Data flow
```
┌─────────────────── DATA FLOW ─────────────────────────────────────────────-──┐
│                                                                              │
│  IoT Sensor / Flink Alert                                                    │
│       ↓                                                                      │
│  Kafka topic: UIP.flink.alert.detected.v1                                    │
│       ↓                                                                      │
│  GenericKafkaTriggerService (@KafkaListener)                                 │
│       │                                                                      │
│       ├─ load TriggerConfigs từ DB (cached Redis)                            │
│       ├─ FilterEvaluator: module=ENVIRONMENT, value > 150 ...                │
│       ├─ VariableMapper: payload.sensorId → "sensorId" (Camunda var)         │
│       └─ workflowService.startProcess( processKey )  ← LINK POINT            │
│                    ↓                                                         │
│  ┌──── TRIGGER CONFIG ────┐         ┌──── PROCESS DEFINITIONS ───┐           │
│  │ scenarioKey: aiC01_... │         │ Camunda process key:        │          │
│  │ processKey: aiC01_...  │ ──────→ │ aiC01_aqiCitizenAlert       │          │
│  │ kafkaTopic: UIP.flink  │         │ (BPMN deployed to Camunda)  │          │
│  │ filterConditions: AQI  │         └─────────────────────────────┘          │
│  └────────────────────────┘                  ↓                               │
│                                  BPMN executes node by node                  │
│                                              ↓                               │
│                                  Service Task: AIAnalysisDelegate            │
│                                    ├─ reads scenarioKey, aqiValue...         │
│                                    ├─ calls Claude API (10s timeout)         │
│                                    └─ sets aiDecision, aiConfidence,         │
│                                         aiReasoning → Camunda variables      │
│                                              ↓                               │
│                                  DecisionRouter (Advanced AI layer):         │
│                                    conf > 0.85  → AUTO_EXECUTE               │
│                                    0.60–0.85    → OPERATOR_QUEUE             │
│                                    < 0.60       → ESCALATE                   │
│                                              ↓                               │
│                                  Notification / Assignment / Escalation      │
└──────────────────────────────────────────────────────────────────────────────┘

```
---

## 3. Setup Guide — Chạy AI Workflow từ đầu

### 3.1. Pre-requisites

```bash
# Kiểm tra infrastructure
docker compose -f infrastructure/docker-compose.yml ps

# Bắt buộc phải RUNNING:
# - postgres (TimescaleDB)     → port 5432
# - kafka                      → port 9092
# - redis                      → port 6379

# Optional (nếu có):
# - Flink jobmanager           → port 8081 (cho Kafka trigger)
```

### 3.2. Backend — Start với Claude API

```bash
# Option A: Có Claude API key (full AI mode)
cd backend
export CLAUDE_API_KEY=sk-ant-xxxxxxxxxx
./gradlew bootRun \
  -Dspring.profiles.active=test \
  -Dclaude.api.key=$CLAUDE_API_KEY

# Option B: Không có API key (fallback rule-based mode)
./gradlew bootRun -Dspring.profiles.active=test
# → System vẫn hoạt động, dùng RuleBasedFallbackDecisionService
```

### 3.3. Verify Camunda Processes Deployed

```bash
# 7 processes phải có sau khi backend start
curl -s http://localhost:8080/api/v1/workflow/definitions | jq '.[].name'

# Expected output:
# "AI-C01: AQI Citizen Alert"
# "AI-C02: Citizen Service Request"
# "AI-C03: Flood Emergency Evacuation"
# "AI-M01: Flood Response Coordination"
# "AI-M02: AQI Traffic Control"
# "AI-M03: Utility Incident Coordination"
# "AI-M04: ESG Anomaly Investigation"
```

### 3.4. Verify Trigger Configs (7 records trong DB)

```bash
# Lấy token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@uip.gov.vn","password":"Admin123!"}' | jq -r '.token')

# List configs
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/admin/workflow-configs | jq '.[].scenarioKey'

# Expected:
# "aiC01_aqiCitizenAlert"       → KAFKA trigger
# "aiC02_citizenServiceRequest" → REST trigger
# "aiC03_floodEmergencyEvacuation" → KAFKA trigger
# "aiM01_floodResponseCoordination" → KAFKA trigger
# "aiM02_aqiTrafficControl"     → KAFKA trigger
# "aiM03_utilityIncidentCoordination" → SCHEDULED trigger
# "aiM04_esgAnomalyInvestigation" → SCHEDULED trigger
```

### 3.5. Frontend — AI Workflow Dashboard

```bash
cd frontend && npm run dev
# → http://localhost:3000
# Navigate: AI Workflow Dashboard (menu icon SmartToy)
# Tabs: Process Instances | Process Definitions | Designer | Config Engine
```

### 3.6. Kiểm tra Claude API key

```bash
# Check config (API key không hiện full — chỉ check xem có key không)
curl -s http://localhost:8080/actuator/health | jq '.components.claudeApi'

# Nếu không có key → fallback mode (log sẽ có):
# "Claude API key not configured, using fallback for scenario: aiM01_floodResponseCoordination"
```

---

## 4. Bảng 7 AI Workflow Scenarios

| Scenario Key | Tên | Trigger | BPMN Process | Use Case |
|---|---|---|---|---|
| `aiC01_aqiCitizenAlert` | AQI Citizen Alert | Kafka | aqi-citizen-alert | AQI > ngưỡng → thông báo công dân |
| `aiC02_citizenServiceRequest` | Citizen Service Request | REST | citizen-service-request | Công dân gửi yêu cầu, AI phân loại |
| `aiC03_floodEmergencyEvacuation` | Flood Emergency Evacuation | Kafka | flood-emergency-evacuation | Lũ lụt → kế hoạch sơ tán |
| `aiM01_floodResponseCoordination` | Flood Response Coordination | Kafka | flood-response-coordination | Điều phối ứng phó lũ đa cơ quan |
| `aiM02_aqiTrafficControl` | AQI Traffic Control | Kafka | aqi-traffic-control | AQI cao → điều chỉnh giao thông |
| `aiM03_utilityIncidentCoordination` | Utility Incident Coordination | Scheduled (Cron) | utility-incident-coordination | Sự cố tiện ích đô thị định kỳ |
| `aiM04_esgAnomalyInvestigation` | ESG Anomaly Investigation | Scheduled (Cron) | esg-anomaly-investigation | Phát hiện bất thường ESG metrics |

---

## 5. Test Cases — BPMN Designer

### TC-AI-001: Tạo Workflow mới trên Designer
**Priority:** P0 | **Type:** Functional
**Feature:** BPMN Visual Editor (S6-AI04)

**Preconditions:**
- Backend running, 7 processes deployed
- Frontend at http://localhost:3000
- Login với role ADMIN

**Steps:**
1. Navigate → AI Workflow Dashboard → Tab "Designer"
2. Click "New Workflow" button
3. Kiểm tra canvas xuất hiện với Start Event mặc định
4. Từ Node Palette (bên phải), kéo "AI Decision" node vào canvas
5. Kéo "Notification" node vào canvas
6. Kéo "End Event" node vào canvas
7. Nối Start → AI Decision → Notification → End (bằng cách hover vào node để thấy arrow)
8. Click vào AI Decision node
9. Trong AI Config Panel, điền:
   - AI Prompt: "Phân tích mức độ ô nhiễm không khí và quyết định có cần cảnh báo không"
   - Confidence Threshold: kéo slider về 0.85
   - AI Model: Claude Sonnet 4.6
10. Click "Save"
11. Click "Deploy"

**Expected:**
- Canvas load với Start Event (StartEvent_1) ngay khi New Workflow
- AI Decision node màu tím (#7b1fa2), phân biệt với Service Task màu xanh (#1e88e5)
- Config Panel hiển thị khi click AI node, ẩn khi click node khác
- Save → snackbar "New workflow created" hoặc "Saved (vX)"
- Deploy → snackbar "Deployed to Camunda!"
- Workflow xuất hiện trong danh sách bên trái với version v1

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED
**Notes:**

---

### TC-AI-002: Node Palette — Kéo thả tất cả 5 node types
**Priority:** P1 | **Type:** Functional

**Steps:**
1. Tạo New Workflow
2. Kéo lần lượt từ Node Palette:
   - "Start Event" (xanh lá #43a047)
   - "Service Task" (xanh dương #1e88e5)
   - "AI Decision" (tím #7b1fa2)
   - "Notification" (cam #f57c00)
   - "End Event" (đỏ #c62828)
3. Với mỗi node: verify màu icon circle đúng, label đúng

**Expected:**
- 5 node types kéo được vào canvas
- Mỗi node có màu viền circle đúng theo spec
- Node Palette còn nguyên sau khi kéo (có thể kéo nhiều lần)

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-AI-003: AI Config Panel — Confidence Threshold Routing Labels
**Priority:** P1 | **Type:** UI

**Steps:**
1. Click vào AI Decision node bất kỳ
2. Quan sát section "Confidence Threshold"
3. Verify 2 chip labels xuất hiện phía dưới slider:
   - "Operator Queue" (vàng/warning)
   - "Auto-Execute" (xanh/success)
4. Kéo slider về 0.6, 0.85, 1.0 → xem value label

**Expected:**
- Slider có marks tại 0.6, 0.85, 1.0
- Chip "Operator Queue" màu warning (cam), "Auto-Execute" màu success (xanh)
- valueLabelDisplay="auto" hiện % khi hover (e.g., "85%")
- Slider không vượt quá 0–1

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-AI-004: Save và Load Workflow
**Priority:** P0 | **Type:** Regression

**Steps:**
1. Tạo workflow "Test Load Workflow" với 2–3 nodes
2. Click Save
3. Refresh trang (F5)
4. Navigate lại AI Workflow → Designer
5. Click vào workflow "Test Load Workflow" trong danh sách

**Expected:**
- Workflow vẫn có trong danh sách sau refresh
- BPMN XML load lại đúng → canvas hiện đúng nodes và connections
- Version hiện trong danh sách

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-AI-005: Delete Workflow — Confirm Dialog
**Priority:** P2 | **Type:** Functional

**Steps:**
1. Chọn một workflow trong danh sách
2. Click nút Delete (trash icon)
3. Verify dialog xác nhận xuất hiện
4. Click Cancel → workflow vẫn còn
5. Click Delete lại → click Confirm Delete
6. Verify workflow biến mất khỏi danh sách

**Expected:**
- Dialog có nút "Cancel" và "Delete" (màu đỏ)
- Cancel: không xóa, đóng dialog
- Confirm Delete: xóa, snackbar thông báo thành công

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

## 6. Test Cases — Workflow Config Engine

### TC-AI-010: Xem danh sách 7 Trigger Configs
**Priority:** P0 | **Type:** Smoke

**Preconditions:** Backend running, DB seeded

**Steps:**
1. Navigate → AI Workflow Dashboard → Tab "Config Engine"
2. Verify bảng hiển thị 7 rows
3. Verify mỗi row có: Scenario Key, Display Name, Trigger Type chip, toggle Enabled

**Expected:**
- 7 configs: aiC01, aiC02, aiC03, aiM01, aiM02, aiM03, aiM04
- Trigger Type chips đúng màu:
  - KAFKA → chip màu primary (xanh dương)
  - REST → chip màu success (xanh lá)
  - SCHEDULED → chip màu secondary (tím nhạt)

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-AI-011: Tạo Workflow Config mới (KAFKA trigger)
**Priority:** P1 | **Type:** Functional

**Steps:**
1. Click "Add Config" button
2. Điền form:
   - Scenario Key: `test_noise_monitoring`
   - Process Key: `aiC01_aqiCitizenAlert`
   - Display Name: "Noise Level Monitoring Test"
   - Trigger Type: KAFKA
   - Kafka Topic: `UIP.flink.alert.detected.v1`
   - Kafka Consumer Group: `uip-workflow-noise-test`
   - Filter Conditions: `[{"field":"module","op":"EQ","value":"NOISE"}]`
   - Variable Mapping: `{"sensorId":{"source":"sensorId","default":"UNKNOWN"}}`
   - AI Confidence Threshold: 0.80
3. Click Save

**Expected:**
- Config được tạo, xuất hiện trong bảng
- Trigger Type chip KAFKA màu xanh
- Enabled = true mặc định

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-AI-012: Demo Preset — Load và Fire
**Priority:** P0 | **Type:** Functional (Demo flow)

**Preconditions:** Ít nhất 1 config đang enabled

**Steps:**
1. Trong Config Engine, tìm row `aiC02_citizenServiceRequest`
2. Click nút "Fire" (play icon) trên row đó
3. Dialog "Fire Trigger" mở ra
4. Điền payload JSON:
   ```json
   {
     "citizenId": "citizen-test-001",
     "requestType": "ENVIRONMENT",
     "description": "Mùi hóa chất gần nhà máy khu D7",
     "district": "D7"
   }
   ```
5. Click "Fire"
6. Verify response hiển thị process instance ID

**Expected:**
- Dialog mở với textarea cho JSON payload
- Fire → response: `{ processInstanceId: "xxx", status: "STARTED" }`
- Chuyển sang tab "Process Instances" → process mới có status ACTIVE hoặc COMPLETED

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-AI-013: Test Filter — Kafka Payload Matching
**Priority:** P1 | **Type:** Functional

**Steps:**
1. Chọn config `aiC01_aqiCitizenAlert` → click "Test" (flask icon)
2. Nhập payload:
   ```json
   {"module":"ENVIRONMENT","measureType":"AQI","value":165,"sensorId":"SENSOR-D7-001","districtCode":"D7"}
   ```
3. Click "Test Filter"

**Expected:**
- filterMatch: true (vì module=ENVIRONMENT và AQI > 150)
- mappedVariables hiển thị đúng variables sau mapping

**Steps (test không match):**
1. Thay payload với module="FLOOD"
2. Click "Test Filter"
3. filterMatch: false

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-AI-014: Toggle Enable/Disable Config
**Priority:** P2 | **Type:** Functional

**Steps:**
1. Tìm config `test_noise_monitoring` đã tạo ở TC-AI-011
2. Toggle switch Enabled → OFF
3. Verify switch chuyển sang disabled
4. Gửi Kafka event tới topic của config này
5. Verify KHÔNG có process mới được trigger (disabled configs bị bỏ qua)
6. Toggle lại → ON

**Expected:**
- Toggle UI cập nhật ngay
- Backend: config.enabled = false → GenericKafkaTriggerService bỏ qua
- Redis cache được invalidate sau toggle (Kafka event published tới cache-invalidation topic)

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

## 7. Test Cases — AI Decision Routing

### TC-AI-020: Confidence > 0.85 → AUTO_EXECUTE
**Priority:** P0 | **Type:** Functional

**Preconditions:** Backend running, Claude API configured (hoặc mock data)

**Steps:**
1. Fire trigger cho `aiC01_aqiCitizenAlert` với AQI = 175 (ngưỡng cao):
   ```bash
   curl -X POST http://localhost:8080/api/v1/workflow/trigger/aiC01_aqiCitizenAlert \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"sensorId":"SENSOR-AIR-001","aqiValue":175,"districtCode":"D7","measuredAt":"2026-06-01T10:00:00Z"}'
   ```
2. Lấy process instance ID từ response
3. GET process instance để xem variables:
   ```bash
   curl -s http://localhost:8080/api/v1/workflow/instances/{instanceId} | jq '.variables'
   ```

**Expected:**
- `aiDecision`: "NOTIFY_CITIZENS"
- `aiConfidence`: >= 0.85
- `aiSeverity`: "HIGH" hoặc "CRITICAL"
- `aiReasoning`: chuỗi giải thích từ Claude
- Process status: COMPLETED (auto-executed)

**Fallback Mode (không có Claude API):**
- `aiDecision`: fallback rule-based decision
- `aiConfidence`: <= 0.7 (fallback luôn cho confidence thấp hơn)
- Process vẫn COMPLETED

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-AI-021: Confidence 0.6–0.85 → OPERATOR_QUEUE
**Priority:** P1 | **Type:** Functional

**Steps:**
1. Inject AQI = 110 (ngưỡng mơ hồ — Unhealthy for Sensitive):
   ```bash
   curl -X POST http://localhost:8080/api/v1/workflow/trigger/aiC01_aqiCitizenAlert \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"sensorId":"SENSOR-AIR-002","aqiValue":110,"districtCode":"D3","measuredAt":"2026-06-01T02:00:00Z"}'
   ```
2. Kiểm tra process instance variables
3. Kiểm tra operator queue (nếu có UI)

**Expected:**
- `aiConfidence`: khoảng 0.6–0.85
- Routing action: OPERATOR_QUEUE
- Process đang chờ human review (status ACTIVE, not COMPLETED immediately)

**Notes:** Kết quả phụ thuộc Claude API. Với fallback mode, kiểm tra logic theo rule-based thresholds.

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-AI-022: Circuit Breaker — Fallback khi Claude API lỗi
**Priority:** P1 | **Type:** Resilience

**Steps:**
1. Stop Claude API (hoặc set sai API key tạm thời):
   ```bash
   # Restart backend với API key sai
   CLAUDE_API_KEY=invalid_key ./gradlew bootRun -Dspring.profiles.active=test
   ```
2. Fire một workflow trigger
3. Kiểm tra logs: `grep "circuit\|fallback" backend/logs/app.log`
4. Kiểm tra process instance vẫn complete

**Expected:**
- Log: "Claude API key not configured, using fallback" hoặc "circuit OPEN"
- Process vẫn COMPLETED với fallback decision
- `aiDecision`: không phải "ERROR" (fallback service cung cấp decision hợp lệ)
- Không có exception unhandled

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-AI-023: Decision Cache — Redis Cache Hit
**Priority:** P2 | **Type:** Performance

**Steps:**
1. Fire `aiM01_floodResponseCoordination` với water_level = 2.1:
   ```bash
   curl -X POST http://localhost:8080/api/v1/workflow/trigger/aiC03_floodEmergencyEvacuation \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"alertId":"ALERT-001","waterLevel":2.1,"location":"district-7"}'
   ```
2. Fire lại với cùng parameters trong vòng 15 phút
3. Kiểm tra response time (lần 2 phải nhanh hơn)
4. Check Redis: `redis-cli keys "decision:*"`

**Expected:**
- Lần 1: gọi Claude API (~1–3 giây)
- Lần 2: hit Redis cache (~milliseconds)
- Redis key `decision:aiM01_floodResponseCoordination:*` có TTL 900 (15 phút)
- Cached result có `cached: true` trong RoutingResult

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

## 8. E2E Scenario Tests

### TC-AI-030: E2E — AQI Citizen Alert (Kafka → AI → Notify)
**Priority:** P0 | **Type:** End-to-End

**Flow:** Sensor → Flink (nếu có) hoặc trực tiếp inject → Kafka → GenericKafkaTrigger → Camunda → AIAnalysisDelegate → Notification

**Preconditions:**
- Kafka running, topic `UIP.flink.alert.detected.v1` tồn tại
- Config `aiC01_aqiCitizenAlert` enabled

**Steps:**
1. Publish Kafka message (simulate Flink output):
   ```bash
   # Dùng Kafka CLI hoặc script
   kafka-console-producer --broker-list localhost:9092 \
     --topic UIP.flink.alert.detected.v1 <<EOF
   {"module":"ENVIRONMENT","measureType":"AQI","value":185,"sensorId":"SENSOR-AIR-007",
    "districtCode":"D7","timestamp":"2026-06-01T10:00:00Z"}
   EOF
   ```
2. Wait 10 seconds
3. Kiểm tra Process Instances tab → tìm process `aiC01_aqiCitizenAlert`
4. Kiểm tra Alerts (nếu notification delegate tạo alert)

**Expected:**
- Process instance tạo trong vòng 10s
- `aiDecision` = "NOTIFY_CITIZENS" (AQI 185 = Unhealthy)
- Process COMPLETED
- Alert/notification log xuất hiện

**Actual Result:**
**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-AI-031: E2E — Citizen Service Request (REST → AI → Handle)
**Priority:** P0 | **Type:** End-to-End (Demo Flow)

**Flow:** Citizen Portal → POST /api/v1/workflow/trigger/aiC02_citizenServiceRequest → Camunda → AI phân loại → gán operator

**Steps:**
1. Từ Citizen Portal (http://localhost:3003) hoặc curl:
   ```bash
   curl -X POST http://localhost:8080/api/v1/workflow/trigger/aiC02_citizenServiceRequest \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "citizenId": "citizen-001",
       "requestType": "ENVIRONMENT",
       "description": "Mùi hóa chất nồng nặc gần khu công nghiệp, ảnh hưởng sức khỏe trẻ em",
       "district": "D7",
       "priority": "HIGH"
     }'
   ```
2. Kiểm tra response: `processInstanceId`
3. GET `/api/v1/workflow/instances/{id}` sau 15 giây

**Expected:**
- Response: `{"processInstanceId": "...", "scenarioKey": "aiC02_citizenServiceRequest", "status": "STARTED"}`
- Process COMPLETED với:
  - `aiDecision`: "ESCALATE_TO_OPERATOR" hoặc "AUTO_ASSIGN" tùy AI
  - `aiSeverity`: "HIGH" (vì mô tả nghiêm trọng, liên quan trẻ em)
  - `aiReasoning`: giải thích từ Claude

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-AI-032: E2E — Flood Response Coordination (Kafka → Multi-step)
**Priority:** P0 | **Type:** End-to-End (Demo Flagship)

**Flow:** Flood sensor alert → Kafka → AI lập kế hoạch ứng phó → Coordinate delegate

**Steps:**
1. Chạy demo flood script:
   ```bash
   ./scripts/demo-flood-alert.sh
   ```
   Hoặc inject thủ công:
   ```bash
   kafka-console-producer --broker-list localhost:9092 \
     --topic UIP.flink.alert.detected.v1 <<EOF
   {"module":"FLOOD","measureType":"WATER_LEVEL","value":2.1,"sensorId":"SENSOR-FLOOD-007",
    "alertId":"FLOOD-ALERT-001","location":"district-7","timestamp":"2026-06-01T10:00:00Z"}
   EOF
   ```
2. Theo dõi Process Instances tab
3. Kiểm tra variables sau khi COMPLETED

**Expected:**
- `aiDecision`: "FULL_RESPONSE" hoặc "PARTIAL_RESPONSE" (water_level 2.1m > threshold 1.8m)
- `aiSeverity`: "HIGH" hoặc "CRITICAL"
- `aiRecommendedActions`: array với các actions như ["deploy emergency team", "coordinate agencies"]
- `aiReasoning`: kế hoạch ứng phó chi tiết
- Process COMPLETED trong vòng 30 giây

**Điểm demo (talking points):**
- "Claude AI phân tích 5 chiều: lượng mưa, mực nước, thổ nhưỡng, dự báo, lịch sử"
- "Confidence 0.92 → auto-execute full response, không cần chờ operator"
- "Audit trail: mọi quyết định lưu với reasoning"

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-AI-033: E2E — Scheduled Trigger (ESG Anomaly)
**Priority:** P1 | **Type:** Functional

**Preconditions:** Config `aiM04_esgAnomalyInvestigation` enabled, schedule cron định kỳ

**Steps:**
1. Kiểm tra cron của config:
   ```bash
   curl -s -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/v1/admin/workflow-configs | \
     jq '.[] | select(.scenarioKey=="aiM04_esgAnomalyInvestigation") | {cron: .scheduleCron, enabled: .enabled}'
   ```
2. Đợi đến thời điểm schedule kích hoạt (hoặc fire thủ công)
3. Kiểm tra Process Instances

**Expected:**
- Process instance tự động tạo theo schedule
- `aiDecision`: INVESTIGATE hoặc NO_ACTION
- Nếu có ESG anomaly: `aiSeverity` HIGH + `aiRecommendedActions` có steps điều tra

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

## 9. API Test Reference — AI Workflow

```bash
# Setup
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@uip.gov.vn","password":"Admin123!"}' | jq -r '.token')

# === WORKFLOW DEFINITIONS (BPMN Designer) ===

# TC-A01: List workflow definitions
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/workflows?page=0&size=10" | jq '.content[].name'

# TC-A02: Create new workflow definition
curl -s -X POST http://localhost:8080/api/v1/workflows \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test API Workflow","description":"API test","bpmnXml":"<?xml version=\"1.0\"?><bpmn:definitions>...</bpmn:definitions>"}' \
  | jq '{id, name, version}'

# TC-A03: Deploy workflow to Camunda
WORKFLOW_ID="<id from above>"
curl -s -X POST "http://localhost:8080/api/v1/workflows/$WORKFLOW_ID/deploy" \
  -H "Authorization: Bearer $TOKEN" | jq '{id, camundaDeploymentId}'

# === CAMUNDA PROCESS INSTANCES ===

# TC-A04: List active process instances
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/workflow/instances?status=ACTIVE&page=0&size=20" \
  | jq '.content[] | {id, processDefinitionKey, status}'

# TC-A05: List all (active + completed)
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/workflow/instances?status=ALL&page=0&size=20" \
  | jq '.totalElements'

# TC-A06: Start process manually
curl -s -X POST http://localhost:8080/api/v1/workflow/start \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"processKey":"aiC01_aqiCitizenAlert","variables":{"scenarioKey":"aiC01_aqiCitizenAlert","sensorId":"SENSOR-TEST","aqiValue":175,"districtCode":"D7","measuredAt":"2026-06-01T10:00:00Z"}}' \
  | jq '{id, processDefinitionKey}'

# === TRIGGER CONFIG CRUD ===

# TC-A07: List all trigger configs
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/admin/workflow-configs | jq '.[].scenarioKey'

# TC-A08: Get specific config
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/admin/workflow-configs/1 | jq '.'

# TC-A09: Fire REST trigger (aiC02)
curl -s -X POST http://localhost:8080/api/v1/workflow/trigger/aiC02_citizenServiceRequest \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"citizenId":"test-001","requestType":"ENVIRONMENT","description":"Smoke from factory","district":"D7"}' \
  | jq '{processInstanceId, status}'

# TC-A10: Test filter evaluation
curl -s -X POST \
  "http://localhost:8080/api/v1/admin/workflow-configs/1/test" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"module":"ENVIRONMENT","measureType":"AQI","value":185,"sensorId":"SENSOR-TEST"}' \
  | jq '{filterMatch, mappedVariables}'

# === DECISION ROUTING ===

# TC-A11: Check Redis decision cache
redis-cli keys "decision:*"
redis-cli ttl "decision:aiM01_floodResponseCoordination:*"
```

---

## 10. Test Session Report — AI Workflow

```markdown
## Test Session Report — [Date] AI Workflow Feature
**Tester:** _____ | **Sprint:** MVP3-6 | **Environment:** local/staging
**Claude API Mode:** LIVE / FALLBACK

### Tests Executed
| Test ID | Title | Result | Notes |
|---------|-------|--------|-------|
| TC-AI-001 | Tạo workflow mới trên Designer | | |
| TC-AI-002 | Node Palette — kéo thả 5 node types | | |
| TC-AI-003 | AI Config Panel — confidence labels | | |
| TC-AI-004 | Save và Load Workflow | | |
| TC-AI-005 | Delete Workflow — confirm dialog | | |
| TC-AI-010 | Xem danh sách 7 Trigger Configs | | |
| TC-AI-011 | Tạo config mới (KAFKA trigger) | | |
| TC-AI-012 | Demo Preset — Load và Fire | | |
| TC-AI-013 | Test Filter — payload matching | | |
| TC-AI-014 | Toggle Enable/Disable | | |
| TC-AI-020 | Confidence > 0.85 → AUTO_EXECUTE | | |
| TC-AI-021 | Confidence 0.6–0.85 → OPERATOR_QUEUE | | |
| TC-AI-022 | Circuit Breaker → Fallback | | |
| TC-AI-023 | Decision Cache — Redis hit | | |
| TC-AI-030 | E2E — AQI Citizen Alert | | |
| TC-AI-031 | E2E — Citizen Service Request | | |
| TC-AI-032 | E2E — Flood Response Coordination | | |
| TC-AI-033 | E2E — Scheduled Trigger (ESG) | | |

### Summary
- Total: 18 | Passed: ___ | Failed: ___ | Blocked: ___

### Bugs Found
| Bug ID | Severity | Title | TC |
|--------|----------|-------|----|
| | | | |

### AI Workflow Selling Points — Verified?
- [ ] BPMN Designer: tạo / save / deploy workflow thành công
- [ ] AI Decision với confidence routing hoạt động (≥1 scenario)
- [ ] Circuit breaker / fallback: không crash khi Claude API lỗi
- [ ] Config Engine: tạo / fire / test trigger hoạt động
- [ ] E2E flood scenario: sensor → process instance → AI decision < 30s

### Sign-off
- [ ] No P0/P1 open bugs
- [ ] BPMN Designer demo-ready (AI Workflow selling point)
- [ ] Tester sign-off: ___________
```

---

## 11. Bug Report Template — AI Workflow

```markdown
## BUG: [Short title — AI Workflow specific]
**Date:** [date] | **Tester:** [name]
**Severity:** P0/P1/P2/P3
**Module:** ai-workflow / bpmn-designer / trigger-config / decision-routing
**Environment:** local/staging | **Claude API Mode:** LIVE/FALLBACK

### Steps to Reproduce
1. [Chính xác action — include scenarioKey, confidence value, trigger type]
2. [Payload/data used]
3. [Step tiếp theo]

### Expected
[Trích dẫn acceptance criteria cụ thể]

### Actual
[Điều gì xảy ra — include error message, wrong routing action, etc.]

### Evidence
- Screenshot / Video: [attach]
- Process Instance ID: [id if applicable]
- API Response:
  ```json
  { "aiDecision": "...", "aiConfidence": ..., "aiReasoning": "..." }
  ```
- Backend logs (AI decision section):
  ```
  [INFO] AI decision for aiM01_floodResponseCoordination: FULL_RESPONSE (confidence: 0.91)
  ```
- Claude API mode: LIVE / FALLBACK
- Redis cache hit: YES / NO

### Frequency
Always / Sometimes (X/10) / Once
```

---

## 12. Checklist Demo AI Workflow (5 phút)

Dùng cho demo với khách hàng / stakeholders:

```
[ ] 1. Backend running + 7 Camunda processes deployed
[ ] 2. Frontend accessible (localhost:3000)
[ ] 3. Tab "Designer" hiện với BPMN canvas
[ ] 4. Demo: New → kéo AI Decision → configure → Save → Deploy
[ ] 5. Tab "Config Engine" hiện 7 configs (KAFKA/REST/SCHEDULED)
[ ] 6. Demo: Fire aiC02 (citizen request) → xem process instance
[ ] 7. Tab "Process Instances" hiện instances với AI variables
[ ] 8. Show decision routing: confidence → auto/queue/escalate
[ ] 9. Optional: run demo-flood-alert.sh → live alert in <30s
[ ] 10. Backup: screenshot/video sẵn sàng nếu infra lỗi
```

**Key talking points (30 giây elevator pitch):**
> *"Với AI Workflow, city admin không cần developer để thêm scenario mới. Chỉ cần kéo thả BPMN, chọn AI model, set confidence threshold — hệ thống tự động quyết định escalate hay auto-execute. Mọi quyết định đều có audit trail với reasoning từ Claude AI."*
