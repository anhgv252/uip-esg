package com.uip.backend.billing.repository;

import com.uip.backend.billing.domain.MonthlyUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * M5-4 T01: Repository for monthly usage aggregates.
 */
@Repository
public interface MonthlyUsageRepository extends JpaRepository<MonthlyUsage, Long> {

    Optional<MonthlyUsage> findByTenantIdAndBuildingIdAndBillingMonth(
            String tenantId, String buildingId, String billingMonth);

    List<MonthlyUsage> findByTenantIdAndBillingMonthOrderByBuildingIdAsc(
            String tenantId, String billingMonth);

    @Query("SELECT m FROM MonthlyUsage m WHERE m.tenantId = :tenantId ORDER BY m.billingMonth DESC, m.buildingId ASC")
    List<MonthlyUsage> findByTenantIdOrderByBillingMonthDesc(String tenantId);
}
