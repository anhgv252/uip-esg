package com.uip.backend.bms.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.bms.repository.BmsDeviceRepository;
import com.uip.backend.notification.service.SseEmitterRegistry;
import com.uip.backend.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * S7-B07 — BMS Command ACK Kafka Consumer.
 *
 * <p>Listens to {@code UIP.bms.command.ack.v1}, updates device status in DB
 * (ONLINE / OFFLINE / ERROR), broadcasts via SSE for real-time UI refresh.</p>
 *
 * <p>TenantContext is set from the ACK payload tenantId before DB operations.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BmsCommandAckConsumer {

    private final SseEmitterRegistry sseEmitterRegistry;
    private final BmsDeviceRepository bmsDeviceRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "UIP.bms.command.ack.v1", groupId = "uip-backend-bms-ack")
    @Transactional
    public void onCommandAck(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> ack = objectMapper.readValue(message, LinkedHashMap.class);

            String commandId = getString(ack, "commandId");
            String ackStatus = getString(ack, "status");   // ACKNOWLEDGED, ONLINE, OFFLINE, ERROR
            String deviceIdStr = getString(ack, "deviceId");
            String tenantId = getString(ack, "tenantId");

            log.info("BMS command ACK: commandId={} status={} device={} tenant={}",
                    commandId, ackStatus, deviceIdStr, tenantId);

            // Update device status in DB with RLS enforcement
            if (deviceIdStr != null && tenantId != null) {
                updateDeviceStatus(deviceIdStr, tenantId, ackStatus);
            }

            // Broadcast to frontend via SSE for real-time device status refresh
            sseEmitterRegistry.broadcast("bms-command-ack", Map.of(
                    "commandId",  commandId != null ? commandId : "",
                    "status",     ackStatus != null ? ackStatus : "UNKNOWN",
                    "deviceId",   deviceIdStr != null ? deviceIdStr : "",
                    "timestamp",  ack.getOrDefault("timestamp", Instant.now().toString())
            ));

        } catch (Exception e) {
            log.error("Failed to process BMS command ACK: {}", e.getMessage(), e);
        }
    }

    private void updateDeviceStatus(String deviceIdStr, String tenantId, String ackStatus) {
        TenantContext.setCurrentTenant(tenantId);
        try {
            UUID deviceUuid;
            try {
                deviceUuid = UUID.fromString(deviceIdStr);
            } catch (IllegalArgumentException e) {
                log.warn("BMS ACK: deviceId is not a valid UUID: {}", deviceIdStr);
                return;
            }

            bmsDeviceRepository.findById(deviceUuid).ifPresentOrElse(device -> {
                String newStatus = mapAckStatus(ackStatus);
                device.setStatus(newStatus);
                device.setLastSeen(Instant.now());
                bmsDeviceRepository.save(device);
                log.debug("BMS device status updated: id={} status={}", deviceUuid, newStatus);
            }, () -> log.warn("BMS ACK: device not found in DB: {}", deviceUuid));

        } finally {
            TenantContext.clear();
        }
    }

    /** Map ACK status from Flink/EMQX convention to BmsDevice status values. */
    static String mapAckStatus(String ackStatus) {
        if (ackStatus == null) return "UNKNOWN";
        return switch (ackStatus.toUpperCase()) {
            case "ACKNOWLEDGED", "SUCCESS", "ONLINE" -> "ONLINE";
            case "OFFLINE", "DISCONNECTED"           -> "OFFLINE";
            case "ERROR", "FAILED"                   -> "ERROR";
            default                                  -> "UNKNOWN";
        };
    }

    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
