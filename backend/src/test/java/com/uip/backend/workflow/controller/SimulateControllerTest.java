package com.uip.backend.workflow.controller;

import com.uip.backend.auth.config.JwtAuthenticationFilter;
import com.uip.backend.common.ratelimit.RateLimitFilter;
import com.uip.backend.workflow.dto.ProcessInstanceDto;
import com.uip.backend.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = SimulateController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class)
    }
)
@WithMockUser
class SimulateControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean WorkflowService workflowService;

    @Test
    void simulateSensorReading_belowThreshold_noAlert() throws Exception {
        mockMvc.perform(post("/api/v1/simulate/iot-sensor")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sensorType\":\"AQI\",\"sensorId\":\"SEN-01\",\"value\":100,\"district\":\"D1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertTriggered").value(false))
                .andExpect(jsonPath("$.processInstanceId").value(""));

        verify(workflowService, never()).startProcess(any(), any());
    }

    @Test
    void simulateSensorReading_aboveThreshold_startsProcess() throws Exception {
        ProcessInstanceDto inst = ProcessInstanceDto.builder().id("proc-1").processDefinitionKey("aiC01_aqiCitizenAlert").build();
        when(workflowService.startProcess(eq("aiC01_aqiCitizenAlert"), any())).thenReturn(inst);

        mockMvc.perform(post("/api/v1/simulate/iot-sensor")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sensorType\":\"AQI\",\"sensorId\":\"SEN-01\",\"value\":200,\"district\":\"D2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertTriggered").value(true))
                .andExpect(jsonPath("$.processInstanceId").value("proc-1"));

        verify(workflowService).startProcess(eq("aiC01_aqiCitizenAlert"), any());
    }

    @Test
    void simulateSensorReading_nonAqiSensorType_noAlert() throws Exception {
        mockMvc.perform(post("/api/v1/simulate/iot-sensor")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sensorType\":\"WATER\",\"value\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertTriggered").value(false));
    }

    @Test
    void simulateSensorReading_defaultValues() throws Exception {
        mockMvc.perform(post("/api/v1/simulate/iot-sensor")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorType").value("AQI"))
                .andExpect(jsonPath("$.sensorId").value("ENV-DEMO-001"))
                .andExpect(jsonPath("$.threshold").value(150.0));
    }

    @Test
    void simulateSensorReading_exactThreshold_noAlert() throws Exception {
        mockMvc.perform(post("/api/v1/simulate/iot-sensor")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sensorType\":\"AQI\",\"value\":150}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertTriggered").value(false));
    }
}
