# Prompt: S4-03 — 3 Citizen AI Delegates (5 SP)

**Story:** S4-03 — Citizen AI Delegates: AI-C01, AI-C02, AI-C03
**Status:** ⬜ Pending (restructured 20/04/2026)
**Owner:** Be-1
**Depends on:** S4-02 (AI Decision Node)
**Prompt cũ (8 SP):** Đã restructure — trigger classes KHÔNG code ở đây, S4-10 Config Engine handle

---

## Thay đổi so với plan cũ

| Trước restructure | Sau restructure |
|---|---|
| Code 3 trigger classes (Kafka/REST) | ❌ KHÔNG code triggers — S4-10 Config Engine |
| Code 3 delegates | ✅ Vẫn code delegates |
| Code DTOs cho REST endpoint | ✅ Vẫn code DTOs |
| Integration test qua triggers | Integration test start process trực tiếp |
| 8 SP | 5 SP |

**Lý do:** Trigger classes sẽ bị code 2 lần (specific rồi generic) → lãng phí. Config Engine (S4-10) sẽ absorps tất cả trigger work.

---

## Trạng thái hiện tại

**Đã có (từ S4-01/S4-02):**
- ✅ BPMN: `aqi-citizen-alert.bpmn`, `citizen-service-request.bpmn`, `flood-emergency-evacuation.bpmn`
- ✅ Notification infra: `NotificationService` (Redis pub/sub → SSE), `AlertEventKafkaConsumer`
- ✅ `WorkflowService.startProcess(processKey, variables)`
- ✅ `AIAnalysisDelegate` — generic AI analysis, đọc `scenarioKey` từ variables
- ✅ `ClaudeApiService` — Claude API client + prompt templates
- ✅ `PlaceholderDelegate` — skeleton (sẽ được thay thế)

**Cần code:**
- ❌ 3 delegate implementations (`AqiCitizenAlertDelegate`, `CitizenServiceRequestDelegate`, `FloodEvacuationDelegate`) — verify/update existing
- ❌ Unit tests cho 3 delegates
- ❌ Integration test: start process trực tiếp → verify delegates execute đúng
- ❌ DTOs: `ServiceRequestDto`, `ServiceRequestResponse` (cho S4-10 REST trigger)

---

## Bước 1 — Phân tích codebase (BẮT BUỘC)

Dùng java-graph-mcp để hiểu delegates hiện tại:

```
find_class("AqiCitizenAlertDelegate")           // đã có? verify logic
find_class("CitizenServiceRequestDelegate")     // đã có? verify logic
find_class("FloodEvacuationDelegate")           // đã có? verify logic
find_class("NotificationService")               // ALERT_CHANNEL constant
find_class("AIAnalysisDelegate")                // hiểu input/output contract
find_class("WorkflowService")                   // startProcess(), hasActiveProcess()
get_class_members("AqiCitizenAlertDelegate")    // methods, dependencies
get_class_members("CitizenServiceRequestDelegate")
get_class_members("FloodEvacuationDelegate")
```

**Goal:** Xác định delegates nào đã implement, nào vẫn là placeholder, dependencies inject đúng chưa.

---

## Bước 2 — Verify/Implement 3 Delegates

### 2a. `AqiCitizenAlertDelegate.java`

**Path:** `backend/src/main/java/com/uip/backend/workflow/delegate/citizen/`

**Input variables (từ generic trigger):**
- `scenarioKey` = `"aiC01_aqiCitizenAlert"`
- `sensorId`, `aqiValue`, `districtCode`, `measuredAt`
- `aiDecision`, `aiReasoning`, `aiConfidence`, `aiSeverity`, `aiRecommendedActions`

**Output variables:**
- `notificationSent` (boolean) — true nếu `aiDecision == "NOTIFY_CITIZENS"`
- `citizensNotified` (int) — mock count

**Logic:**
```
IF aiDecision == "NOTIFY_CITIZENS":
    → Publish Redis message: {"type":"aqi_alert", "sensorId", "district", "aqi", "measuredAt"}
    → Set notificationSent = true
ELSE:
    → Set notificationSent = false
```

**Dependencies:** `StringRedisTemplate`, `NotificationService.ALERT_CHANNEL`

### 2b. `CitizenServiceRequestDelegate.java`

**Path:** `backend/src/main/java/com/uip/backend/workflow/delegate/citizen/`

**Input variables:**
- `scenarioKey` = `"aiC02_citizenServiceRequest"`
- `citizenId`, `requestId`, `requestType`, `description`, `district`
- `aiDecision`, `aiReasoning`, `aiRecommendedActions`

**Output variables:**
- `department` (String) — ENVIRONMENT | UTILITIES | TRANSPORT | GENERAL
- `autoResponseText` (String) — AI recommended action hoặc default text
- `priority` (String) — HIGH | MEDIUM | LOW (dựa trên aiSeverity)

**Logic:**
```
department = switch(aiDecision):
    "ASSIGN_TO_ENVIRONMENT" → "ENVIRONMENT"
    "ASSIGN_TO_UTILITIES"   → "UTILITIES"
    "ASSIGN_TO_TRANSPORT"   → "TRANSPORT"
    default                 → "GENERAL"

autoResponseText = aiRecommendedActions[0] nếu không null, else "Your request has been received"
```

### 2c. `FloodEvacuationDelegate.java`

**Path:** `backend/src/main/java/com/uip/backend/workflow/delegate/citizen/`

**Input variables:**
- `scenarioKey` = `"aiC03_floodEmergencyEvacuation"`
- `waterLevel`, `sensorLocation`, `warningZones`, `detectedAt`
- `aiDecision`, `aiReasoning`, `aiSeverity`

**Output variables:**
- `massSmsTriggered` (boolean)
- `evacuationGuide` (String)
- `affectedCitizens` (int) — mock count

**Logic:**
```
IF aiSeverity == "CRITICAL":
    → Publish Redis: {"type":"flood_evacuation", "severity":"CRITICAL", "zones", "guide"}
    → Set massSmsTriggered = true
    → evacuationGuide = aiReasoning
ELSE:
    → Set massSmsTriggered = false
    → evacuationGuide = "Monitor situation"
```

---

## Bước 3 — DTOs cho S4-10 REST Trigger

**S4-10 Config Engine** cần DTOs cho REST trigger (AI-C02). Tạo sẵn ở đây:

### `ServiceRequestDto.java`

**Path:** `backend/src/main/java/com/uip/backend/citizen/api/dto/`

```java
public record ServiceRequestDto(
    @NotBlank String requestType,   // ELECTRICITY, WATER, ROAD, ENVIRONMENT
    @NotBlank String description,
    String district                 // optional — từ citizen profile nếu null
) {}
```

### `ServiceRequestResponse.java`

**Path:** `backend/src/main/java/com/uip/backend/citizen/api/dto/`

```java
public record ServiceRequestResponse(
    String requestId,
    String processInstanceId,
    String status    // "PROCESSING"
) {}
```

---

## Bước 4 — Unit Tests

**Path:** `backend/src/test/java/com/uip/backend/workflow/delegate/citizen/`

### 4a. `AqiCitizenAlertDelegateTest.java`

```java
@ExtendWith(MockitoExtension.class)
class AqiCitizenAlertDelegateTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private DelegateExecution execution;
    @InjectMocks private AqiCitizenAlertDelegate delegate;
```

| # | Case | Setup | Verify |
|---|------|-------|--------|
| 1 | `aiDecision = NOTIFY_CITIZENS` → publish Redis | `getVariable("aiDecision")` = `"NOTIFY_CITIZENS"`, `aqiValue` = 175.0 | `redisTemplate.convertAndSend("uip:alerts", ...)` called, `notificationSent` = true |
| 2 | `aiDecision = MONITOR_ONLY` → không publish | `getVariable("aiDecision")` = `"MONITOR_ONLY"` | `convertAndSend` never(), `notificationSent` = false |
| 3 | `aiDecision = null` → không throw | `getVariable("aiDecision")` = null | no exception |
| 4 | Redis message đúng JSON structure | capture argument | contains `"type":"aqi_alert"`, `"district"`, `"aqi"` |

### 4b. `CitizenServiceRequestDelegateTest.java`

| # | Case | Setup | Verify |
|---|------|-------|--------|
| 1 | `aiDecision = ASSIGN_TO_ENVIRONMENT` | — | `department` = "ENVIRONMENT" |
| 2 | `aiDecision = ASSIGN_TO_UTILITIES` | — | `department` = "UTILITIES" |
| 3 | `aiDecision = unknown` | `"SOME_VALUE"` | `department` = "GENERAL" |
| 4 | `requestId` được set (UUID format) | — | `requestId` hasSize(36) |
| 5 | `aiRecommendedActions` non-null → autoResponseText từ first | `List.of("Wait 2 days")` | `autoResponseText` = "Wait 2 days" |
| 6 | `aiRecommendedActions` = null → default text | null | `autoResponseText` contains "received" |

### 4c. `FloodEvacuationDelegateTest.java`

| # | Case | Setup | Verify |
|---|------|-------|--------|
| 1 | `aiSeverity = CRITICAL` → publish Redis, massSms = true | `waterLevel` = 4.2 | `convertAndSend` called, `massSmsTriggered` = true |
| 2 | `aiSeverity = HIGH` (non-critical) | — | `convertAndSend` never(), `massSmsTriggered` = false |
| 3 | Redis message đúng structure khi CRITICAL | capture message | contains `"type":"flood_evacuation"`, `"severity":"CRITICAL"` |
| 4 | `evacuationGuide` = aiReasoning khi CRITICAL | `"Water rising fast"` | `evacuationGuide` contains "Water rising fast" |

---

## Bước 5 — Integration Test

**Path:** `backend/src/test/java/com/uip/backend/workflow/CitizenAiScenariosIntegrationTest.java`

**QUAN TRỌNG:** KHÔNG test qua trigger. Start process trực tiếp qua `workflowService.startProcess()`.

```java
@SpringBootTest
@ActiveProfiles("test")
class CitizenAiScenariosIntegrationTest {

    @Autowired private WorkflowService workflowService;
    @Autowired private HistoryService  historyService;

    @MockBean private ClaudeApiService    claudeApiService;
    @MockBean private StringRedisTemplate redisTemplate;
```

### AI-C01 Test
```java
@Test
void aiC01_aqiAlert_processCompletes() {
    // Mock AI decision
    when(claudeApiService.analyzeAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(
            AIDecision.builder().decision("NOTIFY_CITIZENS").confidence(0.95)
                .severity("HIGH").reasoning("AQI dangerously high")
                .recommendedActions(List.of("Stay indoors")).build()));
    when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

    // Start process trực tiếp — KHÔNG qua Kafka trigger
    ProcessInstanceDto instance = workflowService.startProcess("aiC01_aqiCitizenAlert", Map.of(
        "scenarioKey",  "aiC01_aqiCitizenAlert",
        "sensorId",     "AQI-001",
        "aqiValue",     175.0,
        "districtCode", "D7",
        "measuredAt",   Instant.now().toString()
    ));

    // Verify process completes
    await().atMost(10, SECONDS).until(() ->
        historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId()).finished().count() == 1);

    verify(redisTemplate, atLeastOnce())
        .convertAndSend(eq(NotificationService.ALERT_CHANNEL), anyString());
}
```

### AI-C02 Test
```java
@Test
void aiC02_serviceRequest_classifiedAndRouted() {
    when(claudeApiService.analyzeAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(
            AIDecision.builder().decision("ASSIGN_TO_ENVIRONMENT").confidence(0.88)
                .severity("MEDIUM").reasoning("Environmental issue")
                .recommendedActions(List.of("Inspect within 24h")).build()));

    ProcessInstanceDto instance = workflowService.startProcess("aiC02_citizenServiceRequest", Map.of(
        "scenarioKey", "aiC02_citizenServiceRequest",
        "citizenId",   "citizen-001",
        "requestId",   UUID.randomUUID().toString(),
        "requestType", "ENVIRONMENT",
        "description", "Bad smell near factory",
        "district",    "D7"
    ));

    await().atMost(10, SECONDS).until(() ->
        historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId()).finished().count() == 1);
}
```

### AI-C03 Test
```java
@Test
void aiC03_criticalFlood_evacuationTriggered() {
    when(claudeApiService.analyzeAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(
            AIDecision.builder().decision("EVACUATE_IMMEDIATELY").confidence(0.99)
                .severity("CRITICAL").reasoning("Water level critical")
                .recommendedActions(List.of("Evacuate to Zone B")).build()));
    when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

    ProcessInstanceDto instance = workflowService.startProcess("aiC03_floodEmergencyEvacuation", Map.of(
        "scenarioKey",    "aiC03_floodEmergencyEvacuation",
        "waterLevel",     4.2,
        "sensorLocation", "CANAL-D8",
        "warningZones",   "D8,D9",
        "detectedAt",     Instant.now().toString()
    ));

    await().atMost(10, SECONDS).until(() ->
        historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId()).finished().count() == 1);

    verify(redisTemplate, atLeastOnce())
        .convertAndSend(eq(NotificationService.ALERT_CHANNEL), anyString());
}
```

---

## Bước 6 — Chạy test

```bash
cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc

# Unit tests
./gradlew test \
  --tests "com.uip.backend.workflow.delegate.citizen.AqiCitizenAlertDelegateTest" \
  --tests "com.uip.backend.workflow.delegate.citizen.CitizenServiceRequestDelegateTest" \
  --tests "com.uip.backend.workflow.delegate.citizen.FloodEvacuationDelegateTest" \
  -p backend

# Integration test
./gradlew test \
  --tests "com.uip.backend.workflow.CitizenAiScenariosIntegrationTest" \
  -p backend

# Full build
./gradlew build -p backend
```

---

## Acceptance Criteria (DoD)

| # | Criterion | Verify |
|---|-----------|--------|
| AC-1 | 3 delegates implement JavaDelegate, inject đúng deps | Code review |
| AC-2 | `AqiCitizenAlertDelegate`: NOTIFY_CITIZENS → Redis publish; else → skip | 4 unit tests pass |
| AC-3 | `CitizenServiceRequestDelegate`: route đúng department, autoResponseText | 6 unit tests pass |
| AC-4 | `FloodEvacuationDelegate`: CRITICAL → mass notification; else → monitor | 4 unit tests pass |
| AC-5 | DTOs `ServiceRequestDto` + `ServiceRequestResponse` created | Code exists |
| AC-6 | Integration test: 3 scenarios start → process completes | `CitizenAiScenariosIntegrationTest` 3/3 pass |
| AC-7 | `./gradlew build` → BUILD SUCCESSFUL | No compile error |
| AC-8 | KHÔNG tạo trigger classes — S4-10 sẽ handle | No trigger files in `workflow/trigger/` for citizen |

---

## Lưu ý

**1. KHÔNG tạo trigger classes:** `AqiWorkflowTriggerService` và `FloodWorkflowTriggerService` đã tồn tại (từ implementation trước). S4-10 sẽ disable chúng và thay bằng Config Engine. KHÔNG sửa chúng trong story này.

**2. Integration test start process trực tiếp:** Dùng `workflowService.startProcess()` thay vì qua Kafka trigger. S4-10 Config Engine sẽ verify end-to-end (Kafka → Config Engine → startProcess → process completes).

**3. BPMN gateway conditions:**
- `aqi-citizen-alert.bpmn`: `${aiDecision == 'NOTIFY_CITIZENS'}` — delegate phải set đúng `aiDecision` variable (AIAnalysisDelegate đã set)
- `flood-emergency-evacuation.bpmn`: `${aiSeverity == 'CRITICAL'}` — AIAnalysisDelegate đã set

**4. `CitizenController.submitServiceRequest()` endpoint:** KHÔNG thêm vào `CitizenController`. S4-10 `GenericRestTriggerController` sẽ handle tất cả REST-triggered workflows.
