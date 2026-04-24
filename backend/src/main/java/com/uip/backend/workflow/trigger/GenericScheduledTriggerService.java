package com.uip.backend.workflow.trigger;

import com.uip.backend.workflow.config.TriggerConfig;
import com.uip.backend.workflow.config.TriggerConfigRepository;
import com.uip.backend.workflow.config.VariableMapper;
import com.uip.backend.workflow.service.WorkflowService;
import com.uip.backend.workflow.trigger.strategy.ScheduledQueryStrategy;
import com.uip.backend.workflow.trigger.strategy.ScheduledQueryStrategyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenericScheduledTriggerService {

    private final TriggerConfigRepository configRepo;
    private final WorkflowService workflowService;
    private final VariableMapper variableMapper;
    private final ScheduledQueryStrategyRegistry strategyRegistry;

    @Scheduled(fixedDelay = 120_000)
    public void checkScheduledTriggers() {
        List<TriggerConfig> configs = configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true);

        for (TriggerConfig config : configs) {
            try {
                ScheduledQueryStrategy strategy = strategyRegistry.find(config.getScheduleQueryBean())
                    .orElseThrow(() -> new IllegalStateException(
                        "No strategy registered for: " + config.getScheduleQueryBean()));

                List<?> anomalies = strategy.execute();
                if (anomalies == null) continue;

                for (Object anomaly : anomalies) {
                    Map<String, Object> anomalyMap = toMap(anomaly);
                    if (anomalyMap.isEmpty()) continue;

                    if (config.getDeduplicationKey() != null) {
                        String dedupValue = variableMapper.extractValue(
                            "anomaly." + config.getDeduplicationKey(), anomalyMap);
                        if (dedupValue != null && workflowService.hasActiveProcess(
                                config.getProcessKey(), config.getDeduplicationKey(), dedupValue)) {
                            log.debug("Duplicate skipped: scenario={}", config.getScenarioKey());
                            continue;
                        }
                    }

                    Map<String, Object> variables = variableMapper.map(config.getVariableMapping(), anomalyMap);
                    workflowService.startProcess(config.getProcessKey(), variables);
                    log.info("Scheduled trigger: scenario={}", config.getScenarioKey());
                }
            } catch (Exception e) {
                log.error("Scheduled trigger failed: {}", config.getScenarioKey(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        if (obj instanceof Map<?, ?> m) return new HashMap<>((Map<String, Object>) m);
        try {
            Map<String, Object> map = new HashMap<>();
            for (var comp : obj.getClass().getRecordComponents()) {
                Object val = comp.getAccessor().invoke(obj);
                map.put(comp.getName(), val);
            }
            return map;
        } catch (Exception e) {
            log.warn("Cannot convert to map: {}", obj.getClass().getSimpleName());
            return Map.of();
        }
    }
}
