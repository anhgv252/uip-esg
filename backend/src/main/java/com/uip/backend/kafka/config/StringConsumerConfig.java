package com.uip.backend.kafka.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.Map;

/**
 * Kafka consumer factory for consumers that receive raw JSON String payloads.
 *
 * <p>The default {@code kafkaListenerContainerFactory} uses {@code JsonDeserializer}
 * with {@code spring.json.value.default.type=java.util.LinkedHashMap}, which causes
 * a {@code MessageConversionException} when a listener method declares a typed
 * parameter (e.g., {@code DistrictAggregationEvent}) or a plain {@code String}.</p>
 *
 * <p>Use {@code stringKafkaListenerContainerFactory} for listeners that need the
 * raw JSON string and perform their own deserialization.</p>
 */
@Configuration
public class StringConsumerConfig {

    @Bean("stringConsumerFactory")
    public ConsumerFactory<String, String> stringConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean("stringKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> stringKafkaListenerContainerFactory(
            ConsumerFactory<String, String> stringConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(stringConsumerFactory);
        return factory;
    }
}
