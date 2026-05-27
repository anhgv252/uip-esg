package com.uip.backend.forecast;

import com.uip.backend.esg.domain.EsgMetric;
import com.uip.backend.esg.repository.EsgMetricRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NaiveForecastAdapter — in-process rolling average fallback (ADR-032 D6).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NaiveForecastAdapter — unit")
class NaiveForecastAdapterTest {

    @Mock
    private EsgMetricRepository esgMetricRepository;

    @InjectMocks
    private NaiveForecastAdapter naiveForecastAdapter;

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private static EsgMetric buildMetric(String tenantId, String buildingId, double value) {
        EsgMetric m = new EsgMetric();
        m.setTenantId(tenantId);
        m.setBuildingId(buildingId);
        m.setValue(value);
        m.setMetricType("ENERGY");
        return m;
    }

    private static List<EsgMetric> buildMetrics(String tenantId, String buildingId, int count, double value) {
        List<EsgMetric> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(buildMetric(tenantId, buildingId, value));
        }
        return list;
    }

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("forecast — insufficient data (< 720 points) returns insufficientData result")
    void forecast_insufficientData_returnsInsufficient() {
        List<EsgMetric> fewMetrics = buildMetrics("hcm", "B1", 100, 50.0);
        when(esgMetricRepository.findByTypeAndBuilding(
                eq("hcm"), eq("ENERGY"), eq("B1"), any(Instant.class), any(Instant.class)))
                .thenReturn(fewMetrics);

        ForecastResult result = naiveForecastAdapter.forecast("hcm", "B1", 30);

        assertThat(result).isNotNull();
        assertThat(result.tenantId()).isEqualTo("hcm");
        assertThat(result.buildingId()).isEqualTo("B1");
        assertThat(result.isFallback()).isTrue();
        assertThat(result.model()).isEqualTo("NONE");
        assertThat(result.points()).isEmpty();
    }

    @Test
    @DisplayName("forecast — exactly 719 points is insufficient (boundary)")
    void forecast_exactly719_returnsInsufficient() {
        List<EsgMetric> metrics = buildMetrics("hcm", "B1", 719, 42.0);
        when(esgMetricRepository.findByTypeAndBuilding(any(), any(), any(), any(), any()))
                .thenReturn(metrics);

        ForecastResult result = naiveForecastAdapter.forecast("hcm", "B1", 7);

        assertThat(result.model()).isEqualTo("NONE");
        assertThat(result.points()).isEmpty();
    }

    @Test
    @DisplayName("forecast — empty list returns insufficientData")
    void forecast_emptyMetrics_returnsInsufficient() {
        when(esgMetricRepository.findByTypeAndBuilding(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ForecastResult result = naiveForecastAdapter.forecast("hcm", "B1", 30);

        assertThat(result.model()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("forecast — sufficient data (>= 720 points) returns NAIVE forecast with correct points")
    void forecast_sufficientData_returnsNaiveForecast() {
        double avgValue = 100.0;
        List<EsgMetric> metrics = buildMetrics("hcm", "B1", 720, avgValue);
        when(esgMetricRepository.findByTypeAndBuilding(
                eq("hcm"), eq("ENERGY"), eq("B1"), any(Instant.class), any(Instant.class)))
                .thenReturn(metrics);

        ForecastResult result = naiveForecastAdapter.forecast("hcm", "B1", 1);

        assertThat(result.model()).isEqualTo("NAIVE");
        assertThat(result.isFallback()).isTrue();
        assertThat(result.tenantId()).isEqualTo("hcm");
        assertThat(result.buildingId()).isEqualTo("B1");
        // 1 day * 24 hours = 24 forecast points
        assertThat(result.points()).hasSize(24);
    }

    @Test
    @DisplayName("forecast — correct number of points for 30-day horizon")
    void forecast_30DayHorizon_has720Points() {
        List<EsgMetric> metrics = buildMetrics("hcm", "B1", 720, 80.0);
        when(esgMetricRepository.findByTypeAndBuilding(any(), any(), any(), any(), any()))
                .thenReturn(metrics);

        ForecastResult result = naiveForecastAdapter.forecast("hcm", "B1", 30);

        // 30 days * 24 hours = 720 points
        assertThat(result.points()).hasSize(30 * 24);
    }

    @Test
    @DisplayName("forecast — points have correct confidence bounds (±15% of average)")
    void forecast_points_haveCorrectBounds() {
        double avgValue = 200.0;
        List<EsgMetric> metrics = buildMetrics("hcm", "B1", 720, avgValue);
        when(esgMetricRepository.findByTypeAndBuilding(any(), any(), any(), any(), any()))
                .thenReturn(metrics);

        ForecastResult result = naiveForecastAdapter.forecast("hcm", "B1", 1);

        assertThat(result.points()).isNotEmpty();
        ForecastPoint first = result.points().get(0);
        assertThat(first.predictedValue()).isEqualTo(avgValue);
        assertThat(first.confidenceUpper()).isEqualTo(avgValue * 1.15, org.assertj.core.data.Offset.offset(0.001));
        assertThat(first.confidenceLower()).isEqualTo(avgValue * 0.85, org.assertj.core.data.Offset.offset(0.001));
        assertThat(first.actualValue()).isNull();
        assertThat(first.isAnomaly()).isFalse();
    }

    @Test
    @DisplayName("forecast — queries repository with ENERGY type and correct tenant/building")
    void forecast_queriesRepositoryWithCorrectParams() {
        List<EsgMetric> metrics = buildMetrics("tenant1", "BLDG-99", 720, 55.0);
        when(esgMetricRepository.findByTypeAndBuilding(
                eq("tenant1"), eq("ENERGY"), eq("BLDG-99"), any(Instant.class), any(Instant.class)))
                .thenReturn(metrics);

        naiveForecastAdapter.forecast("tenant1", "BLDG-99", 7);

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(esgMetricRepository).findByTypeAndBuilding(
                eq("tenant1"), eq("ENERGY"), eq("BLDG-99"),
                fromCaptor.capture(), toCaptor.capture());

        Instant from = fromCaptor.getValue();
        Instant to = toCaptor.getValue();
        // The from date should be approximately 90 days before to
        long daysDiff = ChronoUnit.DAYS.between(from, to);
        assertThat(daysDiff).isBetween(89L, 91L);
    }

    @Test
    @DisplayName("forecast — average is computed correctly across varied metric values")
    void forecast_averageComputedCorrectly() {
        // Create 720 metrics: 360 at 100 + 360 at 200 → average = 150
        List<EsgMetric> metrics = new ArrayList<>();
        metrics.addAll(buildMetrics("hcm", "B1", 360, 100.0));
        metrics.addAll(buildMetrics("hcm", "B1", 360, 200.0));
        when(esgMetricRepository.findByTypeAndBuilding(any(), any(), any(), any(), any()))
                .thenReturn(metrics);

        ForecastResult result = naiveForecastAdapter.forecast("hcm", "B1", 1);

        assertThat(result.points()).isNotEmpty();
        assertThat(result.points().get(0).predictedValue())
                .isEqualTo(150.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("forecast — exactly 720 points is sufficient (boundary)")
    void forecast_exactly720_returnsForecast() {
        List<EsgMetric> metrics = buildMetrics("hcm", "B1", 720, 60.0);
        when(esgMetricRepository.findByTypeAndBuilding(any(), any(), any(), any(), any()))
                .thenReturn(metrics);

        ForecastResult result = naiveForecastAdapter.forecast("hcm", "B1", 1);

        assertThat(result.model()).isEqualTo("NAIVE");
        assertThat(result.points()).hasSize(24);
    }

    @Test
    @DisplayName("forecast — 90-day horizon generates correct point count")
    void forecast_90DayHorizon_has2160Points() {
        List<EsgMetric> metrics = buildMetrics("hcm", "B1", 1000, 75.0);
        when(esgMetricRepository.findByTypeAndBuilding(any(), any(), any(), any(), any()))
                .thenReturn(metrics);

        ForecastResult result = naiveForecastAdapter.forecast("hcm", "B1", 90);

        // 90 days * 24 hours = 2160 points
        assertThat(result.points()).hasSize(90 * 24);
    }

    @Test
    @DisplayName("forecast — DB exception wraps as ForecastServiceUnavailableException (503)")
    void forecast_dbException_throwsUnavailableException() {
        when(esgMetricRepository.findByTypeAndBuilding(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> naiveForecastAdapter.forecast("hcm", "B1", 7))
                .isInstanceOf(ForecastServiceUnavailableException.class)
                .hasMessageContaining("Naive forecast data unavailable");
    }
}
