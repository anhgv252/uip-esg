package com.uip.backend.esg.config.analytics;

import com.uip.backend.esg.domain.EsgMetric;
import com.uip.backend.esg.repository.EsgMetricRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * GAP-007: Verify CO2 emission factor is configurable via constructor injection.
 *
 * Default: 0.5 kg/kWh (Vietnam grid average).
 * Custom: e.g., 0.3 kg/kWh for renewables-heavy grid.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CO2 Emission Factor — Configurable")
class Co2EmissionFactorConfigurableTest {

    @Mock private EsgMetricRepository metricRepository;

    @Test
    @DisplayName("Default factor 0.5 kg/kWh → 0.0005 tonnes/kWh")
    void defaultFactor_producesCorrectCo2() {
        when(metricRepository.sumByTypeAndRangeFast(anyString(), eq("ENERGY"), any(Instant.class), any(Instant.class)))
                .thenReturn(1000.0);

        TimescaleDbAnalyticsAdapter adapter = new TimescaleDbAnalyticsAdapter(metricRepository, 0.5);
        EsgAggregateResult result = adapter.queryEnergyAggregate("hcm", List.of(),
                Instant.now().minusSeconds(86400).getEpochSecond(),
                Instant.now().getEpochSecond());

        // 1000 kWh * 0.5 kg/kWh = 500 kg = 0.5 tonnes
        assertThat(result.totalCo2Tonnes()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("Custom factor 0.3 kg/kWh → 0.0003 tonnes/kWh")
    void customFactor_producesCorrectCo2() {
        when(metricRepository.sumByTypeAndRangeFast(anyString(), eq("ENERGY"), any(Instant.class), any(Instant.class)))
                .thenReturn(1000.0);

        TimescaleDbAnalyticsAdapter adapter = new TimescaleDbAnalyticsAdapter(metricRepository, 0.3);
        EsgAggregateResult result = adapter.queryEnergyAggregate("hcm", List.of(),
                Instant.now().minusSeconds(86400).getEpochSecond(),
                Instant.now().getEpochSecond());

        // 1000 kWh * 0.3 kg/kWh = 300 kg = 0.3 tonnes
        assertThat(result.totalCo2Tonnes()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("Factor 0.0 → zero CO2 (pure renewables)")
    void zeroFactor_producesZeroCo2() {
        when(metricRepository.sumByTypeAndRangeFast(anyString(), eq("ENERGY"), any(Instant.class), any(Instant.class)))
                .thenReturn(5000.0);

        TimescaleDbAnalyticsAdapter adapter = new TimescaleDbAnalyticsAdapter(metricRepository, 0.0);
        EsgAggregateResult result = adapter.queryEnergyAggregate("hcm", List.of(),
                Instant.now().minusSeconds(86400).getEpochSecond(),
                Instant.now().getEpochSecond());

        assertThat(result.totalCo2Tonnes()).isEqualTo(0.0);
        assertThat(result.totalKwh()).isEqualTo(5000.0);
    }

    @Test
    @DisplayName("High factor 0.8 kg/kWh — coal-heavy grid")
    void highFactor_producesCorrectCo2() {
        when(metricRepository.sumByTypeAndRangeFast(anyString(), eq("ENERGY"), any(Instant.class), any(Instant.class)))
                .thenReturn(10000.0);

        TimescaleDbAnalyticsAdapter adapter = new TimescaleDbAnalyticsAdapter(metricRepository, 0.8);
        EsgAggregateResult result = adapter.queryEnergyAggregate("hcm", List.of(),
                Instant.now().minusSeconds(86400).getEpochSecond(),
                Instant.now().getEpochSecond());

        // 10000 kWh * 0.8 kg/kWh = 8000 kg = 8.0 tonnes
        assertThat(result.totalCo2Tonnes()).isEqualTo(8.0);
    }
}
