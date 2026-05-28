package com.uip.backend.bms.service;

import com.uip.backend.bms.adapter.BmsAdapterException;
import com.uip.backend.bms.adapter.BmsAdapterRegistry;
import com.uip.backend.bms.adapter.BmsProtocolAdapter;
import com.uip.backend.bms.api.dto.BmsCommand;
import com.uip.backend.bms.domain.BmsDevice;
import com.uip.backend.bms.domain.BmsProtocol;
import com.uip.backend.bms.mqtt.MqttPublisher;
import com.uip.backend.bms.repository.BmsDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BmsDeviceCommandService {

    private final BmsDeviceRepository bmsDeviceRepository;
    private final BmsAdapterRegistry adapterRegistry;
    private final MqttPublisher mqttPublisher;

    public String sendCommand(String tenantId, UUID deviceId, BmsCommand command) {
        BmsDevice device = bmsDeviceRepository.findById(deviceId)
                .filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));

        String commandId = UUID.randomUUID().toString();

        if (device.getProtocol() == BmsProtocol.MQTT) {
            mqttPublisher.publishCommand(tenantId, device.getId().toString(), command);
            log.info("MQTT command dispatched: commandId={} device={}", commandId, deviceId);
        } else {
            BmsProtocolAdapter adapter = adapterRegistry.getAdapter(device)
                    .orElseThrow(() -> new IllegalStateException("No adapter for device: " + deviceId));
            try {
                adapter.sendCommand(command);
                log.info("Adapter command sent: commandId={} device={} type={}", commandId, deviceId, command.commandType());
            } catch (BmsAdapterException e) {
                log.error("Command failed: commandId={} device={}: {}", commandId, deviceId, e.getMessage());
                throw e;
            }
        }

        return commandId;
    }
}
