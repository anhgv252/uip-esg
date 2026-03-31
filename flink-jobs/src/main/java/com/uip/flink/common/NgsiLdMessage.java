package com.uip.flink.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * NGSI-LD envelope as produced by Redpanda Connect normalisation pipeline.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NgsiLdMessage implements Serializable {

    private String id;                              // urn:ngsi-ld:Device:DEVICE_ID
    private String type;

    @JsonProperty("deviceId")
    private NgsiLdProperty<String> deviceId;

    @JsonProperty("observedAt")
    private NgsiLdProperty<Long> observedAt;        // epoch millis

    @JsonProperty("sensorType")
    private NgsiLdProperty<String> sensorType;

    @JsonProperty("measurements")
    private NgsiLdProperty<Map<String, Double>> measurements;

    @JsonProperty("_meta")
    private Meta meta;

    public String getDeviceIdValue() {
        return deviceId != null ? deviceId.getValue() : null;
    }

    public long getObservedAtMillis() {
        return observedAt != null && observedAt.getValue() != null ? observedAt.getValue() : System.currentTimeMillis();
    }

    public Map<String, Double> getMeasurementValues() {
        return measurements != null ? measurements.getValue() : Map.of();
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NgsiLdProperty<T> implements Serializable {
        private String type = "Property";
        private T value;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta implements Serializable {
        private String source;
        private String sensorType;
    }
}
