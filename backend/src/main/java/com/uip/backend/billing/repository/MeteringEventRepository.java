package com.uip.backend.billing.repository;

import com.uip.backend.billing.domain.MeteringEvent;
import com.uip.backend.billing.domain.MeteringEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Repository for MeteringEvent.
 * All queries automatically respect tenant_id via RLS (ADR-010).
 */
public interface MeteringEventRepository extends JpaRepository<MeteringEvent, Long> {

    /**
     * Find events by tenant ID and time range.
     * Used by GET /api/v1/billing/metering endpoint with citizen:read scope.
     */
    @Query("""
        SELECT m FROM MeteringEvent m
        WHERE m.tenantId = :tenantId
          AND m.recordedAt >= :from
          AND m.recordedAt <= :to
        ORDER BY m.recordedAt DESC
        """)
    List<MeteringEvent> findByTenantAndTimeRange(
            @Param("tenantId") String tenantId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Find events by tenant ID, event type, and time range.
     * Used by GET /api/v1/billing/metering endpoint with optional event type filter.
     */
    @Query("""
        SELECT m FROM MeteringEvent m
        WHERE m.tenantId = :tenantId
          AND m.eventType = :eventType
          AND m.recordedAt >= :from
          AND m.recordedAt <= :to
        ORDER BY m.recordedAt DESC
        """)
    List<MeteringEvent> findByTenantTypeAndTimeRange(
            @Param("tenantId") String tenantId,
            @Param("eventType") MeteringEventType eventType,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Calculate total cost for a tenant in a time range.
     * Used by GET /api/v1/billing/metering/summary endpoint.
     */
    @Query("""
        SELECT COALESCE(SUM(m.costUsdCents), 0)
        FROM MeteringEvent m
        WHERE m.tenantId = :tenantId
          AND m.recordedAt >= :from
          AND m.recordedAt <= :to
        """)
    Long sumCostByTenantAndTimeRange(
            @Param("tenantId") String tenantId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Check if event ID already exists (idempotency check).
     * Used by Kafka consumer before saving.
     */
    boolean existsByEventId(String eventId);
}
