package com.uip.backend.bms.repository;

import com.uip.backend.bms.domain.BmsFeedbackEvent;
import com.uip.backend.bms.domain.FeedbackStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link BmsFeedbackEvent}.
 */
public interface BmsFeedbackEventRepository extends JpaRepository<BmsFeedbackEvent, Long> {

    /**
     * Returns all feedback events for a command ordered by recording time (ascending).
     * Used to reconstruct the full feedback timeline.
     */
    List<BmsFeedbackEvent> findByPendingCommandIdOrderByRecordedAtAsc(Long pendingCommandId);

    /**
     * Returns feedback events for a command in a specific stage.
     * Used by {@code isLoopComplete} to check if all four stages are present and successful.
     */
    List<BmsFeedbackEvent> findByPendingCommandIdAndStage(Long pendingCommandId, FeedbackStage stage);
}
