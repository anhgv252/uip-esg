package com.uip.backend.bms;

import com.uip.backend.bms.domain.FeedbackStage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * M4-COR-04: Micrometer metrics for the BMS feedback loop production-hardened path.
 *
 * <p>Exposed Prometheus metrics:
 * <ul>
 *   <li>{@code bms_feedback_stages_completed_total{stage}} — successful stage recordings</li>
 *   <li>{@code bms_feedback_loop_complete_total} — commands reaching all 4 stages</li>
 *   <li>{@code bms_feedback_dlq_total} — events routed to DLQ after retries exhausted</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BmsFeedbackMetrics {

    private static final String TAG_STAGE = "stage";
    private static final String METRIC_STAGE      = "bms_feedback_stages_completed_total";
    private static final String METRIC_LOOP_DONE  = "bms_feedback_loop_complete_total";
    private static final String METRIC_DLQ        = "bms_feedback_dlq_total";

    private final MeterRegistry registry;

    /**
     * Increment stage-completed counter for the given feedback stage.
     *
     * @param stage the feedback stage that completed successfully
     */
    public void recordStageCompleted(FeedbackStage stage) {
        Counter.builder(METRIC_STAGE)
                .description("Number of BMS feedback stages recorded successfully")
                .tag(TAG_STAGE, stage.name())
                .register(registry)
                .increment();
        log.debug("[BmsFeedbackMetrics] stage_completed stage={}", stage);
    }

    /**
     * Increment counter when the full 4-stage feedback loop completes for a command.
     */
    public void recordLoopComplete() {
        registry.counter(METRIC_LOOP_DONE).increment();
        log.debug("[BmsFeedbackMetrics] loop_complete");
    }

    /**
     * Increment DLQ counter when an event is routed to dead-letter queue.
     */
    public void recordDlq() {
        registry.counter(METRIC_DLQ).increment();
        log.warn("[BmsFeedbackMetrics] dlq_event recorded");
    }
}
