package com.uip.backend.bms.domain;

/**
 * Lifecycle states for a {@link PendingBmsCommand}.
 *
 * <p>State machine:
 * <pre>
 *   PENDING ──approve──► APPROVED ──sendCommand──► EXECUTED
 *          ──reject──► REJECTED
 *          ──timeout──► EXPIRED   (via @Scheduled expiry task)
 * </pre>
 *
 * <p><strong>BR-010:</strong> Only OPERATOR or ADMIN role can move a command from PENDING to APPROVED/EXECUTED.
 */
public enum CommandStatus {

    /** Command proposed, waiting for operator decision. */
    PENDING,

    /** Operator approved — command is being dispatched to BACnet device. */
    APPROVED,

    /** Operator rejected — no BMS action will be taken. */
    REJECTED,

    /** 30-second approval window elapsed without operator decision — auto-cancelled. */
    EXPIRED,

    /** Command successfully sent to BACnet device via BacnetIpAdapter. */
    EXECUTED
}
