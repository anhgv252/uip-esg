package com.uip.backend.bms.service;

import com.uip.backend.bms.api.dto.BmsDeviceRequest;
import com.uip.backend.bms.api.dto.BmsDeviceResponse;
import com.uip.backend.bms.domain.BmsDevice;
import com.uip.backend.bms.domain.BmsProtocol;
import com.uip.backend.bms.repository.BmsDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BmsDeviceService — unit")
class BmsDeviceServiceTest {

    @Mock
    private BmsDeviceRepository repository;

    private BmsDeviceService service;

    @BeforeEach
    void setUp() {
        service = new BmsDeviceService(repository);
    }

    @Test
    @DisplayName("listDevices — returns tenant-scoped devices")
    void listDevices_returnsTenantDevices() {
        BmsDevice device = new BmsDevice();
        device.setId(UUID.randomUUID());
        device.setTenantId("hcm");
        device.setDeviceName("AHU-01");
        device.setProtocol(BmsProtocol.MODBUS_TCP);
        when(repository.findByTenantId("hcm")).thenReturn(List.of(device));

        List<BmsDeviceResponse> result = service.listDevices("hcm");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).deviceName()).isEqualTo("AHU-01");
    }

    @Test
    @DisplayName("upsertDevice — creates new device when not found")
    void upsertDevice_createsNewDevice() {
        when(repository.findByTenantIdAndDeviceName("hcm", "AHU-01")).thenReturn(Optional.empty());
        when(repository.save(any(BmsDevice.class))).thenAnswer(inv -> {
            BmsDevice d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        BmsDeviceRequest request = new BmsDeviceRequest("AHU-01", "MODBUS_TCP",
                "192.168.1.10", 502, 1, null, 5000, null);

        BmsDevice result = service.upsertDevice("hcm", request);

        assertThat(result.getDeviceName()).isEqualTo("AHU-01");
        assertThat(result.getProtocol()).isEqualTo(BmsProtocol.MODBUS_TCP);
        verify(repository).save(any(BmsDevice.class));
    }

    @Test
    @DisplayName("upsertDevice — updates existing device (idempotent)")
    void upsertDevice_updatesExisting() {
        BmsDevice existing = new BmsDevice();
        existing.setId(UUID.randomUUID());
        existing.setTenantId("hcm");
        existing.setDeviceName("AHU-01");
        existing.setProtocol(BmsProtocol.MODBUS_TCP);
        existing.setHost("192.168.1.10");

        when(repository.findByTenantIdAndDeviceName("hcm", "AHU-01")).thenReturn(Optional.of(existing));
        when(repository.save(any(BmsDevice.class))).thenReturn(existing);

        BmsDeviceRequest request = new BmsDeviceRequest("AHU-01", "BACNET_IP",
                "192.168.1.20", 47808, null, 1001, 3000, null);

        BmsDevice result = service.upsertDevice("hcm", request);

        assertThat(result.getProtocol()).isEqualTo(BmsProtocol.BACNET_IP);
        assertThat(result.getHost()).isEqualTo("192.168.1.20");
    }

    @Test
    @DisplayName("deleteDevice — throws when device not found")
    void deleteDevice_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteDevice("hcm", id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getDevice — returns empty when tenant mismatch")
    void getDevice_returnsEmptyOnTenantMismatch() {
        BmsDevice device = new BmsDevice();
        device.setTenantId("hn");
        UUID id = UUID.randomUUID();
        device.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(device));

        Optional<BmsDeviceResponse> result = service.getDevice("hcm", id);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("registerDiscoveredDevice — creates new when not found")
    void registerDiscoveredDevice_createsNew() {
        when(repository.findByTenantIdAndDeviceName("hcm", "BACNET-1001")).thenReturn(Optional.empty());
        when(repository.save(any(BmsDevice.class))).thenAnswer(inv -> {
            BmsDevice d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        BmsDevice result = service.registerDiscoveredDevice("hcm", "BACNET-1001",
                BmsProtocol.BACNET_IP, "192.168.1.50", 47808, 1001, Map.of("vendorId", 999));

        assertThat(result.getProtocol()).isEqualTo(BmsProtocol.BACNET_IP);
        assertThat(result.getStatus()).isEqualTo("ONLINE");
        assertThat(result.getLastSeen()).isNotNull();
        verify(repository).save(any(BmsDevice.class));
    }

    @Test
    @DisplayName("registerDiscoveredDevice — updates existing device")
    void registerDiscoveredDevice_updatesExisting() {
        BmsDevice existing = new BmsDevice();
        existing.setId(UUID.randomUUID());
        existing.setTenantId("hcm");
        existing.setDeviceName("BACNET-1001");
        existing.setProtocol(BmsProtocol.BACNET_IP);
        existing.setStatus("OFFLINE");

        when(repository.findByTenantIdAndDeviceName("hcm", "BACNET-1001")).thenReturn(Optional.of(existing));
        when(repository.save(any(BmsDevice.class))).thenReturn(existing);

        BmsDevice result = service.registerDiscoveredDevice("hcm", "BACNET-1001",
                BmsProtocol.BACNET_IP, "192.168.1.50", 47808, 1001, null);

        assertThat(result.getStatus()).isEqualTo("ONLINE");
    }
}
