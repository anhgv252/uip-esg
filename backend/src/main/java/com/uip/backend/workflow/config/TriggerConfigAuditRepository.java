package com.uip.backend.workflow.config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TriggerConfigAuditRepository extends JpaRepository<TriggerConfigAudit, Long> {
    List<TriggerConfigAudit> findByConfigIdOrderByChangedAtDesc(Long configId);
}
