package com.uip.backend.workflow.trigger;

import com.uip.backend.alert.kafka.AlertEventKafkaConsumer;
import com.uip.backend.common.exception.WorkflowNotFoundException;
import com.uip.backend.workflow.config.FilterEvaluator;
import com.uip.backend.workflow.config.TriggerConfig;
import com.uip.backend.workflow.config.TriggerConfigRepository;
import com.uip.backend.workflow.config.VariableMapper;
import com.uip.backend.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenericKafkaTriggerService {

    private final TriggerConfigRepository configRepo;
    private final WorkflowService workflowService;
    private final FilterEvaluator filterEvaluator;
    private final VariableMapper variableMapper;

    @KafkaListener(
        topics           = AlertEventKafkaConsumer.TOPIC,
        groupId          = "uip-workflow-generic",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onKafkaEvent(Map<String, Object> payload, Acknowledgment ack) {
        try {
            List<TriggerConfig> configs = configRepo
                .findByTriggerTypeAndKafkaTopicAndEnabled("KAFKA", AlertEventKafkaConsumer.TOPIC, true);

            for (TriggerConfig config : configs) {
                if (!filterEvaluator.matches(config.getFilterConditions(), payload)) continue;

                if (config.getDeduplicationKey() != null) {
                    String dedupValue = variableMapper.extractValue(config.getDeduplicationKey(), payload);
                    if (workflowService.hasActiveProcess(config.getProcessKey(), config.getDeduplicationKey(), dedupValue)) {
                        log.debug("Duplicate skipped: scenario={}", config.getScenarioKey());
                        continue;
                    }
                }

                Map<String, Object> variables = variableMapper.map(config.getVariableMapping(), payload);
                workflowService.startProcess(config.getProcessKey(), variables);
                log.info("Triggered: scenario={}", config.getScenarioKey());
            }
            ack.acknowledge();
        } catch (WorkflowNotFoundException e) {
            log.warn("Process not deployed: {}", e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Generic trigger failed: {}", e.getMessage(), e);
        }
    }
}
