package com.uip.backend.bms.repository;

import com.uip.backend.bms.domain.BmsReadingRaw;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BmsReadingRawRepository extends JpaRepository<BmsReadingRaw, Long> {
    long countByDeviceId(UUID deviceId);
}
