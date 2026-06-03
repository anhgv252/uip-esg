package com.uip.backend.bms.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.bms.api.dto.BmsReadingEvent;
import com.uip.backend.kafka.producer.DualPublishKafkaProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Publishes BMS readings with dual-publish: JSON v1 + Avro v2 (B1-3).
 *
 * <p>v1 JSON consumers (existing) remain unaffected — BACKWARD compat guaranteed.</p>
 * <p>v2 Avro consumers use schema from Apicurio registry.</p>
 */
@Slf4j
@Component
public class BmsReadingKafkaProducer {

    static final String TOPIC_V1    = "UIP.bms.reading.raw.v1";
    static final String TOPIC_V2    = "UIP.bms.reading.raw.v2";
    private static final String DLQ_TOPIC = "UIP.bms.reading.raw.v1.dlq";
    private static final String AVRO_SCHEMA = "avro/BmsReadingEvent.avsc";

    private final KafkaTemplate<String, BmsReadingEvent> kafkaTemplate;
    private final Optional<DualPublishKafkaProducer>     dualPublish;   // optional — only active when Apicurio enabled
    private final ObjectMapper                           objectMapper;

    public BmsReadingKafkaProducer(KafkaTemplate<String, BmsReadingEvent> kafkaTemplate,
                                   Optional<DualPublishKafkaProducer> dualPublish,
                                   ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.dualPublish   = dualPublish;
        this.objectMapper  = objectMapper;
    }

    public void publish(BmsReadingEvent event) {
        String key = event.tenantId() + ":" + event.deviceId();
        try {
            // v1: JSON publish (unchanged — all existing consumers keep working)
            kafkaTemplate.send(TOPIC_V1, key, event);
            log.debug("BMS reading v1 published: device={} type={}", event.deviceId(), event.readingType());
        } catch (Exception e) {
            log.error("Failed to publish BMS reading v1, sending to DLQ: device={}: {}", event.deviceId(), e.getMessage());
            try {
                kafkaTemplate.send(DLQ_TOPIC, key, event);
            } catch (Exception dlqEx) {
                log.error("DLQ publish also failed: {}", dlqEx.getMessage());
            }
            return;
        }

        // v2: Avro dual-publish — only when DualPublishKafkaProducer bean is active (Apicurio enabled)
        dualPublish.ifPresent(dp -> {
            try {
                String jsonV1 = objectMapper.writeValueAsString(event);
                dp.publish(TOPIC_V1, TOPIC_V2, key, jsonV1, AVRO_SCHEMA, Map.of(
                        "tenantId",    event.tenantId(),
                        "deviceId",    event.deviceId().toString(),
                        "readingType", event.readingType(),
                        "value",       event.value(),
                        "unit",        event.unit() != null ? event.unit() : "",
                        "timestampMs", event.timestamp() != null ? event.timestamp().toEpochMilli() : System.currentTimeMillis(),
                        "source",      event.source() != null ? event.source() : ""
                ));
            } catch (JsonProcessingException e) {
                log.warn("Avro v2 dual-publish skipped: {}", e.getMessage());
            }
        });
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
