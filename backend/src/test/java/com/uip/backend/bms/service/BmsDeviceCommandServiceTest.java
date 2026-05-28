package com.uip.backend.bms.service;

import com.uip.backend.bms.adapter.BmsAdapterException;
import com.uip.backend.bms.adapter.BmsAdapterRegistry;
import com.uip.backend.bms.adapter.BmsProtocolAdapter;
import com.uip.backend.bms.api.dto.BmsCommand;
import com.uip.backend.bms.domain.BmsDevice;
import com.uip.backend.bms.domain.BmsProtocol;
import com.uip.backend.bms.mqtt.MqttPublisher;
import com.uip.backend.bms.repository.BmsDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BmsDeviceCommandService — unit")
class BmsDeviceCommandServiceTest {

    @Mock private BmsDeviceRepository repository;
    @Mock private BmsAdapterRegistry adapterRegistry;
    @Mock private BmsProtocolAdapter adapter;
    @Mock private MqttPublisher mqttPublisher;

    private BmsDeviceCommandService service;
    private BmsDevice device;

    @BeforeEach
    void setUp() {
        service = new BmsDeviceCommandService(repository, adapterRegistry, mqttPublisher);
        device = new BmsDevice();
        device.setId(UUID.randomUUID());
        device.setTenantId("hcm");
        device.setDeviceName("AHU-01");
        device.setProtocol(BmsProtocol.MODBUS_TCP);
    }

    @Test
    @DisplayName("sendCommand — returns command ID on success")
    void sendCommand_success() {
        BmsCommand cmd = new BmsCommand("SET_POINT", Map.of("value", 22));
        when(repository.findById(device.getId())).thenReturn(Optional.of(device));
        when(adapterRegistry.getAdapter(device)).thenReturn(Optional.of(adapter));

        String commandId = service.sendCommand("hcm", device.getId(), cmd);

        assertThat(commandId).isNotBlank();
        verify(adapter).sendCommand(cmd);
    }

    @Test
    @DisplayName("sendCommand — throws when device not found")
    void sendCommand_deviceNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        BmsCommand cmd = new BmsCommand("PING", Map.of());
        assertThatThrownBy(() -> service.sendCommand("hcm", id, cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Device not found");
    }

    @Test
    @DisplayName("sendCommand — throws when tenant mismatch")
    void sendCommand_tenantMismatch() {
        when(repository.findById(device.getId())).thenReturn(Optional.of(device));

        BmsCommand cmd = new BmsCommand("PING", Map.of());
        assertThatThrownBy(() -> service.sendCommand("hn", device.getId(), cmd))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sendCommand — throws when no adapter registered")
    void sendCommand_noAdapter() {
        when(repository.findById(device.getId())).thenReturn(Optional.of(device));
        when(adapterRegistry.getAdapter(device)).thenReturn(Optional.empty());

        BmsCommand cmd = new BmsCommand("PING", Map.of());
        assertThatThrownBy(() -> service.sendCommand("hcm", device.getId(), cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No adapter");
    }

    @Test
    @DisplayName("sendCommand — propagates BmsAdapterException")
    void sendCommand_adapterException() {
        when(repository.findById(device.getId())).thenReturn(Optional.of(device));
        when(adapterRegistry.getAdapter(device)).thenReturn(Optional.of(adapter));
        doThrow(new BmsAdapterException("Connection lost")).when(adapter).sendCommand(any());

        BmsCommand cmd = new BmsCommand("SET_POINT", Map.of("value", 22));
        assertThatThrownBy(() -> service.sendCommand("hcm", device.getId(), cmd))
                .isInstanceOf(BmsAdapterException.class);
    }

    @Test
    @DisplayName("sendCommand — MQTT protocol dispatches via MqttPublisher")
    void sendCommand_mqttProtocol_dispatchesViaPublisher() {
        device.setProtocol(BmsProtocol.MQTT);
        BmsCommand cmd = new BmsCommand("SET_POINT", Map.of("value", 22));
        when(repository.findById(device.getId())).thenReturn(Optional.of(device));

        String commandId = service.sendCommand("hcm", device.getId(), cmd);

        assertThat(commandId).isNotBlank();
        verify(mqttPublisher).publishCommand(eq("hcm"), eq(device.getId().toString()), eq(cmd));
        verifyNoInteractions(adapterRegistry);
    }
}
