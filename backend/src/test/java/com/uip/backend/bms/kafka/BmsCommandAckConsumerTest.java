package com.uip.backend.bms.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.bms.domain.BmsDevice;
import com.uip.backend.bms.domain.BmsProtocol;
import com.uip.backend.bms.repository.BmsDeviceRepository;
import com.uip.backend.notification.service.SseEmitterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BmsCommandAckConsumer — device status update + SSE broadcast.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BmsCommandAckConsumer — ACK processing + device status update")
class BmsCommandAckConsumerTest {

    @Mock private SseEmitterRegistry sseEmitterRegistry;
    @Mock private BmsDeviceRepository bmsDeviceRepository;

    private BmsCommandAckConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID DEVICE_UUID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        consumer = new BmsCommandAckConsumer(sseEmitterRegistry, bmsDeviceRepository, objectMapper);
    }

    // ─── Status mapping ──────────────────────────────────────────────────────

    @ParameterizedTest(name = "ACK status '{0}' → device status '{1}'")
    @CsvSource({
            "ACKNOWLEDGED, ONLINE",
            "SUCCESS,      ONLINE",
            "ONLINE,       ONLINE",
            "OFFLINE,      OFFLINE",
            "DISCONNECTED, OFFLINE",
            "ERROR,        ERROR",
            "FAILED,       ERROR",
            "UNKNOWN_VAL,  UNKNOWN",
            ",             UNKNOWN"
    })
    @DisplayName("mapAckStatus maps correctly")
    void mapAckStatus(String input, String expected) {
        assertThat(BmsCommandAckConsumer.mapAckStatus(input)).isEqualTo(expected);
    }

    // ─── Happy path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("ACK received → device status updated to ONLINE + lastSeen set")
    void onCommandAck_updatesDeviceStatus() {
        BmsDevice device = buildDevice("UNKNOWN");
        when(bmsDeviceRepository.findById(DEVICE_UUID)).thenReturn(Optional.of(device));
        when(bmsDeviceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String payload = buildPayload(DEVICE_UUID.toString(), "ACKNOWLEDGED", "hcm");
        consumer.onCommandAck(payload);

        ArgumentCaptor<BmsDevice> captor = ArgumentCaptor.forClass(BmsDevice.class);
        verify(bmsDeviceRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ONLINE");
        assertThat(captor.getValue().getLastSeen()).isNotNull();
    }

    @Test
    @DisplayName("ACK with OFFLINE status → device status updated to OFFLINE")
    void onCommandAck_offline_updatesStatus() {
        BmsDevice device = buildDevice("ONLINE");
        when(bmsDeviceRepository.findById(DEVICE_UUID)).thenReturn(Optional.of(device));
        when(bmsDeviceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        consumer.onCommandAck(buildPayload(DEVICE_UUID.toString(), "OFFLINE", "hcm"));

        ArgumentCaptor<BmsDevice> captor = ArgumentCaptor.forClass(BmsDevice.class);
        verify(bmsDeviceRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("OFFLINE");
    }

    @Test
    @DisplayName("ACK received → SSE broadcast sent")
    void onCommandAck_broadcastsSse() {
        when(bmsDeviceRepository.findById(DEVICE_UUID)).thenReturn(Optional.empty());

        consumer.onCommandAck(buildPayload(DEVICE_UUID.toString(), "ACKNOWLEDGED", "hcm"));

        verify(sseEmitterRegistry).broadcast(eq("bms-command-ack"), anyMap());
    }

    // ─── Edge cases ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("ACK for unknown device → no DB save, SSE still sent")
    void onCommandAck_unknownDevice_noSave() {
        when(bmsDeviceRepository.findById(any())).thenReturn(Optional.empty());

        consumer.onCommandAck(buildPayload(DEVICE_UUID.toString(), "ACKNOWLEDGED", "hcm"));

        verify(bmsDeviceRepository, never()).save(any());
        verify(sseEmitterRegistry).broadcast(eq("bms-command-ack"), anyMap());
    }

    @Test
    @DisplayName("ACK without deviceId → no DB lookup")
    void onCommandAck_noDeviceId_noDbLookup() {
        String payload = """
                {"commandId":"cmd-1","status":"ACKNOWLEDGED","tenantId":"hcm"}
                """;
        consumer.onCommandAck(payload);

        verifyNoInteractions(bmsDeviceRepository);
        verify(sseEmitterRegistry).broadcast(any(), any());
    }

    @Test
    @DisplayName("ACK with invalid UUID → no DB lookup, no exception")
    void onCommandAck_invalidUuid_noException() {
        String payload = buildPayload("not-a-uuid", "ACKNOWLEDGED", "hcm");
        // Should not throw
        consumer.onCommandAck(payload);

        verify(bmsDeviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Malformed JSON → consumer does not throw (graceful degradation)")
    void onCommandAck_malformedJson_noException() {
        consumer.onCommandAck("{invalid json}");

        verifyNoInteractions(bmsDeviceRepository);
        verifyNoInteractions(sseEmitterRegistry);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private BmsDevice buildDevice(String status) {
        BmsDevice d = new BmsDevice();
        d.setTenantId("hcm");
        d.setDeviceName("TEST-DEVICE-001");
        d.setProtocol(BmsProtocol.MODBUS_TCP);
        d.setStatus(status);
        return d;
    }

    private String buildPayload(String deviceId, String status, String tenantId) {
        return """
                {"commandId":"cmd-001","deviceId":"%s","status":"%s","tenantId":"%s"}
                """.formatted(deviceId, status, tenantId);
    }
}
