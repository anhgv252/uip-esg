package com.uip.backend.bms.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a BMS device command method as requiring operator confirmation.
 *
 * <p>The frontend must send:</p>
 * <ul>
 *   <li>{@code X-Confirmation-Reason} header (min 10 chars) — why the command is being executed</li>
 *   <li>{@code X-Confirmation-Actuator-Name} header — required for HIGH and EMERGENCY commands</li>
 * </ul>
 *
 * <p>All confirmed commands are audit-logged with user identity, reason, and timestamp.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresConfirmation {

    /**
     * Danger level of the command. Higher levels require stricter confirmation.
     */
    DangerLevel dangerLevel() default DangerLevel.LOW;

    /**
     * Human-readable description of what the command does.
     */
    String reason() default "";

    enum DangerLevel {
        /** Routine read or status query — no extra confirmation needed. */
        LOW,
        /** Standard control (set point, schedule change) — reason required. */
        MEDIUM,
        /** Critical control (HVAC shutdown, valve close) — reason + actuator name required. */
        HIGH,
        /** Emergency action (fire suppression, emergency door, full shutdown) — reason + actuator name required. */
        EMERGENCY
    }
}
