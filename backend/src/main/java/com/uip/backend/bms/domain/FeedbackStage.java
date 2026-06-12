package com.uip.backend.bms.domain;

/**
 * Stages in the BMS command feedback loop (M4-COR-04).
 *
 * <p>State machine: COMMAND_SENT → COMMAND_ACKNOWLEDGED → ACTION_TAKEN → FEEDBACK_VERIFIED
 *
 * <p>All four stages must reach {@code success=true} for
 * {@link com.uip.backend.bms.service.BmsFeedbackService#isLoopComplete} to return {@code true}.
 */
public enum FeedbackStage {

    /** BACnet write request dispatched to target device. Recorded by BmsCommandService after sendCommand. */
    COMMAND_SENT,

    /** Target device acknowledged receipt of the command. */
    COMMAND_ACKNOWLEDGED,

    /** Physical actuator (HVAC/sprinkler/etc.) has taken the commanded action. */
    ACTION_TAKEN,

    /** Operator or sensor reading confirmed the expected outcome. */
    FEEDBACK_VERIFIED
}
