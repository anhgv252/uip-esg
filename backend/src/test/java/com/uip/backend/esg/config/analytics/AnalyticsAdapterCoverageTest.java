package com.uip.backend.esg.config.analytics;

import com.uip.backend.esg.domain.EsgMetric;
import com.uip.backend.esg.domain.EsgMetricId;
import com.uip.backend.esg.repository.EsgMetricRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Analytics adapter coverage ≥50%: unit tests for
 * {@link TimescaleDbAnalyticsAdapter} and {@link ClickHouseRestAnalyticsAdapter}.
 *
 * No Spring context — HTTP and JPA calls are mocked at unit-test level.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Analytics Adapter Coverage")
class AnalyticsAdapterCoverageTest {

    private static final String TENANT   = "hcm";
    private static final long   FROM_EPOCH = 1_700_000_000L;
    private static final long   TO_EPOCH   = 1_710_000_000L;
    private static final double CO2_FACTOR = 0.5;

    // ─── TimescaleDbAnalyticsAdapter ────────────────────────────────────────

    @Nested
    @DisplayName("TimescaleDbAnalyticsAdapter — Tier 1 JPA")
    class TimescaleDbTest {

        @Mock
        private EsgMetricRepository metricRepository;

        private TimescaleDbAnalyticsAdapter adapter;

        @BeforeEach
        void setUp() {
            adapter = new TimescaleDbAnalyticsAdapter(metricRepository, CO2_FACTOR);
        }

        @Test
        @DisplayName("getEnergyConsumption: cagg fast path returns sum and computes CO2")
        void queryEnergyAggregate_caggFastPath_returnsAggregateResult() {
            when(metricRepository.sumByTypeAndRangeFast(eq(TENANT), eq("ENERGY"), any(), any()))
                    .thenReturn(1200.0);

            EsgAggregateResult result = adapter.queryEnergyAggregate(TENANT, List.of(), FROM_EPOCH, TO_EPOCH);

            assertThat(result.totalKwh()).isEqualTo(1200.0);
            // CO2 = 1200 * (0.5 / 1000) = 0.6 tonnes
            assertThat(result.totalCo2Tonnes()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(result.kwhPerBuilding()).isEmpty();
            // cagg hit → raw scan must NOT be called
            verify(metricRepository, never()).sumByTypeAndRange(any(), any(), any(), any());
        }

        @Test
        @DisplayName("getEnergyConsumption: cagg returns null → fallback raw scan used")
        void queryEnergyAggregate_caggNull_fallsBackToRawScan() {
            when(metricRepository.sumByTypeAndRangeFast(any(), any(), any(), any())).thenReturn(null);
            when(metricRepository.sumByTypeAndRange(eq(TENANT), eq("ENERGY"), any(), any())).thenReturn(800.0);

            EsgAggregateResult result = adapter.queryEnergyAggregate(TENANT, List.of(), FROM_EPOCH, TO_EPOCH);

            assertThat(result.totalKwh()).isEqualTo(800.0);
            verify(metricRepository).sumByTypeAndRange(eq(TENANT), eq("ENERGY"), any(), any());
        }

        @Test
        @DisplayName("getWaterUsage proxy: no data → totalKwh=0.0, co2=0.0 (null-safe)")
        void queryEnergyAggregate_noData_returnsZero() {
            when(metricRepository.sumByTypeAndRangeFast(any(), any(), any(), any())).thenReturn(null);
            when(metricRepository.sumByTypeAndRange(any(), any(), any(), any())).thenReturn(null);

            EsgAggregateResult result = adapter.queryEnergyAggregate(TENANT, List.of(), FROM_EPOCH, TO_EPOCH);

            assertThat(result.totalKwh()).isZero();
            assertThat(result.totalCo2Tonnes()).isZero();
        }

        @Test
        @DisplayName("getCarbonEmissions proxy: co2 emission factor scales proportionally")
        void queryEnergyAggregate_customCo2Factor_scalesCorrectly() {
            TimescaleDbAnalyticsAdapter highFactorAdapter =
                    new TimescaleDbAnalyticsAdapter(metricRepository, 0.9);
            when(metricRepository.sumByTypeAndRangeFast(any(), any(), any(), any())).thenReturn(1000.0);

            EsgAggregateResult result = highFactorAdapter.queryEnergyAggregate(
                    TENANT, List.of(), FROM_EPOCH, TO_EPOCH);

            // CO2 = 1000 * (0.9 / 1000) = 0.9
            assertThat(result.totalCo2Tonnes()).isCloseTo(0.9, org.assertj.core.data.Offset.offset(1e-9));
        }

        @Test
        @DisplayName("per-building breakdown: sums metric values per buildingId")
        void queryEnergyAggregate_withBuildingIds_computesBreakdown() {
            when(metricRepository.sumByTypeAndRangeFast(any(), any(), any(), any())).thenReturn(500.0);

            EsgMetric m1 = buildMetric("BLD-A", 300.0);
            EsgMetric m2 = buildMetric("BLD-A", 100.0);
            EsgMetric m3 = buildMetric("BLD-B", 200.0);

            when(metricRepository.findByTypeAndBuilding(eq(TENANT), eq("ENERGY"), eq("BLD-A"), any(), any()))
                    .thenReturn(List.of(m1, m2));
            when(metricRepository.findByTypeAndBuilding(eq(TENANT), eq("ENERGY"), eq("BLD-B"), any(), any()))
                    .thenReturn(List.of(m3));

            EsgAggregateResult result = adapter.queryEnergyAggregate(
                    TENANT, List.of("BLD-A", "BLD-B"), FROM_EPOCH, TO_EPOCH);

            assertThat(result.kwhPerBuilding())
                    .containsEntry("BLD-A", 400.0)
                    .containsEntry("BLD-B", 200.0);
            assertThat(result.buildingIds()).containsExactlyInAnyOrder("BLD-A", "BLD-B");
        }

        @Test
        @DisplayName("empty building list → no findByTypeAndBuilding calls made")
        void queryEnergyAggregate_emptyBuildingList_skipsPerBuildingQuery() {
            when(metricRepository.sumByTypeAndRangeFast(any(), any(), any(), any())).thenReturn(300.0);

            adapter.queryEnergyAggregate(TENANT, List.of(), FROM_EPOCH, TO_EPOCH);

            verify(metricRepository, never()).findByTypeAndBuilding(any(), any(), any(), any(), any());
        }

        private EsgMetric buildMetric(String buildingId, double value) {
            EsgMetric m = new EsgMetric();
            EsgMetricId id = new EsgMetricId(null, Instant.now());
            m.setId(id);
            m.setTenantId(TENANT);
            m.setBuildingId(buildingId);
            m.setValue(value);
            return m;
        }
    }

    // ─── ClickHouseRestAnalyticsAdapter ──────────────────────────────────────

    @Nested
    @DisplayName("ClickHouseRestAnalyticsAdapter — Tier 2 HTTP")
    class ClickHouseRestTest {

        @Test
        @DisplayName("getEnergyConsumption: RestClientException → graceful fallback to (0.0, 0.0)")
        void queryEnergyAggregate_serviceUnavailable_returnsZeroResult() {
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), any()))
                            .thenThrow(new RestClientException("Connection refused")))) {

                ClickHouseRestAnalyticsAdapter adapter = new ClickHouseRestAnalyticsAdapter(
                        "http://analytics:8082", 0.5);
                EsgAggregateResult result = adapter.queryEnergyAggregate(
                        TENANT, List.of(), FROM_EPOCH, TO_EPOCH);

                assertThat(result.totalKwh()).isZero();
                assertThat(result.totalCo2Tonnes()).isZero();
                assertThat(result.kwhPerBuilding()).isEmpty();
            }
        }

        @Test
        @DisplayName("getCarbonEmissions proxy: null HTTP response → returns zeros, no NPE")
        void queryEnergyAggregate_nullResponse_returnsZeroResult() {
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), any()))
                            .thenReturn(null))) {

                ClickHouseRestAnalyticsAdapter adapter = new ClickHouseRestAnalyticsAdapter(
                        "http://analytics:8082", 0.5);
                EsgAggregateResult result = adapter.queryEnergyAggregate(
                        TENANT, List.of("BLD-X"), FROM_EPOCH, TO_EPOCH);

                assertThat(result.totalKwh()).isZero();
                // buildingIds is still preserved from request
                assertThat(result.buildingIds()).containsExactly("BLD-X");
            }
        }

        @Test
        @DisplayName("getWaterUsage proxy: I/O timeout → returns zeros, does not propagate exception")
        void queryEnergyAggregate_timeout_returnsZeroNotPropagates() {
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), any()))
                            .thenThrow(new ResourceAccessException("Read timed out")))) {

                ClickHouseRestAnalyticsAdapter adapter = new ClickHouseRestAnalyticsAdapter(
                        "http://analytics:8082", 0.5);

                EsgAggregateResult result = adapter.queryEnergyAggregate(
                        TENANT, List.of(), FROM_EPOCH, TO_EPOCH);

                assertThat(result).isNotNull();
                assertThat(result.totalKwh()).isZero();
            }
        }

        @Test
        @DisplayName("custom base URL is forwarded in HTTP request")
        void queryEnergyAggregate_customBaseUrl_passedToRestTemplate() {
            String customUrl = "http://custom-analytics:9090";

            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), any()))
                            .thenReturn(null))) {

                ClickHouseRestAnalyticsAdapter adapter =
                        new ClickHouseRestAnalyticsAdapter(customUrl, 0.5);
                adapter.queryEnergyAggregate(TENANT, List.of(), FROM_EPOCH, TO_EPOCH);

                verify(mocked.constructed().get(0))
                        .postForObject(eq(customUrl + "/energy-aggregate"), any(), any());
            }
        }
    }
}
