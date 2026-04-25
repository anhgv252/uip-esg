# Prompt: S4-01 — Camunda 7 Setup + Spring Security Integration

## Context

Đây là project `uip-esg-poc` — Smart City backend với Spring Boot 3.2.4, Java 17.

Trước khi code, hãy dùng java-graph-mcp để hiểu codebase hiện tại:

```
index_project()
find_by_annotation("Configuration")
find_by_annotation("EnableWebSecurity")
get_class_members("SecurityConfig")
impact_analysis("SecurityConfig")
find_by_annotation("Service")
```

---

## Existing Security Setup (đã biết — KHÔNG thay đổi logic này)

**`SecurityConfig.java`** — `com.uip.backend.auth.config`:
- `@EnableWebSecurity`, `@EnableMethodSecurity`, `SessionCreationPolicy.STATELESS`
- JWT filter: `JwtAuthenticationFilter extends OncePerRequestFilter`
  - Đọc token từ `Authorization: Bearer <token>` hoặc cookie `access_token`
- Roles: `ROLE_ADMIN`, `ROLE_OPERATOR`, `ROLE_CITIZEN`
- Đã có dòng `.requestMatchers("/camunda/**", "/engine-rest/**").permitAll()` — **cần thay thế** thành filter chain riêng (xem bên dưới)

**`build.gradle`** — đã có:
```groovy
implementation platform('org.camunda.bpm:camunda-bom:7.22.0')
implementation 'org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter:7.22.0'
implementation 'org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-rest:7.22.0'
implementation 'org.camunda.bpm.springboot:camunda-bpm-spring-boot-starter-webapp:7.22.0'
```

---

## Task Breakdown

### Task 1 — Tách SecurityFilterChain cho Camunda

**Vấn đề:** Camunda webapp (`/camunda/**`) là session-based (form login), trong khi API của chúng ta là stateless JWT. Một filter chain dùng `STATELESS` sẽ phá vỡ Camunda webapp.

**Giải pháp:** Tạo 2 `SecurityFilterChain` riêng biệt theo thứ tự `@Order`.

Tạo class mới `CamundaSecurityConfig.java` tại `com.uip.backend.workflow.config`:

```java
@Configuration
@Order(1)  // Phải xử lý TRƯỚC API filter chain
public class CamundaSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain camundaFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/camunda/**", "/engine-rest/**", "/lib/**", "/api/cockpit/**",
                             "/api/admin/**", "/api/tasklist/**")
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.disable())
            // Camunda webapp cần session — KHÔNG dùng STATELESS ở đây
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .headers(headers -> headers
                // Camunda webapp dùng iframe nội bộ — phải SameOrigin, không DENY
                .frameOptions(frame -> frame.sameOrigin())
            )
            .authorizeHttpRequests(auth -> auth
                // Camunda static assets và login page — public
                .requestMatchers(
                    "/camunda/app/*/styles/**",
                    "/camunda/app/*/fonts/**",
                    "/camunda/app/*/images/**",
                    "/camunda/app/*/scripts/**",
                    "/camunda/api/admin/auth/user/*/login/*",
                    "/camunda/api/cockpit/auth/user/*/login/*",
                    "/camunda/api/tasklist/auth/user/*/login/*"
                ).permitAll()
                // Tất cả path Camunda còn lại yêu cầu xác thực
                .anyRequest().authenticated()
            )
            // Camunda tự quản lý form login — không config ở đây
            // Camunda's CamundaBpmProcessEngineAutoConfiguration sẽ wire authentication
            ;

        return http.build();
    }
}
```

Sau đó **sửa `SecurityConfig.java`**: Xóa dòng:
```java
.requestMatchers("/camunda/**", "/engine-rest/**").permitAll()
```
Và thêm `@Order(2)` vào bean `securityFilterChain` hiện tại:
```java
@Bean
@Order(2)
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    // ... giữ nguyên toàn bộ nội dung hiện tại ...
}
```

---

### Task 2 — Camunda Application Config

Tạo `CamundaEngineConfig.java` tại `com.uip.backend.workflow.config`:

```java
@Configuration
public class CamundaEngineConfig {

    /**
     * Tie Camunda authentication to Spring Security users.
     * AdminUserConfiguration seeds một Camunda admin user từ application.properties.
     */
    @Bean
    @ConditionalOnMissingBean(AdminUserConfiguration.class)
    public AdminUserConfiguration adminUserConfiguration(
            @Value("${camunda.bpm.admin-user.id}") String id,
            @Value("${camunda.bpm.admin-user.password}") String password) {
        return new AdminUserConfiguration(new AdminUser(id, password));
    }
}
```

Thêm vào `application.yml` (hoặc `application.properties`):
```yaml
camunda:
  bpm:
    admin-user:
      id: ${CAMUNDA_ADMIN_USER:admin}
      password: ${CAMUNDA_ADMIN_PASSWORD:admin}  # Override bằng env var trên prod
    webapp:
      index-redirect-enabled: true
    filter:
      create: All Tasks
    history-level: audit
    auto-deployment-enabled: true
    deployment-resource-pattern: "classpath*:processes/*.bpmn"
```

> **Security note:** `CAMUNDA_ADMIN_PASSWORD` PHẢI được set qua env var trong production — không hardcode.

---

### Task 3 — WorkflowController + WorkflowService

Package: `com.uip.backend.workflow`

**`WorkflowService.java`:**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {

    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final HistoryService historyService;

    /**
     * Get all deployed process definitions.
     */
    public List<ProcessDefinitionDto> getProcessDefinitions() {
        return repositoryService.createProcessDefinitionQuery()
            .latestVersion()
            .list()
            .stream()
            .map(pd -> new ProcessDefinitionDto(
                pd.getId(), pd.getKey(), pd.getName(),
                pd.getVersion(), pd.getResourceName()))
            .toList();
    }

    /**
     * Get process instances by status: ACTIVE | COMPLETED | ALL
     */
    public List<ProcessInstanceDto> getProcessInstances(String status) {
        return switch (status.toUpperCase()) {
            case "ACTIVE" -> runtimeService.createProcessInstanceQuery()
                .list().stream().map(this::toDto).toList();
            case "COMPLETED" -> historyService.createHistoricProcessInstanceQuery()
                .completed()
                .orderByProcessInstanceEndTime().desc()
                .listPage(0, 100)
                .stream().map(this::toHistoricDto).toList();
            default -> {
                var active = runtimeService.createProcessInstanceQuery()
                    .list().stream().map(this::toDto).toList();
                var completed = historyService.createHistoricProcessInstanceQuery()
                    .completed().orderByProcessInstanceEndTime().desc()
                    .listPage(0, 50).stream().map(this::toHistoricDto).toList();
                List<ProcessInstanceDto> all = new ArrayList<>(active);
                all.addAll(completed);
                yield all;
            }
        };
    }

    /**
     * Start a process by key — ADMIN/OPERATOR only.
     * Variables are passed as Map<String, Object> from request body.
     */
    public String startProcess(String processKey, Map<String, Object> variables) {
        try {
            ProcessInstance instance = runtimeService
                .startProcessInstanceByKey(processKey, variables);
            log.info("Started process '{}' — instance id: {}", processKey, instance.getId());
            return instance.getId();
        } catch (ProcessEngineException e) {
            throw new WorkflowNotFoundException("Process not found: " + processKey);
        }
    }

    // --- private mappers ---
    private ProcessInstanceDto toDto(ProcessInstance pi) { ... }
    private ProcessInstanceDto toHistoricDto(HistoricProcessInstance hpi) { ... }
}
```

**`WorkflowController.java`:**

```java
@RestController
@RequestMapping("/api/v1/workflow")
@RequiredArgsConstructor
@Tag(name = "Workflow", description = "Camunda BPM workflow management")
public class WorkflowController {

    private final WorkflowService workflowService;

    @GetMapping("/definitions")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<List<ProcessDefinitionDto>> getDefinitions() {
        return ResponseEntity.ok(workflowService.getProcessDefinitions());
    }

    @GetMapping("/instances")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<List<ProcessInstanceDto>> getInstances(
            @RequestParam(defaultValue = "ALL") String status) {
        return ResponseEntity.ok(workflowService.getProcessInstances(status));
    }

    @PostMapping("/start/{processKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> startProcess(
            @PathVariable String processKey,
            @RequestBody(required = false) Map<String, Object> variables) {
        String instanceId = workflowService.startProcess(
            processKey,
            variables != null ? variables : Map.of()
        );
        return ResponseEntity.ok(Map.of("instanceId", instanceId));
    }
}
```

**DTOs:** `ProcessDefinitionDto`, `ProcessInstanceDto` (record classes, tại `workflow/api/dto/`).

**Exception:** `WorkflowNotFoundException extends RuntimeException` → `@ControllerAdvice` trả 404.

---

### Task 4 — 7 BPMN Skeleton Files

Tạo tại `backend/src/main/resources/processes/`. Mỗi file là skeleton với:
- Start event → Service Task (placeholder) → End event
- `id` và `name` chính xác để match với Sprint 4 AI scenarios

**Danh sách 7 processes:**

| File | processId | name |
|------|-----------|------|
| `aqi-citizen-alert.bpmn` | `aiC01_aqiCitizenAlert` | AI-C01: AQI Citizen Alert |
| `citizen-service-request.bpmn` | `aiC02_citizenServiceRequest` | AI-C02: Citizen Service Request |
| `flood-emergency-evacuation.bpmn` | `aiC03_floodEmergencyEvacuation` | AI-C03: Emergency Evacuation |
| `flood-response-coordination.bpmn` | `aiM01_floodResponseCoordination` | AI-M01: Flood Response Coordination |
| `aqi-traffic-control.bpmn` | `aiM02_aqiTrafficControl` | AI-M02: AQI Traffic Control |
| `utility-incident-coordination.bpmn` | `aiM03_utilityIncidentCoordination` | AI-M03: Utility Incident Coordination |
| `esg-anomaly-investigation.bpmn` | `aiM04_esgAnomalyInvestigation` | AI-M04: ESG Anomaly Investigation |

**Template BPMN (dùng cho tất cả 7):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  id="Definitions_{processId}"
                  targetNamespace="http://bpmn.io/schema/bpmn">

  <bpmn:process id="{processId}" name="{processName}" isExecutable="true">

    <bpmn:startEvent id="StartEvent_1" name="Trigger">
      <bpmn:outgoing>Flow_start_to_ai</bpmn:outgoing>
    </bpmn:startEvent>

    <bpmn:serviceTask id="Task_AIAnalysis" name="AI Analysis (Claude)"
                      camunda:delegateExpression="${aiAnalysisDelegate}">
      <bpmn:incoming>Flow_start_to_ai</bpmn:incoming>
      <bpmn:outgoing>Flow_ai_to_action</bpmn:outgoing>
    </bpmn:serviceTask>

    <bpmn:serviceTask id="Task_ExecuteAction" name="Execute Action"
                      camunda:delegateExpression="${placeholderDelegate}">
      <bpmn:incoming>Flow_ai_to_action</bpmn:incoming>
      <bpmn:outgoing>Flow_action_to_end</bpmn:outgoing>
    </bpmn:serviceTask>

    <bpmn:endEvent id="EndEvent_1" name="Done">
      <bpmn:incoming>Flow_action_to_end</bpmn:incoming>
    </bpmn:endEvent>

    <bpmn:sequenceFlow id="Flow_start_to_ai"
                       sourceRef="StartEvent_1" targetRef="Task_AIAnalysis"/>
    <bpmn:sequenceFlow id="Flow_ai_to_action"
                       sourceRef="Task_AIAnalysis" targetRef="Task_ExecuteAction"/>
    <bpmn:sequenceFlow id="Flow_action_to_end"
                       sourceRef="Task_ExecuteAction" targetRef="EndEvent_1"/>
  </bpmn:process>

</bpmn:definitions>
```

**Placeholder delegate** (để startup không fail khi S4-02 chưa implement):
Tạo `PlaceholderDelegate.java` tại `com.uip.backend.workflow.delegate`:
```java
@Component("placeholderDelegate")
@Slf4j
public class PlaceholderDelegate implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        log.info("[PLACEHOLDER] Process: {}, Activity: {}",
            execution.getProcessDefinitionId(), execution.getCurrentActivityName());
    }
}
```

---

### Task 5 — Startup Verification Test

Tạo `WorkflowStartupTest.java` tại `src/test/java/com/uip/backend/workflow/`:

```java
@SpringBootTest
@ActiveProfiles("test")
class WorkflowStartupTest {

    @Autowired
    private RepositoryService repositoryService;

    @Test
    void allSevenProcessesDeployed() {
        List<String> deployedKeys = repositoryService
            .createProcessDefinitionQuery()
            .latestVersion()
            .list()
            .stream()
            .map(ProcessDefinition::getKey)
            .toList();

        assertThat(deployedKeys).containsExactlyInAnyOrder(
            "aiC01_aqiCitizenAlert",
            "aiC02_citizenServiceRequest",
            "aiC03_floodEmergencyEvacuation",
            "aiM01_floodResponseCoordination",
            "aiM02_aqiTrafficControl",
            "aiM03_utilityIncidentCoordination",
            "aiM04_esgAnomalyInvestigation"
        );
    }
}
```

---

## Acceptance Criteria Checklist (từ S4-01)

Sau khi implement xong, verify:

```
[ ] ./gradlew build  →  BUILD SUCCESSFUL (không có conflict bean)
[ ] Application startup: log "Camunda version: 7.22.x" xuất hiện
[ ] GET /api/v1/workflow/definitions  với ADMIN JWT  →  200, trả 7 process definitions
[ ] GET /api/v1/workflow/instances  →  200
[ ] POST /api/v1/workflow/start/aiC01_aqiCitizenAlert  →  200, trả instanceId
[ ] GET /camunda  →  redirect về Camunda webapp login
[ ] WorkflowStartupTest  →  PASS
[ ] Không có "Bean of type SecurityFilterChain conflict" trong logs
```

---

## Known Pitfalls (R-08)

**Nếu build fail do Camunda + Spring Boot 3.2 conflict:**

1. **`AutoConfiguration` conflict:** Thêm vào `application.yml`:
   ```yaml
   spring:
     autoconfigure:
       exclude:
         - org.camunda.bpm.spring.boot.starter.webapp.CamundaBpmWebappSecurityAutoConfiguration
   ```
   Sau đó tự config security cho Camunda webapp trong `CamundaSecurityConfig`.

2. **Jakarta vs Javax:** Camunda 7.22+ đã migrate sang `jakarta.*` — không cần shim.

3. **`frameOptions().deny()` conflict:** Camunda webapp dùng iframe — filter chain Camunda PHẢI dùng `.sameOrigin()`,
   không phải `.deny()`. Filter chain API (`@Order(2)`) vẫn giữ `.deny()`.

4. **`In-memory H2 vs PostgreSQL`:** Camunda 7 mặc định dùng datasource của Spring Boot.
   TimescaleDB (PostgreSQL) được support đầy đủ — không cần config thêm.
   Camunda sẽ tự tạo tables `ACT_*` khi startup (`schema-update: true` trong camunda config).
