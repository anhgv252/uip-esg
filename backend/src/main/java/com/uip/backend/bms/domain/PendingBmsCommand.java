package com.uip.backend.bms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Represents a proposed BMS command awaiting operator approval (M4-COR-03).
 *
 * <p><strong>BR-010 enforcement:</strong> No BMS command executes without explicit operator approval.
 * Commands are created in {@link CommandStatus#PENDING} and require an OPERATOR or ADMIN role
 * to transition to {@link CommandStatus#APPROVED} → {@link CommandStatus#EXECUTED}.
 *
 * <p>Auto-expires after 30 seconds if not resolved (via scheduled expiry task).
 */
@Entity
@Table(name = "pending_bms_commands",
        indexes = {
                @Index(name = "idx_bms_commands_status",   columnList = "status"),
                @Index(name = "idx_bms_commands_building", columnList = "building_id, status")
        })
@Getter
@Setter
@NoArgsConstructor
public class PendingBmsCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "building_id", nullable = false, length = 50)
    private String buildingId;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    /**
     * High-level command type (e.g. "HVAC_OFF", "SPRINKLER_ON", "EVACUATION").
     * Maps to {@link com.uip.backend.bms.api.dto.BmsCommand#commandType()}.
     */
    @Column(name = "command_type", nullable = false, length = 50)
    private String commandType;

    /**
     * BACnet/BMS device identifier (UUID string of bms_devices.id for Spring dispatch,
     * or raw BACnet device address for direct adapter use).
     */
    @Column(name = "target_device", nullable = false, length = 200)
    private String targetDevice;

    /** Value to write to the target device (numeric string or enum label). */
    @Column(name = "target_value", nullable = false, length = 200)
    private String targetValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CommandStatus status = CommandStatus.PENDING;

    /**
     * Originator of this command proposal — either a correlation incident UUID
     * or a user ID (for manual proposals via API).
     */
    @Column(name = "requested_by", length = 200)
    private String requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Approval window deadline — {@code createdAt + 30s}. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Null until the command is approved, rejected, or expired. */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Operator username who approved/rejected, or "system" for timeout expiry. */
    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
