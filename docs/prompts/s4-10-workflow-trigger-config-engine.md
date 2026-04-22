# Prompt: S4-10 — Workflow Trigger Configuration Engine (8 SP)

**Story:** S4-10 — Data-driven Workflow Trigger Configuration Engine
**Status:** ⬜ Pending
**Owner:** Be-1 (6 SP backend) + Fe-1 (2 SP frontend)
**Depends on:** S4-02 (AI Decision Node), S4-03 (Citizen delegates), S4-04 (Management delegates)
**ADR:** `docs/architecture/adr-workflow-trigger-config-engine.md`

---

## Context

Sprint 4 đã restructure (20/04/2026):
- **S4-01** ✅ Camunda + 7 BPMN — DONE
- **S4-02** AI Decision Node (ClaudeApiService + AIAnalysisDelegate + prompts) — pending
- **S4-03** 3 Citizen delegates — pending, KHÔNG code triggers
- **S4-04** ✅ 4 Management delegates — DONE, trigger classes sẽ bị disable

**Story này:** Build Config Engine + Generic Triggers. Disable old trigger classes. Từ đây thêm AI scenario mới = INSERT 1 row DB + tạo BPMN + tạo delegate. Không cần viết trigger Java code.

---

## Bước 0 — Đọc ADR (BẮT BUỘC)

```
docs/architecture/adr-workflow-trigger-config-engine.md
```

Chứa: architecture diagram, config table DDL, filter JSONB format, variable mapping format, generic service design, migration plan.

---

## Bước 1 — Phân tích codebase (BẮT BUỘC)

```
// Hiểu trigger classes sẽ bị disable
find_class("AqiWorkflowTriggerService")
find_class("FloodWorkflowTriggerService")
find_class("FloodResponseTriggerService")
find_class("AqiTrafficTriggerService")
find_class("ManagementWorkflowScheduler")

// Hiểu Camunda integration
find_class("WorkflowService")           // startProcess(), hasActiveProcess()
find_class("AIAnalysisDelegate")        // cần scenarioKey variable

// Hiểu data sources
find_class("AlertEventKafkaConsumer")   // TOPIC = "UIP.flink.alert.detected.v1"
find_class("EsgService")                // detectUtilityAnomalies(), detectEsgAnomalies()
find_class("NotificationService")       // ALERT_CHANNEL

// Hiểu citizen REST trigger (sẽ bị thay)
find_class("CitizenController")         // submitServiceRequest() endpoint
```

**Mục tiêu:** Map chính xác filter logic, variable mapping, consumer groups cho 7 seed rows.

---

## Bước 2 — Database Migration

**Path:** `backend/src/main/resources/db/migration/V{next}__create_trigger_config.sql`

### 2a. DDL

```sql
CREATE TABLE workflow.trigger_config (
    id                    SERIAL PRIMARY KEY,
    scenario_key          VARCHAR(100) NOT NULL UNIQUE,
    process_key           VARCHAR(100) NOT NULL,
    display_name          VARCHAR(255) NOT NULL,
    description           TEXT,

    trigger_type          VARCHAR(20)  NOT NULL,  -- KAFKA | SCHEDULED | REST

    kafka_topic           VARCHAR(255),
    kafka_consumer_group  VARCHAR(255),

    filter_conditions     JSONB,       -- [{"field":"module","op":"EQ","value":"ENVIRONMENT"}, ...]
    variable_mapping      JSONB NOT NULL, -- {"sensorId":{"source":"payload.sensorId","default":"UNKNOWN"}, ...}

    schedule_cron         VARCHAR(100),
    schedule_query_bean   VARCHAR(255),   -- "esgService.detectUtilityAnomalies"

    prompt_template_path  VARCHAR(255),
    ai_confidence_threshold DECIMAL(3,2) DEFAULT 0.85,

    deduplication_key     VARCHAR(100),   -- variable name để check duplicate process

    enabled               BOOLEAN DEFAULT true,
    created_at            TIMESTAMP DEFAULT NOW(),
    updated_at            TIMESTAMP DEFAULT NOW(),
    updated_by            VARCHAR(100)
);
```

### 2b. Seed Data — 7 Existing Scenarios

```sql
-- AI-C01: AQI Citizen Alert (Kafka trigger)
INSERT INTO workflow.trigger_config
  (scenario_key, process_key, display_name, trigger_type, kafka_topic, kafka_consumer_group, filter_conditions, variable_mapping, prompt_template_path, deduplication_key, enabled)
VALUES (
  'aiC01_aqiCitizenAlert', 'aiC01_aqiCitizenAlert', 'Cảnh báo AQI cho cư dân',
  'KAFKA', 'UIP.flink.alert.detected.v1', 'uip-workflow-generic',
  '[{"field":"module","op":"EQ","value":"ENVIRONMENT"},{"field":"measureType","op":"EQ","value":"AQI"},{"field":"value","op":"GT","value":150.0}]'::jsonb,
  '{"sensorId":{"source":"payload.sensorId","default":"UNKNOWN"},"aqiValue":{"source":"payload.value"},"districtCode":{"source":"payload.districtCode","default":"UNKNOWN"},"measuredAt":{"source":"payload.detectedAt","default":"NOW()"},"scenarioKey":{"static":"aiC01_aqiCitizenAlert"}}'::jsonb,
  'prompts/aiC01_aqiCitizenAlert.txt', 'sensorId', true
);

-- AI-C03: Flood Emergency Evacuation (Kafka trigger)
INSERT INTO workflow.trigger_config
  (scenario_key, process_key, display_name, trigger_type, kafka_topic, kafka_consumer_group, filter_conditions, variable_mapping, prompt_template_path, enabled)
VALUES (
  'aiC03_floodEmergencyEvacuation', 'aiC03_floodEmergencyEvacuation', 'Cảnh báo khẩn cấp & sơ tán lũ',
  'KAFKA', 'UIP.flink.alert.detected.v1', 'uip-workflow-generic',
  '[{"field":"module","op":"IN","value":["FLOOD"]},{"field":"measureType","op":"IN","value":["WATER_LEVEL"]},{"field":"value","op":"GT","value":3.5}]'::jsonb,
  '{"waterLevel":{"source":"payload.value"},"sensorLocation":{"source":"payload.sensorId","default":"UNKNOWN"},"warningZones":{"source":"payload.districtCode","default":"UNKNOWN"},"detectedAt":{"source":"payload.detectedAt","default":"NOW()"},"scenarioKey":{"static":"aiC03_floodEmergencyEvacuation"}}'::jsonb,
  'prompts/aiC03_floodEmergencyEvacuation.txt', true
);

-- AI-M01: Flood Response Coordination (Kafka trigger — management)
INSERT INTO workflow.trigger_config
  (scenario_key, process_key, display_name, trigger_type, kafka_topic, kafka_consumer_group, filter_conditions, variable_mapping, prompt_template_path, enabled)
VALUES (
  'aiM01_floodResponseCoordination', 'aiM01_floodResponseCoordination', 'Phối hợp phản ứng lũ',
  'KAFKA', 'UIP.flink.alert.detected.v1', 'uip-workflow-generic',
  '[{"field":"measureType","op":"IN","value":["WATER_LEVEL"]},{"field":"value","op":"GT","value":3.5}]'::jsonb,
  '{"scenarioKey":{"static":"aiM01_floodResponseCoordination"},"alertId":{"source":"payload.alertId","default":"UUID()"},"waterLevel":{"source":"payload.value"},"location":{"source":"payload.sensorId","default":"UNKNOWN"},"affectedZones":{"source":"payload.districtCode","default":"UNKNOWN"}}'::jsonb,
  'prompts/aiM01_floodResponseCoordination.txt', true
);

-- AI-M02: AQI Traffic Control (Kafka trigger — management)
INSERT INTO workflow.trigger_config
  (scenario_key, process_key, display_name, trigger_type, kafka_topic, kafka_consumer_group, filter_conditions, variable_mapping, prompt_template_path, enabled)
VALUES (
  'aiM02_aqiTrafficControl', 'aiM02_aqiTrafficControl', 'Kiểm soát giao thông khi AQI cao',
  'KAFKA', 'UIP.flink.alert.detected.v1', 'uip-workflow-generic',
  '[{"field":"module","op":"EQ","value":"ENVIRONMENT"},{"field":"measureType","op":"EQ","value":"AQI"},{"field":"value","op":"GT","value":150.0}]'::jsonb,
  '{"scenarioKey":{"static":"aiM02_aqiTrafficControl"},"sensorId":{"source":"payload.sensorId","default":"UNKNOWN"},"aqiValue":{"source":"payload.value"},"pollutants":{"source":"payload.measureType","default":"AQI"},"affectedDistricts":{"source":"payload.districtCode","default":"UNKNOWN"}}'::jsonb,
  'prompts/aiM02_aqiTrafficControl.txt', true
);

-- AI-C02: Citizen Service Request (REST trigger)
INSERT INTO workflow.trigger_config
  (scenario_key, process_key, display_name, trigger_type, variable_mapping, prompt_template_path, enabled)
VALUES (
  'aiC02_citizenServiceRequest', 'aiC02_citizenServiceRequest', 'Xử lý yêu cầu dịch vụ',
  'REST',
  '{"scenarioKey":{"static":"aiC02_citizenServiceRequest"},"citizenId":{"source":"payload.citizenId"},"requestId":{"source":"payload.requestId","default":"UUID()"},"requestType":{"source":"payload.requestType"},"description":{"source":"payload.description"},"district":{"source":"payload.district","default":"UNKNOWN"}}'::jsonb,
  'prompts/aiC02_citizenServiceRequest.txt', true
);

-- AI-M03: Utility Incident (Scheduled trigger)
INSERT INTO workflow.trigger_config
  (scenario_key, process_key, display_name, trigger_type, schedule_cron, schedule_query_bean, variable_mapping, prompt_template_path, deduplication_key, enabled)
VALUES (
  'aiM03_utilityIncidentCoordination', 'aiM03_utilityIncidentCoordination', 'Phối hợp sự cố tiện ích',
  'SCHEDULED', '0 */2 * * *', 'esgService.detectUtilityAnomalies',
  '{"scenarioKey":{"static":"aiM03_utilityIncidentCoordination"},"metricType":{"source":"anomaly.metricType"},"anomalyValue":{"source":"anomaly.currentValue"},"buildingId":{"source":"anomaly.buildingId"},"detectedAt":{"source":"anomaly.detectedAt","default":"NOW()"}}'::jsonb,
  'prompts/aiM03_utilityIncidentCoordination.txt', 'buildingId', true
);

-- AI-M04: ESG Anomaly (Scheduled trigger)
INSERT INTO workflow.trigger_config
  (scenario_key, process_key, display_name, trigger_type, schedule_cron, schedule_query_bean, variable_mapping, prompt_template_path, deduplication_key, enabled)
VALUES (
  'aiM04_esgAnomalyInvestigation', 'aiM04_esgAnomalyInvestigation', 'Điều tra bất thường ESG',
  'SCHEDULED', '0 */2 * * *', 'esgService.detectEsgAnomalies',
  '{"scenarioKey":{"static":"aiM04_esgAnomalyInvestigation"},"metricType":{"source":"anomaly.metricType"},"currentValue":{"source":"anomaly.currentValue"},"historicalAvg":{"source":"anomaly.historicalAvg"},"period":{"source":"anomaly.period"}}'::jsonb,
  'prompts/aiM04_esgAnomalyInvestigation.txt', 'metricType', true
);
```

---

## Bước 3 — JPA Entity + Repository

**Path:** `backend/src/main/java/com/uip/backend/workflow/config/`

### `TriggerConfig.java`

```java
@Entity
@Table(name = "trigger_config", schema = "workflow")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TriggerConfig {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_key", nullable = false, unique = true)
    private String scenarioKey;

    @Column(name = "process_key", nullable = false)
    private String processKey;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String description;

    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;  // KAFKA | SCHEDULED | REST

    @Column(name = "kafka_topic")
    private String kafkaTopic;

    @Column(name = "kafka_consumer_group")
    private String kafkaConsumerGroup;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_conditions", columnDefinition = "jsonb")
    private String filterConditions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variable_mapping", nullable = false, columnDefinition = "jsonb")
    private String variableMapping;

    @Column(name = "schedule_cron")
    private String scheduleCron;

    @Column(name = "schedule_query_bean")
    private String scheduleQueryBean;

    @Column(name = "prompt_template_path")
    private String promptTemplatePath;

    @Column(name = "ai_confidence_threshold", precision = 3, scale = 2)
    private BigDecimal aiConfidenceThreshold;

    @Column(name = "deduplication_key")
    private String deduplicationKey;

    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
```

### `TriggerConfigRepository.java`

```java
@Repository
public interface TriggerConfigRepository extends JpaRepository<TriggerConfig, Long> {
    List<TriggerConfig> findByTriggerTypeAndEnabled(String triggerType, Boolean enabled);
    List<TriggerConfig> findByTriggerTypeAndKafkaTopicAndEnabled(String triggerType, String kafkaTopic, Boolean enabled);
    Optional<TriggerConfig> findByScenarioKey(String scenarioKey);
    List<TriggerConfig> findByEnabled(Boolean enabled);
}
```

---

## Bước 4 — FilterEvaluator

**Path:** `backend/src/main/java/com/uip/backend/workflow/config/FilterEvaluator.java`

```java
@Component
@RequiredArgsConstructor @Slf4j
public class FilterEvaluator {

    private final ObjectMapper objectMapper;

    public boolean matches(String filterConditionsJson, Map<String, Object> payload) {
        if (filterConditionsJson == null || filterConditionsJson.isBlank()) return true;
        try {
            List<FilterCondition> conditions = objectMapper.readValue(
                filterConditionsJson, new TypeReference<>() {});
            return conditions.stream().allMatch(cond -> evaluate(cond, payload));
        } catch (Exception e) {
            log.warn("Failed to parse filter conditions: {}", e.getMessage());
            return false;
        }
    }

    private boolean evaluate(FilterCondition cond, Map<String, Object> payload) {
        Object actual = payload.get(cond.field());
        return switch (cond.op()) {
            case "EQ"          -> Objects.equals(normalize(actual), normalize(cond.value()));
            case "NE"          -> !Objects.equals(normalize(actual), normalize(cond.value()));
            case "GT"          -> compareNumbers(actual, cond.value()) > 0;
            case "GTE"         -> compareNumbers(actual, cond.value()) >= 0;
            case "LT"          -> compareNumbers(actual, cond.value()) < 0;
            case "LTE"         -> compareNumbers(actual, cond.value()) <= 0;
            case "IN"          -> cond.values() != null && cond.values().contains(normalize(actual));
            case "CONTAINS"    -> actual != null && actual.toString().contains(String.valueOf(cond.value()));
            case "IS_NULL"     -> actual == null;
            case "IS_NOT_NULL" -> actual != null;
            default            -> false;
        };
    }

    // helpers: normalize(), compareNumbers(), toDouble()

    public record FilterCondition(String field, String op, Object value, List<Object> values) {}
}
```

**Unit tests (`FilterEvaluatorTest.java`):** ≥10 cases — EQ match/no-match, GT, GTE, IN, multiple AND, null filter, missing field, IS_NULL, IS_NOT_NULL, invalid JSON.

---

## Bước 5 — VariableMapper

**Path:** `backend/src/main/java/com/uip/backend/workflow/config/VariableMapper.java`

```java
@Component
@RequiredArgsConstructor @Slf4j
public class VariableMapper {

    private final ObjectMapper objectMapper;

    public Map<String, Object> map(String variableMappingJson, Map<String, Object> payload) {
        try {
            Map<String, MappingDef> mappings = objectMapper.readValue(
                variableMappingJson, new TypeReference<>() {});
            Map<String, Object> result = new HashMap<>();
            for (var entry : mappings.entrySet()) {
                result.put(entry.getKey(), resolveValue(entry.getValue(), payload));
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse variable mapping: {}", e.getMessage());
            return Map.of();
        }
    }

    private Object resolveValue(MappingDef def, Map<String, Object> payload) {
        if (def.staticVal() != null) return def.staticVal();
        if (def.source() != null) {
            String path = def.source().startsWith("payload.") ? def.source().substring(8) : def.source();
            Object value = getNestedValue(payload, path);
            if (value != null) return value;
        }
        if (def.defaultVal() != null) {
            return switch (def.defaultVal()) {
                case "NOW()" -> Instant.now().toString();
                case "UUID()" -> UUID.randomUUID().toString();
                default -> def.defaultVal();
            };
        }
        return null;
    }

    public String extractValue(String field, Map<String, Object> payload) { ... }

    record MappingDef(
        String source,
        @JsonProperty("default") String defaultVal,
        @JsonProperty("static")  String staticVal
    ) {}
}
```

**Unit tests (`VariableMapperTest.java`):** ≥7 cases — static, source from payload, default, NOW(), UUID(), complex multi-field, invalid JSON.

---

## Bước 6 — GenericKafkaTriggerService

**Path:** `backend/src/main/java/com/uip/backend/workflow/trigger/GenericKafkaTriggerService.java`

```java
@Component
@RequiredArgsConstructor @Slf4j
public class GenericKafkaTriggerService {

    private final TriggerConfigRepository configRepo;
    private final WorkflowService workflowService;
    private final FilterEvaluator filterEvaluator;
    private final VariableMapper variableMapper;

    @KafkaListener(
        topics           = AlertEventKafkaConsumer.TOPIC,
        groupId          = "uip-workflow-generic",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onKafkaEvent(Map<String, Object> payload, Acknowledgment ack) {
        try {
            List<TriggerConfig> configs = configRepo
                .findByTriggerTypeAndKafkaTopicAndEnabled("KAFKA", AlertEventKafkaConsumer.TOPIC, true);

            for (TriggerConfig config : configs) {
                if (!filterEvaluator.matches(config.getFilterConditions(), payload)) continue;

                // Circuit breaker
                if (config.getDeduplicationKey() != null) {
                    String dedupValue = variableMapper.extractValue(config.getDeduplicationKey(), payload);
                    if (workflowService.hasActiveProcess(config.getProcessKey(), config.getDeduplicationKey(), dedupValue)) continue;
                }

                Map<String, Object> variables = variableMapper.map(config.getVariableMapping(), payload);
                workflowService.startProcess(config.getProcessKey(), variables);
                log.info("Triggered: scenario={}", config.getScenarioKey());
            }
            ack.acknowledge();
        } catch (WorkflowNotFoundException e) {
            log.warn("Process not deployed: {}", e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Generic trigger failed: {}", e.getMessage(), e);
            // Không ack → Kafka retry
        }
    }
}
```

**Unit tests (`GenericKafkaTriggerServiceTest.java`):** ≥5 cases — matching config starts process, no match skips, deduplication, multiple configs match, WorkflowNotFoundException acks.

---

## Bước 7 — GenericScheduledTriggerService

**Path:** `backend/src/main/java/com/uip/backend/workflow/trigger/GenericScheduledTriggerService.java`

```java
@Component
@RequiredArgsConstructor @Slf4j
public class GenericScheduledTriggerService {

    private final TriggerConfigRepository configRepo;
    private final WorkflowService workflowService;
    private final VariableMapper variableMapper;
    private final ApplicationContext applicationContext;

    @Scheduled(fixedDelay = 120_000)
    public void checkScheduledTriggers() {
        List<TriggerConfig> configs = configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true);

        for (TriggerConfig config : configs) {
            try {
                List<?> anomalies = invokeQueryBean(config.getScheduleQueryBean());
                if (anomalies == null) continue;

                for (Object anomaly : anomalies) {
                    Map<String, Object> anomalyMap = toMap(anomaly);
                    // Circuit breaker + map + start process (same pattern as Kafka)
                    ...
                }
            } catch (Exception e) {
                log.error("Scheduled trigger failed: {}", config.getScenarioKey(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<?> invokeQueryBean(String beanMethodRef) throws Exception {
        String[] parts = beanMethodRef.split("\\.");
        Object bean = applicationContext.getBean(parts[0]);
        Method method = bean.getClass().getMethod(parts[1]);
        return (List<?>) method.invoke(bean);
    }
}
```

---

## Bước 8 — GenericRestTriggerController

**Path:** `backend/src/main/java/com/uip/backend/workflow/controller/GenericRestTriggerController.java`

```java
@RestController
@RequestMapping("/api/v1/workflow/trigger")
@RequiredArgsConstructor @Slf4j
public class GenericRestTriggerController {

    private final TriggerConfigRepository configRepo;
    private final WorkflowService workflowService;
    private final VariableMapper variableMapper;

    @PostMapping("/{scenarioKey}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> triggerWorkflow(
            @PathVariable String scenarioKey,
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal UserDetails userDetails) {

        TriggerConfig config = configRepo.findByScenarioKey(scenarioKey)
            .filter(c -> c.getEnabled())
            .filter(c -> "REST".equals(c.getTriggerType()))
            .orElseThrow(() -> new IllegalArgumentException(
                "No enabled REST trigger: " + scenarioKey));

        Map<String, Object> enriched = new HashMap<>(payload);
        enriched.put("citizenId", userDetails.getUsername());

        Map<String, Object> variables = variableMapper.map(config.getVariableMapping(), enriched);
        ProcessInstanceDto instance = workflowService.startProcess(config.getProcessKey(), variables);

        return ResponseEntity.accepted().body(Map.of(
            "processInstanceId", instance.getId(),
            "scenarioKey", scenarioKey,
            "status", "PROCESSING"
        ));
    }
}
```

---

## Bước 9 — Admin CRUD API

**Path:** `backend/src/main/java/com/uip/backend/workflow/controller/WorkflowConfigController.java`

```java
@RestController
@RequestMapping("/api/v1/admin/workflow-configs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class WorkflowConfigController {

    private final TriggerConfigRepository configRepo;
    private final FilterEvaluator filterEvaluator;
    private final VariableMapper variableMapper;

    @GetMapping
    public List<TriggerConfig> listConfigs() { return configRepo.findAll(); }

    @GetMapping("/{id}")
    public ResponseEntity<TriggerConfig> getConfig(@PathVariable Long id) { ... }

    @PostMapping
    public TriggerConfig createConfig(@RequestBody TriggerConfig config) { ... }

    @PutMapping("/{id}")
    public ResponseEntity<TriggerConfig> updateConfig(@PathVariable Long id, @RequestBody TriggerConfig updates) { ... }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disableConfig(@PathVariable Long id) { ... }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testTrigger(
            @PathVariable Long id, @RequestBody Map<String, Object> samplePayload) {
        // Returns: { filterMatch: boolean, mappedVariables: Map, processKey: String }
        ...
    }
}
```

---

## Bước 10 — Disable Old Trigger Classes

Comment `@Component` trên mỗi class (KHÔNG xóa — rollback ready):

```java
// @Component  ← DISABLED by S4-10 Config Engine. See trigger_config table row: aiC01_aqiCitizenAlert
// @RequiredArgsConstructor
// @Slf4j
// public class AqiWorkflowTriggerService { ... }
```

**Classes disable:**
1. `AqiWorkflowTriggerService` → row `aiC01_aqiCitizenAlert`
2. `FloodWorkflowTriggerService` → row `aiC03_floodEmergencyEvacuation`
3. `FloodResponseTriggerService` → row `aiM01_floodResponseCoordination`
4. `AqiTrafficTriggerService` → row `aiM02_aqiTrafficControl`
5. `ManagementWorkflowScheduler` → rows `aiM03_utilityIncidentCoordination` + `aiM04_esgAnomalyInvestigation`

**Ngoài ra:** Remove `CitizenController.submitServiceRequest()` method (đã thay bằng `GenericRestTriggerController`).

---

## Bước 11 — Integration Test

**Path:** `backend/src/test/java/com/uip/backend/workflow/GenericTriggerIntegrationTest.java`

```java
@SpringBootTest
@ActiveProfiles("test")
class GenericTriggerIntegrationTest {

    @Autowired private WorkflowService workflowService;
    @Autowired private HistoryService historyService;
    @Autowired private TriggerConfigRepository configRepo;

    @MockBean private ClaudeApiService claudeApiService;
    @MockBean private StringRedisTemplate redisTemplate;
```

**Verify 7 scenarios start process trực tiếp (qua generic path):**
1. AI-C01: AQI > 150 → process completes, Redis notification sent
2. AI-C02: Service request → process completes, department routed
3. AI-C03: Flood CRITICAL → process completes, evacuation triggered
4. AI-M01: Flood response → process completes, team dispatched
5. AI-M02: AQI traffic → process completes, restrictions generated
6. AI-M03: Utility anomaly → process completes, ticket created
7. AI-M04: ESG anomaly → process completes, report generated

**Verify config CRUD:**
8. `GET /api/v1/admin/workflow-configs` → returns 7 configs
9. `POST /api/v1/admin/workflow-configs` → creates new config
10. `POST /api/v1/admin/workflow-configs/{id}/test` → returns filterMatch + mappedVariables

---

## Bước 12 — Chạy tests

```bash
cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc

# Unit tests
./gradlew test \
  --tests "com.uip.backend.workflow.config.FilterEvaluatorTest" \
  --tests "com.uip.backend.workflow.config.VariableMapperTest" \
  --tests "com.uip.backend.workflow.trigger.GenericKafkaTriggerServiceTest" \
  -p backend

# Integration test
./gradlew test \
  --tests "com.uip.backend.workflow.GenericTriggerIntegrationTest" \
  -p backend

# Full build
./gradlew build -p backend
```

---

## Acceptance Criteria (DoD)

| # | Criterion | Verify |
|---|-----------|--------|
| AC-1 | `trigger_config` table + 7 seed rows | Migration pass |
| AC-2 | `FilterEvaluator` — ≥10 unit test cases pass | All operators covered |
| AC-3 | `VariableMapper` — ≥7 unit test cases pass | static, source, default, NOW, UUID |
| AC-4 | `GenericKafkaTriggerService` — ≥5 unit test cases pass | match, skip, dedup, multi, error |
| AC-5 | All 5 old trigger classes disabled (`@Component` commented) | No `@Component` on old triggers |
| AC-6 | `CitizenController.submitServiceRequest()` removed | Method gone |
| AC-7 | `GenericScheduledTriggerService` works — M03 + M04 scenarios pass | Integration test |
| AC-8 | `GenericRestTriggerController` works — C02 scenario pass | Integration test |
| AC-9 | `WorkflowConfigController` CRUD + test-trigger endpoint | REST test |
| AC-10 | All 7 AI scenarios pass via generic triggers | `GenericTriggerIntegrationTest` 7/7 |
| AC-11 | `./gradlew build` → BUILD SUCCESSFUL | No compile error |
| AC-12 | ADR updated | `adr-workflow-trigger-config-engine.md` |

---

## Lưu ý

**1. Rollback:** Old classes chỉ comment `@Component`, không xóa code. Uncomment + restart để rollback.

**2. `@JsonProperty` cho MappingDef:** JSON keys `"default"` và `"static"` là Java keywords. Record fields cần annotation.

**3. `scheduleQueryBean` validation:** Khi admin tạo/update SCHEDULED config, validate `bean.method` tồn tại trong Spring context. Tránh runtime reflection failure.

**4. Config caching (optional):** Nếu performance issue, cache configs in memory, invalidate khi CRUD. <50 scenarios thì query DB mỗi event không đáng kể.

**5. Consumer group change:** Old triggers dùng separate consumer groups (`uip-workflow-aqi`, `uip-workflow-flood`...). Generic dùng `uip-workflow-generic`. Khi cut-over, Kafka có thể deliver duplicate messages → idempotent processing (circuit breaker `hasActiveProcess`).
