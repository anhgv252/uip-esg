package com.uip.backend.scheduler;

import com.uip.backend.common.spi.EnvironmentBroadcastPort;
import com.uip.backend.common.spi.EnvironmentBroadcastPort.AqiSnapshot;
import com.uip.backend.common.spi.SseBroadcastPort;
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

/**
 * Tests {@link EnvironmentBroadcastScheduler}. After ADR-052 migration D3, the scheduler
 * depends on the neutral {@link EnvironmentBroadcastPort} / {@link SseBroadcastPort} ports
 * (not on {@code environment.service} / {@code notification.service} directly).
 */
@ExtendWith(MockitoExtension.class)
class EnvironmentBroadcastSchedulerTest {

    @Mock
    private EnvironmentBroadcastPort environmentBroadcastPort;

    @Mock
    private SseBroadcastPort sseBroadcastPort;

    private EnvironmentBroadcastScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new EnvironmentBroadcastScheduler(environmentBroadcastPort, sseBroadcastPort);
    }

    @Test
    void broadcastSensorUpdates_noClients_skips() {
        when(sseBroadcastPort.activeCount()).thenReturn(0);

        scheduler.broadcastSensorUpdates();

        verifyNoInteractions(environmentBroadcastPort);
        verify(sseBroadcastPort, never()).broadcast(any(), any());
    }

    @Test
    void broadcastSensorUpdates_withClients_broadcastsEachSensor() {
        when(sseBroadcastPort.activeCount()).thenReturn(2);
        when(environmentBroadcastPort.getCurrentAqiSnapshots()).thenReturn(List.of(
                new AqiSnapshot("SEN-01", 45, "GOOD", "D1", Instant.parse("2024-03-01T10:00:00Z")),
                new AqiSnapshot("SEN-02", 120, "UNHEALTHY", "D2", Instant.parse("2024-03-01T10:01:00Z"))
        ));

        scheduler.broadcastSensorUpdates();

        verify(sseBroadcastPort, times(2)).broadcast(eq("message"), any());
    }

    @Test
    void broadcastSensorUpdates_payloadShape() {
        when(sseBroadcastPort.activeCount()).thenReturn(1);
        when(environmentBroadcastPort.getCurrentAqiSnapshots()).thenReturn(List.of(
                new AqiSnapshot("SEN-X", 80, "MODERATE", "D3", Instant.parse("2024-06-01T08:00:00Z"))
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        scheduler.broadcastSensorUpdates();

        verify(sseBroadcastPort).broadcast(eq("message"), captor.capture());
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
        when(sseBroadcastPort.activeCount()).thenReturn(1);
        when(environmentBroadcastPort.getCurrentAqiSnapshots()).thenReturn(List.of(
                new AqiSnapshot("SEN-NULL", null, null, null, null)
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        scheduler.broadcastSensorUpdates();

        verify(sseBroadcastPort).broadcast(eq("message"), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> sensorMap = (Map<String, Object>) captor.getValue().get("sensor");
        assertThat(sensorMap.get("aqiValue")).isEqualTo(0);
        assertThat(sensorMap.get("category")).isEqualTo("UNKNOWN");
        assertThat(sensorMap.get("district")).isEqualTo("");
        assertThat(sensorMap.get("lastSeenAt")).isEqualTo("");
    }

    @Test
    void broadcastSensorUpdates_portThrows_doesNotPropagateException() {
        when(sseBroadcastPort.activeCount()).thenReturn(1);
        when(environmentBroadcastPort.getCurrentAqiSnapshots()).thenThrow(new RuntimeException("DB down"));

        // should not throw
        scheduler.broadcastSensorUpdates();

        verify(sseBroadcastPort, never()).broadcast(any(), any());
    }

    @Test
    void broadcastSensorUpdates_emptyReadings_noBroadcast() {
        when(sseBroadcastPort.activeCount()).thenReturn(1);
        when(environmentBroadcastPort.getCurrentAqiSnapshots()).thenReturn(List.of());

        scheduler.broadcastSensorUpdates();

        verify(sseBroadcastPort, never()).broadcast(any(), any());
    }
}
