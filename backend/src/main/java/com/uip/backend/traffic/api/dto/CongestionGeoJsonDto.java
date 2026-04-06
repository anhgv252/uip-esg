package com.uip.backend.traffic.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * GeoJSON FeatureCollection for congestion map visualization
 * Format: https://geojson.org/
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CongestionGeoJsonDto {
    @Builder.Default
    private String type = "FeatureCollection";
    
    @JsonProperty("features")
    private List<GeoJsonFeature> features;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoJsonFeature {
        private String type = "Feature";
        private GeoJsonProperties properties;
        private GeoJsonGeometry geometry;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoJsonProperties {
        private String intersectionId;
        private Integer vehicleCount;
        private String congestionLevel; // LOW, MODERATE, HIGH, SEVERE
        private String description;
        private Double avgSpeed; // km/h
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeoJsonGeometry {
        private String type = "Point";
        
        @JsonProperty("coordinates")
        private double[] coordinates; // [longitude, latitude]
    }
}
