package com.uip.analytics.service;

import com.uip.analytics.api.dto.EnergyAggregateRequest;
import com.uip.analytics.api.dto.EnergyAggregateResponse;
import com.uip.analytics.api.dto.EnergyAggregateResponse.BuildingEnergyBreakdown;
import com.uip.analytics.repository.ClickHouseEnergyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnergyAggregateService {

    private final ClickHouseEnergyRepository repository;

    public EnergyAggregateResponse aggregate(EnergyAggregateRequest req) {
        log.debug("energy aggregate tenantId={} buildings={} from={} to={}",
                req.tenantId(), req.buildingIds(), req.fromEpoch(), req.toEpoch());

        List<String> buildingIds = req.buildingIds() != null ? req.buildingIds() : List.of();

        List<BuildingEnergyBreakdown> buildings = repository.aggregateByBuilding(
                req.tenantId(), buildingIds, req.fromEpoch(), req.toEpoch());

        double totalKwh = buildings.stream().mapToDouble(BuildingEnergyBreakdown::totalKwh).sum();
        double peakDemandKw = buildings.stream().mapToDouble(BuildingEnergyBreakdown::peakDemandKw).max().orElse(0.0);
        double avgPowerFactor = repository.aggregatePowerFactor(
                req.tenantId(), buildingIds, req.fromEpoch(), req.toEpoch());

        return new EnergyAggregateResponse(
                req.tenantId(), req.fromEpoch(), req.toEpoch(),
                totalKwh, peakDemandKw, avgPowerFactor, buildings);
    }
}
