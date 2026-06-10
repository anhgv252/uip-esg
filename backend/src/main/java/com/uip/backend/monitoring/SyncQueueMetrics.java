package com.uip.backend.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * INFRA-TD-002: Exposes {@code uip_sync_queue_depth} gauge via Prometheus endpoint.
 *
 * <p>The actual sync queue lives in the mobile app (TypeScript SyncQueue).
 * This gauge serves as a placeholder wired to an AtomicInteger, defaulting to 0.
 * Sprint 12 will wire it to real queue depth data via REST API call or Redis.</p>
 *
 * <p>Prometheus alert rule {@code SyncQueueDepthHigh} fires when this metric
 * exceeds 100 for 5 consecutive minutes.</p>
 */
@Slf4j
@Component
public class SyncQueueMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicInteger queueDepth;

    @Value("${uip.monitoring.sync-queue-depth.initial:0}")
    private int initialDepth;

    public SyncQueueMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.queueDepth = new AtomicInteger(0);
    }

    @PostConstruct
    void init() {
        queueDepth.set(initialDepth);
        Gauge.builder("uip_sync_queue_depth", queueDepth, AtomicInteger::get)
                .description("Current depth of the mobile offline sync queue (placeholder — wired to real data in Sprint 12)")
                .register(meterRegistry);
        log.info("Registered gauge uip_sync_queue_depth with initial value={}", initialDepth);
    }

    /**
     * Update the queue depth. Will be called by REST endpoint or Redis listener in Sprint 12.
     */
    public void setQueueDepth(int depth) {
        int previous = queueDepth.getAndSet(depth);
        if (previous != depth) {
            log.debug("uip_sync_queue_depth updated: {} -> {}", previous, depth);
        }
    }

    /**
     * Read current queue depth value.
     */
    public int getQueueDepth() {
        return queueDepth.get();
    }
}
