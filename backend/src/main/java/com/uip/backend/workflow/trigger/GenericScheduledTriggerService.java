package com.uip.backend.workflow.trigger;

import com.uip.backend.workflow.config.TriggerConfig;
import com.uip.backend.workflow.config.TriggerConfigRepository;
import com.uip.backend.workflow.config.VariableMapper;
import com.uip.backend.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
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
    private final ApplicationContext applicationContext;

    @Scheduled(fixedDelay = 120_000)
    public void checkScheduledTriggers() {
        List<TriggerConfig> configs = configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true);

        for (TriggerConfig config : configs) {
            try {
                List<?> anomalies = invokeQueryBean(config.getScheduleQueryBean());
                if (anomalies == null) continue;

                for (Object anomaly : anomalies) {
                    @SuppressWarnings("unchecked")
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
    private List<?> invokeQueryBean(String beanMethodRef) throws Exception {
        String[] parts = beanMethodRef.split("\\.");
        Object bean = applicationContext.getBean(parts[0]);
        Method method = bean.getClass().getMethod(parts[1]);
        return (List<?>) method.invoke(bean);
    }

    private Map<String, Object> toMap(Object obj) {
        if (obj instanceof Map<?, ?> m) return new HashMap<>((Map<String, Object>) m);
        // For records — convert via reflection on record components
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
