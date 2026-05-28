package com.uip.backend.bms.kafka;

import com.uip.backend.bms.api.dto.BmsReadingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BmsReadingKafkaProducer {

    private static final String TOPIC = "UIP.bms.reading.raw.v1";
    private static final String DLQ_TOPIC = "UIP.bms.reading.raw.v1.dlq";

    private final KafkaTemplate<String, BmsReadingEvent> kafkaTemplate;

    public void publish(BmsReadingEvent event) {
        String key = event.tenantId() + ":" + event.deviceId();
        try {
            kafkaTemplate.send(TOPIC, key, event);
            log.debug("BMS reading published: device={} type={}", event.deviceId(), event.readingType());
        } catch (Exception e) {
            log.error("Failed to publish BMS reading, sending to DLQ: device={}: {}", event.deviceId(), e.getMessage());
            try {
                kafkaTemplate.send(DLQ_TOPIC, key, event);
            } catch (Exception dlqEx) {
                log.error("DLQ publish also failed: {}", dlqEx.getMessage());
            }
        }
    }

    public void publishToDlq(BmsReadingEvent event, String reason) {
        String key = event.tenantId() + ":" + event.deviceId();
        log.warn("Publishing to DLQ: device={} reason={}", event.deviceId(), reason);
        try {
            kafkaTemplate.send(DLQ_TOPIC, key, event);
        } catch (Exception e) {
            log.error("DLQ publish failed: {}", e.getMessage());
        }
    }
}
