package com.uip.backend.citizen;

import com.uip.backend.citizen.domain.CitizenAccount;
import com.uip.backend.citizen.domain.Invoice;
import com.uip.backend.citizen.domain.Meter;
import com.uip.backend.citizen.repository.CitizenAccountRepository;
import com.uip.backend.citizen.repository.ConsumptionRecordRepository;
import com.uip.backend.citizen.repository.InvoiceRepository;
import com.uip.backend.citizen.repository.MeterRepository;
import com.uip.backend.citizen.service.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceService unit tests")
class InvoiceServiceTest {

    @Mock InvoiceRepository invoiceRepository;
    @Mock MeterRepository meterRepository;
    @Mock ConsumptionRecordRepository consumptionRepository;
    @Mock CitizenAccountRepository citizenRepository;

    @InjectMocks InvoiceService invoiceService;

    private final UUID citizenId = UUID.randomUUID();
    private final UUID meterId   = UUID.randomUUID();
    private final UUID invoiceId = UUID.randomUUID();

    private CitizenAccount citizen;
    private Meter meter;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        citizen = new CitizenAccount();
        citizen.setId(citizenId);
        citizen.setEmail("test@example.com");
        citizen.setUsername("testuser");

        meter = Meter.builder()
                .id(meterId)
                .citizenId(citizenId)
                .meterCode("ELEC-001")
                .meterType("ELECTRICITY")
                .registeredAt(LocalDateTime.now())
                .build();

        invoice = Invoice.builder()
                .id(invoiceId)
                .citizenId(citizenId)
                .meterId(meterId)
                .billingMonth(3)
                .billingYear(2026)
                .meterType("ELECTRICITY")
                .unitsConsumed(new BigDecimal("120.50"))
                .unitPrice(new BigDecimal("2.50"))
                .amount(new BigDecimal("301.25"))
                .status("UNPAID")
                .issuedAt(LocalDateTime.now().minusDays(5))
                .build();
    }

    @Test
    @DisplayName("registerMeter: should persist and return meter DTO")
    void registerMeter_success() {
        when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));
        when(meterRepository.findByMeterCode("ELEC-001")).thenReturn(Optional.empty());
        when(meterRepository.save(any(Meter.class))).thenReturn(meter);

        var dto = invoiceService.registerMeter(citizenId, "ELEC-001", "ELECTRICITY");

        assertThat(dto).isNotNull();
        assertThat(dto.getMeterCode()).isEqualTo("ELEC-001");
        assertThat(dto.getMeterType()).isEqualTo("ELECTRICITY");
    }

    @Test
    @DisplayName("registerMeter: should reject duplicate meter code")
    void registerMeter_duplicateMeterCode() {
        when(citizenRepository.findById(citizenId)).thenReturn(Optional.of(citizen));
        when(meterRepository.findByMeterCode("ELEC-001")).thenReturn(Optional.of(meter));

        assertThatThrownBy(() -> invoiceService.registerMeter(citizenId, "ELEC-001", "ELECTRICITY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Meter code already registered");
    }

    @Test
    @DisplayName("registerMeter: should reject unknown citizen")
    void registerMeter_citizenNotFound() {
        when(citizenRepository.findById(citizenId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.registerMeter(citizenId, "ELEC-001", "ELECTRICITY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Citizen not found");
    }

    @Test
    @DisplayName("getInvoices: should return paged invoices for citizen")
    void getInvoices_returnsPaged() {
        Page<Invoice> page = new PageImpl<>(List.of(invoice));
        when(invoiceRepository.findByCitizenId(citizenId, PageRequest.of(0, 10))).thenReturn(page);

        var result = invoiceService.getInvoices(citizenId, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getBillingMonth()).isEqualTo(3);
        assertThat(result.getContent().get(0).getBillingYear()).isEqualTo(2026);
    }

    @Test
    @DisplayName("getInvoicesByMonth: should filter by year and month")
    void getInvoicesByMonth_filtersCorrectly() {
        when(invoiceRepository.findByYearAndMonth(citizenId, 2026, 3)).thenReturn(List.of(invoice));

        var result = invoiceService.getInvoicesByMonth(citizenId, 2026, 3);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMeterType()).isEqualTo("ELECTRICITY");
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("301.25");
    }

    @Test
    @DisplayName("getInvoiceById: should return invoice detail")
    void getInvoiceById_success() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        var dto = invoiceService.getInvoiceById(invoiceId);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(invoiceId);
        assertThat(dto.getUnitsConsumed()).isEqualByComparingTo("120.50");
    }

    @Test
    @DisplayName("getInvoiceById: should throw when not found")
    void getInvoiceById_notFound() {
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.getInvoiceById(invoiceId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invoice not found");
    }

    @Test
    @DisplayName("getConsumptionHistory: should throw when meter not found")
    void getConsumptionHistory_meterNotFound() {
        when(meterRepository.findById(meterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.getConsumptionHistory(meterId, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Meter not found");
    }

    @Test
    @DisplayName("getConsumptionHistory: should return records for last N months")
    void getConsumptionHistory_success() {
        when(meterRepository.findById(meterId)).thenReturn(Optional.of(meter));
        when(consumptionRepository.findByMeterAndDateRange(any(), any(), any()))
                .thenReturn(List.of());

        var result = invoiceService.getConsumptionHistory(meterId, 3);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("getMetersByCitizen: should return all citizen meters")
    void getMetersByCitizen_returnsList() {
        when(meterRepository.findByCitizenId(citizenId)).thenReturn(List.of(meter));

        var result = invoiceService.getMetersByCitizen(citizenId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMeterCode()).isEqualTo("ELEC-001");
    }
}
