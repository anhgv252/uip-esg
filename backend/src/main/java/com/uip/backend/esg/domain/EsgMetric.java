package com.uip.backend.esg.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "clean_metrics", schema = "esg")
@Getter
@Setter
@NoArgsConstructor
public class EsgMetric {

    @EmbeddedId
    private EsgMetricId id;

    @Column(name = "source_id", nullable = false, length = 100)
    private String sourceId;

    @Column(name = "metric_type", nullable = false, length = 50)
    private String metricType;

    @Column(nullable = false)
    private Double value;

    @Column(length = 20)
    private String unit;

    @Column(name = "building_id", length = 100)
    private String buildingId;

    @Column(name = "district_code", length = 20)
    private String districtCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private Map<String, Object> rawPayload;
}
