package com.uip.backend.tenant.service;

import com.uip.backend.tenant.api.dto.TenantUsageDto;
import com.uip.backend.tenant.repository.TenantUsageCrossSchemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TenantUsageService {

    private final TenantUsageCrossSchemaRepository usageRepository;

    @Transactional(readOnly = true)
    public TenantUsageDto getUsage(String tenantId, Instant from, Instant to) {
        long count = usageRepository.countSensorReadings(tenantId, from, to);
        return TenantUsageDto.builder()
                .tenantId(tenantId)
                .readingCount(count)
                .from(from)
                .to(to)
                .build();
    }
}
