package com.uip.backend.environment.repository;

import com.uip.backend.environment.domain.Sensor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SensorRepository extends JpaRepository<Sensor, UUID> {

    List<Sensor> findByActiveTrueOrderBySensorNameAsc();

    Optional<Sensor> findBySensorId(String sensorId);

    @Query("SELECT s FROM Sensor s WHERE s.active = true AND s.districtCode = :districtCode")
    List<Sensor> findActiveByDistrict(String districtCode);

    @Modifying
    @Query("UPDATE Sensor s SET s.lastSeenAt = :lastSeenAt WHERE s.sensorId = :sensorId")
    void updateLastSeen(String sensorId, Instant lastSeenAt);
}
