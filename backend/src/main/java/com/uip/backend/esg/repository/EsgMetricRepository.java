package com.uip.backend.esg.repository;

import com.uip.backend.esg.domain.EsgMetric;
import com.uip.backend.esg.domain.EsgMetricId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface EsgMetricRepository extends JpaRepository<EsgMetric, EsgMetricId> {

    @Query("""
        SELECT m FROM EsgMetric m
        WHERE m.metricType = :metricType
          AND m.id.timestamp BETWEEN :from AND :to
        ORDER BY m.id.timestamp ASC
        """)
    List<EsgMetric> findByTypeAndRange(
            @Param("metricType") String metricType,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
        SELECT m FROM EsgMetric m
        WHERE m.metricType = :metricType
          AND m.buildingId = :buildingId
          AND m.id.timestamp BETWEEN :from AND :to
        ORDER BY m.id.timestamp ASC
        """)
    List<EsgMetric> findByTypeAndBuilding(
            @Param("metricType") String metricType,
            @Param("buildingId") String buildingId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
        SELECT SUM(m.value) FROM EsgMetric m
        WHERE m.metricType = :metricType
          AND m.id.timestamp BETWEEN :from AND :to
        """)
    Double sumByTypeAndRange(
            @Param("metricType") String metricType,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
