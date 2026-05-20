package com.uip.backend.esg.export;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.util.Map;

@Component
public class DefaultPdfExportAdapter implements EsgReportExportPort {

    private static final Font TITLE_FONT  = new Font(Font.HELVETICA, 18, Font.BOLD, Color.BLACK);
    private static final Font HEADER_FONT = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);
    private static final Font NORMAL_FONT = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);
    private static final Font SECTION_FONT = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(0x1A, 0x23, 0x7E));

    private static final Color HEADER_BG = new Color(0x1A, 0x23, 0x7E);
    private static final Color ROW_ALT    = new Color(0xE8, 0xEA, 0xF6);

    @Override
    public String getFormatId() { return "pdf"; }

    @Override
    public String getContentType() { return "application/pdf"; }

    @Override
    public String getFileExtension() { return "pdf"; }

    @Override
    public byte[] export(EsgReportData data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 54, 36);
            PdfWriter.getInstance(doc, out);
            doc.open();

            // Title
            Paragraph title = new Paragraph("UIP Smart City — ESG Quarterly Report", TITLE_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            Paragraph period = new Paragraph(
                    "Period: Q%d %d".formatted(data.quarter(), data.year()), NORMAL_FONT);
            period.setAlignment(Element.ALIGN_CENTER);
            period.setSpacingAfter(12);
            doc.add(period);

            doc.add(Chunk.NEWLINE);

            // GRI 302-1 Energy Disclosure
            doc.add(new Paragraph("GRI 302-1: Energy Consumption (Disclosure)", SECTION_FONT));
            doc.add(Chunk.NEWLINE);
            PdfPTable energyTable = new PdfPTable(new float[]{3, 2, 1});
            energyTable.setWidthPercentage(100);
            addTableHeader(energyTable, "Metric", "Value", "Unit");
            addTableRow(energyTable, false, "Total Energy Consumption", fmt(data.energyTotal()), "kWh");
            addTableRow(energyTable, true, "Energy Intensity", fmt(data.energyIntensityKwhPerM2()), "kWh/m²");
            addTableRow(energyTable, false, "Data Quality", data.dataQuality() != null ? data.dataQuality() : "N/A", "");
            doc.add(energyTable);
            doc.add(Chunk.NEWLINE);

            if (data.buildingBreakdown() != null && !data.buildingBreakdown().isEmpty()) {
                doc.add(new Paragraph("Per-Building Breakdown", SECTION_FONT));
                doc.add(Chunk.NEWLINE);
                PdfPTable bldgTable = new PdfPTable(new float[]{3, 2, 1});
                bldgTable.setWidthPercentage(100);
                addTableHeader(bldgTable, "Building ID", "kWh", "% of Total");
                double totalKwh = data.energyTotal() != null ? data.energyTotal() : 0.0;
                boolean alt = false;
                for (Map.Entry<String, Double> entry : data.buildingBreakdown().entrySet()) {
                    String pct = totalKwh > 0 ? "%.1f%%".formatted(entry.getValue() / totalKwh * 100) : "—";
                    addTableRow(bldgTable, alt, entry.getKey(), "%.2f".formatted(entry.getValue()), pct);
                    alt = !alt;
                }
                doc.add(bldgTable);
                doc.add(Chunk.NEWLINE);
            }

            // GRI 305-4 Emissions Disclosure
            doc.add(new Paragraph("GRI 305-4: Emissions (Disclosure)", SECTION_FONT));
            doc.add(Chunk.NEWLINE);
            PdfPTable emitTable = new PdfPTable(new float[]{3, 2, 1});
            emitTable.setWidthPercentage(100);
            addTableHeader(emitTable, "Metric", "Value", "Unit");
            addTableRow(emitTable, false, "Total CO₂ Emissions", fmt(data.carbonTotal()), "tCO₂e");
            addTableRow(emitTable, true, "CO₂ Emissions Intensity", fmt(data.co2EmissionsPerM2()), "tCO₂e/m²");
            addTableRow(emitTable, false, "Data Quality", data.dataQuality() != null ? data.dataQuality() : "N/A", "");
            doc.add(emitTable);
            doc.add(Chunk.NEWLINE);

            // Summary section
            doc.add(new Paragraph("Summary", SECTION_FONT));
            doc.add(Chunk.NEWLINE);
            PdfPTable summaryTable = new PdfPTable(new float[]{3, 2, 1, 1});
            summaryTable.setWidthPercentage(100);
            addTableHeader(summaryTable, "Metric", "Value", "Unit", "vs Target");
            addTableRow(summaryTable, false, "Total Energy Consumption", fmt(data.energyTotal()), "kWh", "—");
            addTableRow(summaryTable, true, "Total Water Consumption", fmt(data.waterTotal()), "m³", "—");
            addTableRow(summaryTable, false, "Total Carbon Emissions", fmt(data.carbonTotal()), "tCO₂e", "—");
            doc.add(summaryTable);

            // Footer
            doc.add(Chunk.NEWLINE);
            Paragraph footer = new Paragraph(
                    "Generated by UIP Smart City Platform — Report ID: " + data.reportId(), NORMAL_FONT);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }

    private void addTableHeader(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, HEADER_FONT));
            cell.setBackgroundColor(HEADER_BG);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(6);
            table.addCell(cell);
        }
    }

    private void addTableRow(PdfPTable table, boolean alt, String... values) {
        for (String v : values) {
            Phrase phrase = new Phrase(v != null ? v : "", NORMAL_FONT);
            PdfPCell cell = new PdfPCell(phrase);
            if (alt) cell.setBackgroundColor(ROW_ALT);
            cell.setPadding(4);
            table.addCell(cell);
        }
    }

    private String fmt(Double value) {
        return value != null ? "%.2f".formatted(value) : "N/A";
    }
}
