package com.uip.backend.tenant.service;

import com.uip.backend.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * M5-4 T01: Service providing tenant metadata queries.
 * Used by BillingAggregationJob to iterate all active tenants for daily roll-up.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;

    /**
     * Returns a list of all registered tenant IDs.
     * Used by BillingAggregationJob to aggregate per-tenant billing data.
     */
    @Transactional(readOnly = true)
    public List<String> getAllTenantIds() {
        return tenantRepository.findAll().stream()
                .map(t -> t.getTenantId())
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());
    }
}
