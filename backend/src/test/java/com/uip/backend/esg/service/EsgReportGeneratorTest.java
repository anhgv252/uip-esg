package com.uip.backend.esg.service;

import com.uip.backend.esg.domain.EsgMetric;
import com.uip.backend.esg.domain.EsgMetricId;
import com.uip.backend.esg.domain.EsgReport;
import com.uip.backend.esg.repository.EsgMetricRepository;
import com.uip.backend.esg.repository.EsgReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EsgReportGenerator")
class EsgReportGeneratorTest {

    @Mock
    private EsgMetricRepository esgMetricRepository;

    @Mock
    private EsgReportRepository esgReportRepository;

    @InjectMocks
    private EsgReportGenerator generator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(generator, "outputDir", tempDir.toString());
    }

    // -------------------------------------------------------------------------
    // generateAsync — happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateAsync: creates XLSX file and marks report DONE")
    void generateAsync_happyPath_statusDone() {
        UUID id = UUID.randomUUID();
        EsgReport report = buildReport(id, 2025, 1);

        when(esgReportRepository.findById(id)).thenReturn(Optional.of(report));
        when(esgReportRepository.save(any())).thenReturn(report);
        when(esgMetricRepository.sumByTypeAndRange(anyString(), any(), any())).thenReturn(100.0);
        when(esgMetricRepository.findByTypeAndRange(anyString(), any(), any()))
                .thenReturn(List.of());

        generator.generateAsync(id);

        assertThat(report.getStatus()).isEqualTo("DONE");
        assertThat(report.getFilePath()).isNotNull().endsWith(".xlsx");
        assertThat(report.getGeneratedAt()).isNotNull();
        // save called twice: once to set GENERATING, once at the end
        verify(esgReportRepository, times(2)).save(report);
    }

    @Test
    @DisplayName("generateAsync: with metric data populates detail sheets")
    void generateAsync_withMetrics_populatesDetailSheets() {
        UUID id = UUID.randomUUID();
        EsgReport report = buildReport(id, 2025, 2);

        EsgMetric m1 = buildMetric("ENERGY", "BLDG-01", "D1", 42.5);
        EsgMetric m2 = buildMetric("ENERGY", null, null, 10.0);

        when(esgReportRepository.findById(id)).thenReturn(Optional.of(report));
        when(esgReportRepository.save(any())).thenReturn(report);
        when(esgMetricRepository.sumByTypeAndRange(anyString(), any(), any())).thenReturn(null);
        when(esgMetricRepository.findByTypeAndRange(eq("ENERGY"), any(), any()))
                .thenReturn(List.of(m1, m2));
        when(esgMetricRepository.findByTypeAndRange(eq("WATER"), any(), any()))
                .thenReturn(List.of());
        when(esgMetricRepository.findByTypeAndRange(eq("CARBON"), any(), any()))
                .thenReturn(List.of());

        generator.generateAsync(id);

        assertThat(report.getStatus()).isEqualTo("DONE");
    }

    @Test
    @DisplayName("generateAsync: null metric sums renders N/A gracefully")
    void generateAsync_nullSums_rendersNA() {
        UUID id = UUID.randomUUID();
        EsgReport report = buildReport(id, 2025, 4);

        when(esgReportRepository.findById(id)).thenReturn(Optional.of(report));
        when(esgReportRepository.save(any())).thenReturn(report);
        when(esgMetricRepository.sumByTypeAndRange(anyString(), any(), any())).thenReturn(null);
        when(esgMetricRepository.findByTypeAndRange(anyString(), any(), any()))
                .thenReturn(List.of());

        generator.generateAsync(id);

        assertThat(report.getStatus()).isEqualTo("DONE");
    }

    // -------------------------------------------------------------------------
    // generateAsync — failure paths
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateAsync: report not found → IllegalArgumentException propagates")
    void generateAsync_reportNotFound_throwsException() {
        UUID id = UUID.randomUUID();
        when(esgReportRepository.findById(id)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> generator.generateAsync(id)
        );
    }

    @Test
    @DisplayName("generateAsync: repository failure during build → status FAILED")
    void generateAsync_repositoryFailure_statusFailed() {
        UUID id = UUID.randomUUID();
        EsgReport report = buildReport(id, 2025, 3);

        when(esgReportRepository.findById(id)).thenReturn(Optional.of(report));
        when(esgReportRepository.save(any())).thenReturn(report);
        when(esgMetricRepository.sumByTypeAndRange(anyString(), any(), any()))
                .thenThrow(new RuntimeException("DB connection refused"));

        generator.generateAsync(id);

        assertThat(report.getStatus()).isEqualTo("FAILED");
        verify(esgReportRepository, times(2)).save(report);
    }

    // -------------------------------------------------------------------------
    // Quarter → date range
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateAsync: Q1 range starts Jan 1 and ends Mar 31")
    void generateAsync_q1Range_correctBoundaries() {
        UUID id = UUID.randomUUID();
        EsgReport report = buildReport(id, 2025, 1);

        when(esgReportRepository.findById(id)).thenReturn(Optional.of(report));
        when(esgReportRepository.save(any())).thenReturn(report);
        when(esgMetricRepository.sumByTypeAndRange(anyString(), any(), any())).thenReturn(0.0);
        when(esgMetricRepository.findByTypeAndRange(anyString(), any(), any()))
                .thenReturn(List.of());

        generator.generateAsync(id);

        // Captures the 'from' instant for the first sumByTypeAndRange call (ENERGY)
        var fromCaptor = org.mockito.ArgumentCaptor.forClass(Instant.class);
        var toCaptor   = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(esgMetricRepository, atLeastOnce())
                .sumByTypeAndRange(anyString(), fromCaptor.capture(), toCaptor.capture());

        Instant from = fromCaptor.getAllValues().get(0);
        Instant to   = toCaptor.getAllValues().get(0);
        assertThat(from.toString()).startsWith("2025-01-01");
        assertThat(to.toString()).startsWith("2025-04-01");
    }

    @Test
    @DisplayName("generateAsync: Q4 range starts Oct 1 and ends Jan 1 next year")
    void generateAsync_q4Range_correctBoundaries() {
        UUID id = UUID.randomUUID();
        EsgReport report = buildReport(id, 2024, 4);

        when(esgReportRepository.findById(id)).thenReturn(Optional.of(report));
        when(esgReportRepository.save(any())).thenReturn(report);
        when(esgMetricRepository.sumByTypeAndRange(anyString(), any(), any())).thenReturn(0.0);
        when(esgMetricRepository.findByTypeAndRange(anyString(), any(), any()))
                .thenReturn(List.of());

        generator.generateAsync(id);

        var fromCaptor = org.mockito.ArgumentCaptor.forClass(Instant.class);
        var toCaptor   = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(esgMetricRepository, atLeastOnce())
                .sumByTypeAndRange(anyString(), fromCaptor.capture(), toCaptor.capture());

        Instant from = fromCaptor.getAllValues().get(0);
        Instant to   = toCaptor.getAllValues().get(0);
        assertThat(from.toString()).startsWith("2024-10-01");
        assertThat(to.toString()).startsWith("2025-01-01");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private EsgReport buildReport(UUID id, int year, int quarter) {
        EsgReport r = new EsgReport();
        r.setId(id);
        r.setYear(year);
        r.setQuarter(quarter);
        r.setPeriodType("QUARTERLY");
        r.setStatus("PENDING");
        return r;
    }

    private EsgMetric buildMetric(String type, String buildingId, String districtCode, double value) {
        EsgMetric m = new EsgMetric();
        m.setId(new EsgMetricId(1L, Instant.now()));
        m.setMetricType(type);
        m.setSourceId("SRC-001");
        m.setValue(value);
        m.setUnit("kWh");
        m.setBuildingId(buildingId);
        m.setDistrictCode(districtCode);
        return m;
    }
}
