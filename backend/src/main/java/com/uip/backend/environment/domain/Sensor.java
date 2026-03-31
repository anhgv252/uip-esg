package com.uip.backend.environment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sensors", schema = "environment")
@Getter
@Setter
@NoArgsConstructor
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sensor_id", nullable = false, unique = true, length = 100)
    private String sensorId;

    @Column(name = "sensor_name", nullable = false)
    private String sensorName;

    @Column(name = "sensor_type", length = 50)
    private String sensorType;

    @Column(name = "district_code", length = 20)
    private String districtCode;

    private Double latitude;
    private Double longitude;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "installed_at", nullable = false, updatable = false)
    private Instant installedAt = Instant.now();
}
