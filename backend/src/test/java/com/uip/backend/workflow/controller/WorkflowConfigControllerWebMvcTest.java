package com.uip.backend.workflow.controller;

import com.uip.backend.auth.config.JwtAuthenticationFilter;
import com.uip.backend.workflow.config.FilterEvaluator;
import com.uip.backend.workflow.config.TriggerConfig;
import com.uip.backend.workflow.config.TriggerConfigRepository;
import com.uip.backend.workflow.config.VariableMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * URL contract regression test — locks the correct path after BUG-S4-001.
 * BUG-S4-001: URL mismatch /wf-config vs /admin/workflow-configs discovered post-implementation.
 * This test fails if @RequestMapping path is changed without updating FE.
 */
@WebMvcTest(
    controllers = WorkflowConfigController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@WithMockUser(roles = "ADMIN")
@DisplayName("WorkflowConfigController — URL Contract (BUG-S4-001 regression lock)")
class WorkflowConfigControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @MockBean TriggerConfigRepository configRepo;
    @MockBean FilterEvaluator filterEvaluator;
    @MockBean VariableMapper variableMapper;

    @Test
    @DisplayName("GET /api/v1/admin/workflow-configs — correct path returns 200")
    void correctUrl_returns200() throws Exception {
        when(configRepo.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/workflow-configs"))
               .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/admin/wf-config — old wrong path not mapped (BUG-S4-001 regression)")
    void oldWrongUrl_notMapped() throws Exception {
        // Any non-2xx response proves the old URL is not a valid endpoint.
        mockMvc.perform(get("/api/v1/admin/wf-config"))
               .andExpect(status().is(org.hamcrest.Matchers.greaterThanOrEqualTo(400)));
    }

    @Test
    @DisplayName("GET /api/v1/workflow/configs — another wrong path not mapped")
    void anotherWrongUrl_notMapped() throws Exception {
        mockMvc.perform(get("/api/v1/workflow/configs"))
               .andExpect(status().is(org.hamcrest.Matchers.greaterThanOrEqualTo(400)));
    }

    @Test
    @DisplayName("GET /api/v1/admin/workflow-configs/{id} — correct subpath returns 200")
    void correctSubUrl_returns200() throws Exception {
        TriggerConfig config = TriggerConfig.builder()
            .id(1L).scenarioKey("aiC01").processKey("aiC01")
            .triggerType("KAFKA").enabled(true)
            .filterConditions("[]").variableMapping("{}")
            .build();
        when(configRepo.findById(1L)).thenReturn(java.util.Optional.of(config));

        mockMvc.perform(get("/api/v1/admin/workflow-configs/1"))
               .andExpect(status().isOk());
    }
}
