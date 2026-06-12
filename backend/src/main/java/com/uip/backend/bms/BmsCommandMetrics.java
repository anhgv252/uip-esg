package com.uip.backend.bms;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Micrometer metrics for the BMS 2-step command confirmation flow (M4-COR-03).
 *
 * <p>Exposed Prometheus metrics:
 * <ul>
 *   <li>{@code bms_commands_proposed_total{building_id}}</li>
 *   <li>{@code bms_commands_approved_total{building_id}}</li>
 *   <li>{@code bms_commands_rejected_total{building_id}}</li>
 *   <li>{@code bms_commands_expired_total{building_id}}</li>
 *   <li>{@code bms_command_latency_seconds{building_id}} — Timer from proposed to approved</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BmsCommandMetrics {

    private static final String TAG_BUILDING = "building_id";

    private final MeterRegistry registry;

    // ── Public API ───────────────────────────────────────────────────────────────

    public void recordProposed(String buildingId) {
        counter("bms_commands_proposed_total", buildingId).increment();
    }

    public void recordApproved(String buildingId) {
        counter("bms_commands_approved_total", buildingId).increment();
    }

    public void recordRejected(String buildingId) {
        counter("bms_commands_rejected_total", buildingId).increment();
    }

    public void recordExpired(String buildingId) {
        counter("bms_commands_expired_total", buildingId).increment();
    }

    /**
     * Records approval latency from the moment a command was proposed.
     *
     * @param buildingId  building tag value
     * @param proposedAt  {@link com.uip.backend.bms.domain.PendingBmsCommand#getCreatedAt()}
     */
    public void recordApprovalLatency(String buildingId, Instant proposedAt) {
        Duration latency = Duration.between(proposedAt, Instant.now());
        Timer.builder("bms_command_latency_seconds")
                .description("Time from command proposed to approved/executed")
                .tag(TAG_BUILDING, safe(buildingId))
                .register(registry)
                .record(latency);
    }

    // ── Internal helpers ─────────────────────────────────────────────────────────

    private Counter counter(String name, String buildingId) {
        return registry.counter(name, TAG_BUILDING, safe(buildingId));
    }

    private String safe(String value) {
        return value != null ? value : "unknown";
    }
}
