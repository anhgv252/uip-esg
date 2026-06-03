package com.uip.backend.kafka.producer;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic dual-publish helper for Kafka.
 *
 * <p>For each logical event, publishes to:</p>
 * <ol>
 *   <li>JSON v1 topic — uses existing String KafkaTemplate (backward compat for all consumers)</li>
 *   <li>Avro v2 topic — uses Apicurio-backed Avro KafkaTemplate (new consumers)</li>
 * </ol>
 *
 * <p>Schema compatibility: BACKWARD. v2 schemas add only {@code ["null", "type"]} optional fields
 * so v1 consumers are never broken.</p>
 *
 * <p>v1 JSON topics remain active until Phase 3 deprecation (see kafka-avro-schema-versioning.md).</p>
 */
@Component
@ConditionalOnBean(name = "avroKafkaTemplate")
public class DualPublishKafkaProducer {

    private static final Logger LOG = LoggerFactory.getLogger(DualPublishKafkaProducer.class);

    private final KafkaTemplate<String, String>        jsonTemplate;
    private final KafkaTemplate<String, GenericRecord> avroTemplate;

    // Cache parsed Avro schemas — loaded once per schema file
    private final Map<String, Schema> schemaCache = new ConcurrentHashMap<>();

    public DualPublishKafkaProducer(
            @Qualifier("kafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            @Qualifier("avroKafkaTemplate") KafkaTemplate<String, GenericRecord> avroTemplate) {
        this.jsonTemplate = kafkaTemplate;
        this.avroTemplate = avroTemplate;
    }

    /**
     * Publish JSON payload to v1 topic and an Avro GenericRecord to v2 topic.
     *
     * @param jsonTopicV1   JSON topic (existing consumers)
     * @param avroTopicV2   Avro v2 topic (new consumers)
     * @param key           Kafka message key
     * @param jsonPayload   Serialized JSON string for v1
     * @param avroSchemaPath classpath path to .avsc schema file, e.g. "avro/BmsReadingEvent.avsc"
     * @param fields         field values to populate the Avro GenericRecord
     */
    public void publish(String jsonTopicV1, String avroTopicV2, String key,
                        String jsonPayload, String avroSchemaPath, Map<String, Object> fields) {
        // v1 JSON publish
        try {
            jsonTemplate.send(jsonTopicV1, key, jsonPayload);
            LOG.debug("Dual-publish v1 JSON: topic={} key={}", jsonTopicV1, key);
        } catch (Exception e) {
            LOG.error("Dual-publish v1 JSON failed: topic={} key={}: {}", jsonTopicV1, key, e.getMessage(), e);
        }

        // v2 Avro publish
        try {
            Schema schema = loadSchema(avroSchemaPath);
            GenericRecord record = buildRecord(schema, fields);
            avroTemplate.send(avroTopicV2, key, record);
            LOG.debug("Dual-publish v2 Avro: topic={} key={}", avroTopicV2, key);
        } catch (Exception e) {
            // Avro v2 failure is non-fatal — v1 consumers are unaffected
            LOG.warn("Dual-publish v2 Avro failed (non-fatal): topic={} key={}: {}",
                    avroTopicV2, key, e.getMessage());
        }
    }

    /** Load and cache Avro schema from classpath. */
    public Schema loadSchema(String resourcePath) throws IOException {
        return schemaCache.computeIfAbsent(resourcePath, path -> {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
                if (is == null) throw new IllegalArgumentException("Avro schema not found: " + path);
                return new Schema.Parser().parse(is);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load Avro schema: " + path, e);
            }
        });
    }

    private static GenericRecord buildRecord(Schema schema, Map<String, Object> fields) {
        GenericRecord record = new GenericData.Record(schema);
        for (Schema.Field field : schema.getFields()) {
            Object value = fields.get(field.name());
            if (value != null) {
                record.put(field.name(), value);
            } else if (field.hasDefaultValue()) {
                record.put(field.name(), GenericData.get().getDefaultValue(field));
            }
        }
        return record;
    }
}
