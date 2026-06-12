package com.uip.backend.correlation.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.correlation.service.CorrelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * M4-COR-01: Consumes correlated incident events produced by the Flink CEP pipeline
 * on the {@code correlated.incidents} topic, delegates persistence to
 * {@link CorrelationService#processIncomingEvent}, and routes unrecoverable failures
 * to the DLQ via {@link KafkaTemplate}.
 *
 * <p>Retry strategy: {@link RetryableTopic} retries up to 3 times with a 1-second
 * exponential back-off before forwarding to the dead-letter handler.</p>
 *
 * <p>DLQ topic: {@value DLQ_TOPIC} — consumed by ops for manual triage.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CorrelationDlqHandler {

    /** Dead-letter queue for messages that fail after all retry attempts. */
    static final String DLQ_TOPIC = "correlated.incidents.dlq";

    private final CorrelationService              correlationService;
    private final KafkaTemplate<String, String>   kafkaTemplate;
    private final ObjectMapper                    objectMapper;

    // ─── Main listener ────────────────────────────────────────────────────────

    /**
     * Consumes a JSON-encoded correlated incident message from Flink's output topic.
     *
     * <p>{@code @RetryableTopic} provides up to 3 automatic retry attempts with 1 s delay
     * between attempts. After exhausting retries, {@link #handleDlt(String)} is invoked.</p>
     *
     * <p>A manual DLQ fallback via {@link KafkaTemplate} is also triggered on the first
     * failure to ensure the message is not silently lost if the retry mechanism is
     * misconfigured or the topic does not exist.</p>
     *
     * @param message JSON string of the correlated incident event
     */
    @RetryableTopic(
            attempts = "3",
            backoff  = @Backoff(delay = 1000),
            autoCreateTopics = "false",
            // Explicitly name the KafkaTemplate to use — required when multiple
            // KafkaTemplate beans exist (e.g. avroKafkaTemplate + Spring Boot default),
            // otherwise Spring Kafka cannot resolve which one owns the retry topics.
            kafkaTemplate = "kafkaTemplate"
    )
    @KafkaListener(
            topics      = "${correlation.flink.outputTopic:correlated.incidents}",
            groupId     = "correlation-consumer"
    )
    public void consume(String message) {
        log.debug("[CorrelationDlqHandler] Received message length={}", message != null ? message.length() : 0);
        try {
            Map<String, Object> event = objectMapper.readValue(message, new TypeReference<>() {});
            correlationService.processIncomingEvent(event);
            log.debug("[CorrelationDlqHandler] Event processed successfully");
        } catch (Exception e) {
            log.error("[CorrelationDlqHandler] Failed to process event — sending to DLQ manually: {}",
                    e.getMessage(), e);
            // Belt-and-suspenders: publish to DLQ now in case @RetryableTopic DLT is unavailable
            safeSendToDlq(message);
            // Re-throw so @RetryableTopic can attempt retries; after exhaustion handleDlt() runs
            throw new RuntimeException("Correlation event processing failed", e);
        }
    }

    // ─── DLT handler (called by Spring Kafka after all retries are exhausted) ──

    /**
     * Dead-letter handler invoked by Spring Kafka after all retry attempts are exhausted.
     * Publishes the original message to {@value DLQ_TOPIC} for operational triage.
     *
     * @param message original Kafka message that failed processing
     */
    @DltHandler
    public void handleDlt(String message) {
        log.error("[CorrelationDlqHandler] All retries exhausted — routing to DLQ: topic={}",
                DLQ_TOPIC);
        safeSendToDlq(message);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void safeSendToDlq(String message) {
        try {
            kafkaTemplate.send(DLQ_TOPIC, message);
            log.warn("[CorrelationDlqHandler] Message forwarded to DLQ: {}", DLQ_TOPIC);
        } catch (Exception dlqEx) {
            log.error("[CorrelationDlqHandler] Failed to forward to DLQ {}: {}",
                    DLQ_TOPIC, dlqEx.getMessage());
        }
    }
}
