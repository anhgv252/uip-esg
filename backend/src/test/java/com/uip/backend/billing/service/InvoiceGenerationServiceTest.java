package com.uip.backend.billing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.billing.domain.Invoice;
import com.uip.backend.billing.domain.InvoiceStatus;
import com.uip.backend.billing.domain.MonthlyUsage;
import com.uip.backend.billing.repository.InvoiceRepository;
import com.uip.backend.billing.repository.MonthlyUsageRepository;
import com.uip.backend.kafka.KafkaProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InvoiceGenerationService (M5-4 T02).
 * 
 * Coverage:
 * - Invoice number generation
 * - Line items building
 * - Subtotal/tax/total calculation
 * - Kafka event emission
 * - Idempotency (duplicate check)
 */
@ExtendWith(MockitoExtension.class)
class InvoiceGenerationServiceTest {

    @Mock
    private MonthlyUsageRepository monthlyUsageRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private InvoiceGenerationService invoiceGenerationService;

    private static final String TENANT_ID = "test-tenant";
    private static final String BILLING_PERIOD = "2026-06";

    @BeforeEach
    void stubSaveReturnsFirstArg() {
        // JPA save() runs @PrePersist (sets generatedAt/dueDate) and assigns the generated id.
        // Default Mockito returns null and skips lifecycle callbacks, which previously caused
        // NPE in emitInvoiceGeneratedEvent (BUG-M5-002). Mimic both here.
        lenient().when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(invocation -> {
                    Invoice saved = invocation.getArgument(0);
                    saved.setId(java.util.UUID.randomUUID());
                    saved.setGeneratedAt(java.time.Instant.now());
                    return saved;
                });
    }

    @Test
    void testGenerateInvoice_calculatesSubtotalTaxTotal() throws Exception {
        // Given: 3 buildings with costs 2M, 2.5M, 3M VND = subtotal 7.5M
        List<MonthlyUsage> usageRecords = List.of(
                createUsage("building-01", 2_000_000L),
                createUsage("building-02", 2_500_000L),
                createUsage("building-03", 3_000_000L)
        );

        when(monthlyUsageRepository.findByTenantIdAndBillingMonthOrderByBuildingIdAsc(TENANT_ID, BILLING_PERIOD))
                .thenReturn(usageRecords);
        when(invoiceRepository.existsByTenantIdAndBillingPeriod(TENANT_ID, BILLING_PERIOD))
                .thenReturn(false);
        when(invoiceRepository.findByTenantIdAndBillingPeriod(TENANT_ID, BILLING_PERIOD))
                .thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        // When
        Invoice invoice = invoiceGenerationService.generateInvoice(TENANT_ID, BILLING_PERIOD);

        // Then
        assertThat(invoice.getSubtotalVnd()).isEqualTo(7_500_000L);
        assertThat(invoice.getTaxVnd()).isEqualTo(750_000L);  // 10% VAT
        assertThat(invoice.getTotalVnd()).isEqualTo(8_250_000L);
    }

    @Test
    void testGenerateInvoice_generatesInvoiceNumber() throws Exception {
        // Given
        List<MonthlyUsage> usageRecords = List.of(createUsage("building-01", 2_000_000L));

        when(monthlyUsageRepository.findByTenantIdAndBillingMonthOrderByBuildingIdAsc(TENANT_ID, BILLING_PERIOD))
                .thenReturn(usageRecords);
        when(invoiceRepository.existsByTenantIdAndBillingPeriod(TENANT_ID, BILLING_PERIOD))
                .thenReturn(false);
        when(invoiceRepository.findByTenantIdAndBillingPeriod(TENANT_ID, BILLING_PERIOD))
                .thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        // When
        Invoice invoice = invoiceGenerationService.generateInvoice(TENANT_ID, BILLING_PERIOD);

        // Then: format INV-YYYY-MM-{tenantId}-{seq}
        assertThat(invoice.getInvoiceNumber())
                .startsWith("INV-" + BILLING_PERIOD + "-" + TENANT_ID);
    }

    @Test
    void testGenerateInvoice_statusIsGenerated() throws Exception {
        // Given
        List<MonthlyUsage> usageRecords = List.of(createUsage("building-01", 2_000_000L));

        when(monthlyUsageRepository.findByTenantIdAndBillingMonthOrderByBuildingIdAsc(TENANT_ID, BILLING_PERIOD))
                .thenReturn(usageRecords);
        when(invoiceRepository.existsByTenantIdAndBillingPeriod(TENANT_ID, BILLING_PERIOD))
                .thenReturn(false);
        when(invoiceRepository.findByTenantIdAndBillingPeriod(TENANT_ID, BILLING_PERIOD))
                .thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        // When
        Invoice invoice = invoiceGenerationService.generateInvoice(TENANT_ID, BILLING_PERIOD);

        // Then
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.GENERATED);
    }

    @Test
    void testGenerateInvoice_emitsKafkaEvent() throws Exception {
        // Given
        List<MonthlyUsage> usageRecords = List.of(createUsage("building-01", 2_000_000L));

        when(monthlyUsageRepository.findByTenantIdAndBillingMonthOrderByBuildingIdAsc(TENANT_ID, BILLING_PERIOD))
                .thenReturn(usageRecords);
        when(invoiceRepository.existsByTenantIdAndBillingPeriod(TENANT_ID, BILLING_PERIOD))
                .thenReturn(false);
        when(invoiceRepository.findByTenantIdAndBillingPeriod(TENANT_ID, BILLING_PERIOD))
                .thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        invoiceGenerationService.generateInvoice(TENANT_ID, BILLING_PERIOD);

        // Then
        verify(kafkaProducerService).send(eq("UIP.billing.invoice.generated.v1"), eq(TENANT_ID), anyString());
    }

    @Test
    void testGenerateInvoice_throwsException_whenNoMonthlyUsage() {
        // Given: empty usage records
        when(monthlyUsageRepository.findByTenantIdAndBillingMonthOrderByBuildingIdAsc(TENANT_ID, BILLING_PERIOD))
                .thenReturn(List.of());
        when(invoiceRepository.existsByTenantIdAndBillingPeriod(TENANT_ID, BILLING_PERIOD))
                .thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> invoiceGenerationService.generateInvoice(TENANT_ID, BILLING_PERIOD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No monthly usage found");
    }

    @Test
    void testGenerateInvoice_returnsExisting_whenAlreadyGenerated() {
        // Given: invoice already exists
        Invoice existing = Invoice.builder()
                .invoiceNumber("INV-2026-06-test-tenant-001")
                .build();

        when(invoiceRepository.existsByTenantIdAndBillingPeriod(TENANT_ID, BILLING_PERIOD))
                .thenReturn(true);
        when(invoiceRepository.findByTenantIdAndBillingPeriod(TENANT_ID, BILLING_PERIOD))
                .thenReturn(Optional.of(existing));

        // When
        Invoice invoice = invoiceGenerationService.generateInvoice(TENANT_ID, BILLING_PERIOD);

        // Then: returns existing, does not create duplicate
        assertThat(invoice.getInvoiceNumber()).isEqualTo("INV-2026-06-test-tenant-001");
        verify(invoiceRepository, never()).save(any());
    }

    private MonthlyUsage createUsage(String buildingId, Long totalCost) {
        return MonthlyUsage.builder()
                .tenantId(TENANT_ID)
                .buildingId(buildingId)
                .billingMonth(BILLING_PERIOD)
                .totalCostVnd(totalCost)
                .totalSensorReadings(100L)
                .totalAiTokens(50_000L)
                .totalAlerts(5L)
                .build();
    }
}
