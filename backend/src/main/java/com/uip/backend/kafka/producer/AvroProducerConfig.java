package com.uip.backend.kafka.producer;

import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

/**
 * Avro producer configuration for Kafka dual-publish (B1-3).
 *
 * <p>Provides an {@code avroKafkaTemplate} bean that serializes Avro {@link GenericRecord}
 * values using Apicurio Registry as the schema registry. This bean is used alongside the
 * existing JSON {@code KafkaTemplate} to achieve dual-publish (v1 JSON + v2 Avro).</p>
 *
 * <p>Avro schemas are auto-registered in Apicurio on first publish if
 * {@code apicurio.registry.auto-register=true} (default). Schema compatibility strategy
 * is BACKWARD — consumers of v1 remain unaffected when v2 adds optional fields.</p>
 */
// Only active when Apicurio registry URL is explicitly configured (not default localhost test URL)
@Configuration
@ConditionalOnProperty(name = "apicurio.registry.enabled", havingValue = "true", matchIfMissing = false)
public class AvroProducerConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AvroProducerConfig.class);

    @Value("${apicurio.registry.url:http://localhost:8087/apis/registry/v2}")
    private String registryUrl;

    @Value("${apicurio.registry.auto-register:true}")
    private boolean autoRegister;

    /**
     * Avro KafkaTemplate keyed by String, value is an Avro GenericRecord.
     * Used for v2 topics (dual-publish alongside JSON v1).
     */
    @Bean("avroKafkaTemplate")
    public KafkaTemplate<String, GenericRecord> avroKafkaTemplate(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());
        // Apicurio serde config keys — string constants for 2.x compatibility
        props.put("apicurio.registry.url", registryUrl);
        props.put("apicurio.registry.auto-register-artifact", String.valueOf(autoRegister));

        ProducerFactory<String, GenericRecord> pf = new DefaultKafkaProducerFactory<>(props);
        LOG.info("Avro KafkaTemplate initialized: registryUrl={} auto-register={}", registryUrl, autoRegister);
        return new KafkaTemplate<>(pf);
    }
}
