package com.uip.backend.esg.export;

import com.uip.backend.esg.domain.EsgMetric;
import com.uip.backend.esg.domain.EsgMetricId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EsgExportTest {

    private EsgReportData data;
    private DefaultCsvExportAdapter csv;
    private DefaultXlsxExportAdapter xlsx;

    @BeforeEach
    void setUp() {
        csv = new DefaultCsvExportAdapter();
        xlsx = new DefaultXlsxExportAdapter();

        EsgMetric metric = new EsgMetric();
        metric.setId(new EsgMetricId(1L, Instant.parse("2024-03-01T00:00:00Z")));
        metric.setSourceId("SRC-001");
        metric.setValue(123.45);
        metric.setBuildingId("BLD-01");
        metric.setDistrictCode("D1");

        data = EsgReportData.builder()
                .reportId(UUID.randomUUID())
                .tenantId("tenant-a")
                .year(2024)
                .quarter(1)
                .from(Instant.parse("2024-01-01T00:00:00Z"))
                .to(Instant.parse("2024-03-31T00:00:00Z"))
                .energyTotal(1000.0)
                .waterTotal(500.0)
                .carbonTotal(250.0)
                .energyMetrics(List.of(metric))
                .waterMetrics(List.of(metric))
                .carbonMetrics(List.of(metric))
                .build();
    }

    // --- EsgReportData tests ---

    @Test
    void reportData_builderAndAccessors() {
        assertThat(data.year()).isEqualTo(2024);
        assertThat(data.quarter()).isEqualTo(1);
        assertThat(data.tenantId()).isEqualTo("tenant-a");
        assertThat(data.energyTotal()).isEqualTo(1000.0);
        assertThat(data.waterTotal()).isEqualTo(500.0);
        assertThat(data.carbonTotal()).isEqualTo(250.0);
        assertThat(data.energyMetrics()).hasSize(1);
    }

    @Test
    void reportData_nullTotals() {
        EsgReportData nullData = EsgReportData.builder()
                .reportId(UUID.randomUUID()).tenantId("t").year(2024).quarter(2)
                .energyTotal(null).waterTotal(null).carbonTotal(null)
                .build();
        assertThat(nullData.energyTotal()).isNull();
        assertThat(nullData.waterTotal()).isNull();
        assertThat(nullData.carbonTotal()).isNull();
    }

    // --- DefaultCsvExportAdapter tests ---

    @Test
    void csv_formatId() {
        assertThat(csv.getFormatId()).isEqualTo("csv");
    }

    @Test
    void csv_contentType() {
        assertThat(csv.getContentType()).isEqualTo("text/csv");
    }

    @Test
    void csv_fileExtension() {
        assertThat(csv.getFileExtension()).isEqualTo("csv");
    }

    @Test
    void csv_export_containsHeader() {
        byte[] result = csv.export(data);
        String content = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).contains("ESG Quarterly Report");
        assertThat(content).contains("Q1 2024");
        assertThat(content).contains("tenant-a");
    }

    @Test
    void csv_export_containsTotals() {
        byte[] result = csv.export(data);
        String content = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).contains("1000.00");
        assertThat(content).contains("500.00");
        assertThat(content).contains("250.00");
    }

    @Test
    void csv_export_containsDetailSections() {
        byte[] result = csv.export(data);
        String content = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).contains("Energy Data");
        assertThat(content).contains("Water Data");
        assertThat(content).contains("Carbon Data");
        assertThat(content).contains("SRC-001");
    }

    @Test
    void csv_export_nullMetrics() {
        EsgReportData noMetrics = EsgReportData.builder()
                .reportId(UUID.randomUUID()).tenantId("t").year(2024).quarter(1)
                .energyTotal(0.0).waterTotal(0.0).carbonTotal(0.0)
                .energyMetrics(null).waterMetrics(null).carbonMetrics(null)
                .build();
        assertThatCode(() -> csv.export(noMetrics)).doesNotThrowAnyException();
    }

    @Test
    void csv_export_specialCharEscape() {
        EsgMetric m = new EsgMetric();
        m.setId(new EsgMetricId(2L, Instant.now()));
        m.setSourceId("SRC,COMMA");
        m.setValue(1.0);
        m.setBuildingId("BLD \"QUOTE\"");
        m.setDistrictCode(null);

        EsgReportData specialData = EsgReportData.builder()
                .reportId(UUID.randomUUID()).tenantId("t,en").year(2024).quarter(1)
                .energyTotal(1.0).waterTotal(1.0).carbonTotal(1.0)
                .energyMetrics(List.of(m)).waterMetrics(List.of()).carbonMetrics(List.of())
                .build();

        byte[] result = csv.export(specialData);
        String content = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).contains("\"SRC,COMMA\"");
    }

    @Test
    void csv_export_nullTotalsShowNA() {
        EsgReportData nullTotals = EsgReportData.builder()
                .reportId(UUID.randomUUID()).tenantId("t").year(2024).quarter(1)
                .energyTotal(null).waterTotal(null).carbonTotal(null)
                .build();
        byte[] result = csv.export(nullTotals);
        String content = new String(result, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).contains("N/A");
    }

    @Test
    void csv_export_bomPresent() {
        byte[] result = csv.export(data);
        assertThat(result[0]).isEqualTo((byte) 0xEF);
        assertThat(result[1]).isEqualTo((byte) 0xBB);
        assertThat(result[2]).isEqualTo((byte) 0xBF);
    }

    // --- DefaultXlsxExportAdapter tests ---

    @Test
    void xlsx_formatId() {
        assertThat(xlsx.getFormatId()).isEqualTo("xlsx");
    }

    @Test
    void xlsx_contentType() {
        assertThat(xlsx.getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @Test
    void xlsx_fileExtension() {
        assertThat(xlsx.getFileExtension()).isEqualTo("xlsx");
    }

    @Test
    void xlsx_export_returnsNonEmpty() {
        byte[] result = xlsx.export(data);
        assertThat(result).isNotNull().isNotEmpty();
        // XLSX magic bytes: PK (zip)
        assertThat(result[0]).isEqualTo((byte) 0x50);
        assertThat(result[1]).isEqualTo((byte) 0x4B);
    }

    @Test
    void xlsx_export_nullMetrics() {
        EsgReportData noMetrics = EsgReportData.builder()
                .reportId(UUID.randomUUID()).tenantId("t").year(2024).quarter(1)
                .energyTotal(100.0).waterTotal(50.0).carbonTotal(25.0)
                .energyMetrics(null).waterMetrics(null).carbonMetrics(null)
                .build();
        assertThatCode(() -> xlsx.export(noMetrics)).doesNotThrowAnyException();
    }

    @Test
    void xlsx_export_nullTotals() {
        EsgReportData nullTotals = EsgReportData.builder()
                .reportId(UUID.randomUUID()).tenantId("t").year(2024).quarter(2)
                .energyTotal(null).waterTotal(null).carbonTotal(null)
                .energyMetrics(List.of()).waterMetrics(List.of()).carbonMetrics(List.of())
                .build();
        byte[] result = xlsx.export(nullTotals);
        assertThat(result).isNotEmpty();
    }

    @Test
    void xlsx_export_metricNullBuildingAndDistrict() {
        EsgMetric m = new EsgMetric();
        m.setId(new EsgMetricId(3L, Instant.now()));
        m.setSourceId("SRC-NULL");
        m.setValue(42.0);
        m.setBuildingId(null);
        m.setDistrictCode(null);

        EsgReportData d = EsgReportData.builder()
                .reportId(UUID.randomUUID()).tenantId("t").year(2024).quarter(1)
                .energyTotal(1.0).waterTotal(1.0).carbonTotal(1.0)
                .energyMetrics(List.of(m)).waterMetrics(List.of()).carbonMetrics(List.of())
                .build();
        assertThatCode(() -> xlsx.export(d)).doesNotThrowAnyException();
    }

    // --- DuplicateExportFormatException tests ---

    @Test
    void duplicateException_message() {
        var ex = new DuplicateExportFormatException("csv", "AdapterA", "AdapterB");
        assertThat(ex.getMessage())
                .contains("csv")
                .contains("AdapterA")
                .contains("AdapterB");
    }

    @Test
    void duplicateException_isRuntimeException() {
        assertThat(new DuplicateExportFormatException("xlsx", "A", "B"))
                .isInstanceOf(RuntimeException.class);
    }
}
