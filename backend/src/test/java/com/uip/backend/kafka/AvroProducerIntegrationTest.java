package com.uip.backend.kafka;

import com.uip.backend.kafka.producer.DualPublishKafkaProducer;
import org.apache.avro.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies the 4 Avro schemas are valid, parseable, and BACKWARD compatible.
 *
 * <p>Tests run without a running Kafka or Apicurio instance — they validate the
 * schema files themselves and the DualPublishKafkaProducer schema loading logic.</p>
 */
@DisplayName("Avro Producer — schema validity + BACKWARD compatibility")
class AvroProducerIntegrationTest {

    // ─── Schema parsing ────────────────────────────────────────────────────────

    @ParameterizedTest(name = "Schema file parseable: {0}")
    @ValueSource(strings = {
            "avro/BmsReadingEvent.avsc",
            "avro/SensorReadingEvent.avsc",
            "avro/AlertDetectedEvent.avsc",
            "avro/HourlyRollupEvent.avsc"
    })
    @DisplayName("All 4 Avro schemas are valid and parseable")
    void schemaFile_parseable(String schemaPath) {
        assertThatCode(() -> {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(schemaPath)) {
                assertThat(is).as("Schema file not found: %s", schemaPath).isNotNull();
                Schema schema = new Schema.Parser().parse(is);
                assertThat(schema.getName()).isNotEmpty();
                assertThat(schema.getNamespace()).isEqualTo("com.uip.iot.avro");
                assertThat(schema.getFields()).isNotEmpty();
            }
        }).doesNotThrowAnyException();
    }

    // ─── Required fields ───────────────────────────────────────────────────────

    @Test
    @DisplayName("BmsReadingEvent: required fields tenantId, deviceId, readingType, value, timestampMs")
    void bmsReadingEvent_requiredFields() throws IOException {
        Schema schema = parseSchema("avro/BmsReadingEvent.avsc");
        assertHasField(schema, "tenantId",    Schema.Type.STRING);
        assertHasField(schema, "deviceId",    Schema.Type.STRING);
        assertHasField(schema, "readingType", Schema.Type.STRING);
        assertHasField(schema, "value",       Schema.Type.DOUBLE);
        assertHasField(schema, "timestampMs", Schema.Type.LONG);
    }

    @Test
    @DisplayName("SensorReadingEvent: required fields tenantId, sensorId, sensorType, value, timestampMs")
    void sensorReadingEvent_requiredFields() throws IOException {
        Schema schema = parseSchema("avro/SensorReadingEvent.avsc");
        assertHasField(schema, "tenantId",    Schema.Type.STRING);
        assertHasField(schema, "sensorId",    Schema.Type.STRING);
        assertHasField(schema, "sensorType",  Schema.Type.STRING);
        assertHasField(schema, "value",       Schema.Type.DOUBLE);
        assertHasField(schema, "timestampMs", Schema.Type.LONG);
    }

    @Test
    @DisplayName("AlertDetectedEvent: required fields tenantId, sensorId, module, severity, value, timestampMs")
    void alertDetectedEvent_requiredFields() throws IOException {
        Schema schema = parseSchema("avro/AlertDetectedEvent.avsc");
        assertHasField(schema, "tenantId",    Schema.Type.STRING);
        assertHasField(schema, "sensorId",    Schema.Type.STRING);
        assertHasField(schema, "module",      Schema.Type.STRING);
        assertHasField(schema, "severity",    Schema.Type.STRING);
        assertHasField(schema, "value",       Schema.Type.DOUBLE);
        assertHasField(schema, "timestampMs", Schema.Type.LONG);
    }

    @Test
    @DisplayName("HourlyRollupEvent: required fields tenantId, buildingId, metricType, hourEpochMs, sum, count")
    void hourlyRollupEvent_requiredFields() throws IOException {
        Schema schema = parseSchema("avro/HourlyRollupEvent.avsc");
        assertHasField(schema, "tenantId",    Schema.Type.STRING);
        assertHasField(schema, "buildingId",  Schema.Type.STRING);
        assertHasField(schema, "metricType",  Schema.Type.STRING);
        assertHasField(schema, "hourEpochMs", Schema.Type.LONG);
        assertHasField(schema, "sum",         Schema.Type.DOUBLE);
        assertHasField(schema, "count",       Schema.Type.LONG);
    }

    // ─── BACKWARD compat — optional fields ────────────────────────────────────

    @ParameterizedTest(name = "BACKWARD compat: nullable optional fields in {0}")
    @ValueSource(strings = {
            "avro/BmsReadingEvent.avsc",
            "avro/SensorReadingEvent.avsc",
            "avro/AlertDetectedEvent.avsc",
            "avro/HourlyRollupEvent.avsc"
    })
    @DisplayName("Optional fields use union [null, string] — BACKWARD compatible")
    void backwardCompat_optionalFieldsAreNullable(String schemaPath) throws IOException {
        Schema schema = parseSchema(schemaPath);
        // Every optional field must have a default value (null) for BACKWARD compat
        schema.getFields().stream()
                .filter(f -> f.schema().getType() == Schema.Type.UNION)
                .forEach(f -> assertThat(f.hasDefaultValue())
                        .as("Optional field '%s' in %s must have a default value for BACKWARD compat",
                                f.name(), schemaPath)
                        .isTrue());
    }

    // ─── DualPublishKafkaProducer schema loading ───────────────────────────────

    @Test
    @DisplayName("DualPublishKafkaProducer: loadSchema caches schemas on second call")
    void dualPublish_schemaLoadingCaches() throws IOException {
        // Minimal test to verify DualPublishKafkaProducer.loadSchema works without Kafka beans
        DualPublishKafkaProducerSchemaOnlyHarness harness = new DualPublishKafkaProducerSchemaOnlyHarness();

        Schema first  = harness.loadSchema("avro/BmsReadingEvent.avsc");
        Schema second = harness.loadSchema("avro/BmsReadingEvent.avsc");

        assertThat(first).isSameAs(second); // cached — same instance
        assertThat(first.getName()).isEqualTo("BmsReadingEvent");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Schema parseSchema(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(is).as("Schema resource not found: " + path).isNotNull();
            return new Schema.Parser().parse(is);
        }
    }

    private void assertHasField(Schema schema, String fieldName, Schema.Type expectedType) {
        Schema.Field field = schema.getField(fieldName);
        assertThat(field).as("Field '%s' missing from schema '%s'", fieldName, schema.getName()).isNotNull();

        Schema fieldSchema = field.schema();
        // Handle union types (e.g., ["null", "string"])
        if (fieldSchema.getType() == Schema.Type.UNION) {
            boolean hasType = fieldSchema.getTypes().stream()
                    .anyMatch(s -> s.getType() == expectedType);
            assertThat(hasType).as("Field '%s' does not contain type %s", fieldName, expectedType).isTrue();
        } else {
            assertThat(fieldSchema.getType())
                    .as("Field '%s' type mismatch", fieldName)
                    .isEqualTo(expectedType);
        }
    }

    /**
     * Test harness that exposes the schema loading logic without requiring Spring/Kafka wiring.
     */
    static class DualPublishKafkaProducerSchemaOnlyHarness extends DualPublishKafkaProducer {

        DualPublishKafkaProducerSchemaOnlyHarness() {
            super(null, null); // Kafka templates not used in schema-only tests
        }

        @Override
        public Schema loadSchema(String path) throws IOException {
            return super.loadSchema(path);
        }
    }
}
