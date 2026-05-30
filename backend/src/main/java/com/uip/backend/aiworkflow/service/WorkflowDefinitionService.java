package com.uip.backend.aiworkflow.service;

import com.uip.backend.aiworkflow.dto.WorkflowSummaryDto;
import com.uip.backend.aiworkflow.model.WorkflowDefinition;
import com.uip.backend.aiworkflow.repository.WorkflowDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing AI Workflow definitions.
 *
 * CRUD + deploy to Camunda + execute process instance.
 * All operations are tenant-isolated via tenantId parameter.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowDefinitionService {

    private static final int MAX_BPMN_BYTES = 1_000_000;

    private final WorkflowDefinitionRepository repository;
    private final RepositoryService camundaRepositoryService;
    private final RuntimeService camundaRuntimeService;

    /**
     * AC-1: Create a new workflow definition.
     */
    @Transactional
    public WorkflowDefinition create(String tenantId, String name, String description, String bpmnXml) {
        validateBpmnXml(bpmnXml);

        if (repository.existsByTenantIdAndNameAndIsActiveTrue(tenantId, name)) {
            throw new IllegalArgumentException(
                    "Workflow with name '" + name + "' already exists for tenant: " + tenantId);
        }

        WorkflowDefinition def = new WorkflowDefinition();
        def.setTenantId(tenantId);
        def.setName(name);
        def.setDescription(description);
        def.setBpmnXml(bpmnXml);
        def.setVersion(1);
        def.setIsActive(true);

        WorkflowDefinition saved = repository.save(def);
        log.info("Created workflow definition: id={} name={} tenant={}", saved.getId(), name, tenantId);
        return saved;
    }

    /**
     * AC-2: List active workflow definitions for a tenant (paginated).
     * Returns summary DTOs — excludes bpmnXml to prevent large transfers.
     */
    @Transactional(readOnly = true)
    public Page<WorkflowSummaryDto> list(String tenantId, Pageable pageable) {
        return repository.findByTenantIdAndIsActiveTrue(tenantId, pageable)
                .map(this::toSummary);
    }

    private WorkflowSummaryDto toSummary(WorkflowDefinition def) {
        return new WorkflowSummaryDto(
                def.getId(),
                def.getTenantId(),
                def.getName(),
                def.getDescription(),
                def.getVersion(),
                def.getIsActive(),
                def.getCamundaDeploymentId() != null,
                def.getCreatedAt(),
                def.getUpdatedAt()
        );
    }

    /**
     * AC-3: Get a specific workflow definition by ID.
     */
    @Transactional(readOnly = true)
    public WorkflowDefinition getById(UUID id, String tenantId) {
        return repository.findByIdAndTenantIdAndIsActiveTrue(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Workflow definition not found: " + id));
    }

    /**
     * AC-4: Update a workflow definition (bumps version).
     */
    @Transactional
    public WorkflowDefinition update(UUID id, String tenantId, String name,
                                      String description, String bpmnXml) {
        WorkflowDefinition def = getById(id, tenantId);
        validateBpmnXml(bpmnXml);

        def.setName(name);
        def.setDescription(description);
        def.setBpmnXml(bpmnXml);
        def.setVersion(def.getVersion() + 1);
        // Reset deployment since XML changed
        def.setCamundaDeploymentId(null);

        WorkflowDefinition saved = repository.save(def);
        log.info("Updated workflow definition: id={} version={} tenant={}", id, saved.getVersion(), tenantId);
        return saved;
    }

    /**
     * AC-5: Soft-delete a workflow definition.
     */
    @Transactional
    public void delete(UUID id, String tenantId) {
        WorkflowDefinition def = getById(id, tenantId);
        def.setIsActive(false);
        repository.save(def);
        log.info("Soft-deleted workflow definition: id={} tenant={}", id, tenantId);
    }

    /**
     * AC-6: Deploy the workflow BPMN XML to Camunda engine.
     */
    @Transactional
    public WorkflowDefinition deploy(UUID id, String tenantId) {
        WorkflowDefinition def = getById(id, tenantId);

        String resourceName = sanitizeFilename(def.getName()) + "-v" + def.getVersion() + ".bpmn";
        Deployment deployment = camundaRepositoryService.createDeployment()
                .addString(resourceName, def.getBpmnXml())
                .name(def.getName())
                .tenantId(tenantId)
                .deploy();

        def.setCamundaDeploymentId(deployment.getId());
        WorkflowDefinition saved = repository.save(def);
        log.info("Deployed workflow: id={} deploymentId={} tenant={}", id, deployment.getId(), tenantId);
        return saved;
    }

    /**
     * AC-7: Execute (start) a deployed workflow as a Camunda process instance.
     * Extracts the process key from the deployment.
     */
    @Transactional
    public Map<String, Object> execute(UUID id, String tenantId) {
        WorkflowDefinition def = getById(id, tenantId);

        if (def.getCamundaDeploymentId() == null) {
            throw new IllegalStateException(
                    "Workflow must be deployed before execution. Deploy first: id=" + id);
        }

        // Find the process definition key from the deployment
        var processDefinitions = camundaRepositoryService.createProcessDefinitionQuery()
                .deploymentId(def.getCamundaDeploymentId())
                .list();

        if (processDefinitions.isEmpty()) {
            throw new IllegalStateException(
                    "No process definition found for deployment: " + def.getCamundaDeploymentId());
        }

        String processKey = processDefinitions.get(0).getKey();

        ProcessInstance instance = camundaRuntimeService.createProcessInstanceByKey(processKey)
                .processDefinitionTenantId(tenantId)
                .execute();

        log.info("Executed workflow: id={} processKey={} instanceId={} tenant={}",
                id, processKey, instance.getId(), tenantId);

        return Map.of(
                "processInstanceId", instance.getId(),
                "processDefinitionId", instance.getProcessDefinitionId(),
                "processKey", processKey,
                "tenantId", tenantId
        );
    }

    private void validateBpmnXml(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            throw new IllegalArgumentException("BPMN XML must not be empty");
        }
        if (bpmnXml.getBytes(StandardCharsets.UTF_8).length > MAX_BPMN_BYTES) {
            throw new IllegalArgumentException("BPMN XML exceeds maximum allowed size of 1MB");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable XXE attacks
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(
                    bpmnXml.getBytes(StandardCharsets.UTF_8)));
            String localName = doc.getDocumentElement().getLocalName();
            if (!"definitions".equals(localName)) {
                throw new IllegalArgumentException(
                        "BPMN XML must have <definitions> as root element, found: <" + localName + ">");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid BPMN XML: " + e.getMessage(), e);
        }
    }

    private static String sanitizeFilename(String name) {
        if (name == null) return "workflow";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
