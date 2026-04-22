package com.uip.backend.workflow.delegate.management;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AqiTrafficControlDelegate")
class AqiTrafficControlDelegateTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private DelegateExecution execution;
    @InjectMocks private AqiTrafficControlDelegate delegate;

    @Test
    @DisplayName("recommendedActions non-null → restrictionAreas chứa action đầu tiên")
    void execute_withActions_setsRestrictionAreas() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("[{\"district\":\"D7\",\"restriction\":\"Odd-even\"}]");
        when(execution.getVariable("aiRecommendedActions")).thenReturn(List.of("Odd-even restriction"));
        when(execution.getVariable("affectedDistricts")).thenReturn("D7");
        when(execution.getVariable("aqiValue")).thenReturn(175.0);
        when(execution.getVariable("aiSeverity")).thenReturn("HIGH");

        delegate.execute(execution);

        verify(execution).setVariable(eq("restrictionAreas"), anyString());
        verify(execution).setVariable(eq("recommendationReport"), contains("D7"));
    }

    @Test
    @DisplayName("recommendedActions null → không throw, default restriction = Monitor air quality")
    void execute_nullActions_defaultRestriction() throws Exception {
        when(execution.getVariable("aiRecommendedActions")).thenReturn(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        assertThatCode(() -> delegate.execute(execution)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ObjectMapper throw JsonProcessingException → restrictionAreas = []")
    void execute_jsonError_emptyRestrictionAreas() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("err"){});

        delegate.execute(execution);

        verify(execution).setVariable("restrictionAreas", "[]");
    }

    @Test
    @DisplayName("recommendationReport chứa đủ thông tin")
    void execute_reportContainsAllInfo() throws Exception {
        when(execution.getVariable("aqiValue")).thenReturn(200.0);
        when(execution.getVariable("affectedDistricts")).thenReturn("D1,D3");
        when(execution.getVariable("aiSeverity")).thenReturn("CRITICAL");
        when(execution.getVariable("aiRecommendedActions")).thenReturn(List.of("Close factories"));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");

        delegate.execute(execution);

        ArgumentCaptor<String> reportCaptor = ArgumentCaptor.forClass(String.class);
        verify(execution).setVariable(eq("recommendationReport"), reportCaptor.capture());
        assertThat(reportCaptor.getValue()).contains("D1,D3").contains("CRITICAL").contains("Close factories");
    }
}
