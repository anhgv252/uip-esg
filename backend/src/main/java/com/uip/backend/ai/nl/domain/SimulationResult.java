package com.uip.backend.ai.nl.domain;

import java.util.List;

/**
 * Result of BPMN workflow simulation (dry-run test).
 *
 * @param intent      Workflow intent type
 * @param success     Whether simulation completed without errors
 * @param steps       Ordered list of simulated execution steps
 * @param warnings    Non-fatal issues detected during simulation
 * @param durationMs  Total simulation execution time
 */
public record SimulationResult(
    String intent,
    boolean success,
    List<SimulationStep> steps,
    List<String> warnings,
    long durationMs
) {
}
