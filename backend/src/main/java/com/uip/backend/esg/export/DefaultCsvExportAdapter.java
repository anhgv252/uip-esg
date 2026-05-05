package com.uip.backend.esg.export;

import com.uip.backend.esg.domain.EsgMetric;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class DefaultCsvExportAdapter implements EsgReportExportPort {

    @Override
    public String getFormatId() { return "csv"; }

    @Override
    public String getContentType() { return "text/csv"; }

    @Override
    public String getFileExtension() { return "csv"; }

    @Override
    public byte[] export(EsgReportData data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {

            // BOM for Excel UTF-8 recognition
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            // Summary section
            writer.write("ESG Quarterly Report\n");
            writer.write("Period,Q" + data.quarter() + " " + data.year() + "\n");
            writer.write("Tenant," + escape(data.tenantId()) + "\n\n");

            writer.write("Metric,Value,Unit\n");
            writer.write("Total Energy Consumption," + formatValue(data.energyTotal()) + ",kWh\n");
            writer.write("Total Water Consumption," + formatValue(data.waterTotal()) + ",m³\n");
            writer.write("Total Carbon Emissions," + formatValue(data.carbonTotal()) + ",tCO₂e\n\n");

            // Detail sections
            writeDetailSection(writer, "Energy Data", "kWh", data.energyMetrics());
            writeDetailSection(writer, "Water Data", "m³", data.waterMetrics());
            writeDetailSection(writer, "Carbon Data", "tCO₂e", data.carbonMetrics());

            writer.flush();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate CSV report", e);
        }
    }

    private void writeDetailSection(OutputStreamWriter writer, String sectionName,
                                     String unit, List<EsgMetric> metrics) throws java.io.IOException {
        writer.write(sectionName + "\n");
        writer.write("Source ID,Timestamp,Value (" + unit + "),Building,District\n");
        if (metrics != null) {
            for (var m : metrics) {
                writer.write(escape(m.getSourceId()) + ","
                        + m.getId().getTimestamp() + ","
                        + m.getValue() + ","
                        + escape(m.getBuildingId()) + ","
                        + escape(m.getDistrictCode()) + "\n");
            }
        }
        writer.write("\n");
    }

    private String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String formatValue(Double value) {
        return value != null ? String.format("%.2f", value) : "N/A";
    }
}
