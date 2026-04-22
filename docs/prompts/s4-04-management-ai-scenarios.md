# Prompt: S4-04 — 4 Management AI Scenarios (8 SP)

**Story:** S4-04 — Management AI Scenarios: AI-M01, AI-M02, AI-M03, AI-M04
**Owner:** Be-2
**Status:** ✅ DONE (18/04/2026) — Delegates + BPMN + Tests
**⚠️ Note (20/04 restructure):** Trigger classes (FloodResponseTriggerService, AqiTrafficTriggerService, ManagementWorkflowScheduler) sẽ bị disable bởi S4-10 Config Engine. Delegates + BPMN + unit tests + integration tests = GIỮ NGUYÊN 100%.

---

## Trạng thái hiện tại (đã verify qua java-graph)

**Production code đã có — KHÔNG tạo lại:**

| Class | File | Variables (in → out) |
|-------|------|----------------------|
| `FloodResponseDelegate` | `delegate/management/` | in: `alertId`, `waterLevel`, `location`, `aiReasoning`, `aiSeverity` → out: `teamDispatched`, `operationsLogId` |
| `AqiTrafficControlDelegate` | `delegate/management/` | in: `aqiValue`, `pollutants`, `affectedDistricts`, `aiRecommendedActions`, `aiSeverity` → out: `restrictionAreas`, `recommendationReport` |
| `UtilityIncidentDelegate` | `delegate/management/` | in: `metricType`, `anomalyValue`, `buildingId`, `aiReasoning`, `aiDecision` → out: `maintenanceTicketId`, `assignedTeam`, `diagnosisReport` |
| `EsgAnomalyDelegate` | `delegate/management/` | in: `metricType`, `currentValue`, `historicalAvg`, `period`, `aiReasoning`, `aiDecision` → out: `investigationReportId`, `anomalyCategory`, `investigationSummary` |
| `AIAnalysisDelegate` | `delegate/` | in: `scenarioKey` + all context vars → out: `aiDecision`, `aiReasoning`, `aiConfidence`, `aiRecommendedActions`, `aiSeverity` |
| `WorkflowService` | `service/` | `startProcess(processKey, variables)` → `ProcessInstanceDto` |

**BPMN đã có (process keys):**
- `aiM01_floodResponseCoordination` — `flood-response-coordination.bpmn`
- `aiM02_aqiTrafficControl` — `aqi-traffic-control.bpmn`
- `aiM03_utilityIncidentCoordination` — `utility-incident-coordination.bpmn`
- `aiM04_esgAnomalyInvestigation` — `esg-anomaly-investigation.bpmn`

**Còn thiếu:**
- ❌ 4 Trigger services (`workflow/trigger/management/`)
- ❌ Unit tests cho 4 delegates (`workflow/delegate/management/`)
- ❌ Integration test `ManagementAiScenariosIntegrationTest`

---

## Pattern tham chiếu (KHÔNG đọc lại — đã biết từ S4-03)

Trigger services M01/M02 copy y chang pattern `AqiWorkflowTriggerService`:
- Kafka topic: `AlertEventKafkaConsumer.TOPIC` (`"UIP.flink.alert.detected.v1"`)
- Consumer group: **mỗi trigger có group riêng** (tránh conflict)
- Helper methods: `getString()`, `getDouble()`, `getOrDefault()` — copy từ `AqiWorkflowTriggerService`
- Error handling: `WorkflowNotFoundException` → ack + warn; `Exception` → không ack (Kafka retry)

---

## Bước 1 — Trigger M01: Flood Response (Management)

**Lưu ý:** Khác với `FloodWorkflowTriggerService` (C03, citizen evacuation).  
M01 dispatch operations team; C03 notify citizens. Cùng Kafka topic, khác consumer group + process key.

**Tạo `FloodResponseTriggerService.java`** tại `com.uip.backend.workflow.trigger.management`:

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class FloodResponseTriggerService {

    public static final String PROCESS_KEY      = "aiM01_floodResponseCoordination";
    public static final double FLOOD_THRESHOLD_M = 3.5;

    private final WorkflowService workflowService;

    @KafkaListener(
        topics           = AlertEventKafkaConsumer.TOPIC,
        groupId          = "uip-workflow-m01-flood",       // khác "uip-workflow-flood" của C03
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAlertEvent(Map<String, Object> payload, Acknowledgment ack) {
        try {
            String measureType = getString(payload, "measureType");
            Double value       = getDouble(payload, "value");

            if (!"WATER_LEVEL".equalsIgnoreCase(measureType)
                    || value == null || value <= FLOOD_THRESHOLD_M) {
                ack.acknowledge();
                return;
            }

            Map<String, Object> variables = Map.of(
                "scenarioKey", PROCESS_KEY,
                "alertId",     getOrDefault(payload, "alertId", UUID.randomUUID().toString()),
                "waterLevel",  value,
                "location",    getOrDefault(payload, "sensorId", "UNKNOWN"),
                "affectedZones", getOrDefault(payload, "districtCode", "UNKNOWN")
            );

            workflowService.startProcess(PROCESS_KEY, variables);
            log.info("AI-M01 started: location={}, waterLevel={}", variables.get("location"), value);
            ack.acknowledge();

        } catch (WorkflowNotFoundException e) {
            log.warn("AI-M01 process not deployed: {}", e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to start AI-M01: {}", e.getMessage(), e);
        }
    }

    // helper: getString, getDouble, getOrDefault (same as AqiWorkflowTriggerService)
}
```

---

## Bước 2 — Trigger M02: AQI Traffic Control

**Lưu ý:** Cùng topic với `AqiWorkflowTriggerService` (C01) nhưng consumer group khác, process key khác.  
M02 generate traffic restriction report; C01 notify citizens.

**Tạo `AqiTrafficTriggerService.java`** tại `com.uip.backend.workflow.trigger.management`:

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class AqiTrafficTriggerService {

    public static final String PROCESS_KEY    = "aiM02_aqiTrafficControl";
    public static final double AQI_THRESHOLD  = 150.0;

    private final WorkflowService workflowService;

    @KafkaListener(
        topics           = AlertEventKafkaConsumer.TOPIC,
        groupId          = "uip-workflow-m02-aqi",         // khác "uip-workflow-aqi" của C01
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAlertEvent(Map<String, Object> payload, Acknowledgment ack) {
        try {
            String module      = getString(payload, "module");
            String measureType = getString(payload, "measureType");
            Double value       = getDouble(payload, "value");

            if (!"ENVIRONMENT".equalsIgnoreCase(module)
                    || !"AQI".equalsIgnoreCase(measureType)
                    || value == null || value <= AQI_THRESHOLD) {
                ack.acknowledge();
                return;
            }

            Map<String, Object> variables = Map.of(
                "scenarioKey",       PROCESS_KEY,
                "sensorId",          getOrDefault(payload, "sensorId", "UNKNOWN"),
                "aqiValue",          value,
                "pollutants",        getOrDefault(payload, "measureType", "AQI"),
                "affectedDistricts", getOrDefault(payload, "districtCode", "UNKNOWN")
            );

            workflowService.startProcess(PROCESS_KEY, variables);
            log.info("AI-M02 started: aqi={}, district={}", value, variables.get("affectedDistricts"));
            ack.acknowledge();

        } catch (WorkflowNotFoundException e) {
            log.warn("AI-M02 process not deployed: {}", e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to start AI-M02: {}", e.getMessage(), e);
        }
    }

    // helper: getString, getDouble, getOrDefault
}
```

---

## Bước 3 — Trigger M03 & M04: Scheduled Anomaly Detection

M03/M04 không có Kafka topic riêng. Dùng `@Scheduled` query DB — pattern giống `EnvironmentBroadcastScheduler`.

**Tạo `ManagementWorkflowScheduler.java`** tại `com.uip.backend.workflow.trigger.management`:

```java
/**
 * Scheduled triggers cho AI-M03 (utility incident) và AI-M04 (ESG anomaly).
 * Mỗi 2 phút query ESG metrics, nếu phát hiện anomaly → start Camunda process.
 * Circuit breaker: skip nếu process cùng buildingId/metricType đang chạy (tránh flood).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ManagementWorkflowScheduler {

    private final WorkflowService workflowService;
    private final EsgService      esgService;

    // AI-M03: Utility Incident — check energy/water anomaly
    @Scheduled(fixedDelay = 120_000)
    public void checkUtilityAnomalies() {
        try {
            // Lấy ESG metrics bất thường (>2 standard deviations từ 30-day avg)
            List<EsgAnomalyDto> anomalies = esgService.detectUtilityAnomalies();

            for (EsgAnomalyDto anomaly : anomalies) {
                // Circuit breaker: skip nếu process đang chạy cho building này
                boolean alreadyRunning = workflowService
                    .hasActiveProcess("aiM03_utilityIncidentCoordination",
                                      "buildingId", anomaly.buildingId());
                if (alreadyRunning) continue;

                Map<String, Object> variables = Map.of(
                    "scenarioKey",  "aiM03_utilityIncidentCoordination",
                    "metricType",   anomaly.metricType(),
                    "anomalyValue", anomaly.currentValue(),
                    "buildingId",   anomaly.buildingId(),
                    "detectedAt",   Instant.now().toString()
                );
                workflowService.startProcess("aiM03_utilityIncidentCoordination", variables);
                log.info("AI-M03 started: building={}, metric={}, value={}",
                         anomaly.buildingId(), anomaly.metricType(), anomaly.currentValue());
            }
        } catch (Exception e) {
            log.error("AI-M03 scheduler failed: {}", e.getMessage(), e);
        }
    }

    // AI-M04: ESG Anomaly Investigation
    @Scheduled(fixedDelay = 120_000)
    public void checkEsgAnomalies() {
        try {
            List<EsgAnomalyDto> anomalies = esgService.detectEsgAnomalies();

            for (EsgAnomalyDto anomaly : anomalies) {
                boolean alreadyRunning = workflowService
                    .hasActiveProcess("aiM04_esgAnomalyInvestigation",
                                      "metricType", anomaly.metricType());
                if (alreadyRunning) continue;

                Map<String, Object> variables = Map.of(
                    "scenarioKey",   "aiM04_esgAnomalyInvestigation",
                    "metricType",    anomaly.metricType(),
                    "currentValue",  anomaly.currentValue(),
                    "historicalAvg", anomaly.historicalAvg(),
                    "period",        anomaly.period()
                );
                workflowService.startProcess("aiM04_esgAnomalyInvestigation", variables);
                log.info("AI-M04 started: metric={}, current={}, avg={}",
                         anomaly.metricType(), anomaly.currentValue(), anomaly.historicalAvg());
            }
        } catch (Exception e) {
            log.error("AI-M04 scheduler failed: {}", e.getMessage(), e);
        }
    }
}
```

### 3a. DTO và methods cần thêm

**Tạo record `EsgAnomalyDto.java`** tại `com.uip.backend.esg.dto`:
```java
public record EsgAnomalyDto(
    String metricType,     // "energy", "water", "carbon"
    Double currentValue,
    Double historicalAvg,
    String buildingId,     // nullable cho ESG report-level
    String period          // e.g. "2026-Q1"
) {}
```

**Thêm 2 methods vào `EsgService.java`** (KHÔNG sửa existing methods):
```java
/**
 * Trả list metrics có current > historicalAvg * 1.3 (30% anomaly threshold).
 * Query từ esg.clean_metrics so sánh với 30-day average.
 * Trả list rỗng nếu không có anomaly — không throw exception.
 */
public List<EsgAnomalyDto> detectUtilityAnomalies() { ... }

public List<EsgAnomalyDto> detectEsgAnomalies() { ... }
```

**Thêm method `hasActiveProcess` vào `WorkflowService.java`**:
```java
/**
 * Check xem có process instance đang chạy (không finished) với businessKey hoặc variable match.
 * Dùng để circuit-break scheduler — tránh khởi động duplicate process.
 */
public boolean hasActiveProcess(String processKey, String variableName, String variableValue) {
    return runtimeService.createProcessInstanceQuery()
        .processDefinitionKey(processKey)
        .variableValueEquals(variableName, variableValue)
        .active()
        .count() > 0;
}
```

---

## Bước 4 — Unit Tests cho 4 Delegates

**Path chung:** `backend/src/test/java/com/uip/backend/workflow/delegate/management/`

### 4a. `FloodResponseDelegateTest.java`

```java
@ExtendWith(MockitoExtension.class)
class FloodResponseDelegateTest {

    @Mock private DelegateExecution execution;
    @InjectMocks private FloodResponseDelegate delegate;
```

**Case 1 — aiSeverity không phải LOW → teamDispatched = true, operationsLogId được set:**
```java
when(execution.getVariable("aiSeverity")).thenReturn("CRITICAL");
when(execution.getVariable("waterLevel")).thenReturn(4.2);
when(execution.getVariable("location")).thenReturn("CANAL-D8");
when(execution.getVariable("aiReasoning")).thenReturn("Rapid water rise");
delegate.execute(execution);
verify(execution).setVariable("teamDispatched", true);
ArgumentCaptor<String> logIdCaptor = ArgumentCaptor.forClass(String.class);
verify(execution).setVariable(eq("operationsLogId"), logIdCaptor.capture());
assertThat(logIdCaptor.getValue()).hasSize(36); // UUID
```

**Case 2 — aiSeverity = LOW → teamDispatched = false:**
```java
when(execution.getVariable("aiSeverity")).thenReturn("LOW");
delegate.execute(execution);
verify(execution).setVariable("teamDispatched", false);
```

**Case 3 — aiSeverity = null → teamDispatched = true (null != "LOW"):**
```java
when(execution.getVariable("aiSeverity")).thenReturn(null);
delegate.execute(execution);
verify(execution).setVariable("teamDispatched", true);
```

**Case 4 — không throw exception với tất cả variables = null:**
```java
assertThatCode(() -> delegate.execute(execution)).doesNotThrowAnyException();
```

### 4b. `AqiTrafficControlDelegateTest.java`

```java
@ExtendWith(MockitoExtension.class)
class AqiTrafficControlDelegateTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private DelegateExecution execution;
    @InjectMocks private AqiTrafficControlDelegate delegate;
```

**Case 1 — recommendedActions non-null → restrictionAreas chứa action đầu tiên:**
```java
when(objectMapper.writeValueAsString(any())).thenReturn("[{\"district\":\"D7\",\"restriction\":\"Odd-even\"}]");
when(execution.getVariable("aiRecommendedActions")).thenReturn(List.of("Odd-even restriction"));
when(execution.getVariable("affectedDistricts")).thenReturn("D7");
when(execution.getVariable("aqiValue")).thenReturn(175.0);
when(execution.getVariable("aiSeverity")).thenReturn("HIGH");
delegate.execute(execution);
verify(execution).setVariable(eq("restrictionAreas"), anyString());
verify(execution).setVariable(eq("recommendationReport"), contains("D7"));
```

**Case 2 — recommendedActions = null → không throw, default restriction = "Monitor air quality":**
```java
when(execution.getVariable("aiRecommendedActions")).thenReturn(null);
when(objectMapper.writeValueAsString(any())).thenReturn("[]");
assertThatCode(() -> delegate.execute(execution)).doesNotThrowAnyException();
```

**Case 3 — ObjectMapper throw JsonProcessingException → restrictionAreas = "[]":**
```java
when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("err"){});
delegate.execute(execution);
verify(execution).setVariable("restrictionAreas", "[]");
```

**Case 4 — recommendationReport chứa đủ thông tin:**
```java
when(execution.getVariable("aqiValue")).thenReturn(200.0);
when(execution.getVariable("affectedDistricts")).thenReturn("D1,D3");
when(execution.getVariable("aiSeverity")).thenReturn("CRITICAL");
when(execution.getVariable("aiRecommendedActions")).thenReturn(List.of("Close factories"));
when(objectMapper.writeValueAsString(any())).thenReturn("[]");
delegate.execute(execution);
ArgumentCaptor<String> reportCaptor = ArgumentCaptor.forClass(String.class);
verify(execution).setVariable(eq("recommendationReport"), reportCaptor.capture());
assertThat(reportCaptor.getValue()).contains("D1,D3").contains("CRITICAL").contains("Close factories");
```

### 4c. `UtilityIncidentDelegateTest.java`

```java
@ExtendWith(MockitoExtension.class)
class UtilityIncidentDelegateTest {

    @Mock private DelegateExecution execution;
    @InjectMocks private UtilityIncidentDelegate delegate;
```

**Case 1 — metricType = "electricity" → assignedTeam = "ELECTRICAL_TEAM":**
```java
when(execution.getVariable("metricType")).thenReturn("electricity");
delegate.execute(execution);
verify(execution).setVariable("assignedTeam", "ELECTRICAL_TEAM");
```

**Case 2 — metricType = "water" → assignedTeam = "PLUMBING_TEAM":**
```java
when(execution.getVariable("metricType")).thenReturn("water");
delegate.execute(execution);
verify(execution).setVariable("assignedTeam", "PLUMBING_TEAM");
```

**Case 3 — metricType không match → assignedTeam = "GENERAL_MAINTENANCE":**
```java
when(execution.getVariable("metricType")).thenReturn("UNKNOWN_METRIC");
delegate.execute(execution);
verify(execution).setVariable("assignedTeam", "GENERAL_MAINTENANCE");
```

**Case 4 — maintenanceTicketId là UUID và diagnosisReport chứa buildingId:**
```java
when(execution.getVariable("buildingId")).thenReturn("BLDG-001");
when(execution.getVariable("metricType")).thenReturn("electricity");
when(execution.getVariable("anomalyValue")).thenReturn(450.0);
delegate.execute(execution);
ArgumentCaptor<String> ticketCaptor = ArgumentCaptor.forClass(String.class);
verify(execution).setVariable(eq("maintenanceTicketId"), ticketCaptor.capture());
assertThat(ticketCaptor.getValue()).hasSize(36);
ArgumentCaptor<String> reportCaptor = ArgumentCaptor.forClass(String.class);
verify(execution).setVariable(eq("diagnosisReport"), reportCaptor.capture());
assertThat(reportCaptor.getValue()).contains("BLDG-001");
```

### 4d. `EsgAnomalyDelegateTest.java`

```java
@ExtendWith(MockitoExtension.class)
class EsgAnomalyDelegateTest {

    @Mock private DelegateExecution execution;
    @InjectMocks private EsgAnomalyDelegate delegate;
```

**Case 1 — aiDecision chứa "SPIKE" → anomalyCategory = "SUDDEN_SPIKE":**
```java
when(execution.getVariable("aiDecision")).thenReturn("INVESTIGATE_SPIKE");
when(execution.getVariable("metricType")).thenReturn("energy");
delegate.execute(execution);
verify(execution).setVariable("anomalyCategory", "SUDDEN_SPIKE");
```

**Case 2 — metricType = "energy", aiDecision không match pattern → anomalyCategory = "ENERGY_ANOMALY":**
```java
when(execution.getVariable("aiDecision")).thenReturn("INVESTIGATE");
when(execution.getVariable("metricType")).thenReturn("energy_consumption");
delegate.execute(execution);
verify(execution).setVariable("anomalyCategory", "ENERGY_ANOMALY");
```

**Case 3 — investigationReportId là UUID:**
```java
delegate.execute(execution);
ArgumentCaptor<String> reportIdCaptor = ArgumentCaptor.forClass(String.class);
verify(execution).setVariable(eq("investigationReportId"), reportIdCaptor.capture());
assertThat(reportIdCaptor.getValue()).hasSize(36);
```

**Case 4 — investigationSummary chứa đủ thông tin:**
```java
when(execution.getVariable("metricType")).thenReturn("carbon");
when(execution.getVariable("currentValue")).thenReturn(120.5);
when(execution.getVariable("historicalAvg")).thenReturn(80.0);
when(execution.getVariable("period")).thenReturn("2026-Q1");
when(execution.getVariable("aiReasoning")).thenReturn("Carbon spike detected");
delegate.execute(execution);
ArgumentCaptor<String> summaryCaptor = ArgumentCaptor.forClass(String.class);
verify(execution).setVariable(eq("investigationSummary"), summaryCaptor.capture());
assertThat(summaryCaptor.getValue())
    .contains("carbon").contains("120.5").contains("80.0").contains("2026-Q1");
```

---

## Bước 5 — Integration Test

**Tạo `ManagementAiScenariosIntegrationTest.java`** tại `backend/src/test/java/com/uip/backend/workflow/`:

```java
/**
 * Integration test cho 4 Management AI Scenarios (S4-04).
 * Pattern giống CitizenAiScenariosIntegrationTest — embedded Camunda, mock external deps.
 */
@SpringBootTest
@DisplayName("S4-04 Management AI Scenarios — Integration")
class ManagementAiScenariosIntegrationTest {

    @Autowired private WorkflowService workflowService;
    @Autowired private HistoryService  historyService;

    @MockBean private ClaudeApiService     claudeApiService;
    @MockBean private StringRedisTemplate  redisTemplate;
    @MockBean private EsgService           esgService;

    private AIDecision mockDecision(String decision, String severity) {
        return AIDecision.builder()
            .decision(decision).confidence(0.9)
            .reasoning("Test reasoning").severity(severity)
            .recommendedActions(List.of("Take action"))
            .build();
    }

    private void assertProcessCompleted(String instanceId) {
        await().atMost(10, SECONDS).until(() ->
            historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId).finished().count() == 1
        );
    }
```

**Test AI-M01:**
```java
@Test
@DisplayName("AI-M01: Flood alert → operations team dispatched")
void aiM01_floodAlert_teamDispatched() throws Exception {
    when(claudeApiService.analyzeAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(mockDecision("DISPATCH_TEAM", "CRITICAL")));

    ProcessInstanceDto instance = workflowService.startProcess(
        "aiM01_floodResponseCoordination",
        Map.of("scenarioKey", "aiM01_floodResponseCoordination",
               "alertId", UUID.randomUUID().toString(),
               "waterLevel", 4.5,
               "location", "CANAL-D8",
               "affectedZones", "D8,D9")
    );

    assertProcessCompleted(instance.getId());
}
```

**Test AI-M02:**
```java
@Test
@DisplayName("AI-M02: AQI > 150 → restriction recommendation generated")
void aiM02_highAqi_restrictionGenerated() throws Exception {
    when(claudeApiService.analyzeAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(
            mockDecision("RESTRICT_TRAFFIC", "HIGH")));
    when(objectMapper_or_inline_json).thenReturn("[]"); // AqiTrafficControlDelegate dùng ObjectMapper

    ProcessInstanceDto instance = workflowService.startProcess(
        "aiM02_aqiTrafficControl",
        Map.of("scenarioKey", "aiM02_aqiTrafficControl",
               "aqiValue", 180.0,
               "pollutants", "PM2.5",
               "affectedDistricts", "D7")
    );

    assertProcessCompleted(instance.getId());
}
```

**Test AI-M03:**
```java
@Test
@DisplayName("AI-M03: Utility anomaly → maintenance ticket created")
void aiM03_utilityAnomaly_ticketCreated() throws Exception {
    when(claudeApiService.analyzeAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(
            mockDecision("CREATE_MAINTENANCE_TICKET", "MEDIUM")));

    ProcessInstanceDto instance = workflowService.startProcess(
        "aiM03_utilityIncidentCoordination",
        Map.of("scenarioKey", "aiM03_utilityIncidentCoordination",
               "metricType", "electricity",
               "anomalyValue", 450.0,
               "buildingId", "BLDG-001",
               "detectedAt", Instant.now().toString())
    );

    assertProcessCompleted(instance.getId());
}
```

**Test AI-M04:**
```java
@Test
@DisplayName("AI-M04: ESG anomaly → investigation report generated")
void aiM04_esgAnomaly_reportGenerated() throws Exception {
    when(claudeApiService.analyzeAsync(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(
            mockDecision("INVESTIGATE_SPIKE", "MEDIUM")));

    ProcessInstanceDto instance = workflowService.startProcess(
        "aiM04_esgAnomalyInvestigation",
        Map.of("scenarioKey", "aiM04_esgAnomalyInvestigation",
               "metricType", "carbon_emission",
               "currentValue", 120.5,
               "historicalAvg", 80.0,
               "period", "2026-Q1")
    );

    assertProcessCompleted(instance.getId());
}
```

**Import cần thiết** (giống `CitizenAiScenariosIntegrationTest`):
```java
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
```

---

## Bước 6 — Chạy tests và verify

```bash
cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc

# Unit tests 4 delegates
./gradlew test \
  --tests "com.uip.backend.workflow.delegate.management.FloodResponseDelegateTest" \
  --tests "com.uip.backend.workflow.delegate.management.AqiTrafficControlDelegateTest" \
  --tests "com.uip.backend.workflow.delegate.management.UtilityIncidentDelegateTest" \
  --tests "com.uip.backend.workflow.delegate.management.EsgAnomalyDelegateTest" \
  -p backend

# Integration test
./gradlew test \
  --tests "com.uip.backend.workflow.ManagementAiScenariosIntegrationTest" \
  -p backend

# Full build
./gradlew build -p backend
```

---

## Acceptance Criteria S4-04 (DoD)

| # | Criterion | Verify |
|---|-----------|--------|
| AC-1 | AI-M01: WATER_LEVEL > 3.5m trên Kafka → `aiM01_floodResponseCoordination` start | `FloodResponseTriggerService` với consumer group `uip-workflow-m01-flood` |
| AC-2 | AI-M01: `teamDispatched = true` khi severity ≠ LOW; `operationsLogId` được set | `FloodResponseDelegate` + unit test |
| AC-3 | AI-M02: AQI > 150 trên Kafka → `aiM02_aqiTrafficControl` start | `AqiTrafficTriggerService` với consumer group `uip-workflow-m02-aqi` |
| AC-4 | AI-M02: `restrictionAreas` + `recommendationReport` được tạo | `AqiTrafficControlDelegate` + unit test |
| AC-5 | AI-M03: Scheduler 2 phút detect utility anomaly → start process | `ManagementWorkflowScheduler.checkUtilityAnomalies()` |
| AC-6 | AI-M03: `maintenanceTicketId` (UUID) + `assignedTeam` được set | `UtilityIncidentDelegate` + unit test |
| AC-7 | AI-M04: Scheduler detect ESG anomaly → start process | `ManagementWorkflowScheduler.checkEsgAnomalies()` |
| AC-8 | AI-M04: `investigationReportId` (UUID) + `investigationSummary` được set | `EsgAnomalyDelegate` + unit test |
| AC-9 | Unit tests: 4 delegate tests pass (≥4 cases each) | 16+ unit test cases |
| AC-10 | Integration test: 4 scenarios process đến completion | `ManagementAiScenariosIntegrationTest` 4 tests pass |
| AC-11 | `./gradlew build` → BUILD SUCCESSFUL | No compile error |
| AC-12 | Sau khi pass → cập nhật `detail-plan.md`: S4-04 → ✅ Done | |

---

## Lưu ý quan trọng

**1. Consumer group isolation (M01/M02 vs C01/C03):**
- C01: `uip-workflow-aqi` → start `aiC01_aqiCitizenAlert` (notify citizens)
- M02: `uip-workflow-m02-aqi` → start `aiM02_aqiTrafficControl` (generate restriction report)
- C03: `uip-workflow-flood` → start `aiC03_floodEmergencyEvacuation` (citizens)
- M01: `uip-workflow-m01-flood` → start `aiM01_floodResponseCoordination` (operations team)

Bốn consumer group này hoàn toàn độc lập — mỗi alert Kafka message được xử lý bởi cả 4 consumers.

**2. AqiTrafficControlDelegate cần `ObjectMapper` inject:**  
Delegate này dùng `objectMapper.writeValueAsString(restrictionAreas)`. Integration test cần mock `ObjectMapper` hoặc `@Autowired` real bean. Cách đơn giản nhất: dùng `@Autowired` real `ObjectMapper` trong integration test (không mock).

**3. Scheduler circuit breaker — tránh process flood:**  
`hasActiveProcess()` trong `WorkflowService` ngăn khởi động duplicate processes. Không implement logic này thì mỗi 2 phút scheduler có thể start 10+ processes cho cùng building.

**4. `EsgAnomalyDto` — scope:**  
Đây là DTO nội bộ workflow trigger, không expose qua API. Đặt trong `com.uip.backend.esg.dto` (cùng package với ESG DTOs khác) để `EsgService` có thể trả về.

**5. Integration test — ObjectMapper không cần mock:**  
`AqiTrafficControlDelegate` dùng `@Autowired ObjectMapper`. Trong `@SpringBootTest`, Spring auto-configure `ObjectMapper` → không cần `@MockBean`. Chỉ mock `ClaudeApiService` và `StringRedisTemplate`.
