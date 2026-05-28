package com.uip.backend.bms.repository;

import com.uip.backend.bms.domain.BmsDevice;
import com.uip.backend.bms.domain.BmsProtocol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BmsDeviceRepository extends JpaRepository<BmsDevice, UUID> {

    List<BmsDevice> findByTenantId(String tenantId);

    List<BmsDevice> findByTenantIdAndProtocol(String tenantId, BmsProtocol protocol);

    Optional<BmsDevice> findByTenantIdAndDeviceName(String tenantId, String deviceName);

    boolean existsByTenantIdAndDeviceName(String tenantId, String deviceName);
}
