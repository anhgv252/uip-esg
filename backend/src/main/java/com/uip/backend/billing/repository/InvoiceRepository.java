package com.uip.backend.billing.repository;

import com.uip.backend.billing.domain.Invoice;
import com.uip.backend.billing.domain.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * M5-4 T02: Repository for invoices.
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    Page<Invoice> findByTenantIdOrderByGeneratedAtDesc(String tenantId, Pageable pageable);

    Page<Invoice> findByTenantIdAndStatusOrderByGeneratedAtDesc(String tenantId, InvoiceStatus status, Pageable pageable);

    Optional<Invoice> findByTenantIdAndBillingPeriod(String tenantId, String billingPeriod);

    boolean existsByTenantIdAndBillingPeriod(String tenantId, String billingPeriod);
}
