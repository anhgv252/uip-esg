package com.uip.backend.tenant.repository;

import com.uip.backend.tenant.domain.TenantConfigEntry;
import com.uip.backend.tenant.domain.TenantConfigEntryId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenantConfigRepository extends JpaRepository<TenantConfigEntry, TenantConfigEntryId> {

    List<TenantConfigEntry> findByTenantId(String tenantId);
}
