package com.uip.backend.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * M5-4 T02: Simple Kafka string producer service for billing events.
 * Sends JSON-serialized events to Kafka topics using the default string KafkaTemplate.
 * Used by InvoiceGenerationService to publish UIP.billing.invoice.generated.v1 events.
 */
@Service
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducerService(@Qualifier("kafkaTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Send a JSON payload to a Kafka topic with the given key.
     *
     * @param topic   Kafka topic name
     * @param key     Message key (e.g., tenantId)
     * @param payload JSON string payload
     */
    public void send(String topic, String key, String payload) {
        log.debug("Publishing to topic={} key={} payloadLength={}", topic, key, payload.length());
        kafkaTemplate.send(topic, key, payload);
    }
}
