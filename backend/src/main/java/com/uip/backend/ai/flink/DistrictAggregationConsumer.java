package com.uip.backend.ai.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.ai.AiAnalysisResponse;
import com.uip.backend.ai.AiInferenceService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * M4-AI-01: Consumes batched district-aggregation events emitted by the Flink
 * {@code DistrictAggregationJob} on the {@code ai.district.aggregations} topic
 * and delegates each to {@link AiInferenceService#analyzeBatch}.
 *
 * <p>This is the first real caller of the AI cost-optimization stack
 * (batching → routing → caching → budget). Without it the stack is wired but
 * never exercised, so G1 (AI cost &lt; $1/day) cannot be measured.</p>
 *
 * <p>Retry/DLQ mirrors {@code CorrelationDlqHandler}: up to 3 attempts with
 * 1 s back-off, then {@code ai.district.aggregations.dlq}.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DistrictAggregationConsumer {

    /** Dead-letter queue for events that fail after all retry attempts. */
    static final String DLQ_TOPIC = "ai.district.aggregations.dlq";

    private final AiInferenceService aiInferenceService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000),
            autoCreateTopics = "false",
            kafkaTemplate = "kafkaTemplate"
    )
    @KafkaListener(
            topics = "${ai.flink.district.outputTopic:ai.district.aggregations}",
            groupId = "ai-aggregation-consumer"
    )
    public void consume(String message) {
        log.debug("[DistrictAggregationConsumer] Received message length={}",
                message != null ? message.length() : 0);
        try {
            DistrictAggregationEvent event = objectMapper.readValue(message, DistrictAggregationEvent.class);
            if (event == null || event.districtCode() == null) {
                log.warn("[DistrictAggregationConsumer] Event missing districtCode — skipping");
                return;
            }
            AiAnalysisResponse response = aiInferenceService.analyzeBatch(event);
            meterRegistry.counter("ai_batched_events_consumed_total").increment();
            log.debug("[DistrictAggregationConsumer] Analyzed district={} type={} model={}",
                    event.districtCode(), event.sensorType(), response.modelUsed());
        } catch (Exception e) {
            log.error("[DistrictAggregationConsumer] Failed to process event — sending to DLQ: {}",
                    e.getMessage(), e);
            safeSendToDlq(message);
            throw new RuntimeException("District aggregation processing failed", e);
        }
    }

    @DltHandler
    public void handleDlt(String message) {
        log.error("[DistrictAggregationConsumer] All retries exhausted — routing to DLQ: topic={}",
                DLQ_TOPIC);
        safeSendToDlq(message);
    }

    private void safeSendToDlq(String message) {
        try {
            kafkaTemplate.send(DLQ_TOPIC, message);
            log.warn("[DistrictAggregationConsumer] Message forwarded to DLQ: {}", DLQ_TOPIC);
        } catch (Exception dlqEx) {
            log.error("[DistrictAggregationConsumer] Failed to forward to DLQ {}: {}",
                    DLQ_TOPIC, dlqEx.getMessage());
        }
    }
}
