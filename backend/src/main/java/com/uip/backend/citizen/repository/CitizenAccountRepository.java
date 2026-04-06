package com.uip.backend.citizen.repository;

import com.uip.backend.citizen.domain.CitizenAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CitizenAccountRepository extends JpaRepository<CitizenAccount, UUID> {
    Optional<CitizenAccount> findByUsername(String username);
    Optional<CitizenAccount> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
