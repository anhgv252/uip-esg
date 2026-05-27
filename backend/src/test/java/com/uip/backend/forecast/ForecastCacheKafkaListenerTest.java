package com.uip.backend.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * Unit tests for ForecastCacheKafkaListener (S4-17).
 * Verifies cache eviction behaviour on ESG metric events.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ForecastCacheKafkaListener — unit")
class ForecastCacheKafkaListenerTest {

    @Mock
    private ForecastCacheStatsService cacheStatsService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ForecastCacheKafkaListener listener;

    @Test
    @DisplayName("onEsgMetricEvent — null message does not trigger eviction")
    void onEsgMetricEvent_nullMessage_doesNotEvict() {
        listener.onEsgMetricEvent(null);

        verify(cacheStatsService, never()).evictAll();
    }

    @Test
    @DisplayName("onEsgMetricEvent — ENERGY message triggers evictAll")
    void onEsgMetricEvent_energyMessage_evictsAll() {
        listener.onEsgMetricEvent("{\"type\":\"ENERGY\",\"buildingId\":\"B1\",\"value\":100.5}");

        verify(cacheStatsService, times(1)).evictAll();
    }

    @Test
    @DisplayName("onEsgMetricEvent — WATER message triggers evictAll")
    void onEsgMetricEvent_waterMessage_evictsAll() {
        listener.onEsgMetricEvent("{\"type\":\"WATER\",\"buildingId\":\"B2\",\"value\":50.0}");

        verify(cacheStatsService, times(1)).evictAll();
    }

    @Test
    @DisplayName("onEsgMetricEvent — AIR_QUALITY message does not trigger eviction")
    void onEsgMetricEvent_airQualityMessage_doesNotEvict() {
        listener.onEsgMetricEvent("{\"type\":\"AIR_QUALITY\",\"value\":35.2}");

        verify(cacheStatsService, never()).evictAll();
    }

    @Test
    @DisplayName("onEsgMetricEvent — empty string does not trigger eviction")
    void onEsgMetricEvent_emptyMessage_doesNotEvict() {
        listener.onEsgMetricEvent("");

        verify(cacheStatsService, never()).evictAll();
    }

    @Test
    @DisplayName("onEsgMetricEvent — TRAFFIC message does not trigger eviction")
    void onEsgMetricEvent_trafficMessage_doesNotEvict() {
        listener.onEsgMetricEvent("{\"type\":\"TRAFFIC\",\"value\":123}");

        verify(cacheStatsService, never()).evictAll();
    }

    @Test
    @DisplayName("onEsgMetricEvent — lowercase energy does not trigger eviction")
    void onEsgMetricEvent_lowercaseEnergy_doesNotEvict() {
        listener.onEsgMetricEvent("{\"type\":\"energy\",\"value\":100}");

        verify(cacheStatsService, never()).evictAll();
    }

    @Test
    @DisplayName("onEsgMetricEvent — invalid JSON does not crash, does not evict")
    void onEsgMetricEvent_invalidJson_doesNotEvict() {
        listener.onEsgMetricEvent("not valid json {{{");

        verify(cacheStatsService, never()).evictAll();
    }

    @Test
    @DisplayName("onEsgMetricEvent — JSON without type field does not evict")
    void onEsgMetricEvent_noTypeField_doesNotEvict() {
        listener.onEsgMetricEvent("{\"buildingId\":\"B1\",\"value\":100.5}");

        verify(cacheStatsService, never()).evictAll();
    }

    @Test
    @DisplayName("onEsgMetricEvent — building named ENERGY in other fields does not false-trigger")
    void onEsgMetricEvent_energyInBuildingName_doesNotFalseTrigger() {
        listener.onEsgMetricEvent("{\"type\":\"AIR_QUALITY\",\"buildingId\":\"ENERGY Tower\",\"value\":35.2}");

        verify(cacheStatsService, never()).evictAll();
    }
}
