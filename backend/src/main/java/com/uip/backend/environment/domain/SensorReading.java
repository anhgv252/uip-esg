package com.uip.backend.environment.domain;

import com.uip.backend.tenant.domain.TenantAware;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "sensor_readings", schema = "environment")
@EntityListeners(com.uip.backend.tenant.hibernate.TenantEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class SensorReading implements TenantAware {

    @EmbeddedId
    private SensorReadingId id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "sensor_id", nullable = false, length = 100)
    private String sensorId;

    private Double aqi;
    private Double pm25;
    private Double pm10;
    private Double o3;
    private Double no2;
    private Double so2;
    private Double co;
    private Double temperature;
    private Double humidity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private Map<String, Object> rawPayload;
}
