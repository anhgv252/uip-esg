package com.uip.backend.workflow.controller;

import com.uip.backend.workflow.config.FilterEvaluator;
import com.uip.backend.workflow.config.TriggerConfig;
import com.uip.backend.workflow.config.TriggerConfigAuditService;
import com.uip.backend.workflow.config.TriggerConfigCacheInvalidator;
import com.uip.backend.workflow.config.TriggerConfigRepository;
import com.uip.backend.workflow.config.VariableMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/workflow-configs")
@Tag(name = "Workflow Config", description = "Admin CRUD for workflow trigger configuration")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class WorkflowConfigController {

    private final TriggerConfigRepository configRepo;
    private final FilterEvaluator filterEvaluator;
    private final VariableMapper variableMapper;
    private final TriggerConfigAuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @GetMapping
    @Operation(summary = "List all trigger configurations")
    public List<TriggerConfig> listConfigs() {
        return configRepo.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get trigger configuration by ID")
    public ResponseEntity<TriggerConfig> getConfig(@PathVariable Long id) {
        return configRepo.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create a new trigger configuration")
    public TriggerConfig createConfig(@Valid @RequestBody TriggerConfig config, Authentication auth) {
        TriggerConfig saved = configRepo.save(config);
        auditService.record(saved, "CREATE", auth.getName());
        publishConfigUpdated(saved.getId(), "CREATE");
        return saved;
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update trigger configuration")
    public ResponseEntity<TriggerConfig> updateConfig(
            @PathVariable Long id, @RequestBody TriggerConfig updates, Authentication auth) {
        return configRepo.findById(id)
            .map(existing -> {
                updateFields(existing, updates);
                TriggerConfig saved = configRepo.save(existing);
                auditService.record(saved, "UPDATE", auth.getName());
                publishConfigUpdated(saved.getId(), "UPDATE");
                return ResponseEntity.ok(saved);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Disable trigger configuration (soft delete)")
    public ResponseEntity<Void> disableConfig(@PathVariable Long id, Authentication auth) {
        return configRepo.findById(id)
            .map(config -> {
                config.setEnabled(false);
                TriggerConfig saved = configRepo.save(config);
                auditService.record(saved, "DISABLE", auth.getName());
                publishConfigUpdated(saved.getId(), "DISABLE");
                return ResponseEntity.noContent().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/audit")
    @Operation(summary = "Get audit history for a trigger configuration")
    public ResponseEntity<?> getAuditHistory(@PathVariable Long id) {
        if (!configRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(auditService.getHistory(id));
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Test trigger with sample payload — dry run, no process started")
    public ResponseEntity<Map<String, Object>> testTrigger(
            @PathVariable Long id, @RequestBody Map<String, Object> samplePayload) {
        return configRepo.findById(id)
            .map(config -> {
                boolean filterMatch = filterEvaluator.matches(config.getFilterConditions(), samplePayload);
                Map<String, Object> mappedVars = variableMapper.map(config.getVariableMapping(), samplePayload);
                return ResponseEntity.ok(Map.<String, Object>of(
                    "filterMatch", filterMatch,
                    "mappedVariables", mappedVars,
                    "processKey", config.getProcessKey(),
                    "scenarioKey", config.getScenarioKey()
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private void publishConfigUpdated(Long configId, String action) {
        try {
            kafkaTemplate.send(TriggerConfigCacheInvalidator.TOPIC,
                Map.of("configId", configId, "action", action));
        } catch (Exception e) {
            log.warn("[CACHE] Failed to publish config-updated event configId={}: {}", configId, e.getMessage());
        }
    }

    private void updateFields(TriggerConfig existing, TriggerConfig updates) {
        if (updates.getScenarioKey() != null) existing.setScenarioKey(updates.getScenarioKey());
        if (updates.getProcessKey() != null) existing.setProcessKey(updates.getProcessKey());
        if (updates.getDisplayName() != null) existing.setDisplayName(updates.getDisplayName());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getTriggerType() != null) existing.setTriggerType(updates.getTriggerType());
        if (updates.getKafkaTopic() != null) existing.setKafkaTopic(updates.getKafkaTopic());
        if (updates.getKafkaConsumerGroup() != null) existing.setKafkaConsumerGroup(updates.getKafkaConsumerGroup());
        if (updates.getFilterConditions() != null) existing.setFilterConditions(updates.getFilterConditions());
        if (updates.getVariableMapping() != null) existing.setVariableMapping(updates.getVariableMapping());
        if (updates.getScheduleCron() != null) existing.setScheduleCron(updates.getScheduleCron());
        if (updates.getScheduleQueryBean() != null) existing.setScheduleQueryBean(updates.getScheduleQueryBean());
        if (updates.getPromptTemplatePath() != null) existing.setPromptTemplatePath(updates.getPromptTemplatePath());
        if (updates.getAiConfidenceThreshold() != null) existing.setAiConfidenceThreshold(updates.getAiConfidenceThreshold());
        if (updates.getDeduplicationKey() != null) existing.setDeduplicationKey(updates.getDeduplicationKey());
        if (updates.getEnabled() != null) existing.setEnabled(updates.getEnabled());
    }
}
