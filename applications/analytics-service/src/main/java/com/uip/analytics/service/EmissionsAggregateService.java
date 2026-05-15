package com.uip.analytics.service;

import com.uip.analytics.api.dto.EmissionsAggregateRequest;
import com.uip.analytics.api.dto.EmissionsAggregateResponse;
import com.uip.analytics.api.dto.EmissionsAggregateResponse.TenantEmissionsBreakdown;
import com.uip.analytics.repository.ClickHouseEnergyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmissionsAggregateService {

    private final ClickHouseEnergyRepository repository;

    public EmissionsAggregateResponse aggregate(EmissionsAggregateRequest req) {
        log.debug("emissions aggregate tenantId={} buildings={} from={} to={}",
                req.tenantId(), req.buildingIds(), req.fromEpoch(), req.toEpoch());

        List<String> buildingIds = req.buildingIds() != null ? req.buildingIds() : List.of();

        List<TenantEmissionsBreakdown> buildings = repository.aggregateEmissionsByBuilding(
                req.tenantId(), buildingIds, req.fromEpoch(), req.toEpoch());

        double totalCo2 = buildings.stream()
                .mapToDouble(TenantEmissionsBreakdown::totalCo2Kg)
                .sum();

        return new EmissionsAggregateResponse(
                req.tenantId(), req.fromEpoch(), req.toEpoch(), totalCo2, buildings);
    }
}
