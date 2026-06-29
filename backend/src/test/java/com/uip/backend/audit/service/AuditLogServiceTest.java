package com.uip.backend.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.audit.domain.AuditEvent;
import com.uip.backend.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditLogService (M5-4 T09).
 * 
 * Coverage:
 * - Event logging (append-only)
 * - Metadata serialization
 * - SYSTEM actor default
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditLogService auditLogService;

    private static final String TENANT_ID = "test-tenant";

    @Test
    void testLogEvent_savesAuditEvent() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"key\":\"value\"}");

        // When
        auditLogService.logEvent(
                TENANT_ID,
                "BILLING_INVOICE_GENERATED",
                "user-123",
                "invoice-001",
                "INVOICE",
                Map.of("key", "value")
        );

        // Then
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        AuditEvent saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getEventType()).isEqualTo("BILLING_INVOICE_GENERATED");
        assertThat(saved.getActorId()).isEqualTo("user-123");
        assertThat(saved.getEntityId()).isEqualTo("invoice-001");
        assertThat(saved.getEntityType()).isEqualTo("INVOICE");
        assertThat(saved.getMetadata()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void testLogSystemEvent_defaultsActorToSystem() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        auditLogService.logSystemEvent(
                TENANT_ID,
                "BILLING_AGGREGATION_COMPLETED",
                "month-2026-06",
                "AGGREGATION",
                Map.of()
        );

        // Then
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        AuditEvent saved = captor.getValue();
        assertThat(saved.getActorId()).isEqualTo("SYSTEM");
    }

    @Test
    void testLogEvent_gracefullyHandlesSerializationError() throws Exception {
        // Given: ObjectMapper throws exception
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("Serialization failed"));

        // When: should not throw, only log error
        auditLogService.logEvent(
                TENANT_ID,
                "TEST_EVENT",
                "user-123",
                "entity-001",
                "TEST",
                Map.of("key", "value")
        );

        // Then: no exception thrown, repository.save not called
        verify(auditEventRepository, never()).save(any());
    }
}
