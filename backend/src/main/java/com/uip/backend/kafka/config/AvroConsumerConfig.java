package com.uip.backend.kafka.config;

import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.Map;

/**
 * Avro consumer configuration for Kafka dual-format support (B2-6).
 *
 * <p>Provides an {@code avroKafkaListenerContainerFactory} bean that deserializes
 * Avro v2 messages using Apicurio Registry. Use this factory for consumers listening
 * to v2 topics (e.g., {@code UIP.bms.reading.raw.v2}).</p>
 *
 * <p>Existing consumers using {@code kafkaListenerContainerFactory} (JSON/String)
 * continue to work unchanged — this is purely additive.</p>
 *
 * <p>Migration plan: once all producers have migrated to Avro v2 and v1 topics
 * are deprecated (Phase 3), existing JSON consumers can switch to this factory.</p>
 */
@Configuration
@ConditionalOnProperty(name = "apicurio.registry.enabled", havingValue = "true", matchIfMissing = false)
public class AvroConsumerConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AvroConsumerConfig.class);

    @Value("${apicurio.registry.url:http://localhost:8087/apis/registry/v2}")
    private String registryUrl;

    @Bean("avroConsumerFactory")
    public ConsumerFactory<String, GenericRecord> avroConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, AvroKafkaDeserializer.class.getName());
        props.put("apicurio.registry.url", registryUrl);
        props.put("apicurio.registry.use-specific-avro-reader", "false"); // use GenericRecord

        LOG.info("Avro ConsumerFactory initialized: registryUrl={}", registryUrl);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Kafka listener container factory for Avro v2 topics.
     *
     * <p>Usage in consumers:
     * <pre>{@code
     * @KafkaListener(
     *     topics = "UIP.bms.reading.raw.v2",
     *     containerFactory = "avroKafkaListenerContainerFactory"
     * )
     * public void consumeAvro(GenericRecord record) { ... }
     * }</pre>
     * </p>
     */
    @Bean("avroKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, GenericRecord> avroKafkaListenerContainerFactory(
            ConsumerFactory<String, GenericRecord> avroConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, GenericRecord> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(avroConsumerFactory);
        return factory;
    }
}
