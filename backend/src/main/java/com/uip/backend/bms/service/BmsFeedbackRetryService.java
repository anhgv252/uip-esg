package com.uip.backend.bms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.bms.BmsFeedbackMetrics;
import com.uip.backend.bms.domain.FeedbackStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M4-COR-04: Production-hardened facade over {@link BmsFeedbackService}.
 *
 * <p>Wraps {@link BmsFeedbackService#recordStage} with a 3-attempt retry
 * (exponential backoff: 200 ms → 400 ms → 800 ms). After all retries are
 * exhausted the failed event payload is published to the DLQ Kafka topic
 * {@value DLQ_TOPIC} for operational triage.</p>
 *
 * <p>Metrics counters updated on success and DLQ routing:
 * <ul>
 *   <li>{@code bms_feedback_stages_completed_total{stage}}</li>
 *   <li>{@code bms_feedback_loop_complete_total}</li>
 *   <li>{@code bms_feedback_dlq_total}</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BmsFeedbackRetryService {

    static final String DLQ_TOPIC     = "bms.feedback.dlq";
    static final int    MAX_ATTEMPTS  = 3;
    static final long   BASE_DELAY_MS = 200L;

    private final BmsFeedbackService               feedbackService;
    private final KafkaTemplate<String, String>    kafkaTemplate;
    private final ObjectMapper                     objectMapper;
    private final BmsFeedbackMetrics               metrics;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Records a feedback stage for a command with retry + DLQ fallback.
     *
     * @param commandId  the PendingBmsCommand id
     * @param buildingId building identifier for structured logging
     * @param stage      feedback stage to record
     * @param success    whether the stage completed successfully
     * @param notes      optional operator notes (nullable)
     */
    public void recordStageWithRetry(Long commandId, String buildingId,
                                     FeedbackStage stage, boolean success, String notes) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                feedbackService.recordStage(commandId, buildingId, stage, success, notes);
                metrics.recordStageCompleted(stage);

                if (feedbackService.isLoopComplete(commandId)) {
                    metrics.recordLoopComplete();
                    log.info("[BmsFeedbackRetry] Loop complete: commandId={} buildingId={}",
                            commandId, buildingId);
                }
                log.info("[BmsFeedbackRetry] Stage recorded: commandId={} buildingId={} stage={} attempt={}",
                        commandId, buildingId, stage, attempt);
                return;

            } catch (Exception e) {
                lastException = e;
                long delayMs = BASE_DELAY_MS * (1L << (attempt - 1)); // 200, 400, 800
                log.warn("[BmsFeedbackRetry] Attempt {}/{} failed for commandId={} stage={}: {} — retrying in {}ms",
                        attempt, MAX_ATTEMPTS, commandId, stage, e.getMessage(), delayMs);
                if (attempt < MAX_ATTEMPTS) {
                    sleepQuietly(delayMs);
                }
            }
        }

        // All retries exhausted — route to DLQ
        log.error("[BmsFeedbackRetry] All {} attempts failed for commandId={} stage={} — routing to DLQ",
                MAX_ATTEMPTS, commandId, stage, lastException);
        routeToDlq(commandId, buildingId, stage, success, notes, lastException);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void routeToDlq(Long commandId, String buildingId,
                             FeedbackStage stage, boolean success, String notes,
                             Exception cause) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("commandId",  commandId);
            payload.put("buildingId", buildingId);
            payload.put("stage",      stage != null ? stage.name() : null);
            payload.put("success",    success);
            payload.put("notes",      notes);
            payload.put("error",      cause != null ? cause.getMessage() : "unknown");
            payload.put("timestamp",  Instant.now().toString());

            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(DLQ_TOPIC, commandId != null ? commandId.toString() : "unknown", json);
            metrics.recordDlq();
            log.warn("[BmsFeedbackRetry] Routed to DLQ: commandId={} buildingId={} stage={} topic={}",
                    commandId, buildingId, stage, DLQ_TOPIC);

        } catch (JsonProcessingException serEx) {
            log.error("[BmsFeedbackRetry] Failed to serialize DLQ payload for commandId={}: {}",
                    commandId, serEx.getMessage());
        } catch (Exception kafkaEx) {
            log.error("[BmsFeedbackRetry] Failed to publish to DLQ topic={} for commandId={}: {}",
                    DLQ_TOPIC, commandId, kafkaEx.getMessage());
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[BmsFeedbackRetry] Retry sleep interrupted");
        }
    }
}
