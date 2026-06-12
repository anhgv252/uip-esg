package com.uip.backend.correlation.repository;

import com.uip.backend.correlation.domain.CorrelatedIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * M4-COR-01: Repository for persisted correlated incidents.
 */
@Repository
public interface CorrelatedIncidentRepository extends JpaRepository<CorrelatedIncident, UUID> {

    /**
     * Find all incidents for a building detected after a given timestamp.
     * Used by the CEP de-duplication check to avoid re-creating incidents
     * within the same correlation window.
     */
    List<CorrelatedIncident> findByBuildingIdAndDetectedAtAfter(String buildingId, Instant after);
}
