package com.uip.backend.esg.service;

import com.uip.backend.esg.domain.EsgReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * Synchronous PDF generation for ESG GRI reports (S7-B06).
 *
 * <p>Unlike {@link EsgReportGenerator} which supports async generation + storage,
 * this service generates a PDF on-demand and returns raw bytes immediately.
 * Caller is responsible for streaming the response with {@code Content-Type: application/pdf}.</p>
 *
 * <p>Requires {@code esg:write} scope — enforced at controller layer.</p>
 * <p>SLA: <30s generation time.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EsgPdfService {

    private final EsgReportGenerator reportGenerator;

    /**
     * Generate a GRI 302-1 / 305-4 PDF for the given quarter.
     *
     * @param tenantId tenant scope
     * @param year     report year, e.g. 2026
     * @param quarter  report quarter 1-4
     * @return raw PDF bytes, typically 50-200 KB
     */
    public byte[] generatePdf(String tenantId, int year, int quarter) {
        long start = System.currentTimeMillis();
        log.info("PDF generation start: tenant={} year={} Q{}", tenantId, year, quarter);

        // Build a transient EsgReport for the period — no DB persistence needed for on-demand export
        EsgReport report = new EsgReport();
        report.setTenantId(tenantId);
        report.setYear(year);
        report.setQuarter(quarter);
        report.setStatus("DONE");
        report.setPeriodType("QUARTERLY");

        byte[] pdf = reportGenerator.exportReport(report, "pdf");

        log.info("PDF generation done: tenant={} Q{}{}  bytes={} elapsed={}ms",
                tenantId, quarter, year, pdf.length, System.currentTimeMillis() - start);
        return pdf;
    }
}
