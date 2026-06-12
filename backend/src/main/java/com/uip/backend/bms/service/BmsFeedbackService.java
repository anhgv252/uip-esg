package com.uip.backend.bms.service;

import com.uip.backend.bms.domain.BmsFeedbackEvent;
import com.uip.backend.bms.domain.FeedbackStage;
import com.uip.backend.bms.repository.BmsFeedbackEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Manages the BMS feedback loop state machine (M4-COR-04).
 *
 * <p>State machine: COMMAND_SENT → COMMAND_ACKNOWLEDGED → ACTION_TAKEN → FEEDBACK_VERIFIED.
 * The loop is considered complete when all four stages have been recorded with {@code success=true}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BmsFeedbackService {

    private final BmsFeedbackEventRepository feedbackEventRepository;

    /**
     * Records one stage of the feedback loop for a command.
     *
     * @param commandId   the {@link com.uip.backend.bms.domain.PendingBmsCommand} id
     * @param stage       which feedback stage is being recorded
     * @param success     whether the stage completed successfully
     * @param notes       optional operator notes or BMS acknowledgment message (nullable)
     */
    @Transactional
    public void recordStage(Long commandId, FeedbackStage stage, boolean success, String notes) {
        BmsFeedbackEvent event = new BmsFeedbackEvent();
        event.setPendingCommandId(commandId);
        event.setStage(stage);
        event.setSuccess(success);
        event.setNotes(notes);
        // buildingId is set if available via caller — left empty for COMMAND_SENT recorded internally
        feedbackEventRepository.save(event);

        log.info("[BMS-FEEDBACK] commandId={} stage={} success={}", commandId, stage, success);
    }

    /**
     * Records one stage with explicit buildingId context for structured logging.
     */
    @Transactional
    public void recordStage(Long commandId, String buildingId, FeedbackStage stage,
                            boolean success, String notes) {
        BmsFeedbackEvent event = new BmsFeedbackEvent();
        event.setPendingCommandId(commandId);
        event.setBuildingId(buildingId);
        event.setStage(stage);
        event.setSuccess(success);
        event.setNotes(notes);
        feedbackEventRepository.save(event);

        log.info("[BMS-FEEDBACK] commandId={} buildingId={} stage={} success={}",
                commandId, buildingId, stage, success);
    }

    /**
     * Returns all feedback events for a command ordered by recording time (oldest first).
     *
     * @param commandId the command to query
     * @return ordered list of feedback events (may be empty)
     */
    @Transactional(readOnly = true)
    public List<BmsFeedbackEvent> getFeedbackTimeline(Long commandId) {
        return feedbackEventRepository.findByPendingCommandIdOrderByRecordedAtAsc(commandId);
    }

    /**
     * Returns {@code true} if all four feedback stages have been recorded with {@code success=true}.
     *
     * @param commandId the command to evaluate
     * @return true when the full loop is complete and all stages succeeded
     */
    @Transactional(readOnly = true)
    public boolean isLoopComplete(Long commandId) {
        return Arrays.stream(FeedbackStage.values())
                .allMatch(stage -> {
                    List<BmsFeedbackEvent> events =
                            feedbackEventRepository.findByPendingCommandIdAndStage(commandId, stage);
                    return events.stream().anyMatch(BmsFeedbackEvent::isSuccess);
                });
    }
}
