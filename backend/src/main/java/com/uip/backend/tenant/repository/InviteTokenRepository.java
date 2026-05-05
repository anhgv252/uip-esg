package com.uip.backend.tenant.repository;

import com.uip.backend.tenant.domain.InviteToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteTokenRepository extends JpaRepository<InviteToken, UUID> {

    Optional<InviteToken> findByToken(UUID token);

    List<InviteToken> findByTenantIdAndEmail(String tenantId, String email);

    @Query("SELECT it FROM InviteToken it WHERE it.tenantId = :tenantId AND it.usedAt IS NULL AND it.expiresAt > :now")
    List<InviteToken> findValidTokensByTenant(@Param("tenantId") String tenantId, @Param("now") Instant now);

    @Query("SELECT COUNT(it) FROM InviteToken it WHERE it.tenantId = :tenantId AND it.createdAt > :since")
    long countByTenantSince(@Param("tenantId") String tenantId, @Param("since") Instant since);

    @Modifying
    @Query("DELETE FROM InviteToken it WHERE it.usedAt IS NOT NULL AND it.usedAt < :cutoff")
    int deleteUsedTokensOlderThan(@Param("cutoff") Instant cutoff);
}
