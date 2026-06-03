package com.uip.backend.esg.service;

import com.uip.backend.esg.domain.EsgReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EsgPdfService — PDF generation delegation + report setup.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EsgPdfService — PDF generation")
class EsgPdfServiceTest {

    @Mock private EsgReportGenerator reportGenerator;

    private EsgPdfService service;

    private static final byte[] FAKE_PDF = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF magic bytes

    @BeforeEach
    void setUp() {
        service = new EsgPdfService(reportGenerator);
    }

    @Test
    @DisplayName("generatePdf — delegates to EsgReportGenerator with format=pdf")
    void generatePdf_delegatesToGenerator() {
        when(reportGenerator.exportReport(any(EsgReport.class), eq("pdf"))).thenReturn(FAKE_PDF);

        byte[] result = service.generatePdf("hcm", 2026, 2);

        assertThat(result).isEqualTo(FAKE_PDF);
        verify(reportGenerator).exportReport(any(EsgReport.class), eq("pdf"));
    }

    @Test
    @DisplayName("generatePdf — EsgReport built with correct tenant/year/quarter")
    void generatePdf_buildsCorrectReport() {
        when(reportGenerator.exportReport(any(EsgReport.class), anyString())).thenReturn(FAKE_PDF);

        service.generatePdf("hcm", 2026, 3);

        ArgumentCaptor<EsgReport> captor = ArgumentCaptor.forClass(EsgReport.class);
        verify(reportGenerator).exportReport(captor.capture(), eq("pdf"));

        EsgReport report = captor.getValue();
        assertThat(report.getTenantId()).isEqualTo("hcm");
        assertThat(report.getYear()).isEqualTo(2026);
        assertThat(report.getQuarter()).isEqualTo(3);
        assertThat(report.getPeriodType()).isEqualTo("QUARTERLY");
    }

    @Test
    @DisplayName("generatePdf — returns non-empty bytes on success")
    void generatePdf_returnsNonEmptyBytes() {
        when(reportGenerator.exportReport(any(EsgReport.class), anyString()))
                .thenReturn(new byte[1024]);

        byte[] result = service.generatePdf("hcm", 2026, 1);

        assertThat(result).isNotEmpty().hasSize(1024);
    }

    @Test
    @DisplayName("generatePdf — different quarters produce separate calls")
    void generatePdf_differentQuarters() {
        when(reportGenerator.exportReport(any(EsgReport.class), anyString())).thenReturn(FAKE_PDF);

        service.generatePdf("hcm", 2026, 1);
        service.generatePdf("hcm", 2026, 4);

        ArgumentCaptor<EsgReport> captor = ArgumentCaptor.forClass(EsgReport.class);
        verify(reportGenerator, times(2)).exportReport(captor.capture(), eq("pdf"));

        assertThat(captor.getAllValues()).extracting(EsgReport::getQuarter)
                .containsExactly(1, 4);
    }
}
