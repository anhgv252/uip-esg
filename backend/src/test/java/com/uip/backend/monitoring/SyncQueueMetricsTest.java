package com.uip.backend.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyncQueueMetricsTest {

    private MeterRegistry meterRegistry;
    private SyncQueueMetrics syncQueueMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        syncQueueMetrics = new SyncQueueMetrics(meterRegistry);
    }

    @Test
    void init_registersGaugeWithDefaultValue() {
        syncQueueMetrics.init();

        assertThat(meterRegistry.get("uip_sync_queue_depth").gauge().value()).isEqualTo(0.0);
    }

    @Test
    void setQueueDepth_updatesGaugeValue() {
        syncQueueMetrics.init();
        syncQueueMetrics.setQueueDepth(42);

        assertThat(meterRegistry.get("uip_sync_queue_depth").gauge().value()).isEqualTo(42.0);
    }

    @Test
    void getQueueDepth_returnsCurrentValue() {
        syncQueueMetrics.init();
        syncQueueMetrics.setQueueDepth(150);

        assertThat(syncQueueMetrics.getQueueDepth()).isEqualTo(150);
    }

    @Test
    void setQueueDepth_sameValue_noError() {
        syncQueueMetrics.init();
        syncQueueMetrics.setQueueDepth(10);
        syncQueueMetrics.setQueueDepth(10);

        assertThat(meterRegistry.get("uip_sync_queue_depth").gauge().value()).isEqualTo(10.0);
        assertThat(syncQueueMetrics.getQueueDepth()).isEqualTo(10);
    }
}
