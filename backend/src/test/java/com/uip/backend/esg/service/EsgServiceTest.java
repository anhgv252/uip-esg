package com.uip.backend.esg.service;

import com.uip.backend.esg.api.dto.EsgSummaryDto;
import com.uip.backend.esg.domain.EsgReport;
import com.uip.backend.esg.repository.EsgMetricRepository;
import com.uip.backend.esg.repository.EsgReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EsgService")
class EsgServiceTest {

    private static final String TENANT_ID = "hcm";

    @Mock private EsgMetricRepository  metricRepository;
    @Mock private EsgReportRepository  reportRepository;
    @Mock private EsgReportGenerator   reportGenerator;

    @InjectMocks private EsgService esgService;

    @Captor private ArgumentCaptor<EsgReport> reportCaptor;

    // ─── getSummary ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSummary(QUARTERLY) sums all four metric types for tenant")
    void getSummary_quarterly_returnsAggregates() {
        when(metricRepository.sumByTypeAndRange(eq(TENANT_ID), eq("ENERGY"), any(), any())).thenReturn(1000.0);
        when(metricRepository.sumByTypeAndRange(eq(TENANT_ID), eq("WATER"),  any(), any())).thenReturn(500.0);
        when(metricRepository.sumByTypeAndRange(eq(TENANT_ID), eq("CARBON"), any(), any())).thenReturn(200.0);
        when(metricRepository.sumByTypeAndRange(eq(TENANT_ID), eq("WASTE"),  any(), any())).thenReturn(50.0);

        EsgSummaryDto dto = esgService.getSummary(TENANT_ID, "QUARTERLY", 2025, 1);

        assertThat(dto.getTotalEnergyKwh()).isEqualTo(1000.0);
        assertThat(dto.getTotalWaterM3()).isEqualTo(500.0);
        assertThat(dto.getTotalCarbonTco2e()).isEqualTo(200.0);
        assertThat(dto.getTotalWasteTons()).isEqualTo(50.0);
        assertThat(dto.getYear()).isEqualTo(2025);
        assertThat(dto.getQuarter()).isEqualTo(1);
    }

    @Test
    @DisplayName("getSummary: null metric values are handled gracefully (not NPE)")
    void getSummary_nullMetrics_doesNotThrow() {
        when(metricRepository.sumByTypeAndRange(eq(TENANT_ID), anyString(), any(), any())).thenReturn(null);

        EsgSummaryDto dto = esgService.getSummary(TENANT_ID, "QUARTERLY", 2025, 2);

        assertThat(dto).isNotNull();
        assertThat(dto.getTotalEnergyKwh()).isNull();
    }

    @Test
    @DisplayName("getSummary: different tenants get isolated data")
    void getSummary_differentTenants_queriesIsolated() {
        when(metricRepository.sumByTypeAndRange(eq("tenant-a"), anyString(), any(), any())).thenReturn(100.0);
        when(metricRepository.sumByTypeAndRange(eq("tenant-b"), anyString(), any(), any())).thenReturn(200.0);

        EsgSummaryDto dtoA = esgService.getSummary("tenant-a", "QUARTERLY", 2025, 1);
        EsgSummaryDto dtoB = esgService.getSummary("tenant-b", "QUARTERLY", 2025, 1);

        assertThat(dtoA.getTotalEnergyKwh()).isEqualTo(100.0);
        assertThat(dtoB.getTotalEnergyKwh()).isEqualTo(200.0);
    }

    // ─── triggerReportGeneration ──────────────────────────────────────────────

    @Test
    @DisplayName("triggerReportGeneration saves report with tenantId and invokes async generator")
    void triggerReportGeneration_savesAndDispatchesAsync() {
        EsgReport savedReport = new EsgReport();
        UUID reportId = UUID.randomUUID();
        when(reportRepository.save(any(EsgReport.class))).thenReturn(savedReport);
        savedReport.setId(reportId);
        savedReport.setStatus("PENDING");

        esgService.triggerReportGeneration(TENANT_ID, "QUARTERLY", 2025, 1);

        verify(reportRepository).save(reportCaptor.capture());
        EsgReport persisted = reportCaptor.getValue();
        assertThat(persisted.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(persisted.getPeriodType()).isEqualTo("QUARTERLY");
        assertThat(persisted.getYear()).isEqualTo(2025);
        assertThat(persisted.getQuarter()).isEqualTo(1);

        verify(reportGenerator).generateAsync(reportId);
    }

    // ─── getReportStatus ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getReportStatus returns DTO when report exists")
    void getReportStatus_found_returnsDto() {
        UUID id = UUID.randomUUID();
        EsgReport report = new EsgReport();
        report.setId(id);
        report.setStatus("DONE");
        when(reportRepository.findById(id)).thenReturn(Optional.of(report));

        var dto = esgService.getReportStatus(TENANT_ID, id);

        assertThat(dto.getStatus()).isEqualTo("DONE");
    }

    @Test
    @DisplayName("getReportStatus throws EntityNotFoundException when not found")
    void getReportStatus_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(reportRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> esgService.getReportStatus(TENANT_ID, id))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    // ─── getReportForDownload ─────────────────────────────────────────────────

    @Test
    @DisplayName("getReportForDownload throws when report status is GENERATING")
    void getReportForDownload_notDone_throwsIllegalState() {
        UUID id = UUID.randomUUID();
        EsgReport report = new EsgReport();
        report.setId(id);
        report.setStatus("GENERATING");
        when(reportRepository.findById(id)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> esgService.getReportForDownload(TENANT_ID, id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not ready");
    }

    @Test
    @DisplayName("getReportForDownload returns report when status is DONE")
    void getReportForDownload_done_returnsReport() {
        UUID id = UUID.randomUUID();
        EsgReport report = new EsgReport();
        report.setId(id);
        report.setStatus("DONE");
        report.setFilePath("/tmp/report.xlsx");
        when(reportRepository.findById(id)).thenReturn(Optional.of(report));

        EsgReport result = esgService.getReportForDownload(TENANT_ID, id);

        assertThat(result.getFilePath()).endsWith(".xlsx");
    }

    // ─── Anomaly detection ────────────────────────────────────────────────────

    @Test
    @DisplayName("detectUtilityAnomalies filters by tenantId")
    void detectUtilityAnomalies_usesTenantId() {
        // ENERGY: current=150, historical=100 → 150 > 100*1.3=130 → anomaly
        when(metricRepository.sumByTypeAndRange(eq(TENANT_ID), eq("ENERGY"), any(), any()))
                .thenReturn(150.0)   // current
                .thenReturn(100.0);  // historical
        // WATER: current=0 → skipped
        when(metricRepository.sumByTypeAndRange(eq(TENANT_ID), eq("WATER"), any(), any()))
                .thenReturn(0.0);

        var anomalies = esgService.detectUtilityAnomalies(TENANT_ID);

        assertThat(anomalies).hasSize(1);
        assertThat(anomalies.get(0).metricType()).isEqualTo("energy");
    }
}
