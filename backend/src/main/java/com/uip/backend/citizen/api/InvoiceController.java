package com.uip.backend.citizen.api;

import com.uip.backend.citizen.api.dto.ConsumptionHistoryDto;
import com.uip.backend.citizen.api.dto.InvoiceDto;
import com.uip.backend.citizen.api.dto.MeterDto;
import com.uip.backend.citizen.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.uip.backend.citizen.repository.CitizenAccountRepository;
import java.util.List;
import java.util.UUID;

/**
 * S3-05 — Citizen Utilities Module
 * Meter management, invoice billing, and consumption tracking
 */
@RestController
@RequestMapping("/api/v1/citizen")
@Tag(name = "Citizen Utilities", description = "Meter management, invoices, and consumption history")
@RequiredArgsConstructor
public class InvoiceController {
    
    private final InvoiceService invoiceService;
    private final CitizenAccountRepository citizenRepository;
    
    /**
     * Register/link a meter to citizen account
     * POST /api/v1/citizen/meters
     */
    @PostMapping("/meters")
    @Operation(summary = "Register a meter to citizen account")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<MeterDto> registerMeter(
            @RequestParam String meterCode,
            @RequestParam String meterType,
            Authentication authentication) {
        
        String username = authentication.getName();
        UUID citizenId = citizenRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Citizen not found: " + username))
            .getId();
        
        MeterDto meter = invoiceService.registerMeter(citizenId, meterCode, meterType);
        return ResponseEntity.status(201).body(meter);
    }
    
    /**
     * Get invoices for current citizen
     * GET /api/v1/citizen/invoices?page=0&size=20
     */
    @GetMapping("/invoices")
    @Operation(summary = "Get invoices for current citizen")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<Page<InvoiceDto>> getInvoices(
            Authentication authentication,
            Pageable pageable) {
        
        String username = authentication.getName();
        UUID citizenId = citizenRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Citizen not found: " + username))
            .getId();
        
        Page<InvoiceDto> invoices = invoiceService.getInvoices(citizenId, pageable);
        return ResponseEntity.ok(invoices);
    }
    
    /**
     * Get invoices for specific month/year
     * GET /api/v1/citizen/invoices?month=4&year=2026
     */
    @GetMapping("/invoices/by-month")
    @Operation(summary = "Get invoices for specific month")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<List<InvoiceDto>> getInvoicesByMonth(
            @RequestParam Integer month,
            @RequestParam Integer year,
            Authentication authentication) {
        
        String username = authentication.getName();
        UUID citizenId = citizenRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Citizen not found: " + username))
            .getId();
        
        List<InvoiceDto> invoices = invoiceService.getInvoicesByMonth(citizenId, year, month);
        return ResponseEntity.ok(invoices);
    }
    
    /**
     * Get invoice detail
     * GET /api/v1/citizen/invoices/{id}
     */
    @GetMapping("/invoices/{id}")
    @Operation(summary = "Get invoice detail")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<InvoiceDto> getInvoiceDetail(@PathVariable UUID id) {
        InvoiceDto invoice = invoiceService.getInvoiceById(id);
        return ResponseEntity.ok(invoice);
    }
    
    /**
     * Get consumption history for a meter
     * GET /api/v1/citizen/consumption/history?meterId=&months=3
     */
    @GetMapping("/consumption/history")
    @Operation(summary = "Get consumption history for a meter")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<List<ConsumptionHistoryDto>> getConsumptionHistory(
            @RequestParam UUID meterId,
            @RequestParam(required = false, defaultValue = "3") Integer months) {
        
        List<ConsumptionHistoryDto> history = invoiceService.getConsumptionHistory(meterId, months);
        return ResponseEntity.ok(history);
    }
    
    /**
     * Get meters for current citizen
     * GET /api/v1/citizen/meters
     */
    @GetMapping("/meters")
    @Operation(summary = "Get meters for current citizen")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<List<MeterDto>> getMeters(Authentication authentication) {
        String username = authentication.getName();
        UUID citizenId = citizenRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("Citizen not found: " + username))
            .getId();
        
        List<MeterDto> meters = invoiceService.getMetersByCitizen(citizenId);
        return ResponseEntity.ok(meters);
    }
}
