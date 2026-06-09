package com.uip.backend.esg.api;

import com.uip.backend.esg.api.dto.EsgMetricDto;
import com.uip.backend.esg.api.dto.EsgReportDto;
import com.uip.backend.esg.api.dto.EsgSummaryDto;
import com.uip.backend.esg.domain.EsgReport;
import com.uip.backend.esg.service.EsgService;
import com.uip.backend.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@RestController
@RequestMapping("/api/v1/esg")
@RequiredArgsConstructor
@Validated
@Tag(name = "ESG", description = "Environmental, Social, and Governance metrics and reports")
@SecurityRequirement(name = "Bearer Authentication")
public class EsgController {

    private final EsgService esgService;

    // Must match the value in EsgReportGenerator so path validation uses the same base directory.
    @Value("${esg.report.output-dir:${java.io.tmpdir}/uip-reports}")
    private String reportOutputDir;

    @GetMapping("/summary")
    @Operation(summary = "ESG summary aggregation for a given period")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "ESG summary returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EsgSummaryDto> getSummary(
            @RequestParam(defaultValue = "quarterly") String period,
            @RequestParam(defaultValue = "2026")      int year,
            @RequestParam(defaultValue = "1")          int quarter) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(esgService.getSummary(tenantId, period, year, quarter));
    }

    @GetMapping("/energy")
    @Operation(summary = "Energy consumption time-series")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Energy metrics returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EsgMetricDto>> getEnergy(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String building) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(esgService.getEnergyData(tenantId, from, to, building));
    }

    @GetMapping("/carbon")
    @Operation(summary = "Carbon emission time-series")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Carbon metrics returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EsgMetricDto>> getCarbon(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(esgService.getCarbonData(tenantId, from, to));
    }

    @PostMapping("/reports/generate")
    @Operation(summary = "Trigger async ESG report generation")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN') and hasAuthority('esg:write')")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Report generation started"),
        @ApiResponse(responseCode = "400", description = "Invalid year/quarter parameters"),
        @ApiResponse(responseCode = "403", description = "Requires esg:write authority")
    })
    public ResponseEntity<EsgReportDto> generateReport(
            @RequestParam(defaultValue = "quarterly") String period,
            @RequestParam(defaultValue = "2026") @Min(value = 2020, message = "year must be 2020 or later") int year,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "quarter must be between 1 and 4") @Max(value = 4, message = "quarter must be between 1 and 4") int quarter) {
        String tenantId = TenantContext.getCurrentTenant();
        EsgReportDto dto = esgService.triggerReportGeneration(tenantId, period, year, quarter);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
    }

    @GetMapping("/reports/{id}/status")
    @Operation(summary = "Check ESG report generation status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Report status returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT"),
        @ApiResponse(responseCode = "404", description = "Report not found")
    })
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EsgReportDto> getReportStatus(@PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(esgService.getReportStatus(tenantId, id));
    }

    @GetMapping("/reports/{id}/download")
    @Operation(summary = "Download ESG report (XLSX or CSV)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Report file stream"),
        @ApiResponse(responseCode = "401", description = "Unauthorized — invalid or missing JWT"),
        @ApiResponse(responseCode = "404", description = "Report not found")
    })
    @PreAuthorize("isAuthenticated()")
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "report.getFilePath() is server-written (set by EsgReportGenerator, never from user input); further guarded by getCanonicalFile() + startsWith(baseDir) check")
    public ResponseEntity<?> downloadReport(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "xlsx") String format) {
        String tenantId = TenantContext.getCurrentTenant();
        EsgReport report = esgService.getReportForDownload(tenantId, id);

        if ("xlsx".equalsIgnoreCase(format) && report.getFilePath() != null) {
            try {
                // getCanonicalFile() resolves symlinks/traversal sequences — FindSecBugs-recognized sanitizer.
                Path normalizedPath = new File(report.getFilePath()).getCanonicalFile().toPath();
                Path baseDir = new File(reportOutputDir).getCanonicalFile().toPath();
                if (!normalizedPath.startsWith(baseDir)) {
                    throw new IllegalArgumentException("Report file path is outside the configured output directory");
                }
                Resource resource = new PathResource(normalizedPath);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"esg-report-%s.xlsx\"".formatted(id))
                        .body(resource);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot resolve report file path", e);
            }
        }

        byte[] data = esgService.exportReport(report, format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        esgService.getReportContentType(format)))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"esg-report-%s.%s\"".formatted(id, esgService.getReportFileExtension(format)))
                .body(data);
    }
}
