package com.uip.backend.billing.service;

import com.uip.backend.billing.domain.Invoice;
import com.uip.backend.billing.domain.MonthlyUsage;
import com.uip.backend.billing.repository.InvoiceRepository;
import com.uip.backend.billing.repository.MeteringEventRepository;
import com.uip.backend.billing.repository.MonthlyUsageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BillingReconciliationService (M5-4 T03).
 * 
 * Coverage:
 * - Accuracy calculation (99.5% threshold)
 * - Pass/fail determination
 * - Discrepancy reporting
 */
@ExtendWith(MockitoExtension.class)
class BillingReconciliationServiceTest {

    @Mock
    private MeteringEventRepository meteringEventRepository;

    @Mock
    private MonthlyUsageRepository monthlyUsageRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private BillingReconciliationService reconciliationService;

    private static final String TENANT_ID = "test-tenant";
    private static final String BILLING_PERIOD = "2026-06";

    @Test
    void testReconcile_passes_whenAccuracyAboveThreshold() {
        // Given: raw events 10M VND, invoice 9.96M VND = 40K discrepancy = 99.6% accuracy
        mockRawEventCost(10_000_000L);
        mockAggregatedCost(10_000_000L);
        mockInvoiceCost(9_960_000L);

        // When
        BillingReconciliationService.ReconciliationReport report = 
                reconciliationService.reconcile(TENANT_ID, BILLING_PERIOD);

        // Then
        assertThat(report.passed()).isTrue();
        assertThat(report.accuracyPercentage()).isGreaterThanOrEqualTo(new BigDecimal("0.995"));
    }

    @Test
    void testReconcile_fails_whenAccuracyBelowThreshold() {
        // Given: raw events 10M VND, invoice 9.90M VND = 100K discrepancy = 99.0% accuracy
        mockRawEventCost(10_000_000L);
        mockAggregatedCost(10_000_000L);
        mockInvoiceCost(9_900_000L);

        // When
        BillingReconciliationService.ReconciliationReport report = 
                reconciliationService.reconcile(TENANT_ID, BILLING_PERIOD);

        // Then
        assertThat(report.passed()).isFalse();
        assertThat(report.accuracyPercentage()).isLessThan(new BigDecimal("0.995"));
    }

    @Test
    void testReconcile_calculatesDiscrepancyCorrectly() {
        // Given: raw events 5M VND, invoice 4.9M VND = 100K discrepancy
        mockRawEventCost(5_000_000L);
        mockAggregatedCost(5_000_000L);
        mockInvoiceCost(4_900_000L);

        // When
        BillingReconciliationService.ReconciliationReport report = 
                reconciliationService.reconcile(TENANT_ID, BILLING_PERIOD);

        // Then
        assertThat(report.discrepancy()).isEqualTo(100_000L);
    }

    @Test
    void testReconcile_handles100PercentAccuracy() {
        // Given: perfect match
        mockRawEventCost(10_000_000L);
        mockAggregatedCost(10_000_000L);
        mockInvoiceCost(10_000_000L);

        // When
        BillingReconciliationService.ReconciliationReport report = 
                reconciliationService.reconcile(TENANT_ID, BILLING_PERIOD);

        // Then
        assertThat(report.passed()).isTrue();
        assertThat(report.accuracyPercentage()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(report.discrepancy()).isZero();
    }

    @Test
    void testReconcile_handlesZeroEvents() {
        // Given: no events
        mockRawEventCost(0L);
        mockAggregatedCost(0L);
        mockInvoiceCost(0L);

        // When
        BillingReconciliationService.ReconciliationReport report = 
                reconciliationService.reconcile(TENANT_ID, BILLING_PERIOD);

        // Then
        assertThat(report.passed()).isTrue();
        assertThat(report.accuracyPercentage()).isEqualByComparingTo(BigDecimal.ONE);
    }

    private void mockRawEventCost(long cost) {
        // Build a single metering event whose costUsdCents sums to `cost`.
        // (BUG-M5-003: previously returned List.of() and ignored `cost`, so rawEventCost was
        //  always 0 → accuracy forced to 100% and discrepancy computed against 0.)
        com.uip.backend.billing.domain.MeteringEvent event =
                com.uip.backend.billing.domain.MeteringEvent.builder()
                        .costUsdCents((int) cost)
                        .build();
        when(meteringEventRepository.findByTenantAndTimeRange(eq(TENANT_ID), any(), any()))
                .thenReturn(List.of(event));
    }

    private void mockAggregatedCost(long cost) {
        MonthlyUsage usage = MonthlyUsage.builder()
                .totalCostVnd(cost)
                .build();
        when(monthlyUsageRepository.findByTenantIdAndBillingMonthOrderByBuildingIdAsc(TENANT_ID, BILLING_PERIOD))
                .thenReturn(List.of(usage));
    }

    private void mockInvoiceCost(long cost) {
        Invoice invoice = Invoice.builder()
                .subtotalVnd(cost)
                .build();
        when(invoiceRepository.findByTenantIdAndBillingPeriod(TENANT_ID, BILLING_PERIOD))
                .thenReturn(Optional.of(invoice));
    }
}
