package com.uip.backend.ai.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.ai.AiAnalysisResponse;
import com.uip.backend.ai.AiInferenceService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M4-AI-01: Unit tests for {@link DistrictAggregationConsumer}.
 *
 * <p>Pure Mockito — no Spring context. Verifies the consumer delegates to
 * {@link AiInferenceService#analyzeBatch}, increments the consumed counter,
 * and silently skips malformed payloads (no re-throw, no DLQ in simplified flow).</p>
 */
@DisplayName("DistrictAggregationConsumer — batched AI consumer")
class DistrictAggregationConsumerTest {

    private AiInferenceService aiInferenceService;
    private SimpleMeterRegistry meterRegistry;
    private DistrictAggregationConsumer consumer;

    @BeforeEach
    void setUp() {
        aiInferenceService = mock(AiInferenceService.class);
        meterRegistry = new SimpleMeterRegistry();
        consumer = new DistrictAggregationConsumer(
                aiInferenceService, new ObjectMapper(), meterRegistry);
    }

    @Test
    @DisplayName("AQI event → delegates to analyzeBatch, increments counter")
    void consume_aqiEvent_delegates() throws Exception {
        DistrictAggregationEvent event = new DistrictAggregationEvent(
                "tenant-A", "HCM-D1", "AQI", 3, 120.0, 100.0,
                1_700_000_000_000L, 1_700_000_060_000L,
                List.of(new DistrictAggregationEvent.SensorSnapshot("s1", 120.0, 1L)));
        String json = new ObjectMapper().writeValueAsString(event);

        when(aiInferenceService.analyzeBatch(any()))
                .thenReturn(new AiAnalysisResponse("HCM-D1", "100-500", "ok", "haiku", 1L));

        consumer.consume(json);

        verify(aiInferenceService, times(1)).analyzeBatch(any());
        assertThat(meterRegistry.counter("ai_batched_events_consumed_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Non-AQI event (NOISE) → still delegated (generic path)")
    void consume_noiseEvent_delegates() throws Exception {
        DistrictAggregationEvent event = new DistrictAggregationEvent(
                "tenant-A", "HCM-D2", "NOISE", 5, 85.0, 70.0,
                1L, 2L, List.of());
        String json = new ObjectMapper().writeValueAsString(event);

        when(aiInferenceService.analyzeBatch(any()))
                .thenReturn(new AiAnalysisResponse("HCM-D2", "50-100", "ok", "haiku", 1L));

        consumer.consume(json);

        verify(aiInferenceService, times(1)).analyzeBatch(any());
    }

    @Test
    @DisplayName("Malformed JSON → error counter incremented, no exception thrown, no AI call")
    void consume_malJson_incrementsErrorCounter() {
        String bad = "{not json";

        // Should NOT throw — consumer logs and skips
        consumer.consume(bad);

        verify(aiInferenceService, never()).analyzeBatch(any());
        assertThat(meterRegistry.counter("ai_batched_events_error_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Event missing districtCode → skipped, not delegated")
    void consume_missingDistrict_skipped() throws Exception {
        DistrictAggregationEvent event = new DistrictAggregationEvent(
                "tenant-A", null, "AQI", 1, 50.0, 50.0, 1L, 2L, List.of());
        String json = new ObjectMapper().writeValueAsString(event);

        consumer.consume(json);

        verify(aiInferenceService, never()).analyzeBatch(any());
        assertThat(meterRegistry.counter("ai_batched_events_consumed_total").count()).isZero();
    }
}
