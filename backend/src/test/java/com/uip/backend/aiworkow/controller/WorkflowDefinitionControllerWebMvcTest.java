package com.uip.backend.aiworkow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.aiworkflow.model.WorkflowDefinition;
import com.uip.backend.aiworkflow.service.WorkflowDefinitionService;
import com.uip.backend.auth.config.JwtAuthenticationFilter;
import com.uip.backend.common.ratelimit.RateLimitFilter;
import com.uip.backend.common.ratelimit.TenantRateLimiter;
import com.uip.backend.tenant.context.TenantContext;
import com.uip.backend.tenant.filter.TenantContextFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WebMvc tests for WorkflowDefinitionController — 7 tests (one per AC).
 *
 * Uses @AutoConfigureMockMvc(addFilters = false) to bypass security filters,
 * matching the project's established pattern for controller slice tests.
 */
@WebMvcTest(
    controllers = WorkflowDefinitionController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = TenantContextFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class)
    }
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("WorkflowDefinitionController — WebMvc")
class WorkflowDefinitionControllerWebMvcTest {

    private static final String TENANT = "hcm";
    private static final String BASE_URL = "/api/v1/workflows";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean WorkflowDefinitionService service;
    @MockBean @SuppressWarnings("unused") TenantRateLimiter tenantRateLimiter;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private WorkflowDefinition sampleDefinition() {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setId(UUID.randomUUID());
        def.setTenantId(TENANT);
        def.setName("Flood Alert Workflow");
        def.setDescription("Test workflow");
        def.setBpmnXml("<definitions><process id=\"test\"/></definitions>");
        def.setVersion(1);
        def.setIsActive(true);
        def.setCreatedAt(Instant.now());
        def.setUpdatedAt(Instant.now());
        return def;
    }

    @Test
    @DisplayName("POST /api/v1/workflows — AC-1: create workflow")
    void createWorkflow() throws Exception {
        WorkflowDefinition def = sampleDefinition();
        when(service.create(eq(TENANT), anyString(), anyString(), anyString())).thenReturn(def);

        var request = new WorkflowDefinitionController.CreateWorkflowRequest(
                "Flood Alert Workflow", "Test workflow",
                "<definitions><process id=\"test\"/></definitions>");

        try (MockedStatic<TenantContext> tc = Mockito.mockStatic(TenantContext.class)) {
            tc.when(TenantContext::getCurrentTenant).thenReturn(TENANT);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Flood Alert Workflow"))
                    .andExpect(jsonPath("$.tenantId").value(TENANT));
        }
    }

    @Test
    @DisplayName("GET /api/v1/workflows — AC-2: list workflows")
    void listWorkflows() throws Exception {
        WorkflowDefinition def = sampleDefinition();
        when(service.list(eq(TENANT), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(def)));

        try (MockedStatic<TenantContext> tc = Mockito.mockStatic(TenantContext.class)) {
            tc.when(TenantContext::getCurrentTenant).thenReturn(TENANT);

            mockMvc.perform(get(BASE_URL)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].name").value("Flood Alert Workflow"));
        }
    }

    @Test
    @DisplayName("GET /api/v1/workflows/{id} — AC-3: get by ID")
    void getWorkflow() throws Exception {
        WorkflowDefinition def = sampleDefinition();
        when(service.getById(def.getId(), TENANT)).thenReturn(def);

        try (MockedStatic<TenantContext> tc = Mockito.mockStatic(TenantContext.class)) {
            tc.when(TenantContext::getCurrentTenant).thenReturn(TENANT);

            mockMvc.perform(get(BASE_URL + "/" + def.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(def.getId().toString()));
        }
    }

    @Test
    @DisplayName("PUT /api/v1/workflows/{id} — AC-4: update workflow")
    void updateWorkflow() throws Exception {
        WorkflowDefinition def = sampleDefinition();
        def.setVersion(2);
        when(service.update(eq(def.getId()), eq(TENANT), anyString(), anyString(), anyString()))
                .thenReturn(def);

        var request = new WorkflowDefinitionController.UpdateWorkflowRequest(
                "Updated Name", "Updated desc",
                "<definitions><process id=\"test-v2\"/></definitions>");

        try (MockedStatic<TenantContext> tc = Mockito.mockStatic(TenantContext.class)) {
            tc.when(TenantContext::getCurrentTenant).thenReturn(TENANT);

            mockMvc.perform(put(BASE_URL + "/" + def.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(2));
        }
    }

    @Test
    @DisplayName("DELETE /api/v1/workflows/{id} — AC-5: soft delete")
    void deleteWorkflow() throws Exception {
        UUID id = UUID.randomUUID();

        try (MockedStatic<TenantContext> tc = Mockito.mockStatic(TenantContext.class)) {
            tc.when(TenantContext::getCurrentTenant).thenReturn(TENANT);

            mockMvc.perform(delete(BASE_URL + "/" + id))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    @DisplayName("POST /api/v1/workflows/{id}/deploy — AC-6: deploy to Camunda")
    void deployWorkflow() throws Exception {
        WorkflowDefinition def = sampleDefinition();
        def.setCamundaDeploymentId("deploy-001");
        when(service.deploy(def.getId(), TENANT)).thenReturn(def);

        try (MockedStatic<TenantContext> tc = Mockito.mockStatic(TenantContext.class)) {
            tc.when(TenantContext::getCurrentTenant).thenReturn(TENANT);

            mockMvc.perform(post(BASE_URL + "/" + def.getId() + "/deploy"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.camundaDeploymentId").value("deploy-001"));
        }
    }

    @Test
    @DisplayName("POST /api/v1/workflows/{id}/execute — AC-7: execute workflow")
    void executeWorkflow() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.execute(id, TENANT)).thenReturn(Map.of(
                "processInstanceId", "proc-001",
                "processKey", "flood-alert",
                "tenantId", TENANT
        ));

        try (MockedStatic<TenantContext> tc = Mockito.mockStatic(TenantContext.class)) {
            tc.when(TenantContext::getCurrentTenant).thenReturn(TENANT);

            mockMvc.perform(post(BASE_URL + "/" + id + "/execute"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.processInstanceId").value("proc-001"))
                    .andExpect(jsonPath("$.processKey").value("flood-alert"));
        }
    }
}
