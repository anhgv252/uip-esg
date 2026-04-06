package com.uip.backend.traffic.repository;

import com.uip.backend.traffic.domain.TrafficCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TrafficCountRepository extends JpaRepository<TrafficCount, UUID> {
    
    @Query("SELECT tc FROM TrafficCount tc WHERE tc.intersectionId = :intersectionId " +
           "AND tc.recordedAt >= :from AND tc.recordedAt <= :to " +
           "ORDER BY tc.recordedAt DESC")
    List<TrafficCount> findByIntersectionAndTimeRange(
        @Param("intersectionId") String intersectionId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );
    
    @Query("SELECT tc FROM TrafficCount tc WHERE tc.intersectionId = :intersectionId " +
           "ORDER BY tc.recordedAt DESC LIMIT 10")
    List<TrafficCount> findRecentByIntersection(@Param("intersectionId") String intersectionId);
    
    @Query("SELECT tc.vehicleType, SUM(tc.vehicleCount) as totalCount " +
           "FROM TrafficCount tc WHERE tc.intersectionId = :intersectionId " +
           "AND tc.recordedAt >= :from GROUP BY tc.vehicleType")
    List<Object> getCountsByType(
        @Param("intersectionId") String intersectionId,
        @Param("from") LocalDateTime from
    );
}
