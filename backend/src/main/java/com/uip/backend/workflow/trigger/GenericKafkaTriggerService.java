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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenericKafkaTriggerService {

    public static final String DLQ_TOPIC = "UIP.workflow.trigger.dlq.v1";

    private final TriggerConfigRepository configRepo;
    private final WorkflowService workflowService;
    private final FilterEvaluator filterEvaluator;
    private final VariableMapper variableMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(
        topics           = AlertEventKafkaConsumer.TOPIC,
        groupId          = "uip-workflow-generic",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onKafkaEvent(Map<String, Object> payload, Acknowledgment ack) {
        List<TriggerConfig> configs;
        try {
            configs = configRepo
                .findByTriggerTypeAndKafkaTopicAndEnabled("KAFKA", AlertEventKafkaConsumer.TOPIC, true);
        } catch (Exception e) {
            log.error("Failed to load trigger configs, routing to DLQ: {}", e.getMessage(), e);
            sendToDlq("CONFIG_LOAD_ERROR", payload, e);
            ack.acknowledge();
            return;
        }

        for (TriggerConfig config : configs) {
            try {
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

            } catch (WorkflowNotFoundException e) {
                log.warn("Process not deployed: scenario={}, process={}", config.getScenarioKey(), config.getProcessKey());
            } catch (Exception e) {
                log.error("Config processing failed, routing to DLQ: scenario={}", config.getScenarioKey(), e);
                sendToDlq(config.getScenarioKey(), payload, e);
            }
        }
        ack.acknowledge();
    }

    private void sendToDlq(String scenarioKey, Map<String, Object> originalPayload, Exception cause) {
        try {
            Map<String, Object> dlqMessage = Map.of(
                "scenarioKey", scenarioKey,
                "originalPayload", originalPayload,
                "error", cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName(),
                "timestamp", Instant.now().toString()
            );
            kafkaTemplate.send(DLQ_TOPIC, scenarioKey, dlqMessage);
            log.info("DLQ message sent: scenario={}, topic={}", scenarioKey, DLQ_TOPIC);
        } catch (Exception dlqEx) {
            log.error("Failed to send DLQ message for scenario={}: {}", scenarioKey, dlqEx.getMessage(), dlqEx);
        }
    }
}
