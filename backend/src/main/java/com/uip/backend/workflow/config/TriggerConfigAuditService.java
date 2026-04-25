package com.uip.backend.workflow.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TriggerConfigAuditService {

    private final TriggerConfigAuditRepository auditRepo;
    private final ObjectMapper objectMapper;

    // PROPAGATION_REQUIRES_NEW: audit must be saved even if the caller's transaction rolls back
    public List<TriggerConfigAudit> getHistory(Long configId) {
        return auditRepo.findByConfigIdOrderByChangedAtDesc(configId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(TriggerConfig config, String action, String changedBy) {
        try {
            String snapshot = objectMapper.writeValueAsString(config);
            TriggerConfigAudit entry = TriggerConfigAudit.builder()
                    .configId(config.getId())
                    .scenarioKey(config.getScenarioKey())
                    .action(action)
                    .changedBy(changedBy)
                    .snapshot(snapshot)
                    .build();
            auditRepo.save(entry);
        } catch (Exception e) {
            // Audit failure must never break the main operation
            log.error("[AUDIT] Failed to record audit for config={} action={}: {}",
                    config.getId(), action, e.getMessage());
        }
    }
}
