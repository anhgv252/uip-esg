package com.uip.backend.billing.service;

import com.uip.backend.billing.domain.Invoice;
import com.uip.backend.billing.domain.MonthlyUsage;
import com.uip.backend.billing.repository.InvoiceRepository;
import com.uip.backend.billing.repository.MeteringEventRepository;
import com.uip.backend.billing.repository.MonthlyUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * M5-4 T03: Billing Reconciliation Service (SP:3)
 * 
 * Ensures 99.5% accuracy between:
 * 1. Raw metering_events (source of truth)
 * 2. Aggregated monthly_usage (materialized view)
 * 3. Generated invoices (customer-facing)
 * 
 * Reconciliation report flags discrepancies for manual review.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillingReconciliationService {

    private final MeteringEventRepository meteringEventRepository;
    private final MonthlyUsageRepository monthlyUsageRepository;
    private final InvoiceRepository invoiceRepository;

    private static final BigDecimal ACCURACY_THRESHOLD = new BigDecimal("0.995");  // 99.5%

    /**
     * Reconciliation report for a billing period.
     * 
     * @param tenantId Tenant identifier
     * @param period YYYY-MM format
     * @param rawEventCost Cost calculated from raw metering_events
     * @param aggregatedCost Cost from monthly_usage table
     * @param invoicedCost Cost from invoice
     * @param discrepancy Absolute difference (rawEventCost - invoicedCost)
     * @param accuracyPercentage Accuracy = 1 - (discrepancy / rawEventCost)
     * @param passed Whether accuracy >= 99.5%
     */
    public record ReconciliationReport(
            String tenantId,
            String period,
            long rawEventCost,
            long aggregatedCost,
            long invoicedCost,
            long discrepancy,
            BigDecimal accuracyPercentage,
            boolean passed
    ) {}

    /**
     * Run reconciliation for a tenant's billing period.
     * 
     * @param tenantId Tenant identifier
     * @param billingPeriod YYYY-MM format (null = current month)
     * @return Reconciliation report
     */
    @Transactional(readOnly = true)
    public ReconciliationReport reconcile(String tenantId, String billingPeriod) {
        String period = (billingPeriod != null && !billingPeriod.isBlank())
                ? billingPeriod
                : YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        
        log.info("Running billing reconciliation for tenant {} period {}", tenantId, period);
        
        // 1. Calculate cost from raw metering_events (source of truth)
        long rawEventCost = calculateRawEventCost(tenantId, period);
        
        // 2. Get cost from aggregated monthly_usage
        long aggregatedCost = calculateAggregatedCost(tenantId, period);
        
        // 3. Get cost from generated invoice
        long invoicedCost = calculateInvoicedCost(tenantId, period);
        
        // 4. Calculate discrepancy and accuracy
        long discrepancy = Math.abs(rawEventCost - invoicedCost);
        BigDecimal accuracyPercentage = calculateAccuracy(rawEventCost, discrepancy);
        
        boolean passed = accuracyPercentage.compareTo(ACCURACY_THRESHOLD) >= 0;
        
        ReconciliationReport report = new ReconciliationReport(
                tenantId,
                period,
                rawEventCost,
                aggregatedCost,
                invoicedCost,
                discrepancy,
                accuracyPercentage,
                passed
        );
        
        if (!passed) {
            log.warn("Reconciliation FAILED for tenant {} period {}: accuracy {}% (threshold 99.5%)",
                    tenantId, period, accuracyPercentage.multiply(BigDecimal.valueOf(100)));
        } else {
            log.info("Reconciliation PASSED for tenant {} period {}: accuracy {}%",
                    tenantId, period, accuracyPercentage.multiply(BigDecimal.valueOf(100)));
        }
        
        return report;
    }

    /**
     * Calculate cost from raw metering_events.
     * This is the source of truth.
     */
    private long calculateRawEventCost(String tenantId, String billingPeriod) {
        YearMonth targetMonth = YearMonth.parse(billingPeriod, DateTimeFormatter.ofPattern("yyyy-MM"));
        Instant monthStart = targetMonth.atDay(1).atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant monthEnd = targetMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.of("UTC")).toInstant();
        
        return meteringEventRepository.findByTenantAndTimeRange(tenantId, monthStart, monthEnd)
                .stream()
                .mapToLong(event -> event.getCostUsdCents() != null ? event.getCostUsdCents() : 0)
                .sum();
    }

    /**
     * Calculate cost from aggregated monthly_usage table.
     */
    private long calculateAggregatedCost(String tenantId, String billingPeriod) {
        return monthlyUsageRepository.findByTenantIdAndBillingMonthOrderByBuildingIdAsc(tenantId, billingPeriod)
                .stream()
                .mapToLong(MonthlyUsage::getTotalCostVnd)
                .sum();
    }

    /**
     * Get cost from generated invoice.
     */
    private long calculateInvoicedCost(String tenantId, String billingPeriod) {
        Optional<Invoice> invoice = invoiceRepository.findByTenantIdAndBillingPeriod(tenantId, billingPeriod);
        
        if (invoice.isEmpty()) {
            log.warn("No invoice found for tenant {} period {}", tenantId, billingPeriod);
            return 0L;
        }
        
        // Return subtotal (before tax)
        return invoice.get().getSubtotalVnd();
    }

    /**
     * Calculate accuracy percentage.
     * Formula: accuracy = 1 - (discrepancy / rawEventCost)
     * 
     * @param rawEventCost Source of truth cost
     * @param discrepancy Absolute difference
     * @return Accuracy as percentage (0.0 to 1.0)
     */
    private BigDecimal calculateAccuracy(long rawEventCost, long discrepancy) {
        if (rawEventCost == 0) {
            return BigDecimal.ONE;  // 100% accuracy if no events
        }
        
        BigDecimal rawCostDecimal = BigDecimal.valueOf(rawEventCost);
        BigDecimal discrepancyDecimal = BigDecimal.valueOf(discrepancy);
        
        BigDecimal accuracy = BigDecimal.ONE.subtract(
                discrepancyDecimal.divide(rawCostDecimal, 4, RoundingMode.HALF_UP)
        );
        
        return accuracy.max(BigDecimal.ZERO);  // Clamp to 0.0 minimum
    }
}
