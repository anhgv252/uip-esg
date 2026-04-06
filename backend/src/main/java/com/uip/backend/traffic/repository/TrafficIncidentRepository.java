package com.uip.backend.traffic.repository;

import com.uip.backend.traffic.domain.TrafficIncident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TrafficIncidentRepository extends JpaRepository<TrafficIncident, UUID> {
    
    Page<TrafficIncident> findByStatus(String status, Pageable pageable);
    
    @Query("SELECT ti FROM TrafficIncident ti WHERE ti.status = :status ORDER BY ti.occurredAt DESC")
    List<TrafficIncident> findRecentByStatus(@Param("status") String status, Pageable pageable);
    
    @Query("SELECT ti FROM TrafficIncident ti WHERE ti.incidentType = :type AND ti.occurredAt >= :from ORDER BY ti.occurredAt DESC")
    List<TrafficIncident> findByTypeAndDateRange(
        @Param("type") String type,
        @Param("from") LocalDateTime from,
        Pageable pageable
    );
    
    Page<TrafficIncident> findByIntersectionId(String intersectionId, Pageable pageable);
}
