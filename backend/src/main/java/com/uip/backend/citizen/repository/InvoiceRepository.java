package com.uip.backend.citizen.repository;

import com.uip.backend.citizen.domain.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    
    Page<Invoice> findByCitizenId(UUID citizenId, Pageable pageable);
    
    @Query("SELECT i FROM Invoice i WHERE i.citizenId = :citizenId " +
           "AND i.billingYear = :year AND i.billingMonth = :month")
    List<Invoice> findByYearAndMonth(
        @Param("citizenId") UUID citizenId,
        @Param("year") Integer year,
        @Param("month") Integer month
    );
    
    @Query("SELECT i FROM Invoice i WHERE i.citizenId = :citizenId " +
           "ORDER BY i.billingYear DESC, i.billingMonth DESC")
    List<Invoice> findRecentInvoices(@Param("citizenId") UUID citizenId, Pageable pageable);
    
    List<Invoice> findByStatus(String status);
}
