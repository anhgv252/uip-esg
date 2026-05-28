package com.uip.backend.bms.adapter;

import com.uip.backend.bms.domain.BmsDevice;
import com.uip.backend.bms.domain.BmsProtocol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class BmsAdapterRegistry {

    private final Map<String, BmsProtocolAdapter> adapters = new ConcurrentHashMap<>();

    public BmsProtocolAdapter createAdapter(BmsDevice device) {
        String key = adapterKey(device);
        BmsProtocolAdapter adapter = switch (device.getProtocol()) {
            case MODBUS_TCP -> new ModbusTcpAdapter(
                    device.getMetadata() != null
                            ? (Map<String, String>) (Map) device.getMetadata().get("registerMap")
                            : Map.of());
            case BACNET_IP -> new BacnetIpAdapter(
                    device.getMetadata() != null
                            ? (Map<String, String>) (Map) device.getMetadata().get("propertyMap")
                            : Map.of());
            case MANUAL, MQTT -> null;
        };

        if (adapter != null) {
            BmsDeviceConfig config = new BmsDeviceConfig(
                    device.getHost(),
                    device.getPort() != null ? device.getPort() : 502,
                    device.getUnitId() != null ? device.getUnitId() : 1,
                    device.getDeviceId() != null ? device.getDeviceId() : 0,
                    device.getPollInterval() != null ? device.getPollInterval() : 5000,
                    null
            );
            try {
                adapter.connect(config);
                adapters.put(key, adapter);
                log.info("Adapter registered: {} → {}", key, device.getProtocol());
            } catch (BmsAdapterException e) {
                log.error("Adapter connect failed: {}: {}", key, e.getMessage());
            }
        }
        return adapter;
    }

    public Optional<BmsProtocolAdapter> getAdapter(BmsDevice device) {
        return Optional.ofNullable(adapters.get(adapterKey(device)));
    }

    public void removeAdapter(BmsDevice device) {
        String key = adapterKey(device);
        BmsProtocolAdapter adapter = adapters.remove(key);
        if (adapter != null) {
            adapter.disconnect();
            log.info("Adapter removed: {}", key);
        }
    }

    public List<BmsProtocolAdapter> getAllAdapters() {
        return List.copyOf(adapters.values());
    }

    public void disconnectAll() {
        adapters.forEach((key, adapter) -> {
            try {
                adapter.disconnect();
            } catch (Exception e) {
                log.warn("Error disconnecting adapter {}: {}", key, e.getMessage());
            }
        });
        adapters.clear();
    }

    private String adapterKey(BmsDevice device) {
        return device.getTenantId() + ":" + device.getId();
    }
}
