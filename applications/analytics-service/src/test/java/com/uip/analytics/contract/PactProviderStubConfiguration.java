package com.uip.analytics.contract;

import com.uip.analytics.api.dto.AqiTrendRequest;
import com.uip.analytics.api.dto.AqiTrendResponse;
import com.uip.analytics.api.dto.EmissionsAggregateRequest;
import com.uip.analytics.api.dto.EmissionsAggregateResponse;
import com.uip.analytics.api.dto.EnergyAggregateRequest;
import com.uip.analytics.api.dto.EnergyAggregateResponse;
import com.uip.analytics.api.dto.EnergyAggregateResponse.BuildingEnergyBreakdown;
import com.uip.analytics.service.AqiTrendService;
import com.uip.analytics.service.EmissionsAggregateService;
import com.uip.analytics.service.EnergyAggregateService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Stub service implementations for Pact provider verification.
 *
 * <p>These {@code @Primary} beans override the real analytics services during
 * contract verification so the provider returns deterministic, contract-shaped
 * responses without requiring a live ClickHouse instance. The real services
 * delegate to {@code ClickHouseEnergyRepository}, which in the test profile has
 * no reachable ClickHouse and would return zero/empty results — failing the
 * contract on body matching.
 *
 * <p>Rationale: Pact provider verification should assert <b>contract shape</b>
 * (paths, status codes, body structure and field names). Concrete values come
 * from a controlled fixture, not from a live database. End-to-end data
 * correctness is verified separately by {@code ClickHouseEnergyRepositoryIT}
 * (Testcontainers + seeded ClickHouse).
 *
 * <p>Loaded only when {@code @Import}-ed by the provider test — does not affect
 * other integration tests ({@code RowPolicyIsolationIT}, repository ITs) which
 * exercise the real service/repository stack. The anonymous subclasses pass
 * {@code null} for the repository dependency because every method is overridden
 * and never delegates to the parent.
 */
@TestConfiguration
public class PactProviderStubConfiguration {

    @Bean
    @Primary
    public EnergyAggregateService stubEnergyAggregateService() {
        return new EnergyAggregateService(null) {
            @Override
            public EnergyAggregateResponse aggregate(EnergyAggregateRequest req) {
                return new EnergyAggregateResponse(
                        req.tenantId(),
                        req.fromEpoch(),
                        req.toEpoch(),
                        15000.0,
                        120.5,
                        0.95,
                        List.of(new BuildingEnergyBreakdown("B01", 15000.0, 120.5)));
            }
        };
    }

    @Bean
    @Primary
    public EmissionsAggregateService stubEmissionsAggregateService() {
        return new EmissionsAggregateService(null) {
            @Override
            public EmissionsAggregateResponse aggregate(EmissionsAggregateRequest req) {
                return new EmissionsAggregateResponse(
                        req.tenantId(),
                        req.fromEpoch(),
                        req.toEpoch(),
                        15000.0,
                        List.of(new EmissionsAggregateResponse.TenantEmissionsBreakdown(
                                "B01", 15000.0, 15000.0)));
            }
        };
    }

    @Bean
    @Primary
    public AqiTrendService stubAqiTrendService() {
        return new AqiTrendService(null) {
            @Override
            public AqiTrendResponse getTrend(AqiTrendRequest req) {
                return new AqiTrendResponse(
                        req.tenantId(),
                        List.of(new AqiTrendResponse.AqiDataPoint(
                                "B01", req.fromEpoch(), 42.0, 55.0)));
            }
        };
    }
}
