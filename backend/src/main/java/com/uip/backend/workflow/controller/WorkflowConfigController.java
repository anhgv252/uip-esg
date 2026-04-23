package com.uip.backend.workflow.controller;

import com.uip.backend.workflow.config.FilterEvaluator;
import com.uip.backend.workflow.config.TriggerConfig;
import com.uip.backend.workflow.config.TriggerConfigRepository;
import com.uip.backend.workflow.config.VariableMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/workflow-configs")
@Tag(name = "Workflow Config", description = "Admin CRUD for workflow trigger configuration")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class WorkflowConfigController {

    private final TriggerConfigRepository configRepo;
    private final FilterEvaluator filterEvaluator;
    private final VariableMapper variableMapper;

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
    public TriggerConfig createConfig(@Valid @RequestBody TriggerConfig config) {
        return configRepo.save(config);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update trigger configuration")
    public ResponseEntity<TriggerConfig> updateConfig(
            @PathVariable Long id, @RequestBody TriggerConfig updates) {
        return configRepo.findById(id)
            .map(existing -> {
                updateFields(existing, updates);
                return ResponseEntity.ok(configRepo.save(existing));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Disable trigger configuration (soft delete)")
    public ResponseEntity<Void> disableConfig(@PathVariable Long id) {
        return configRepo.findById(id)
            .map(config -> {
                config.setEnabled(false);
                configRepo.save(config);
                return ResponseEntity.noContent().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
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
