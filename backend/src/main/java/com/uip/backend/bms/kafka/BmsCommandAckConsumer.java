package com.uip.backend.bms.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.notification.service.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BmsCommandAckConsumer {

    private final SseEmitterRegistry sseEmitterRegistry;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "UIP.bms.command.ack.v1", groupId = "uip-backend-bms-ack")
    public void onCommandAck(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> ack = objectMapper.readValue(message, LinkedHashMap.class);
            String commandId = (String) ack.get("commandId");
            String status = (String) ack.get("status");

            log.info("BMS command ACK received: commandId={} status={}", commandId, status);

            sseEmitterRegistry.broadcast("bms-command-ack", Map.of(
                    "commandId", commandId,
                    "status", status,
                    "deviceId", ack.getOrDefault("deviceId", ""),
                    "timestamp", ack.getOrDefault("timestamp", java.time.Instant.now().toString())
            ));
        } catch (Exception e) {
            log.error("Failed to process BMS command ACK: {}", e.getMessage());
        }
    }
}
