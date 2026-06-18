package com.uip.backend.ai.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.ai.AiAnalysisResponse;
import com.uip.backend.ai.AiInferenceService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
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
 * <p>Uses {@code stringKafkaListenerContainerFactory} so the raw JSON string is
 * delivered and deserialized manually, avoiding conflicts with the default
 * {@code JsonDeserializer} factory's {@code LinkedHashMap} default type.</p>
 *
 * <p>Errors are caught and logged; the message is committed (no re-queue) to
 * avoid blocking the partition. Persistent failures are monitored via the
 * {@code ai_batched_events_error_total} counter.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DistrictAggregationConsumer {

    private final AiInferenceService aiInferenceService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @KafkaListener(
            topics = "${ai.flink.district.outputTopic:ai.district.aggregations}",
            groupId = "ai-aggregation-consumer",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void consume(String message) {
        log.info("[DistrictAggregationConsumer] ENTRY: length={}", message != null ? message.length() : 0);
        try {
            DistrictAggregationEvent event = objectMapper.readValue(message, DistrictAggregationEvent.class);
            if (event == null || event.districtCode() == null) {
                log.warn("[DistrictAggregationConsumer] Event missing districtCode — skipping");
                return;
            }
            log.info("[DistrictAggregationConsumer] Calling analyzeBatch: district={} type={} count={}",
                    event.districtCode(), event.sensorType(), event.count());
            AiAnalysisResponse response = aiInferenceService.analyzeBatch(event);
            meterRegistry.counter("ai_batched_events_consumed_total").increment();
            log.info("[DistrictAggregationConsumer] SUCCESS: district={} type={} model={} aqiRange={}",
                    event.districtCode(), event.sensorType(), response.modelUsed(), response.aqiRange());
        } catch (Exception e) {
            log.error("[DistrictAggregationConsumer] FAILED to process message — skipping: {}", e.getMessage(), e);
            meterRegistry.counter("ai_batched_events_error_total").increment();
        }
    }
}
