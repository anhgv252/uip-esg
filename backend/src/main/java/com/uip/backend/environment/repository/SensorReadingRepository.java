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

    /**
     * Lấy latest reading cho mỗi sensor bằng LATERAL JOIN — tránh DISTINCT ON sort toàn bảng.
     * Thay vì sort 1M+ rows, query này index-seek trực tiếp đến latest row per sensor.
     */
    @Query(value = """
        SELECT sr.id, sr.sensor_id, sr.timestamp, sr.aqi, sr.pm25, sr.pm10,
               sr.o3, sr.no2, sr.so2, sr.co, sr.temperature, sr.humidity, sr.raw_payload
        FROM environment.sensors s
        CROSS JOIN LATERAL (
            SELECT id, sensor_id, timestamp, aqi, pm25, pm10, o3, no2, so2, co,
                   temperature, humidity, raw_payload
            FROM environment.sensor_readings r
            WHERE r.sensor_id = s.sensor_id
            ORDER BY timestamp DESC
            LIMIT 1
        ) sr
        WHERE s.is_active = true
        ORDER BY s.sensor_id
        """, nativeQuery = true)
    List<SensorReading> findLatestPerSensor();

    /**
     * Lấy latest readings kèm district_code trong 1 query — loại bỏ N+1.
     * Trả về Object[] = [SensorReading fields..., district_code]
     */
    @Query(value = """
        SELECT sr.id, sr.sensor_id, sr.timestamp, sr.aqi, sr.pm25, sr.pm10,
               sr.o3, sr.no2, sr.so2, sr.co, sr.temperature, sr.humidity,
               s.district_code
        FROM environment.sensors s
        CROSS JOIN LATERAL (
            SELECT id, sensor_id, timestamp, aqi, pm25, pm10, o3, no2, so2, co,
                   temperature, humidity
            FROM environment.sensor_readings r
            WHERE r.sensor_id = s.sensor_id
            ORDER BY timestamp DESC
            LIMIT 1
        ) sr
        WHERE s.is_active = true
        ORDER BY s.sensor_id
        """, nativeQuery = true)
    List<Object[]> findLatestPerSensorWithDistrict();

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
