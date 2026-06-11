package com.uip.analytics.service;

import com.uip.analytics.api.dto.AqiTrendRequest;
import com.uip.analytics.api.dto.AqiTrendResponse;
import com.uip.analytics.api.dto.AqiTrendResponse.AqiDataPoint;
import com.uip.analytics.repository.ClickHouseEnergyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AqiTrendService — unit tests")
class AqiTrendServiceTest {

    @Mock
    private ClickHouseEnergyRepository repository;

    @InjectMocks
    private AqiTrendService service;

    private static final String TENANT_A = "tenant-a";
    private static final long FROM = 1000L;
    private static final long TO = 2000L;

    @Nested
    @DisplayName("getTrend()")
    class GetTrend {

        @Test
        @DisplayName("returns hourly AQI data points")
        void hourlyDataPoints() {
            var dataPoints = List.of(
                    new AqiDataPoint("B1", 1000L, 45.5, 60.0),
                    new AqiDataPoint("B1", 3600L, 50.0, 65.0),
                    new AqiDataPoint("B1", 7200L, 55.2, 70.0)
            );
            when(repository.getAqiTrend(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(dataPoints);

            var req = new AqiTrendRequest(TENANT_A, List.of("B1"), FROM, TO);
            AqiTrendResponse resp = service.getTrend(req);

            assertThat(resp.tenantId()).isEqualTo(TENANT_A);
            assertThat(resp.dataPoints()).hasSize(3);
            assertThat(resp.dataPoints().get(0).avgAqi()).isCloseTo(45.5, within(0.001));
            assertThat(resp.dataPoints().get(0).maxAqi()).isCloseTo(60.0, within(0.001));
        }

        @Test
        @DisplayName("returns empty list when no data found")
        void noData_returnsEmptyList() {
            when(repository.getAqiTrend(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(Collections.emptyList());

            var req = new AqiTrendRequest(TENANT_A, List.of(), FROM, TO);
            AqiTrendResponse resp = service.getTrend(req);

            assertThat(resp.dataPoints()).isEmpty();
            assertThat(resp.tenantId()).isEqualTo(TENANT_A);
        }

        @Test
        @DisplayName("handles null buildingIds as empty list")
        void nullBuildingIds_treatedAsEmpty() {
            when(repository.getAqiTrend(eq(TENANT_A), eq(List.of()), eq(FROM), eq(TO)))
                    .thenReturn(Collections.emptyList());

            var req = new AqiTrendRequest(TENANT_A, null, FROM, TO);
            service.getTrend(req);

            verify(repository).getAqiTrend(TENANT_A, List.of(), FROM, TO);
        }

        @Test
        @DisplayName("data points ordered by timestamp")
        void dataPoints_orderedByTimestamp() {
            var dataPoints = List.of(
                    new AqiDataPoint("B1", 1000L, 40.0, 50.0),
                    new AqiDataPoint("B1", 3600L, 55.0, 65.0),
                    new AqiDataPoint("B1", 7200L, 70.0, 80.0)
            );
            when(repository.getAqiTrend(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(dataPoints);

            var req = new AqiTrendRequest(TENANT_A, List.of(), FROM, TO);
            AqiTrendResponse resp = service.getTrend(req);

            assertThat(resp.dataPoints()).extracting(AqiDataPoint::timestampEpoch)
                    .containsExactly(1000L, 3600L, 7200L);
        }

        @Test
        @DisplayName("building_ids filter passed through to repository")
        void buildingIdsFilter_passedToRepo() {
            when(repository.getAqiTrend(TENANT_A, List.of("B2"), FROM, TO))
                    .thenReturn(Collections.emptyList());

            var req = new AqiTrendRequest(TENANT_A, List.of("B2"), FROM, TO);
            service.getTrend(req);

            verify(repository).getAqiTrend(TENANT_A, List.of("B2"), FROM, TO);
        }

        @Test
        @DisplayName("multiple buildings each have their own data points")
        void multipleBuildings_separateDataPoints() {
            var dataPoints = List.of(
                    new AqiDataPoint("B1", 1000L, 45.0, 60.0),
                    new AqiDataPoint("B2", 1000L, 55.0, 70.0),
                    new AqiDataPoint("B1", 3600L, 50.0, 65.0),
                    new AqiDataPoint("B2", 3600L, 60.0, 75.0)
            );
            when(repository.getAqiTrend(eq(TENANT_A), anyList(), eq(FROM), eq(TO)))
                    .thenReturn(dataPoints);

            var req = new AqiTrendRequest(TENANT_A, List.of("B1", "B2"), FROM, TO);
            AqiTrendResponse resp = service.getTrend(req);

            assertThat(resp.dataPoints()).hasSize(4);
            long b1Count = resp.dataPoints().stream()
                    .filter(dp -> dp.buildingId().equals("B1")).count();
            long b2Count = resp.dataPoints().stream()
                    .filter(dp -> dp.buildingId().equals("B2")).count();
            assertThat(b1Count).isEqualTo(2);
            assertThat(b2Count).isEqualTo(2);
        }
    }
}
