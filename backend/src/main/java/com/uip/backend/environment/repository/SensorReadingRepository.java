package com.uip.backend.environment.repository;

import com.uip.backend.environment.domain.SensorReading;
import com.uip.backend.environment.domain.SensorReadingId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SensorReadingRepository extends JpaRepository<SensorReading, SensorReadingId> {

    @Query("""
        SELECT r FROM SensorReading r
        WHERE r.sensorId = :sensorId
          AND r.id.timestamp BETWEEN :from AND :to
        ORDER BY r.id.timestamp DESC
        """)
    List<SensorReading> findByRange(
            @Param("sensorId") String sensorId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    @Query("""
        SELECT r FROM SensorReading r
        WHERE r.sensorId = :sensorId
        ORDER BY r.id.timestamp DESC
        LIMIT 1
        """)
    Optional<SensorReading> findLatestBySensorId(@Param("sensorId") String sensorId);

    @Query("""
        SELECT r FROM SensorReading r
        WHERE r.id.timestamp = (
            SELECT MAX(r2.id.timestamp) FROM SensorReading r2
            WHERE r2.sensorId = r.sensorId
        )
        ORDER BY r.sensorId
        """)
    List<SensorReading> findLatestPerSensor();

    @Query("""
        SELECT r FROM SensorReading r
        WHERE r.id.timestamp BETWEEN :from AND :to
        ORDER BY r.id.timestamp DESC
        """)
    List<SensorReading> findAllInRange(
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
