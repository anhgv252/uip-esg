package com.uip.backend.workflow.controller;

import com.uip.backend.auth.config.JwtAuthenticationFilter;
import com.uip.backend.common.ratelimit.RateLimitFilter;
import com.uip.backend.workflow.dto.ProcessDefinitionDto;
import com.uip.backend.workflow.dto.ProcessInstanceDto;
import com.uip.backend.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = WorkflowController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class)
    }
)
@WithMockUser(roles = "OPERATOR")
class WorkflowControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean WorkflowService workflowService;

    @Test
    void listDefinitions_returns200() throws Exception {
        ProcessDefinitionDto def = ProcessDefinitionDto.builder()
                .id("def-1").key("aiC01_aqiCitizenAlert").name("AQI Alert").version(1).build();
        when(workflowService.listDefinitions()).thenReturn(List.of(def));

        mockMvc.perform(get("/api/v1/workflow/definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("aiC01_aqiCitizenAlert"));
    }

    @Test
    void getDefinitionXml_returns200() throws Exception {
        when(workflowService.getProcessDefinitionXml("def-1")).thenReturn("<bpmn:definitions/>");

        mockMvc.perform(get("/api/v1/workflow/definitions/def-1/xml"))
                .andExpect(status().isOk())
                .andExpect(content().string("<bpmn:definitions/>"));
    }

    @Test
    void listInstances_returns200() throws Exception {
        when(workflowService.listInstances(eq("ALL"), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/workflow/instances"))
                .andExpect(status().isOk());
    }

    @Test
    void getInstanceVariables_returns200() throws Exception {
        when(workflowService.getInstanceVariables("inst-1")).thenReturn(Map.of("key", "value"));

        mockMvc.perform(get("/api/v1/workflow/instances/inst-1/variables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("value"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void startProcess_returns200() throws Exception {
        ProcessInstanceDto inst = ProcessInstanceDto.builder().id("inst-2").processDefinitionKey("aiC01").build();
        when(workflowService.startProcess(eq("aiC01"), any())).thenReturn(inst);

        mockMvc.perform(post("/api/v1/workflow/start/aiC01")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("inst-2"));
    }
}
