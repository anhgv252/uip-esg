package com.uip.backend.scheduler;

import com.uip.backend.environment.api.dto.AqiResponseDto;
import com.uip.backend.environment.service.EnvironmentService;
import com.uip.backend.notification.service.SseEmitterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvironmentBroadcastSchedulerTest {

    @Mock
    private EnvironmentService environmentService;

    @Mock
    private SseEmitterRegistry sseEmitterRegistry;

    private EnvironmentBroadcastScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new EnvironmentBroadcastScheduler(environmentService, sseEmitterRegistry);
    }

    @Test
    void broadcastSensorUpdates_noClients_skips() {
        when(sseEmitterRegistry.activeCount()).thenReturn(0);

        scheduler.broadcastSensorUpdates();

        verifyNoInteractions(environmentService);
        verify(sseEmitterRegistry, never()).broadcast(any(), any());
    }

    @Test
    void broadcastSensorUpdates_withClients_broadcastsEachSensor() {
        when(sseEmitterRegistry.activeCount()).thenReturn(2);
        AqiResponseDto s1 = AqiResponseDto.builder()
                .sensorId("SEN-01").aqiValue(45).category("GOOD")
                .districtCode("D1").timestamp(Instant.parse("2024-03-01T10:00:00Z"))
                .build();
        AqiResponseDto s2 = AqiResponseDto.builder()
                .sensorId("SEN-02").aqiValue(120).category("UNHEALTHY")
                .districtCode("D2").timestamp(Instant.parse("2024-03-01T10:01:00Z"))
                .build();
        when(environmentService.getCurrentAqi()).thenReturn(List.of(s1, s2));

        scheduler.broadcastSensorUpdates();

        verify(sseEmitterRegistry, times(2)).broadcast(eq("message"), any());
    }

    @Test
    void broadcastSensorUpdates_payloadShape() {
        when(sseEmitterRegistry.activeCount()).thenReturn(1);
        AqiResponseDto sensor = AqiResponseDto.builder()
                .sensorId("SEN-X").aqiValue(80).category("MODERATE")
                .districtCode("D3").timestamp(Instant.parse("2024-06-01T08:00:00Z"))
                .build();
        when(environmentService.getCurrentAqi()).thenReturn(List.of(sensor));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        scheduler.broadcastSensorUpdates();

        verify(sseEmitterRegistry).broadcast(eq("message"), captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertThat(payload.get("type")).isEqualTo("SENSOR_UPDATE");

        @SuppressWarnings("unchecked")
        Map<String, Object> sensorMap = (Map<String, Object>) payload.get("sensor");
        assertThat(sensorMap.get("id")).isEqualTo("SEN-X");
        assertThat(sensorMap.get("aqiValue")).isEqualTo(80);
        assertThat(sensorMap.get("category")).isEqualTo("MODERATE");
        assertThat(sensorMap.get("district")).isEqualTo("D3");
        assertThat(sensorMap.get("status")).isEqualTo("ONLINE");
    }

    @Test
    void broadcastSensorUpdates_nullFields_useDefaults() {
        when(sseEmitterRegistry.activeCount()).thenReturn(1);
        AqiResponseDto sensor = AqiResponseDto.builder()
                .sensorId("SEN-NULL").aqiValue(null).category(null)
                .districtCode(null).timestamp(null)
                .build();
        when(environmentService.getCurrentAqi()).thenReturn(List.of(sensor));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        scheduler.broadcastSensorUpdates();

        verify(sseEmitterRegistry).broadcast(eq("message"), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> sensorMap = (Map<String, Object>) captor.getValue().get("sensor");
        assertThat(sensorMap.get("aqiValue")).isEqualTo(0);
        assertThat(sensorMap.get("category")).isEqualTo("UNKNOWN");
        assertThat(sensorMap.get("district")).isEqualTo("");
        assertThat(sensorMap.get("lastSeenAt")).isEqualTo("");
    }

    @Test
    void broadcastSensorUpdates_serviceThrows_doesNotPropagateException() {
        when(sseEmitterRegistry.activeCount()).thenReturn(1);
        when(environmentService.getCurrentAqi()).thenThrow(new RuntimeException("DB down"));

        // should not throw
        scheduler.broadcastSensorUpdates();

        verify(sseEmitterRegistry, never()).broadcast(any(), any());
    }

    @Test
    void broadcastSensorUpdates_emptyReadings_noBroadcast() {
        when(sseEmitterRegistry.activeCount()).thenReturn(1);
        when(environmentService.getCurrentAqi()).thenReturn(List.of());

        scheduler.broadcastSensorUpdates();

        verify(sseEmitterRegistry, never()).broadcast(any(), any());
    }
}
