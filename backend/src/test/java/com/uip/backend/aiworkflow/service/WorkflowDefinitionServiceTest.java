package com.uip.backend.aiworkflow.service;

import com.uip.backend.aiworkflow.dto.WorkflowSummaryDto;
import com.uip.backend.aiworkflow.model.WorkflowDefinition;
import com.uip.backend.aiworkflow.repository.WorkflowDefinitionRepository;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.repository.ProcessDefinitionQuery;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstantiationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WorkflowDefinitionService — 12 tests covering all 7 ACs.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowDefinitionService — unit")
class WorkflowDefinitionServiceTest {

    private static final String TENANT = "hcm";
    private static final String VALID_BPMN = """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <process id="test-process" />
            </definitions>
            """;

    @Mock private WorkflowDefinitionRepository repository;
    @Mock private RepositoryService camundaRepositoryService;
    @Mock private RuntimeService camundaRuntimeService;

    private WorkflowDefinitionService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowDefinitionService(repository, camundaRepositoryService, camundaRuntimeService);
    }

    // --- AC-1: Create ---

    @Test
    @DisplayName("create — valid input → saves with version 1")
    void create_validInput_savesWithVersion1() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkflowDefinition result = service.create(TENANT, "Flood Alert", "Desc", VALID_BPMN);

        assertThat(result.getTenantId()).isEqualTo(TENANT);
        assertThat(result.getName()).isEqualTo("Flood Alert");
        assertThat(result.getVersion()).isEqualTo(1);
        assertThat(result.getIsActive()).isTrue();

        ArgumentCaptor<WorkflowDefinition> captor = ArgumentCaptor.forClass(WorkflowDefinition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getBpmnXml()).isEqualTo(VALID_BPMN);
    }

    @Test
    @DisplayName("create — null BPMN XML → throws")
    void create_nullBpmnXml_throws() {
        assertThatThrownBy(() -> service.create(TENANT, "Test", "Desc", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    @DisplayName("create — invalid BPMN XML (no definitions) → throws")
    void create_invalidBpmnXml_throws() {
        assertThatThrownBy(() -> service.create(TENANT, "Test", "Desc", "<html/>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("<definitions>");
    }

    // --- AC-2: List ---

    @Test
    @DisplayName("list — returns paginated results for tenant")
    void list_returnsPaginated() {
        PageRequest pageable = PageRequest.of(0, 10);
        WorkflowDefinition def = new WorkflowDefinition();
        def.setTenantId(TENANT);
        when(repository.findByTenantIdAndIsActiveTrue(TENANT, pageable))
                .thenReturn(new PageImpl<>(List.of(def)));

        Page<WorkflowSummaryDto> result = service.list(TENANT, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).tenantId()).isEqualTo(TENANT);
    }

    // --- AC-3: GetById ---

    @Test
    @DisplayName("getById — found → returns definition")
    void getById_found() {
        UUID id = UUID.randomUUID();
        WorkflowDefinition def = new WorkflowDefinition();
        def.setId(id);
        when(repository.findByIdAndTenantIdAndIsActiveTrue(id, TENANT))
                .thenReturn(Optional.of(def));

        WorkflowDefinition result = service.getById(id, TENANT);
        assertThat(result.getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("getById — not found → throws")
    void getById_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantIdAndIsActiveTrue(id, TENANT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id, TENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // --- AC-4: Update ---

    @Test
    @DisplayName("update — bumps version and clears deploymentId")
    void update_bumpsVersion() {
        UUID id = UUID.randomUUID();
        WorkflowDefinition existing = new WorkflowDefinition();
        existing.setId(id);
        existing.setVersion(1);
        existing.setCamundaDeploymentId("deploy-123");

        when(repository.findByIdAndTenantIdAndIsActiveTrue(id, TENANT))
                .thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkflowDefinition result = service.update(id, TENANT, "Updated", "New desc", VALID_BPMN);

        assertThat(result.getVersion()).isEqualTo(2);
        assertThat(result.getCamundaDeploymentId()).isNull();
    }

    // --- AC-5: Delete ---

    @Test
    @DisplayName("delete — sets isActive=false")
    void delete_softDelete() {
        UUID id = UUID.randomUUID();
        WorkflowDefinition def = new WorkflowDefinition();
        def.setId(id);
        def.setIsActive(true);

        when(repository.findByIdAndTenantIdAndIsActiveTrue(id, TENANT))
                .thenReturn(Optional.of(def));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.delete(id, TENANT);

        ArgumentCaptor<WorkflowDefinition> captor = ArgumentCaptor.forClass(WorkflowDefinition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getIsActive()).isFalse();
    }

    // --- AC-6: Deploy ---

    @Test
    @DisplayName("deploy — deploys to Camunda and saves deploymentId")
    void deploy_success() {
        UUID id = UUID.randomUUID();
        WorkflowDefinition def = new WorkflowDefinition();
        def.setId(id);
        def.setTenantId(TENANT);
        def.setName("Flood Alert");
        def.setVersion(1);
        def.setBpmnXml(VALID_BPMN);

        when(repository.findByIdAndTenantIdAndIsActiveTrue(id, TENANT))
                .thenReturn(Optional.of(def));

        DeploymentBuilder builder = mock(DeploymentBuilder.class);
        when(camundaRepositoryService.createDeployment()).thenReturn(builder);
        when(builder.addString(anyString(), anyString())).thenReturn(builder);
        when(builder.name(anyString())).thenReturn(builder);
        when(builder.tenantId(anyString())).thenReturn(builder);

        Deployment deployment = mock(Deployment.class);
        when(deployment.getId()).thenReturn("camunda-deploy-001");
        when(builder.deploy()).thenReturn(deployment);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkflowDefinition result = service.deploy(id, TENANT);

        assertThat(result.getCamundaDeploymentId()).isEqualTo("camunda-deploy-001");
        verify(builder).addString(contains("Flood_Alert"), eq(VALID_BPMN));
    }

    // --- AC-7: Execute ---

    @Test
    @DisplayName("execute — starts Camunda process instance")
    void execute_success() {
        UUID id = UUID.randomUUID();
        WorkflowDefinition def = new WorkflowDefinition();
        def.setId(id);
        def.setTenantId(TENANT);
        def.setCamundaDeploymentId("deploy-001");

        when(repository.findByIdAndTenantIdAndIsActiveTrue(id, TENANT))
                .thenReturn(Optional.of(def));

        ProcessDefinitionQuery pdQuery = mock(ProcessDefinitionQuery.class);
        when(camundaRepositoryService.createProcessDefinitionQuery()).thenReturn(pdQuery);
        when(pdQuery.deploymentId("deploy-001")).thenReturn(pdQuery);

        ProcessDefinition pd = mock(ProcessDefinition.class);
        when(pd.getKey()).thenReturn("flood-alert-pipeline");
        when(pdQuery.list()).thenReturn(List.of(pd));

        ProcessInstantiationBuilder pib = mock(ProcessInstantiationBuilder.class);
        when(camundaRuntimeService.createProcessInstanceByKey("flood-alert-pipeline")).thenReturn(pib);
        when(pib.processDefinitionTenantId(TENANT)).thenReturn(pib);

        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getId()).thenReturn("proc-inst-001");
        when(instance.getProcessDefinitionId()).thenReturn("proc-def-001");
        when(pib.execute()).thenReturn(instance);

        Map<String, Object> result = service.execute(id, TENANT);

        assertThat(result.get("processInstanceId")).isEqualTo("proc-inst-001");
        assertThat(result.get("processKey")).isEqualTo("flood-alert-pipeline");
    }

    @Test
    @DisplayName("execute — not deployed → throws")
    void execute_notDeployed_throws() {
        UUID id = UUID.randomUUID();
        WorkflowDefinition def = new WorkflowDefinition();
        def.setId(id);
        def.setCamundaDeploymentId(null);

        when(repository.findByIdAndTenantIdAndIsActiveTrue(id, TENANT))
                .thenReturn(Optional.of(def));

        assertThatThrownBy(() -> service.execute(id, TENANT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be deployed");
    }
}
