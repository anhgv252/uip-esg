package com.uip.backend.citizen.repository;

import com.uip.backend.citizen.domain.Meter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeterRepository extends JpaRepository<Meter, UUID> {
    List<Meter> findByCitizenId(UUID citizenId);
    Optional<Meter> findByMeterCode(String meterCode);
}
