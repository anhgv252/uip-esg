package com.uip.backend.bms.adapter;

import com.uip.backend.bms.domain.BmsDevice;
import com.uip.backend.bms.domain.BmsProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BmsAdapterRegistry — unit")
class BmsAdapterRegistryTest {

    private BmsAdapterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BmsAdapterRegistry();
    }

    @Test
    @DisplayName("createAdapter — returns null for MANUAL protocol")
    void createAdapter_manualProtocol_returnsNull() {
        BmsDevice device = buildDevice(BmsProtocol.MANUAL);
        BmsProtocolAdapter result = registry.createAdapter(device);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getAdapter — returns empty when not registered")
    void getAdapter_notRegistered_returnsEmpty() {
        BmsDevice device = buildDevice(BmsProtocol.MODBUS_TCP);
        assertThat(registry.getAdapter(device)).isEmpty();
    }

    @Test
    @DisplayName("getAllAdapters — returns empty list initially")
    void getAllAdapters_initiallyEmpty() {
        assertThat(registry.getAllAdapters()).isEmpty();
    }

    @Test
    @DisplayName("removeAdapter — no-op when adapter not found")
    void removeAdapter_notFound_noOp() {
        BmsDevice device = buildDevice(BmsProtocol.MODBUS_TCP);
        registry.removeAdapter(device);
        // no exception thrown
    }

    @Test
    @DisplayName("disconnectAll — clears all adapters")
    void disconnectAll_clearsAdapters() {
        registry.disconnectAll();
        assertThat(registry.getAllAdapters()).isEmpty();
    }

    private BmsDevice buildDevice(BmsProtocol protocol) {
        BmsDevice device = new BmsDevice();
        device.setId(UUID.randomUUID());
        device.setTenantId("hcm");
        device.setDeviceName("TEST-01");
        device.setProtocol(protocol);
        device.setHost("192.168.1.1");
        device.setPort(502);
        return device;
    }
}
