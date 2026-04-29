package com.uip.backend.common.service;

import com.uip.backend.common.domain.AuditLog;
import com.uip.backend.common.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * MVP2-03c (3b): Unit tests for AuditLogService.
 * Verifies that audit entries are saved with correct field values.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MVP2-03c AuditLogService")
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    @DisplayName("logAction(5-arg): saves audit entry with all fields populated")
    void logAction_fiveArgs_savesWithCorrectFields() {
        // When
        auditLogService.logAction("admin", "DELETE_RULE", "AlertRule", "rule-123", "tenant-001");

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getActor()).isEqualTo("admin");
        assertThat(saved.getAction()).isEqualTo("DELETE_RULE");
        assertThat(saved.getResourceType()).isEqualTo("AlertRule");
        assertThat(saved.getResourceId()).isEqualTo("rule-123");
        assertThat(saved.getTenantId()).isEqualTo("tenant-001");
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("logAction(4-arg): saves with tenantId null when not provided")
    void logAction_fourArgs_tenantIdIsNull() {
        // When
        auditLogService.logAction("operator1", "ACKNOWLEDGE", "AlertEvent", "alert-456");

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getActor()).isEqualTo("operator1");
        assertThat(saved.getAction()).isEqualTo("ACKNOWLEDGE");
        assertThat(saved.getResourceType()).isEqualTo("AlertEvent");
        assertThat(saved.getResourceId()).isEqualTo("alert-456");
        assertThat(saved.getTenantId()).isNull();
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("logAction(6-arg with details map): saves entry with details")
    void logAction_sixArgs_withDetailsMap() {
        // When
        Map<String, Object> details = Map.of("oldValue", 100, "newValue", 200);
        auditLogService.logAction("admin", "UPDATE_THRESHOLD", "AlertRule", "rule-789", "tenant-001", details);

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getActor()).isEqualTo("admin");
        assertThat(saved.getAction()).isEqualTo("UPDATE_THRESHOLD");
        assertThat(saved.getResourceType()).isEqualTo("AlertRule");
        assertThat(saved.getResourceId()).isEqualTo("rule-789");
        assertThat(saved.getTenantId()).isEqualTo("tenant-001");
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("logAction(6-arg with null details): saves entry without details")
    void logAction_sixArgs_nullDetails_savesWithoutError() {
        // When
        auditLogService.logAction("system", "ESCALATE", "AlertEvent", "alert-999", null, null);

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getActor()).isEqualTo("system");
        assertThat(saved.getAction()).isEqualTo("ESCALATE");
        assertThat(saved.getTenantId()).isNull();
    }

    @Test
    @DisplayName("logAction: calls repository save exactly once per invocation")
    void logAction_callsSaveExactlyOnce() {
        auditLogService.logAction("admin", "CREATE", "TriggerConfig", "tc-1");

        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }
}
