package com.uip.backend.esg.api;

import com.uip.backend.esg.api.dto.EsgMetricDto;
import com.uip.backend.esg.api.dto.EsgReportDto;
import com.uip.backend.esg.api.dto.EsgSummaryDto;
import com.uip.backend.esg.domain.EsgReport;
import com.uip.backend.esg.service.EsgService;
import com.uip.backend.tenant.context.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/esg")
@RequiredArgsConstructor
@Tag(name = "ESG", description = "Environmental, Social, and Governance metrics and reports")
public class EsgController {

    private final EsgService esgService;

    @GetMapping("/summary")
    @Operation(summary = "ESG summary aggregation for a given period")
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<EsgMetricDto>> getCarbon(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(esgService.getCarbonData(tenantId, from, to));
    }

    @PostMapping("/reports/generate")
    @Operation(summary = "Trigger async ESG report generation")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<EsgReportDto> generateReport(
            @RequestParam(defaultValue = "quarterly") String period,
            @RequestParam(defaultValue = "2026")      int year,
            @RequestParam(defaultValue = "1")          int quarter) {
        String tenantId = TenantContext.getCurrentTenant();
        EsgReportDto dto = esgService.triggerReportGeneration(tenantId, period, year, quarter);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
    }

    @GetMapping("/reports/{id}/status")
    @Operation(summary = "Check ESG report generation status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EsgReportDto> getReportStatus(@PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        return ResponseEntity.ok(esgService.getReportStatus(tenantId, id));
    }

    @GetMapping("/reports/{id}/download")
    @Operation(summary = "Download ESG report XLSX")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadReport(@PathVariable UUID id) {
        String tenantId = TenantContext.getCurrentTenant();
        EsgReport report = esgService.getReportForDownload(tenantId, id);
        Resource resource = new PathResource(Paths.get(report.getFilePath()));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"esg-report-%s.xlsx\"".formatted(id))
                .body(resource);
    }
}
