package com.uip.backend.citizen.repository;

import com.uip.backend.citizen.domain.ConsumptionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ConsumptionRecordRepository extends JpaRepository<ConsumptionRecord, UUID> {
    
    @Query("SELECT cr FROM ConsumptionRecord cr WHERE cr.meterId = :meterId " +
           "AND cr.recordedAt >= :from AND cr.recordedAt <= :to " +
           "ORDER BY cr.recordedAt DESC")
    List<ConsumptionRecord> findByMeterAndDateRange(
        @Param("meterId") UUID meterId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );
    
    @Query("SELECT cr FROM ConsumptionRecord cr WHERE cr.meterId = :meterId " +
           "ORDER BY cr.recordedAt DESC")
    List<ConsumptionRecord> findByMeterId(@Param("meterId") UUID meterId);
}
