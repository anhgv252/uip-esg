package com.uip.backend.esg.service;

import com.uip.backend.esg.api.dto.EsgMetricDto;
import com.uip.backend.esg.api.dto.EsgReportDto;
import com.uip.backend.esg.api.dto.EsgSummaryDto;
import com.uip.backend.esg.domain.EsgMetric;
import com.uip.backend.esg.domain.EsgReport;
import com.uip.backend.esg.repository.EsgMetricRepository;
import com.uip.backend.esg.repository.EsgReportRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.uip.backend.esg.dto.EsgAnomalyDto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EsgService {

    private final EsgMetricRepository  metricRepository;
    private final EsgReportRepository  reportRepository;
    private final EsgReportGenerator   reportGenerator;

    // ─── Summary ──────────────────────────────────────────────────────────────

    public EsgSummaryDto getSummary(String periodType, int year, int quarter) {
        Instant[] range = periodType.equalsIgnoreCase("ANNUAL")
                ? yearRange(year)
                : quarterRange(year, quarter);

        Double energy = metricRepository.sumByTypeAndRange("ENERGY", range[0], range[1]);
        Double water  = metricRepository.sumByTypeAndRange("WATER",  range[0], range[1]);
        Double carbon = metricRepository.sumByTypeAndRange("CARBON", range[0], range[1]);
        Double waste  = metricRepository.sumByTypeAndRange("WASTE",  range[0], range[1]);

        return EsgSummaryDto.builder()
                .period(periodType)
                .year(year)
                .quarter(quarter)
                .totalEnergyKwh(energy)
                .totalWaterM3(water)
                .totalCarbonTco2e(carbon)
                .totalWasteTons(waste)
                .build();
    }

    // ─── Energy & Carbon ──────────────────────────────────────────────────────

    public List<EsgMetricDto> getEnergyData(Instant from, Instant to, String buildingId) {
        Instant effectiveTo   = to   != null ? to   : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(30, ChronoUnit.DAYS);
        List<EsgMetric> metrics = buildingId != null
                ? metricRepository.findByTypeAndBuilding("ENERGY", buildingId, effectiveFrom, effectiveTo)
                : metricRepository.findByTypeAndRange("ENERGY", effectiveFrom, effectiveTo);
        return metrics.stream().map(this::toDto).toList();
    }

    public List<EsgMetricDto> getCarbonData(Instant from, Instant to) {
        Instant effectiveTo   = to   != null ? to   : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(30, ChronoUnit.DAYS);
        return metricRepository.findByTypeAndRange("CARBON", effectiveFrom, effectiveTo)
                .stream().map(this::toDto).toList();
    }

    // ─── Report generation ────────────────────────────────────────────────────

    @Transactional
    public EsgReportDto triggerReportGeneration(String periodType, int year, int quarter) {
        EsgReport report = new EsgReport();
        report.setPeriodType(periodType.toUpperCase());
        report.setYear(year);
        report.setQuarter(quarter);
        EsgReport saved = reportRepository.save(report);
        UUID reportId = saved.getId();
        // Schedule async generation AFTER commit to avoid race where async thread
        // reads the report before the INSERT is visible. Falls back to direct call
        // when invoked outside a transaction (e.g., unit tests).
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    reportGenerator.generateAsync(reportId);
                }
            });
        } else {
            reportGenerator.generateAsync(reportId);
        }
        return toReportDto(saved);
    }

    public EsgReportDto getReportStatus(UUID reportId) {
        EsgReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("Report not found: " + reportId));
        return toReportDto(report);
    }

    public EsgReport getReportForDownload(UUID reportId) {
        EsgReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("Report not found: " + reportId));
        if (!"DONE".equals(report.getStatus())) {
            throw new IllegalStateException("Report not ready: status=" + report.getStatus());
        }
        return report;
    }

    // ─── Anomaly Detection (M03/M04 scheduler) ─────────────────────────────────

    private static final double ANOMALY_THRESHOLD_RATIO = 1.3;

    public List<EsgAnomalyDto> detectUtilityAnomalies() {
        Instant now   = Instant.now();
        Instant from  = now.minus(30, ChronoUnit.DAYS);
        List<EsgAnomalyDto> anomalies = new ArrayList<>();

        for (String type : List.of("ENERGY", "WATER")) {
            Double current = metricRepository.sumByTypeAndRange(type, from, now);
            if (current == null || current == 0) continue;

            Instant histFrom = from.minus(30, ChronoUnit.DAYS);
            Double historical = metricRepository.sumByTypeAndRange(type, histFrom, from);
            if (historical == null || historical == 0) continue;

            if (current > historical * ANOMALY_THRESHOLD_RATIO) {
                anomalies.add(new EsgAnomalyDto(
                        type.toLowerCase(), current, historical, null, null));
            }
        }
        return anomalies;
    }

    public List<EsgAnomalyDto> detectEsgAnomalies() {
        Instant now   = Instant.now();
        Instant from  = now.minus(30, ChronoUnit.DAYS);
        List<EsgAnomalyDto> anomalies = new ArrayList<>();

        for (String type : List.of("ENERGY", "WATER", "CARBON", "WASTE")) {
            Double current = metricRepository.sumByTypeAndRange(type, from, now);
            if (current == null || current == 0) continue;

            Instant histFrom = from.minus(30, ChronoUnit.DAYS);
            Double historical = metricRepository.sumByTypeAndRange(type, histFrom, from);
            if (historical == null || historical == 0) continue;

            if (current > historical * ANOMALY_THRESHOLD_RATIO) {
                anomalies.add(new EsgAnomalyDto(
                        type.toLowerCase(), current, historical, null,
                        now.toString()));
            }
        }
        return anomalies;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Instant[] quarterRange(int year, int quarter) {
        int startMonth = (quarter - 1) * 3 + 1;
        LocalDate start = LocalDate.of(year, startMonth, 1);
        LocalDate end   = start.plusMonths(3);
        return new Instant[]{
            start.atStartOfDay().toInstant(ZoneOffset.UTC),
            end.atStartOfDay().toInstant(ZoneOffset.UTC)
        };
    }

    private Instant[] yearRange(int year) {
        return new Instant[]{
            LocalDate.of(year, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
            LocalDate.of(year + 1, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
        };
    }

    private EsgMetricDto toDto(EsgMetric m) {
        return EsgMetricDto.builder()
                .sourceId(m.getSourceId())
                .metricType(m.getMetricType())
                .timestamp(m.getId().getTimestamp())
                .value(m.getValue())
                .unit(m.getUnit())
                .buildingId(m.getBuildingId())
                .districtCode(m.getDistrictCode())
                .build();
    }

    private EsgReportDto toReportDto(EsgReport r) {
        String downloadUrl = "DONE".equals(r.getStatus())
                ? "/api/v1/esg/reports/" + r.getId() + "/download"
                : null;
        return EsgReportDto.builder()
                .id(r.getId())
                .periodType(r.getPeriodType())
                .year(r.getYear())
                .quarter(r.getQuarter())
                .status(r.getStatus())
                .downloadUrl(downloadUrl)
                .generatedAt(r.getGeneratedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
