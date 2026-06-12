package com.uip.backend.bms.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.bms.BmsFeedbackMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * M4-COR-04: DLQ consumer for the BMS feedback loop.
 *
 * <p>Listens on {@code bms.feedback.dlq} for events that exhausted all retry
 * attempts in {@link com.uip.backend.bms.service.BmsFeedbackRetryService}.
 * Logs a structured WARNING and increments the DLQ metric counter for
 * Prometheus alerting.</p>
 *
 * <p>This consumer is intentionally read-only — it does NOT attempt
 * reprocessing (ops team triages manually via the log warning and Prometheus
 * {@code bms_feedback_dlq_total} alert rule).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BmsFeedbackDlqConsumer {

    static final String DLQ_TOPIC = "bms.feedback.dlq";

    private final ObjectMapper      objectMapper;
    private final BmsFeedbackMetrics metrics;

    // ─── Listener ─────────────────────────────────────────────────────────────

    /**
     * Consumes a DLQ message published after all retry attempts were exhausted.
     *
     * @param message JSON payload containing commandId, buildingId, stage, error, timestamp
     */
    @KafkaListener(topics = DLQ_TOPIC, groupId = "uip-backend-bms-feedback-dlq")
    public void onDlqMessage(String message) {
        try {
            Map<String, Object> payload = objectMapper.readValue(message, new TypeReference<>() {});
            Object commandId  = payload.get("commandId");
            Object buildingId = payload.get("buildingId");
            Object stage      = payload.get("stage");
            Object error      = payload.get("error");
            Object timestamp  = payload.get("timestamp");

            log.warn("[BmsFeedbackDLQ] DLQ event received: commandId={} buildingId={} stage={} error={} timestamp={}",
                    commandId, buildingId, stage, error, timestamp);
            metrics.recordDlq();

        } catch (Exception e) {
            log.error("[BmsFeedbackDLQ] Failed to deserialize DLQ message: {} — raw={}",
                    e.getMessage(), message);
        }
    }
}
