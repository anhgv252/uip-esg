package com.uip.backend.esg.export;

import com.uip.backend.esg.domain.EsgMetric;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultXlsxExportAdapter implements EsgReportExportPort {

    @Override
    public String getFormatId() { return "xlsx"; }

    @Override
    public String getContentType() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override
    public String getFileExtension() { return "xlsx"; }

    @Override
    public byte[] export(EsgReportData data) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = createHeaderStyle(wb);

            Sheet summarySheet = wb.createSheet("ESG Summary");
            Row titleRow = summarySheet.createRow(0);
            createCell(titleRow, 0, "UIP Smart City — ESG Quarterly Report", headerStyle);
            summarySheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

            Row periodRow = summarySheet.createRow(1);
            createCell(periodRow, 0, "Period:");
            createCell(periodRow, 1, "Q%d %d".formatted(data.quarter(), data.year()));

            Row header = summarySheet.createRow(3);
            String[] cols = {"Metric", "Value", "Unit", "vs Target"};
            for (int i = 0; i < cols.length; i++) createCell(header, i, cols[i], headerStyle);

            addMetricRow(summarySheet, 4, "Total Energy Consumption", data.energyTotal(), "kWh");
            addMetricRow(summarySheet, 5, "Total Water Consumption", data.waterTotal(), "m³");
            addMetricRow(summarySheet, 6, "Total Carbon Emissions", data.carbonTotal(), "tCO₂e");

            for (int i = 0; i < 4; i++) summarySheet.autoSizeColumn(i);

            buildDetailSheet(wb, "Energy Data", data.energyMetrics(), "kWh", headerStyle);
            buildDetailSheet(wb, "Water Data", data.waterMetrics(), "m³", headerStyle);
            buildDetailSheet(wb, "Carbon Data", data.carbonMetrics(), "tCO₂e", headerStyle);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate XLSX report", e);
        }
    }

    private void buildDetailSheet(Workbook wb, String sheetName, List<EsgMetric> metrics,
                                   String unit, CellStyle headerStyle) {
        Sheet sheet = wb.createSheet(sheetName);
        Row header = sheet.createRow(0);
        String[] cols = {"Source ID", "Timestamp", "Value (" + unit + ")", "Building", "District"};
        for (int i = 0; i < cols.length; i++) createCell(header, i, cols[i], headerStyle);

        if (metrics != null) {
            int rowNum = 1;
            for (var m : metrics) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, m.getSourceId());
                createCell(row, 1, m.getId().getTimestamp().toString());
                createCell(row, 2, m.getValue().toString());
                createCell(row, 3, m.getBuildingId() != null ? m.getBuildingId() : "");
                createCell(row, 4, m.getDistrictCode() != null ? m.getDistrictCode() : "");
            }
        }
        for (int i = 0; i < 5; i++) sheet.autoSizeColumn(i);
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
