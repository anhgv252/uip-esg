package com.uip.analytics.service;

import com.uip.analytics.api.dto.AqiTrendRequest;
import com.uip.analytics.api.dto.AqiTrendResponse;
import com.uip.analytics.repository.ClickHouseEnergyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AqiTrendService {

    private final ClickHouseEnergyRepository repository;

    public AqiTrendResponse getTrend(AqiTrendRequest req) {
        log.debug("aqi trend tenantId={} buildings={} from={} to={}",
                req.tenantId(), req.buildingIds(), req.fromEpoch(), req.toEpoch());

        List<String> buildingIds = req.buildingIds() != null ? req.buildingIds() : List.of();

        var dataPoints = repository.getAqiTrend(
                req.tenantId(), buildingIds, req.fromEpoch(), req.toEpoch());

        return new AqiTrendResponse(req.tenantId(), dataPoints);
    }
}
