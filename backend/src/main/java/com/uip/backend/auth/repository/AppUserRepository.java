package com.uip.backend.auth.repository;

import com.uip.backend.auth.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    List<AppUser> findByTenantId(String tenantId);
}
