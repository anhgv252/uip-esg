package com.uip.backend.workflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("VariableMapper")
class VariableMapperTest {

    private VariableMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new VariableMapper(new ObjectMapper());
    }

    @Test
    @DisplayName("Static value — returns fixed string")
    void staticValue() {
        String json = "{\"scenarioKey\":{\"static\":\"aiC01_aqiCitizenAlert\"}}";
        Map<String, Object> result = mapper.map(json, Map.of());
        assertThat(result).containsEntry("scenarioKey", "aiC01_aqiCitizenAlert");
    }

    @Test
    @DisplayName("Source from payload — extracts value")
    void sourceFromPayload() {
        String json = "{\"sensorId\":{\"source\":\"payload.sensorId\",\"default\":\"UNKNOWN\"}}";
        Map<String, Object> result = mapper.map(json, Map.of("sensorId", "AQI-001"));
        assertThat(result).containsEntry("sensorId", "AQI-001");
    }

    @Test
    @DisplayName("Source without payload prefix — extracts from root")
    void sourceWithoutPrefix() {
        String json = "{\"sensorId\":{\"source\":\"sensorId\",\"default\":\"UNKNOWN\"}}";
        Map<String, Object> result = mapper.map(json, Map.of("sensorId", "AQI-002"));
        assertThat(result).containsEntry("sensorId", "AQI-002");
    }

    @Test
    @DisplayName("Default value — used when source missing")
    void defaultValue() {
        String json = "{\"sensorId\":{\"source\":\"payload.sensorId\",\"default\":\"UNKNOWN\"}}";
        Map<String, Object> result = mapper.map(json, Map.of());
        assertThat(result).containsEntry("sensorId", "UNKNOWN");
    }

    @Test
    @DisplayName("NOW() — returns ISO timestamp")
    void nowFunction() {
        String json = "{\"measuredAt\":{\"source\":\"payload.missing\",\"default\":\"NOW()\"}}";
        Map<String, Object> result = mapper.map(json, Map.of());
        assertThat(result.get("measuredAt").toString()).contains("20"); // contains year
    }

    @Test
    @DisplayName("UUID() — returns valid UUID string")
    void uuidFunction() {
        String json = "{\"requestId\":{\"source\":\"payload.requestId\",\"default\":\"UUID()\"}}";
        Map<String, Object> result = mapper.map(json, Map.of());
        assertThat(result.get("requestId").toString()).hasSize(36);
        assertThat(UUID.fromString(result.get("requestId").toString())).isNotNull();
    }

    @Test
    @DisplayName("Complex multi-field mapping")
    void complexMultiField() {
        String json = "{\"scenarioKey\":{\"static\":\"aiC01_aqiCitizenAlert\"},\"sensorId\":{\"source\":\"payload.sensorId\",\"default\":\"UNKNOWN\"},\"aqiValue\":{\"source\":\"payload.value\"}}";
        Map<String, Object> payload = Map.of("sensorId", "AQI-001", "value", 175.0);
        Map<String, Object> result = mapper.map(json, payload);
        assertThat(result).containsEntry("scenarioKey", "aiC01_aqiCitizenAlert");
        assertThat(result).containsEntry("sensorId", "AQI-001");
        assertThat(result).containsEntry("aqiValue", 175.0);
    }

    @Test
    @DisplayName("Invalid JSON → returns empty map")
    void invalidJson_returnsEmpty() {
        Map<String, Object> result = mapper.map("not json", Map.of());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Null source with null default → excluded from result")
    void nullSourceNullDefault_excluded() {
        String json = "{\"optional\":{\"source\":\"payload.missing\"}}";
        Map<String, Object> result = mapper.map(json, Map.of("payload", Map.of()));
        assertThat(result).doesNotContainKey("optional");
    }
}
