package com.uip.backend.environment.repository;

import com.uip.backend.environment.domain.SensorReading;
import com.uip.backend.environment.domain.SensorReadingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * M5-4 T06: Air quality reading queries for LOTUS VN and ISO 37120 scoring.
 * Provides PM2.5 aggregate queries by building or tenant over a time period.
 */
@Repository
public interface AirQualityReadingRepository extends JpaRepository<SensorReading, SensorReadingId> {

    /**
     * Average PM2.5 for sensors whose sensorId starts with the given buildingId prefix,
     * within the specified time range.
     * Used by LotusVnScoringService for IEQ category scoring.
     */
    @Query("SELECT AVG(r.pm25) FROM SensorReading r " +
           "WHERE r.sensorId LIKE CONCAT(:buildingId, '%') " +
           "AND r.id.timestamp BETWEEN :from AND :to " +
           "AND r.pm25 IS NOT NULL")
    Double findAveragePm25ByBuildingAndPeriod(
            @Param("buildingId") String buildingId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Average PM2.5 for all sensors of a tenant over a time period.
     * Used by Iso37120IndicatorEngine for ENV-2 air quality indicator.
     */
    @Query("SELECT AVG(r.pm25) FROM SensorReading r " +
           "WHERE r.tenantId = :tenantId " +
           "AND r.id.timestamp BETWEEN :start AND :end " +
           "AND r.pm25 IS NOT NULL")
    Double findAveragePm25ByPeriod(
            @Param("tenantId") String tenantId,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
