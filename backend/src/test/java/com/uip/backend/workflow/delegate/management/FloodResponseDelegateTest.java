package com.uip.backend.workflow.delegate.management;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FloodResponseDelegate")
class FloodResponseDelegateTest {

    @Mock private DelegateExecution execution;
    @InjectMocks private FloodResponseDelegate delegate;

    @Test
    @DisplayName("aiSeverity CRITICAL → teamDispatched = true, operationsLogId được set")
    void execute_criticalSeverity_teamDispatched() throws Exception {
        when(execution.getVariable("aiSeverity")).thenReturn("CRITICAL");
        when(execution.getVariable("waterLevel")).thenReturn(4.2);
        when(execution.getVariable("location")).thenReturn("CANAL-D8");
        when(execution.getVariable("aiReasoning")).thenReturn("Rapid water rise");

        delegate.execute(execution);

        verify(execution).setVariable("teamDispatched", true);
        ArgumentCaptor<String> logIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(execution).setVariable(eq("operationsLogId"), logIdCaptor.capture());
        assertThat(logIdCaptor.getValue()).hasSize(36);
    }

    @Test
    @DisplayName("aiSeverity LOW → teamDispatched = false")
    void execute_lowSeverity_noDispatch() throws Exception {
        when(execution.getVariable("aiSeverity")).thenReturn("LOW");

        delegate.execute(execution);

        verify(execution).setVariable("teamDispatched", false);
    }

    @Test
    @DisplayName("aiSeverity null → teamDispatched = true")
    void execute_nullSeverity_dispatchesTeam() throws Exception {
        when(execution.getVariable("aiSeverity")).thenReturn(null);

        delegate.execute(execution);

        verify(execution).setVariable("teamDispatched", true);
    }

    @Test
    @DisplayName("tất cả variables null → không throw exception")
    void execute_allNull_doesNotThrow() {
        assertThatCode(() -> delegate.execute(execution)).doesNotThrowAnyException();
    }
}
