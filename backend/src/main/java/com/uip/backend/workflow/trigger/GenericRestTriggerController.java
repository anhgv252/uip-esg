package com.uip.backend.workflow.trigger;

import com.uip.backend.workflow.config.TriggerConfig;
import com.uip.backend.workflow.config.TriggerConfigRepository;
import com.uip.backend.workflow.config.VariableMapper;
import com.uip.backend.workflow.dto.ProcessInstanceDto;
import com.uip.backend.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/workflow/trigger")
@Tag(name = "Workflow Trigger", description = "Generic REST workflow trigger")
@RequiredArgsConstructor
@Slf4j
public class GenericRestTriggerController {

    private final TriggerConfigRepository configRepo;
    private final WorkflowService workflowService;
    private final VariableMapper variableMapper;

    @PostMapping("/{scenarioKey}")
    @Operation(summary = "Trigger a workflow via scenario key")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> triggerWorkflow(
            @PathVariable String scenarioKey,
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal UserDetails userDetails) {

        TriggerConfig config = configRepo.findByScenarioKey(scenarioKey)
            .filter(c -> Boolean.TRUE.equals(c.getEnabled()))
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
