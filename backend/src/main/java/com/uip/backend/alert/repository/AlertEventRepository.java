package com.uip.backend.alert.repository;

import com.uip.backend.alert.domain.AlertEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AlertEventRepository extends JpaRepository<AlertEvent, UUID>, JpaSpecificationExecutor<AlertEvent> {

    @Query("""
        SELECT a FROM AlertEvent a
        WHERE a.sensorId    = :sensorId
          AND a.measureType = :measureType
          AND a.detectedAt >= :since
          AND a.status      = 'OPEN'
        """)
    List<AlertEvent> findOpenDuplicates(
            @Param("sensorId")    String sensorId,
            @Param("measureType") String measureType,
            @Param("since")       Instant since);
}
