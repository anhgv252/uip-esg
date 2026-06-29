package com.uip.backend.billing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.audit.service.AuditLogService;
import com.uip.backend.billing.domain.Invoice;
import com.uip.backend.billing.domain.InvoiceStatus;
import com.uip.backend.billing.domain.MonthlyUsage;
import com.uip.backend.billing.repository.InvoiceRepository;
import com.uip.backend.billing.repository.MonthlyUsageRepository;
import com.uip.backend.kafka.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M5-4 T02: Invoice Auto-Generation (SP:5)
 * 
 * Generates monthly invoices from billing.monthly_usage aggregates.
 * Emits Kafka event: UIP.billing.invoice.generated.v1
 * Stores PDF path (stub: returns HTML for now).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceGenerationService {

    private final MonthlyUsageRepository monthlyUsageRepository;
    private final InvoiceRepository invoiceRepository;
    private final KafkaProducerService kafkaProducerService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    private static final String KAFKA_TOPIC = "UIP.billing.invoice.generated.v1";
    private static final double VAT_RATE = 0.10;  // 10% VAT for Vietnam

    /**
     * Generate invoice for a tenant's billing period.
     * 
     * @param tenantId Tenant identifier
     * @param billingPeriod YYYY-MM format (e.g., 2026-06)
     * @return Generated invoice
     */
    @Transactional
    public Invoice generateInvoice(String tenantId, String billingPeriod) {
        log.info("Generating invoice for tenant {} billing period {}", tenantId, billingPeriod);
        
        // Check if invoice already exists
        if (invoiceRepository.existsByTenantIdAndBillingPeriod(tenantId, billingPeriod)) {
            log.warn("Invoice already exists for tenant {} period {}", tenantId, billingPeriod);
            return invoiceRepository.findByTenantIdAndBillingPeriod(tenantId, billingPeriod)
                    .orElseThrow();
        }
        
        // Fetch monthly usage records for this tenant + period
        List<MonthlyUsage> usageRecords = monthlyUsageRepository
                .findByTenantIdAndBillingMonthOrderByBuildingIdAsc(tenantId, billingPeriod);
        
        if (usageRecords.isEmpty()) {
            throw new IllegalStateException("No monthly usage found for tenant " + tenantId + " period " + billingPeriod);
        }
        
        // Calculate totals
        long subtotalVnd = usageRecords.stream()
                .mapToLong(MonthlyUsage::getTotalCostVnd)
                .sum();
        
        long taxVnd = (long) (subtotalVnd * VAT_RATE);
        long totalVnd = subtotalVnd + taxVnd;
        
        // Generate invoice number: INV-YYYY-MM-{tenantId}-{seq}
        String invoiceNumber = generateInvoiceNumber(tenantId, billingPeriod);
        
        // Build line items JSON
        String lineItems = buildLineItemsJson(usageRecords);
        
        // Create invoice
        Invoice invoice = Invoice.builder()
                .tenantId(tenantId)
                .invoiceNumber(invoiceNumber)
                .billingPeriod(billingPeriod)
                .subtotalVnd(subtotalVnd)
                .taxVnd(taxVnd)
                .totalVnd(totalVnd)
                .status(InvoiceStatus.GENERATED)
                .lineItems(lineItems)
                .pdfPath("/invoices/" + invoiceNumber + ".html")  // Stub: HTML for now
                .build();
        
        invoice = invoiceRepository.save(invoice);
        
        log.info("Invoice generated: {} for tenant {} amount {} VND", 
                invoiceNumber, tenantId, totalVnd);
        
        // Emit Kafka event
        emitInvoiceGeneratedEvent(invoice);
        
        // Log audit event
        auditLogService.logEvent(
                tenantId,
                "BILLING_INVOICE_GENERATED",
                "SYSTEM",
                invoice.getId().toString(),
                "INVOICE",
                Map.of(
                        "invoiceNumber", invoiceNumber,
                        "billingPeriod", billingPeriod,
                        "totalVnd", totalVnd,
                        "buildingCount", usageRecords.size()
                )
        );
        
        return invoice;
    }

    /**
     * Generate unique invoice number.
     * Format: INV-YYYY-MM-{tenantId}-{seq}
     */
    private String generateInvoiceNumber(String tenantId, String billingPeriod) {
        // Count existing invoices for this tenant + period to get sequence
        long count = invoiceRepository.findByTenantIdAndBillingPeriod(tenantId, billingPeriod)
                .stream()
                .count() + 1;
        
        return String.format("INV-%s-%s-%03d", billingPeriod, tenantId, count);
    }

    /**
     * Build line items JSON array from monthly usage records.
     * Format: [{buildingId, baseFee, aiOverage, total, sensors, aiTokens}]
     */
    private String buildLineItemsJson(List<MonthlyUsage> usageRecords) {
        try {
            List<Map<String, Object>> lineItems = usageRecords.stream()
                    .map(usage -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("buildingId", usage.getBuildingId());
                        item.put("baseFeeVnd", usage.getBaseFeeVnd());
                        item.put("aiOverageVnd", usage.getAiOverageVnd());
                        item.put("totalCostVnd", usage.getTotalCostVnd());
                        item.put("totalSensorReadings", usage.getTotalSensorReadings());
                        item.put("totalAiTokens", usage.getTotalAiTokens());
                        item.put("totalAlerts", usage.getTotalAlerts());
                        return item;
                    })
                    .toList();
            
            return objectMapper.writeValueAsString(lineItems);
        } catch (Exception e) {
            log.error("Failed to serialize line items JSON", e);
            return "[]";
        }
    }

    /**
     * Emit Kafka event: UIP.billing.invoice.generated.v1
     */
    private void emitInvoiceGeneratedEvent(Invoice invoice) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("invoiceId", invoice.getId().toString());
            event.put("invoiceNumber", invoice.getInvoiceNumber());
            event.put("tenantId", invoice.getTenantId());
            event.put("billingPeriod", invoice.getBillingPeriod());
            event.put("totalVnd", invoice.getTotalVnd());
            event.put("status", invoice.getStatus().name());
            event.put("generatedAt", invoice.getGeneratedAt().toString());
            
            String payload = objectMapper.writeValueAsString(event);
            kafkaProducerService.send(KAFKA_TOPIC, invoice.getTenantId(), payload);
            
            log.info("Emitted Kafka event to {} for invoice {}", KAFKA_TOPIC, invoice.getInvoiceNumber());
        } catch (Exception e) {
            log.error("Failed to emit Kafka event for invoice {}", invoice.getInvoiceNumber(), e);
        }
    }
}
