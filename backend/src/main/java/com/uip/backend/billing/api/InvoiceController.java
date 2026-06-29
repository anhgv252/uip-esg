package com.uip.backend.billing.api;

import com.uip.backend.billing.domain.Invoice;
import com.uip.backend.billing.domain.InvoiceStatus;
import com.uip.backend.billing.repository.InvoiceRepository;
import com.uip.backend.billing.service.InvoiceGenerationService;
import com.uip.backend.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * M5-4 T02: Invoice REST API
 * 
 * Endpoints:
 * - POST /api/v1/billing/invoices/generate?period=YYYY-MM
 * - GET  /api/v1/billing/invoices (paginated, optional status filter)
 * - GET  /api/v1/billing/invoices/{id}
 * - GET  /api/v1/billing/invoices/{id}/pdf
 * 
 * Access: ADMIN, TENANT_ADMIN only
 */
@RestController
@RequestMapping("/api/v1/billing/invoices")
@RequiredArgsConstructor
@Slf4j
public class InvoiceController {

    private final InvoiceGenerationService invoiceGenerationService;
    private final InvoiceRepository invoiceRepository;

    /**
     * Generate invoice for a billing period.
     * 
     * POST /api/v1/billing/invoices/generate?period=2026-06
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<Invoice> generateInvoice(@RequestParam String period) {
        String tenantId = TenantContext.getCurrentTenantId();
        log.info("Generating invoice for tenant {} period {}", tenantId, period);
        
        Invoice invoice = invoiceGenerationService.generateInvoice(tenantId, period);
        return ResponseEntity.ok(invoice);
    }

    /**
     * List invoices (paginated, optional status filter).
     * 
     * GET /api/v1/billing/invoices?page=0&size=20&status=GENERATED
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<Page<Invoice>> listInvoices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) InvoiceStatus status) {
        String tenantId = TenantContext.getCurrentTenantId();
        Pageable pageable = PageRequest.of(page, size);
        
        Page<Invoice> invoices = (status != null)
                ? invoiceRepository.findByTenantIdAndStatusOrderByGeneratedAtDesc(tenantId, status, pageable)
                : invoiceRepository.findByTenantIdOrderByGeneratedAtDesc(tenantId, pageable);
        
        return ResponseEntity.ok(invoices);
    }

    /**
     * Get invoice by ID.
     * 
     * GET /api/v1/billing/invoices/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<Invoice> getInvoice(@PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenantId();
        
        return invoiceRepository.findById(id)
                .filter(invoice -> invoice.getTenantId().equals(tenantId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get invoice PDF (stub: returns HTML for now).
     * 
     * GET /api/v1/billing/invoices/{id}/pdf
     */
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<String> getInvoicePdf(@PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenantId();
        
        return invoiceRepository.findById(id)
                .filter(invoice -> invoice.getTenantId().equals(tenantId))
                .map(invoice -> {
                    // Stub: return simple HTML representation
                    String html = String.format("""
                            <html>
                            <head><title>Invoice %s</title></head>
                            <body>
                                <h1>Invoice %s</h1>
                                <p>Tenant: %s</p>
                                <p>Billing Period: %s</p>
                                <p>Subtotal: %,d VND</p>
                                <p>Tax (10%%): %,d VND</p>
                                <p><strong>Total: %,d VND</strong></p>
                                <p>Status: %s</p>
                                <hr>
                                <p>Line Items:</p>
                                <pre>%s</pre>
                            </body>
                            </html>
                            """,
                            invoice.getInvoiceNumber(),
                            invoice.getInvoiceNumber(),
                            invoice.getTenantId(),
                            invoice.getBillingPeriod(),
                            invoice.getSubtotalVnd(),
                            invoice.getTaxVnd(),
                            invoice.getTotalVnd(),
                            invoice.getStatus(),
                            invoice.getLineItems()
                    );
                    
                    return ResponseEntity.ok()
                            .header("Content-Type", "text/html; charset=UTF-8")
                            .body(html);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
