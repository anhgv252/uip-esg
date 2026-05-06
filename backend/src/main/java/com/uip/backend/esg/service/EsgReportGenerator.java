package com.uip.backend.esg.service;

import com.uip.backend.esg.domain.EsgReport;
import com.uip.backend.esg.export.*;
import com.uip.backend.esg.repository.EsgMetricRepository;
import com.uip.backend.esg.repository.EsgReportRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Service
@RequiredArgsConstructor
@Slf4j
public class EsgReportGenerator {

    private final EsgMetricRepository esgMetricRepository;
    private final EsgReportRepository esgReportRepository;
    private final List<EsgReportExportPort> exportPorts;

    private Map<String, EsgReportExportPort> adapterMap;

    @PostConstruct
    void initAdapters() {
        Map<String, List<EsgReportExportPort>> grouped = exportPorts.stream()
                .collect(Collectors.groupingBy(EsgReportExportPort::getFormatId));
        Map<String, EsgReportExportPort> map = new HashMap<>();
        grouped.forEach((formatId, ports) -> {
            if (ports.size() > 1) {
                throw new DuplicateExportFormatException(formatId,
                        ports.get(0).getClass().getSimpleName(),
                        ports.get(1).getClass().getSimpleName());
            }
            map.put(formatId, ports.get(0));
        });
        this.adapterMap = Map.copyOf(map);
        log.info("ESG export adapters registered: {}", adapterMap.keySet());
    }

    public EsgReportExportPort resolveAdapter(String format) {
        String f = (format != null && !format.isBlank()) ? format.toLowerCase() : "xlsx";
        EsgReportExportPort adapter = adapterMap.get(f);
        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported export format: " + f + ". Supported: " + adapterMap.keySet());
        }
        return adapter;
    }

    public byte[] exportReport(EsgReport report, String format) {
        EsgReportExportPort adapter = resolveAdapter(format);
        Instant[] range = quarterRange(report.getYear(), report.getQuarter());
        String tenantId = report.getTenantId();

        Double energy = esgMetricRepository.sumByTypeAndRange(tenantId, "ENERGY", range[0], range[1]);
        Double water  = esgMetricRepository.sumByTypeAndRange(tenantId, "WATER",  range[0], range[1]);
        Double carbon = esgMetricRepository.sumByTypeAndRange(tenantId, "CARBON", range[0], range[1]);

        EsgReportData data = EsgReportData.builder()
                .reportId(report.getId())
                .tenantId(tenantId)
                .year(report.getYear())
                .quarter(report.getQuarter())
                .from(range[0])
                .to(range[1])
                .energyTotal(energy)
                .waterTotal(water)
                .carbonTotal(carbon)
                .energyMetrics(esgMetricRepository.findByTypeAndRange(tenantId, "ENERGY", range[0], range[1]))
                .waterMetrics(esgMetricRepository.findByTypeAndRange(tenantId, "WATER", range[0], range[1]))
                .carbonMetrics(esgMetricRepository.findByTypeAndRange(tenantId, "CARBON", range[0], range[1]))
                .build();

        return adapter.export(data);
    }

    @Value("${esg.report.output-dir:${java.io.tmpdir}/uip-reports}")
    private String outputDir;

    @Async("reportTaskExecutor")
    public void generateAsync(UUID reportId) {
        EsgReport report = esgReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        report.setStatus("GENERATING");
        report.setUpdatedAt(Instant.now());
        esgReportRepository.save(report);

        try {
            String filePath = buildReport(report);
            report.setStatus("DONE");
            report.setFilePath(filePath);
            report.setGeneratedAt(Instant.now());
            log.info("ESG report generated: reportId={} tenant={} file={}", reportId, report.getTenantId(), filePath);
        } catch (Exception e) {
            report.setStatus("FAILED");
            log.error("ESG report generation failed: reportId={}", reportId, e);
        }
        report.setUpdatedAt(Instant.now());
        esgReportRepository.save(report);
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "outputDir is @Value-injected server config, not user input; guarded by getCanonicalFile() + startsWith(dir) check on next line")
    private String buildReport(EsgReport report) throws IOException {
        String tenantId = report.getTenantId();
        // getCanonicalFile() resolves symlinks and normalizes traversal sequences,
        // producing a trusted canonical path (FindSecBugs-recognized PATH_TRAVERSAL sanitizer).
        Path dir = new File(outputDir).getCanonicalFile().toPath();
        Files.createDirectories(dir);

        String fileName = "esg-report-%s-Q%d-%d.xlsx"
                .formatted(report.getId(), report.getQuarter(), report.getYear());
        Path outputPath = dir.resolve(fileName).normalize();
        if (!outputPath.startsWith(dir)) {
            throw new IOException("Computed output path escapes configured report directory");
        }

        Instant[] range = quarterRange(report.getYear(), report.getQuarter());

        try (Workbook wb = new XSSFWorkbook();
             OutputStream out = Files.newOutputStream(outputPath)) {

            // Summary sheet
            Sheet summarySheet = wb.createSheet("ESG Summary");
            CellStyle headerStyle = createHeaderStyle(wb);

            Row titleRow = summarySheet.createRow(0);
            createCell(titleRow, 0, "UIP Smart City — ESG Quarterly Report", headerStyle);
            summarySheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

            Row periodRow = summarySheet.createRow(1);
            createCell(periodRow, 0, "Period:");
            createCell(periodRow, 1, "Q%d %d".formatted(report.getQuarter(), report.getYear()));

            Row header = summarySheet.createRow(3);
            String[] cols = {"Metric", "Value", "Unit", "vs Target"};
            for (int i = 0; i < cols.length; i++) {
                createCell(header, i, cols[i], headerStyle);
            }

            Double energy = esgMetricRepository.sumByTypeAndRange(tenantId, "ENERGY", range[0], range[1]);
            Double water  = esgMetricRepository.sumByTypeAndRange(tenantId, "WATER",  range[0], range[1]);
            Double carbon = esgMetricRepository.sumByTypeAndRange(tenantId, "CARBON", range[0], range[1]);

            addMetricRow(summarySheet, 4, "Total Energy Consumption", energy, "kWh");
            addMetricRow(summarySheet, 5, "Total Water Consumption",  water,  "m³");
            addMetricRow(summarySheet, 6, "Total Carbon Emissions",   carbon, "tCO₂e");

            // Auto-size columns
            for (int i = 0; i < 4; i++) summarySheet.autoSizeColumn(i);

            // Energy detail sheet
            buildDetailSheet(wb, "Energy Data", tenantId, "ENERGY", "kWh", range);
            buildDetailSheet(wb, "Water Data",  tenantId, "WATER",  "m³",  range);
            buildDetailSheet(wb, "Carbon Data", tenantId, "CARBON", "tCO₂e", range);

            wb.write(out);
        }

        return outputPath.toString();
    }

    private void buildDetailSheet(Workbook wb, String sheetName, String tenantId,
                                   String metricType, String unit, Instant[] range) {
        Sheet sheet = wb.createSheet(sheetName);
        CellStyle headerStyle = createHeaderStyle(wb);

        Row header = sheet.createRow(0);
        String[] cols = {"Source ID", "Timestamp", "Value (" + unit + ")", "Building", "District"};
        for (int i = 0; i < cols.length; i++) createCell(header, i, cols[i], headerStyle);

        var metrics = esgMetricRepository.findByTypeAndRange(tenantId, metricType, range[0], range[1]);
        int rowNum = 1;
        for (var m : metrics) {
            Row row = sheet.createRow(rowNum++);
            createCell(row, 0, m.getSourceId());
            createCell(row, 1, m.getId().getTimestamp().toString());
            createCell(row, 2, m.getValue().toString());
            createCell(row, 3, m.getBuildingId() != null ? m.getBuildingId() : "");
            createCell(row, 4, m.getDistrictCode() != null ? m.getDistrictCode() : "");
        }
        for (int i = 0; i < 5; i++) sheet.autoSizeColumn(i);
    }

    private Instant[] quarterRange(int year, int quarter) {
        int startMonth = (quarter - 1) * 3 + 1;
        LocalDate start = LocalDate.of(year, startMonth, 1);
        LocalDate end   = start.plusMonths(3);
        return new Instant[]{
            start.atStartOfDay().toInstant(ZoneOffset.UTC),
            end.atStartOfDay().toInstant(ZoneOffset.UTC)
        };
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void createCell(Row row, int col, String value) {
        row.createCell(col).setCellValue(value != null ? value : "");
    }

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private void addMetricRow(Sheet sheet, int rowNum, String label, Double value, String unit) {
        Row row = sheet.createRow(rowNum);
        createCell(row, 0, label);
        createCell(row, 1, value != null ? String.format("%.2f", value) : "N/A");
        createCell(row, 2, unit);
        createCell(row, 3, "—");
    }
}
