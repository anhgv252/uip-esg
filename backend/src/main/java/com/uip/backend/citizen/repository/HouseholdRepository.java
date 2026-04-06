package com.uip.backend.citizen.repository;

import com.uip.backend.citizen.domain.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HouseholdRepository extends JpaRepository<Household, UUID> {
    Optional<Household> findByCitizenId(UUID citizenId);
}
