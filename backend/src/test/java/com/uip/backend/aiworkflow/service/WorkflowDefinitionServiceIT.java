package com.uip.backend.aiworkflow.service;

import com.uip.backend.aiworkflow.dto.WorkflowSummaryDto;
import com.uip.backend.aiworkflow.model.WorkflowDefinition;
import com.uip.backend.aiworkflow.repository.WorkflowDefinitionRepository;
import com.uip.backend.tenant.context.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for WorkflowDefinitionService — CRUD, deploy, and execute with Camunda.
 *
 * WF-IT-01..10: tenant-isolated CRUD, version bump, soft-delete, deploy, execute.
 * Camunda 7 embedded engine is started with the Spring context (no mock needed).
 * PostgreSQL is provided by Testcontainers; Redis/Kafka are mocked.
 */
@Tag("integration")
@SpringBootTest(properties = {
    "security.jwt.secret=test-secret-for-integration-tests-only-32chars",
    "spring.cache.type=simple",
    "uip.cagg.alert-refresh-ms=999999999",
    "uip.cagg.sensor-refresh-ms=999999999",
    "camunda.bpm.auto-deployment-enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowDefinitionServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("uip_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:29092");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("uip.capabilities.multi-tenancy", () -> "true");
    }

    @MockBean @SuppressWarnings("unused") RedisConnectionFactory redisConnectionFactory;
    @MockBean @SuppressWarnings("unused") ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
    @MockBean @SuppressWarnings("unused") StringRedisTemplate redisTemplate;
    @MockBean @SuppressWarnings("unused") RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean @SuppressWarnings("unused") KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired private WorkflowDefinitionService service;
    @Autowired private WorkflowDefinitionRepository repository;

    private static final String TENANT_A = "wf-it-tenant-a";
    private static final String TENANT_B = "wf-it-tenant-b";

    /**
     * Minimal valid BPMN 2.0 process: start → end.
     * process id "test-flood-alert" becomes the Camunda process definition key.
     */
    private static final String SAMPLE_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                         targetNamespace="http://uip.com/bpmn">
              <process id="test-flood-alert" isExecutable="true">
                <startEvent id="start"/>
                <endEvent id="end"/>
                <sequenceFlow id="flow1" sourceRef="start" targetRef="end"/>
              </process>
            </definitions>
            """;

    // ─── WF-IT-01 ──────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void create_persistsWorkflowWithVersion1() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            WorkflowDefinition def = service.create(TENANT_A, "Flood Alert Workflow", "Detect flood events", SAMPLE_BPMN);

            assertThat(def.getId()).isNotNull();
            assertThat(def.getVersion()).isEqualTo(1);
            assertThat(def.getIsActive()).isTrue();
            assertThat(def.getTenantId()).isEqualTo(TENANT_A);
            assertThat(def.getName()).isEqualTo("Flood Alert Workflow");
            assertThat(def.getDescription()).isEqualTo("Detect flood events");
            assertThat(def.getBpmnXml()).isEqualTo(SAMPLE_BPMN);
            assertThat(def.getCamundaDeploymentId()).isNull();
            assertThat(def.getCreatedAt()).isNotNull();
            assertThat(def.getUpdatedAt()).isNotNull();
        } finally {
            TenantContext.clear();
        }
    }

    // ─── WF-IT-02 ──────────────────────────────────────────────────────────

    @Test
    @Order(2)
    void create_duplicateName_throwsException() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            service.create(TENANT_A, "Duplicate Workflow", "First copy", SAMPLE_BPMN);

            assertThatThrownBy(() ->
                    service.create(TENANT_A, "Duplicate Workflow", "Second copy", SAMPLE_BPMN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate Workflow");
        } finally {
            TenantContext.clear();
        }
    }

    // ─── WF-IT-03 ──────────────────────────────────────────────────────────

    @Test
    @Order(3)
    void list_returnsSummariesWithoutBpmnXml() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            service.create(TENANT_A, "List Workflow Alpha", "alpha desc", SAMPLE_BPMN);
            service.create(TENANT_A, "List Workflow Beta", "beta desc", SAMPLE_BPMN);

            Page<WorkflowSummaryDto> result = service.list(TENANT_A, PageRequest.of(0, 50));

            assertThat(result.getContent()).isNotEmpty();
            // WorkflowSummaryDto record has no bpmnXml field — structural proof bpmnXml is excluded
            assertThat(result.getContent()).anySatisfy(dto -> {
                assertThat(dto.id()).isNotNull();
                assertThat(dto.name()).isIn("List Workflow Alpha", "List Workflow Beta");
                assertThat(dto.tenantId()).isEqualTo(TENANT_A);
                assertThat(dto.version()).isEqualTo(1);
                assertThat(dto.isActive()).isTrue();
                assertThat(dto.deployed()).isFalse();
            });
        } finally {
            TenantContext.clear();
        }
    }

    // ─── WF-IT-04 ──────────────────────────────────────────────────────────

    @Test
    @Order(4)
    void getById_returnsWorkflow() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            WorkflowDefinition created = service.create(TENANT_A, "GetById Workflow", "fetch test", SAMPLE_BPMN);

            WorkflowDefinition fetched = service.getById(created.getId(), TENANT_A);

            assertThat(fetched.getId()).isEqualTo(created.getId());
            assertThat(fetched.getName()).isEqualTo("GetById Workflow");
            assertThat(fetched.getDescription()).isEqualTo("fetch test");
            assertThat(fetched.getTenantId()).isEqualTo(TENANT_A);
            assertThat(fetched.getBpmnXml()).isEqualTo(SAMPLE_BPMN);
            assertThat(fetched.getVersion()).isEqualTo(1);
            assertThat(fetched.getIsActive()).isTrue();
        } finally {
            TenantContext.clear();
        }
    }

    // ─── WF-IT-05 ──────────────────────────────────────────────────────────

    @Test
    @Order(5)
    void getById_wrongTenant_throwsException() {
        UUID createdId;
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            WorkflowDefinition created = service.create(TENANT_A, "TenantA Private Workflow", "private", SAMPLE_BPMN);
            createdId = created.getId();
        } finally {
            TenantContext.clear();
        }

        TenantContext.setCurrentTenant(TENANT_B);
        try {
            assertThatThrownBy(() -> service.getById(createdId, TENANT_B))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            TenantContext.clear();
        }
    }

    // ─── WF-IT-06 ──────────────────────────────────────────────────────────

    @Test
    @Order(6)
    void update_bumpsVersion() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            WorkflowDefinition created = service.create(TENANT_A, "Versioned Workflow", "version 1 desc", SAMPLE_BPMN);
            assertThat(created.getVersion()).isEqualTo(1);

            WorkflowDefinition updated = service.update(
                    created.getId(), TENANT_A, "Versioned Workflow", "version 2 desc", SAMPLE_BPMN);

            assertThat(updated.getVersion()).isEqualTo(2);
            assertThat(updated.getCamundaDeploymentId()).isNull();
            assertThat(updated.getDescription()).isEqualTo("version 2 desc");
            assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(created.getCreatedAt());
        } finally {
            TenantContext.clear();
        }
    }

    // ─── WF-IT-07 ──────────────────────────────────────────────────────────

    @Test
    @Order(7)
    void delete_softDeletesWorkflow() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            WorkflowDefinition created = service.create(TENANT_A, "Delete Me Workflow", "to delete", SAMPLE_BPMN);
            UUID id = created.getId();

            service.delete(id, TENANT_A);

            // Verify isActive=false via raw repository lookup (bypasses the isActive filter in service)
            WorkflowDefinition raw = repository.findById(id).orElseThrow();
            assertThat(raw.getIsActive()).isFalse();

            // getById filters on isActive=true, so it must throw after soft-delete
            assertThatThrownBy(() -> service.getById(id, TENANT_A))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            TenantContext.clear();
        }
    }

    // ─── WF-IT-08 ──────────────────────────────────────────────────────────

    @Test
    @Order(8)
    void deploy_setsCamundaDeploymentId() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            WorkflowDefinition created = service.create(TENANT_A, "Deploy Workflow", "deploy test", SAMPLE_BPMN);
            assertThat(created.getCamundaDeploymentId()).isNull();

            WorkflowDefinition deployed = service.deploy(created.getId(), TENANT_A);

            assertThat(deployed.getCamundaDeploymentId()).isNotNull().isNotBlank();
        } finally {
            TenantContext.clear();
        }
    }

    // ─── WF-IT-09 ──────────────────────────────────────────────────────────

    @Test
    @Order(9)
    void execute_afterDeploy_returnsProcessInstanceId() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            WorkflowDefinition created = service.create(TENANT_A, "Execute Workflow", "execute test", SAMPLE_BPMN);
            service.deploy(created.getId(), TENANT_A);

            Map<String, Object> result = service.execute(created.getId(), TENANT_A);

            assertThat(result).containsKey("processInstanceId");
            assertThat(result.get("processInstanceId")).isNotNull();
            assertThat((String) result.get("processInstanceId")).isNotBlank();
            assertThat(result).containsKey("processKey");
            assertThat(result.get("processKey")).isEqualTo("test-flood-alert");
        } finally {
            TenantContext.clear();
        }
    }

    // ─── WF-IT-10 ──────────────────────────────────────────────────────────

    @Test
    @Order(10)
    void tenantIsolation_listOnlyOwnTenant() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            service.create(TENANT_A, "TenantA Exclusive Workflow", "only for tenant A", SAMPLE_BPMN);
        } finally {
            TenantContext.clear();
        }

        TenantContext.setCurrentTenant(TENANT_B);
        try {
            Page<WorkflowSummaryDto> result = service.list(TENANT_B, PageRequest.of(0, 50));

            assertThat(result.getContent())
                    .noneMatch(dto -> "TenantA Exclusive Workflow".equals(dto.name()));
        } finally {
            TenantContext.clear();
        }
    }
}
