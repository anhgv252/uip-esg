package com.uip.backend.esg.api;

import com.uip.backend.esg.service.EsgPdfService;
import com.uip.backend.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Synchronous ESG PDF export endpoint (S7-B06).
 *
 * <p>POST /api/v1/esg/reports/pdf → returns binary PDF directly (Content-Type: application/pdf).
 * Unlike the async generate+poll+download pattern in {@link EsgController},
 * this endpoint is synchronous with a 30s SLA.</p>
 *
 * <p>Requires {@code esg:write} scope (matches B1-1 permission fix).</p>
 */
@RestController
@RequestMapping("/api/v1/esg/reports")
@RequiredArgsConstructor
@Tag(name = "ESG Reports", description = "ESG PDF report generation")
@SecurityRequirement(name = "Bearer Authentication")
public class EsgReportController {

    private final EsgPdfService esgPdfService;

    /**
     * Generate a GRI 302-1 / 305-4 PDF report for a quarter and stream it directly.
     *
     * @param year    report year (e.g. 2026)
     * @param quarter report quarter 1-4
     */
    @PostMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Generate GRI 302-1/305-4 ESG PDF report — synchronous, <30s SLA")
    @PreAuthorize("hasAnyRole('ADMIN') and hasAuthority('esg:write')")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PDF generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid year/quarter parameters"),
        @ApiResponse(responseCode = "403", description = "Requires ADMIN role and esg:write authority")
    })
    public ResponseEntity<byte[]> generatePdf(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().get(T(java.time.temporal.IsoFields).QUARTER_OF_YEAR)}") int quarter) {

        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null || tenantId.isBlank()) {
            return ResponseEntity.status(403).build();
        }

        byte[] pdf = esgPdfService.generatePdf(tenantId, year, quarter);

        String filename = "esg-report-Q%d-%d.pdf".formatted(quarter, year);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .body(pdf);
    }
}
