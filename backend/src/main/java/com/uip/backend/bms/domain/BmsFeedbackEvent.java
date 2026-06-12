package com.uip.backend.bms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Records one stage of the BMS feedback loop for a completed command (M4-COR-04).
 *
 * <p>Full loop: COMMAND_SENT → COMMAND_ACKNOWLEDGED → ACTION_TAKEN → FEEDBACK_VERIFIED.
 * All four stages with {@code success=true} indicate the feedback loop is complete.
 */
@Entity
@Table(name = "bms_feedback_events",
        indexes = @Index(name = "idx_bms_feedback_command", columnList = "pending_command_id, recorded_at"))
@Getter
@Setter
@NoArgsConstructor
public class BmsFeedbackEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to {@link PendingBmsCommand#getId()} — stored as plain column (no @ManyToOne to avoid eager load). */
    @Column(name = "pending_command_id", nullable = false)
    private Long pendingCommandId;

    @Column(name = "building_id", nullable = false, length = 50)
    private String buildingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FeedbackStage stage;

    /** Operator notes, BMS ACK message, or sensor reading that confirmed the action. */
    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @PrePersist
    void prePersist() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }
}
