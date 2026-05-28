package com.uip.backend.bms.api.dto;

import com.uip.backend.bms.domain.BmsDevice;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record BmsDeviceResponse(
        UUID id,
        String tenantId,
        String deviceName,
        String protocol,
        String host,
        Integer port,
        Integer unitId,
        Integer deviceId,
        Integer pollInterval,
        Instant lastSeen,
        String status,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public static BmsDeviceResponse from(BmsDevice device) {
        return new BmsDeviceResponse(
                device.getId(),
                device.getTenantId(),
                device.getDeviceName(),
                device.getProtocol().name(),
                device.getHost(),
                device.getPort(),
                device.getUnitId(),
                device.getDeviceId(),
                device.getPollInterval(),
                device.getLastSeen(),
                device.getStatus(),
                device.getMetadata(),
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }
}
