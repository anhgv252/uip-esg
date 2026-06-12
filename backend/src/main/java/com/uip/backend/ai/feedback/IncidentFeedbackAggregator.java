package com.uip.backend.ai.feedback;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.repository.AlertEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * M4-COR-07: Collects 30-day alert feedback records from the DB.
 *
 * <p>Fetches {@link AlertEvent} rows that have operator feedback
 * ({@code feedback_correct IS NOT NULL}) and whose {@code detected_at}
 * falls within the last 30 days. Uses an inline JPA {@link Specification}
 * to avoid cross-schema JOIN — module boundary compliant.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentFeedbackAggregator {

    private static final int LOOKBACK_DAYS = 30;

    private final AlertEventRepository alertEventRepository;

    /**
     * Returns all alert events with feedback recorded in the last 30 days.
     *
     * @return list of alert events — never null, may be empty
     */
    @Transactional(readOnly = true)
    public List<AlertEvent> collectRecentFeedback() {
        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);

        Specification<AlertEvent> spec = (root, query, cb) ->
                cb.and(
                        cb.isNotNull(root.get("feedbackCorrect")),
                        cb.greaterThanOrEqualTo(root.get("detectedAt"), since)
                );

        List<AlertEvent> feedback = alertEventRepository.findAll(spec);
        log.info("[IncidentFeedbackAggregator] Collected {} feedback records (last {} days)",
                feedback.size(), LOOKBACK_DAYS);
        return feedback;
    }
}
