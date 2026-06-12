package com.uip.backend.bms.repository;

import com.uip.backend.bms.domain.CommandStatus;
import com.uip.backend.bms.domain.PendingBmsCommand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data repository for {@link PendingBmsCommand}.
 */
public interface PendingBmsCommandRepository extends JpaRepository<PendingBmsCommand, Long> {

    /**
     * Returns all commands for a building filtered by status.
     * Used by the controller to list PENDING commands awaiting operator approval.
     */
    List<PendingBmsCommand> findByBuildingIdAndStatus(String buildingId, CommandStatus status);

    /**
     * Returns commands in a given status that have surpassed their expiry timestamp.
     * Used by the scheduled expiry task to find stale PENDING commands.
     */
    List<PendingBmsCommand> findByStatusAndExpiresAtBefore(CommandStatus status, Instant now);

    /**
     * Returns all commands for a tenant filtered by status.
     * Used by the operator command list endpoint (cross-building view).
     */
    List<PendingBmsCommand> findByTenantIdAndStatus(String tenantId, CommandStatus status);
}
