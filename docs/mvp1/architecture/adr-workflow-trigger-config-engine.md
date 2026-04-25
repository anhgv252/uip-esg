# ADR: Workflow Trigger Configuration Engine

**Date:** 2026-04-20
**Status:** Accepted (restructured Sprint 4)
**Author:** Solution Architect
**PO Decision (20/04):** Không code trigger classes cụ thể → Config Engine là primary approach ngay từ đầu. Old S4-03 restructured từ 8 SP (triggers+delegates) → 5 SP (delegates only). Old S4-04 trigger classes sẽ bị disable. Tiết kiệm double-work.

---

## 1. Context & Problem Statement

### Hiện trạng

Hệ thống có 7 AI scenarios (C01-C03, M01-M04). Mỗi scenario cần **3 lớp code riêng biệt**:

```
Trigger Class (Java)       → lắng nghe Kafka / Scheduler / REST
BPMN Process Definition    → định nghĩa workflow steps
Delegate Class (Java)      → business logic sau AI decision
```

**Ví dụ trigger hiện tại** — mỗi cái là 1 Java class riêng:

| Trigger Class | Scenario | Trigger Type | Filter Logic (hardcoded) |
|---|---|---|---|
| `AqiWorkflowTriggerService` | AI-C01 | Kafka | `module == ENVIRONMENT && measureType == AQI && value > 150` |
| `FloodWorkflowTriggerService` | AI-C03 | Kafka | `module == FLOOD \|\| measureType == WATER_LEVEL && value > 3.5` |
| `FloodResponseTriggerService` | AI-M01 | Kafka | `measureType == WATER_LEVEL && value > 3.5` |
| `AqiTrafficTriggerService` | AI-M02 | Kafka | `module == ENVIRONMENT && measureType == AQI && value > 150` |
| `ManagementWorkflowScheduler` | AI-M03 | Scheduled | Query ESG anomaly từ DB |
| `ManagementWorkflowScheduler` | AI-M04 | Scheduled | Query ESG anomaly từ DB |
| `CitizenController` (endpoint) | AI-C02 | REST | Citizen gửi request |

### Vấn đề

> **Muốn thêm 1 luồng AI mới → phải viết code Java mới.**

Business user không thể:
- Thêm scenario mới (phải nhờ developer)
- Thay đổi threshold (phải sửa code, build, deploy)
- Thêm filter condition mới (phải viết Java logic mới)
- Tắt/bật scenario (phải comment code hoặc dùng feature flag)

### Mục tiêu

> **Business chỉ cần cấu hình** — chọn data source, đặt filter, map variables, upload prompt → workflow tự động chạy.

---

## 2. Decision

**Chọn: Data-driven Workflow Trigger Configuration Engine**

Thay vì mỗi trigger là 1 Java class hardcode, ta lưu config vào DB. 3 generic services (Kafka / Scheduled / REST) đọc config và xử lý.

---

## 3. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     Configuration Layer                         │
│                                                                 │
│   Admin Console ──► REST API ──► PostgreSQL                    │
│   (UI Config)       (CRUD)       (trigger_config table)        │
└──────────────────────────┬──────────────────────────────────────┘
                           │ read config at runtime
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Runtime Layer                               │
│                                                                 │
│  ┌──────────────────┐ ┌──────────────────┐ ┌─────────────────┐ │
│  │ Generic Kafka    │ │ Generic          │ │ Generic REST    │ │
│  │ Trigger Service  │ │ Scheduler        │ │ Trigger         │ │
│  │                  │ │ Trigger Service  │ │ Controller      │ │
│  └────────┬─────────┘ └────────┬─────────┘ └────────┬────────┘ │
│           │                    │                     │          │
│           │   filter + map     │                     │          │
│           ▼                    ▼                     ▼          │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │              WorkflowService.startProcess(key, vars)        ││
│  └─────────────────────────────┬───────────────────────────────┘│
└────────────────────────────────┼────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Camunda Engine                              │
│                                                                 │
│  BPMN Process ──► AIAnalysisDelegate ──► Claude API             │
│  (parameterized)   (generic, already exists)                     │
│                          │                                      │
│                          ▼                                      │
│                  prompts/{scenarioKey}.txt                       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. Component Breakdown

### 4.1. Configuration Table

```sql
CREATE TABLE workflow.trigger_config (
    id                  SERIAL PRIMARY KEY,
    scenario_key        VARCHAR(100) NOT NULL UNIQUE,
    process_key         VARCHAR(100) NOT NULL,
    display_name        VARCHAR(255) NOT NULL,
    description         TEXT,

    -- Trigger source
    trigger_type        VARCHAR(20) NOT NULL,   -- KAFKA | SCHEDULED | REST

    -- Kafka-specific
    kafka_topic         VARCHAR(255),
    kafka_consumer_group VARCHAR(255),

    -- Filter conditions (JSONB — flexible)
    filter_conditions   JSONB,

    -- Variable mapping: Kafka fields → Camunda variables
    variable_mapping    JSONB,

    -- Schedule config (SCHEDULED type only)
    schedule_cron       VARCHAR(100),
    schedule_query_bean VARCHAR(255),           -- "esgService.detectUtilityAnomalies"

    -- AI config
    prompt_template_path VARCHAR(255),
    ai_confidence_threshold DECIMAL(3,2) DEFAULT 0.85,

    -- Circuit breaker: tránh duplicate process
    deduplication_key   VARCHAR(100),

    -- Lifecycle
    enabled             BOOLEAN DEFAULT true,
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW(),
    updated_by          VARCHAR(100)
);
```

### 4.2. Filter Conditions (JSONB)

```json
// AI-C01: AQI > 150 từ Environment module
[
  {"field": "module",      "op": "EQ",  "value": "ENVIRONMENT"},
  {"field": "measureType", "op": "EQ",  "value": "AQI"},
  {"field": "value",       "op": "GT",  "value": 150.0}
]

// AI-C03: Water level > 3.5m (FLOOD hoặc WATER_LEVEL)
[
  {"field": "module",      "op": "IN",  "value": ["FLOOD"]},
  {"field": "measureType", "op": "IN",  "value": ["WATER_LEVEL"]},
  {"field": "value",       "op": "GT",  "value": 3.5}
]
```

**Supported operators:** `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `IN`, `CONTAINS`, `IS_NULL`, `IS_NOT_NULL`

### 4.3. Variable Mapping (JSONB)

```json
// AI-C01: map Kafka payload fields → Camunda process variables
{
  "sensorId":     {"source": "payload.sensorId",     "default": "UNKNOWN"},
  "aqiValue":     {"source": "payload.value"},
  "districtCode": {"source": "payload.districtCode", "default": "UNKNOWN"},
  "measuredAt":   {"source": "payload.detectedAt",   "default": "NOW()"},
  "scenarioKey":  {"static": "aiC01_aqiCitizenAlert"}
}
```

- `source`: đường dẫn đến field trong Kafka payload (dùng dot notation)
- `default`: giá trị mặc định nếu field không tồn tại
- `static`: giá trị cố định (không đọc từ payload)

### 4.4. Generic Kafka Trigger Service

Thay thế 4 class: `AqiWorkflowTriggerService`, `FloodWorkflowTriggerService`, `FloodResponseTriggerService`, `AqiTrafficTriggerService`.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class GenericKafkaTriggerService {

    private final TriggerConfigRepository configRepo;
    private final WorkflowService workflowService;
    private final FilterEvaluator filterEvaluator;
    private final VariableMapper variableMapper;

    /**
     * Được gọi bởi 1 Kafka listener duy nhất trên topic UIP.flink.alert.detected.v1
     * Hoặc dynamic listener per topic nếu cần.
     */
    public void onKafkaEvent(String topic, Map<String, Object> payload, Acknowledgment ack) {
        try {
            // 1. Load tất cả enabled configs cho topic này
            List<TriggerConfig> configs = configRepo
                .findByTriggerTypeAndKafkaTopicAndEnabled("KAFKA", topic, true);

            for (TriggerConfig config : configs) {
                // 2. Evaluate filter conditions
                if (!filterEvaluator.matches(config.getFilterConditions(), payload)) {
                    continue;
                }

                // 3. Circuit breaker — check duplicate
                if (isDuplicate(config, payload)) {
                    log.debug("Duplicate process skipped: {}", config.getScenarioKey());
                    continue;
                }

                // 4. Map variables
                Map<String, Object> variables = variableMapper.map(
                    config.getVariableMapping(), payload);

                // 5. Start Camunda process
                workflowService.startProcess(config.getProcessKey(), variables);
                log.info("Triggered workflow: scenario={}, process={}",
                    config.getScenarioKey(), config.getProcessKey());
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Generic trigger failed: {}", e.getMessage(), e);
            // Không ack → Kafka retry
        }
    }

    private boolean isDuplicate(TriggerConfig config, Map<String, Object> payload) {
        if (config.getDeduplicationKey() == null) return false;
        String dedupValue = variableMapper.extractValue(
            config.getDeduplicationKey(), payload);
        return workflowService.hasActiveProcess(
            config.getProcessKey(), config.getDeduplicationKey(), dedupValue);
    }
}
```

### 4.5. FilterEvaluator

```java
@Component
public class FilterEvaluator {

    public boolean matches(List<FilterCondition> conditions, Map<String, Object> payload) {
        if (conditions == null || conditions.isEmpty()) return true;

        return conditions.stream().allMatch(cond -> {
            Object actual = extractField(payload, cond.field());
            return switch (cond.op()) {
                case EQ    -> Objects.equals(actual, cond.value());
                case NE    -> !Objects.equals(actual, cond.value());
                case GT    -> compareNumbers(actual, cond.value()) > 0;
                case GTE   -> compareNumbers(actual, cond.value()) >= 0;
                case LT    -> compareNumbers(actual, cond.value()) < 0;
                case LTE   -> compareNumbers(actual, cond.value()) <= 0;
                case IN    -> cond.values().contains(actual);
                case CONTAINS -> actual != null && actual.toString().contains(cond.value().toString());
                default    -> false;
            };
        });
    }
}
```

### 4.6. Admin REST API

```java
@RestController
@RequestMapping("/api/v1/admin/workflow-configs")
@RequiredArgsConstructor
public class WorkflowConfigController {

    private final TriggerConfigService configService;

    @GetMapping
    public List<TriggerConfigDto> listConfigs() { ... }

    @PostMapping
    public TriggerConfigDto createConfig(@RequestBody @Valid CreateTriggerConfigRequest req) { ... }

    @PutMapping("/{id}")
    public TriggerConfigDto updateConfig(@PathVariable Long id,
                                          @RequestBody @Valid UpdateTriggerConfigRequest req) { ... }

    @DeleteMapping("/{id}")
    public void disableConfig(@PathVariable Long id) { ... }

    @PostMapping("/{id}/test")
    public TestResultDto testTrigger(@PathVariable Long id,
                                      @RequestBody Map<String, Object> samplePayload) { ... }
}
```

---

## 5. Business User Flow

```
Bước 1: Admin Console → "Workflow Configuration" → "Add New"

Bước 2: Chọn Trigger Type
         ┌──────────┐  ┌───────────┐  ┌──────────┐
         │  Kafka   │  │ Scheduled │  │   REST   │
         └──────────┘  └───────────┘  └──────────┘

Bước 3: Cấu hình Filter (UI dropdown, không code)
         ┌─────────────────────────────────────────┐
         │  Field: [module     ▼]  Op: [EQ ▼]      │
         │         Value: [ENVIRONMENT           ]  │
         │                                         │
         │  Field: [value      ▼]  Op: [GT ▼]      │
         │         Value: [150.0                  ]  │
         │                                         │
         │  [+ Add Filter]                         │
         └─────────────────────────────────────────┘

Bước 4: Map Variables (drag-drop hoặc table)
         ┌──────────────────┬───────────────────────┐
         │  Payload Field   │  Process Variable      │
         ├──────────────────┼───────────────────────┤
         │  sensorId        │  sensorId              │
         │  value           │  aqiValue              │
         │  districtCode    │  districtCode          │
         │  (default)       │  "UNKNOWN"             │
         └──────────────────┴───────────────────────┘

Bước 5: Upload Prompt Template (file .txt)

Bước 6: [Test với Sample Payload] → [Activate]
```

---

## 6. Option Comparison

| Criteria | Option A: Status Quo (1 class/scenario) | Option B: Config Engine (data-driven) | Option C: BPMN Signal Events |
|---|---|---|---|
| **Business tự thêm luồng** | ❌ Phải viết Java code | ✅ Chỉ cấu hình qua UI | ⚠️ Chỉ giải quyết trigger, vẫn cần filter code |
| **Thay đổi threshold runtime** | ❌ Sửa code + redeploy | ✅ Sửa trong Admin Console | ❌ Cần redeploy |
| **Độ phức tạp implement** | Thấp (hiện tại đã có) | Trung bình | Trung bình |
| **Flexibility** | Thấp — hardcode | Cao — thay đổi runtime | Trung bình |
| **Time to implement** | 0 (đã có) | ~5-8 SP | ~3 SP |
| **Testable** | ✅ Đơn giản | ✅ Unit test filter/mapper | ⚠️ Khó test signal routing |
| **Maintainability** | ❌ 5+ class giống nhau | ✅ 1 generic class | ⚠️ Signal routing phức tạp |
| **Observability** | ❌ Check log từng class | ✅ Admin page tổng hợp | ⚠️ Camunda cockpit |

**Recommendation:** Option B — Config Engine

---

## 7. Migration Plan

### 7.1. Seed Data cho 7 Existing Scenarios

Full DDL + 7 INSERT statements xem tại: `docs/prompts/s4-10-workflow-trigger-config-engine.md` (Bước 2).

### 7.2. Migration Steps (Accepted — 20/04/2026)

**Lưu ý:** S4-04 đã code 5 trigger classes (DONE 18/04). PO quyết định:
- Delegates + BPMN = giữ nguyên 100% (giá trị cốt lõi)
- Trigger classes = disable (`@Component` commented), không xóa (rollback ready)
- Config Engine = primary approach, absorps tất cả trigger logic

```
S4-10 Stage 1 — Foundation
├── Tạo trigger_config table + 7 seed rows
├── FilterEvaluator + VariableMapper
└── Unit tests

S4-10 Stage 2 — Kafka Generic
├── GenericKafkaTriggerService
├── Disable 4 Kafka trigger classes
└── Integration test: C01, C03, M01, M02

S4-10 Stage 3 — Scheduled + REST
├── GenericScheduledTriggerService
├── GenericRestTriggerController
├── Disable ManagementWorkflowScheduler + remove CitizenController.submitServiceRequest()
└── Integration test: C02, M03, M04

S4-10 Stage 4 — Admin
├── WorkflowConfigController CRUD
└── Admin Console UI
```

### 7.3. Classes bị thay thế

| Old Class (disable `@Component`) | Replaced By | Giá trị cũ |
|---|---|---|
| `AqiWorkflowTriggerService` | `GenericKafkaTriggerService` + config row aiC01 | ✅ Đã verify BPMN + delegate hoạt động |
| `FloodWorkflowTriggerService` | `GenericKafkaTriggerService` + config row aiC03 | ✅ Đã verify BPMN + delegate hoạt động |
| `FloodResponseTriggerService` | `GenericKafkaTriggerService` + config row aiM01 | ✅ Đã verify BPMN + delegate hoạt động |
| `AqiTrafficTriggerService` | `GenericKafkaTriggerService` + config row aiM02 | ✅ Đã verify BPMN + delegate hoạt động |
| `ManagementWorkflowScheduler` | `GenericScheduledTriggerService` + config rows aiM03, aiM04 | ✅ Đã verify delegates + anomaly queries |
| `CitizenController.submitServiceRequest()` | `GenericRestTriggerController` + config row aiC02 | — Chưa code (S4-03 restructured) |

---

## 8. Implementation Plan (Staged)

### Stage 1 — Foundation (3 SP)

| Task | SP | Description |
|---|---|---|
| `trigger_config` table + migration | 0.5 | DDL + Flyway migration |
| `TriggerConfig` JPA entity | 0.5 | Entity + Repository |
| `FilterEvaluator` | 1 | Generic filter matching engine |
| `VariableMapper` | 0.5 | JSON variable mapping |
| `WorkflowConfigController` CRUD | 0.5 | Admin REST API |

### Stage 2 — Kafka Generic Trigger (2 SP)

| Task | SP | Description |
|---|---|---|
| `GenericKafkaTriggerService` | 1 | 1 Kafka listener → nhiều configs |
| Seed data migration (4 Kafka scenarios) | 0.5 | INSERT cho C01, C03, M01, M02 |
| Integration test | 0.5 | Verify 4 scenarios |

### Stage 3 — Scheduled + REST Generic (2 SP, Be-1)

| Task | SP | Description |
|---|---|---|
| `GenericScheduledTriggerService` | 0.5 | Thay ManagementWorkflowScheduler |
| `GenericRestTriggerController` | 0.5 | Thay CitizenController endpoint |
| Seed data migration (3 scenarios) | 0.5 | INSERT cho C02, M03, M04 |
| Integration test | 0.5 | Verify 3 scenarios |

### Stage 4 — Admin API + UI (2 SP, Be-1 backend + Fe-1 frontend)

| Task | Description |
|---|---|
| `WorkflowConfigController` | CRUD + test-trigger endpoint |
| `WorkflowConfigPage.tsx` | List configs + enable/disable toggle |
| `WorkflowConfigForm.tsx` | Create/edit form + filter builder |
| Prompt template upload | Upload .txt file |

**Total backend:** ~6 SP (Be-1)
**Total frontend:** ~2 SP (Fe-1)
**Total story:** 8 SP

---

## 9. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| JSONB filter chậm với nhiều configs | Thấp (<50 scenarios) | Thấp | Cache configs in memory, reload khi update |
| Generic trigger miss edge case | Trung bình | Trung bình | Unit test FilterEvaluator tất cả operators; old classes kept as rollback |
| Business user cấu hình sai filter | Trung bình | Trung bình | "Test trigger" button trước khi activate |
| Reflection call `scheduleQueryBean` fragile | Trung bình | Thấp | Validate bean.method tồn tại khi CRUD config |
| Config table down → không trigger | Thấp | Cao | Cache configs in memory, load at startup |
| Consumer group change → duplicate messages | Thấp | Trung bình | Idempotent processing via `hasActiveProcess()` circuit breaker |

---

## 10. Open Questions

1. **BPMN template:** Liệu delegate có nên cũng configurable? Hoặc giữ nguyên delegate class?
   - **Đã quyết định (20/04):** Giữ delegate class. Chỉ configurable phần **trigger + filter + variable mapping**. Business logic phức tạp, code an toàn hơn config.

2. **Multi-topic Kafka:** Phase 1 chỉ hỗ trợ alert topic (`UIP.flink.alert.detected.v1`). Tương lai cần dynamic Kafka listener container cho topics khác.

3. **Versioning:** Khi filter/variable mapping thay đổi, process đang chạy dùng config mới hay cũ?
   - **Đã quyết định:** Load config lúc trigger. Process đang chạy không bị ảnh hưởng.

---

## 11. Consequences

### Positive

- Business tự thêm AI workflow mới qua Admin Console — không cần developer
- Thay đổi threshold/filter mà không cần redeploy application
- Runtime observability: 1 admin page thấy tất cả workflow configs + trạng thái
- Giảm code duplication: 5 trigger classes → 3 generic services
- Dễ mở rộng: thêm scenario mới = 1 row INSERT + BPMN + delegate
- **Sprint 4 restructure saves ~2 SP** (không code specific triggers rồi rewrite generic)
- S4-04 trigger classes không lãng phí: đã verify BPMN + delegates hoạt động

### Trade-offs

- Thêm 1 table + CRUD API → complexity tăng nhẹ
- Generic code khó debug hơn specific code → cần tốt logging
- JSONB filter chậm hơn Java hardcode (không đáng kể với <50 configs)
- S4-10 mất 8 SP đầu tư (thay vì 0 nếu dùng old approach)
- S4-04 trigger code (5.5 SP) bị disable — nhưng delegates/tests 100% reuse
- Reflection `scheduleQueryBean` cần naming convention đúng
