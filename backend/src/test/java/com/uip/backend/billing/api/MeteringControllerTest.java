package com.uip.backend.billing.api;

import com.uip.backend.billing.domain.MeteringEvent;
import com.uip.backend.billing.domain.MeteringEventType;
import com.uip.backend.billing.repository.MeteringEventRepository;
import com.uip.backend.tenant.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for MeteringController.
 * Uses @WebMvcTest for lightweight controller testing.
 */
@WebMvcTest(MeteringController.class)
@ActiveProfiles("test")
class MeteringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MeteringEventRepository meteringEventRepository;

    private String tenantId;
    private Instant now;

    @BeforeEach
    void setUp() {
        tenantId = "test-tenant-001";
        now = Instant.now();
        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @WithMockUser(roles = {"CITIZEN"})
    void testGetEvents_Success() throws Exception {
        // Given
        MeteringEvent event1 = createMeteringEvent("evt-001", MeteringEventType.AI_PREDICTION, 100, now);
        MeteringEvent event2 = createMeteringEvent("evt-002", MeteringEventType.WORKFLOW_RUN, 200, now);
        when(meteringEventRepository.findByTenantAndTimeRange(eq(tenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(event1, event2));

        // When & Then
        mockMvc.perform(get("/api/v1/billing/metering/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventId").value("evt-001"))
                .andExpect(jsonPath("$[1].eventId").value("evt-002"));
    }

    @Test
    @WithMockUser(roles = {"CITIZEN"})
    void testGetEvents_WithEventTypeFilter() throws Exception {
        // Given
        MeteringEvent event1 = createMeteringEvent("evt-001", MeteringEventType.AI_PREDICTION, 100, now);
        when(meteringEventRepository.findByTenantTypeAndTimeRange(
                eq(tenantId), eq(MeteringEventType.AI_PREDICTION), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(event1));

        // When & Then
        mockMvc.perform(get("/api/v1/billing/metering/events")
                        .param("eventType", "AI_PREDICTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventType").value("AI_PREDICTION"));
    }

    @Test
    void testGetEvents_Unauthenticated() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/billing/metering/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void testGetSummary_Success() throws Exception {
        // Given
        when(meteringEventRepository.sumCostByTenantAndTimeRange(eq(tenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(350L);

        // When & Then
        mockMvc.perform(get("/api/v1/billing/metering/summary")
                        .param("tenantId", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantId))
                .andExpect(jsonPath("$.totalCostUsdCents").value(350))
                .andExpect(jsonPath("$.totalCostUsd").value("$3.50"));
    }

    @Test
    @WithMockUser(roles = {"OPERATOR"})
    void testGetSummary_OperatorRole() throws Exception {
        // Given
        when(meteringEventRepository.sumCostByTenantAndTimeRange(eq(tenantId), any(Instant.class), any(Instant.class)))
                .thenReturn(100L);

        // When & Then
        mockMvc.perform(get("/api/v1/billing/metering/summary")
                        .param("tenantId", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCostUsdCents").value(100));
    }

    @Test
    @WithMockUser(roles = {"CITIZEN"})
    void testGetSummary_Forbidden_CitizenRole() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/billing/metering/summary")
                        .param("tenantId", tenantId))
                .andExpect(status().isForbidden());
    }

    @Test
    void testGetSummary_Unauthenticated() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/billing/metering/summary")
                        .param("tenantId", tenantId))
                .andExpect(status().isUnauthorized());
    }

    private MeteringEvent createMeteringEvent(String eventId, MeteringEventType eventType, int costCents, Instant recordedAt) {
        MeteringEvent event = new MeteringEvent();
        event.setTenantId(tenantId);
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setCostUsdCents(costCents);
        event.setRecordedAt(recordedAt);
        return event;
    }
}
