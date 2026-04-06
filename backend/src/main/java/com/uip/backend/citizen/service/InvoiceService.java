package com.uip.backend.citizen.service;

import com.uip.backend.citizen.api.dto.ConsumptionHistoryDto;
import com.uip.backend.citizen.api.dto.InvoiceDto;
import com.uip.backend.citizen.api.dto.MeterDto;
import com.uip.backend.citizen.domain.ConsumptionRecord;
import com.uip.backend.citizen.domain.Invoice;
import com.uip.backend.citizen.domain.Meter;
import com.uip.backend.citizen.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {
    
    private final InvoiceRepository invoiceRepository;
    private final MeterRepository meterRepository;
    private final ConsumptionRecordRepository consumptionRepository;
    private final CitizenAccountRepository citizenRepository;
    
    /**
     * Link a meter to a citizen account
     * POST /api/v1/citizen/meters
     */
    @Transactional
    public MeterDto registerMeter(UUID citizenId, String meterCode, String meterType) {
        log.info("Registering meter {} for citizen {}", meterCode, citizenId);
        
        // Verify citizen exists
        citizenRepository.findById(citizenId)
            .orElseThrow(() -> new IllegalArgumentException("Citizen not found: " + citizenId));
        
        // Check meter code uniqueness
        if (meterRepository.findByMeterCode(meterCode).isPresent()) {
            throw new IllegalArgumentException("Meter code already registered: " + meterCode);
        }
        
        Meter meter = Meter.builder()
            .citizenId(citizenId)
            .meterCode(meterCode)
            .meterType(meterType)
            .registeredAt(LocalDateTime.now())
            .build();
        
        Meter saved = meterRepository.save(meter);
        return mapToMeterDto(saved);
    }
    
    /**
     * Get all invoices for a citizen
     * GET /api/v1/citizen/invoices
     */
    @Transactional(readOnly = true)
    public Page<InvoiceDto> getInvoices(UUID citizenId, Pageable pageable) {
        log.info("Fetching invoices for citizen {}", citizenId);
        Page<Invoice> invoices = invoiceRepository.findByCitizenId(citizenId, pageable);
        return invoices.map(this::mapToInvoiceDto);
    }
    
    /**
     * Get invoices for a specific month/year
     */
    @Transactional(readOnly = true)
    public List<InvoiceDto> getInvoicesByMonth(UUID citizenId, Integer year, Integer month) {
        log.info("Fetching invoices for citizen {} - {}/{}", citizenId, month, year);
        List<Invoice> invoices = invoiceRepository.findByYearAndMonth(citizenId, year, month);
        return invoices.stream().map(this::mapToInvoiceDto).toList();
    }
    
    /**
     * Get single invoice detail
     * GET /api/v1/citizen/invoices/{id}
     */
    @Transactional(readOnly = true)
    public InvoiceDto getInvoiceById(UUID invoiceId) {
        log.info("Fetching invoice {}", invoiceId);
        Invoice invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + invoiceId));
        return mapToInvoiceDto(invoice);
    }
    
    /**
     * Get consumption history for a meter
     * GET /api/v1/citizen/consumption/history?meterId=&months=3
     */
    @Transactional(readOnly = true)
    public List<ConsumptionHistoryDto> getConsumptionHistory(UUID meterId, Integer months) {
        log.info("Fetching consumption history for meter {} (last {} months)", meterId, months);
        
        meterRepository.findById(meterId)
            .orElseThrow(() -> new IllegalArgumentException("Meter not found: " + meterId));
        
        LocalDateTime fromDate = LocalDateTime.now().minusMonths(months != null ? months : 3);
        LocalDateTime toDate = LocalDateTime.now();
        
        List<ConsumptionRecord> records = consumptionRepository.findByMeterAndDateRange(meterId, fromDate, toDate);
        return records.stream().map(this::mapToConsumptionDto).toList();
    }
    
    /**
     * Get meters for a citizen
     */
    @Transactional(readOnly = true)
    public List<MeterDto> getMetersByCitizen(UUID citizenId) {
        log.info("Fetching meters for citizen {}", citizenId);
        List<Meter> meters = meterRepository.findByCitizenId(citizenId);
        return meters.stream().map(this::mapToMeterDto).toList();
    }
    
    // Helper methods
    private InvoiceDto mapToInvoiceDto(Invoice invoice) {
        return InvoiceDto.builder()
            .id(invoice.getId())
            .billingMonth(invoice.getBillingMonth())
            .billingYear(invoice.getBillingYear())
            .meterType(invoice.getMeterType())
            .unitsConsumed(invoice.getUnitsConsumed())
            .unitPrice(invoice.getUnitPrice())
            .amount(invoice.getAmount())
            .status(invoice.getStatus())
            .issuedAt(invoice.getIssuedAt())
            .dueAt(invoice.getDueAt())
            .paidAt(invoice.getPaidAt())
            .build();
    }
    
    private MeterDto mapToMeterDto(Meter meter) {
        return MeterDto.builder()
            .id(meter.getId())
            .meterCode(meter.getMeterCode())
            .meterType(meter.getMeterType())
            .registeredAt(meter.getRegisteredAt())
            .build();
    }
    
    private ConsumptionHistoryDto mapToConsumptionDto(ConsumptionRecord record) {
        return ConsumptionHistoryDto.builder()
            .id(record.getId())
            .recordedAt(record.getRecordedAt())
            .readingValue(record.getReadingValue())
            .unitsUsed(record.getUnitsUsed())
            .build();
    }
}
