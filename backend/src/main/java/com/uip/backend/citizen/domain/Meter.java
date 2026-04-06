package com.uip.backend.citizen.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "meters", schema = "citizens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meter {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "citizen_id", nullable = false)
    private UUID citizenId;

    @Column(name = "meter_code", nullable = false, unique = true, length = 50)
    private String meterCode;

    @Column(name = "meter_type", nullable = false, length = 20)
    private String meterType; // ELECTRICITY, WATER

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
