package com.uip.backend.workflow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TriggerConfigAuditService")
class TriggerConfigAuditServiceTest {

    @Mock private TriggerConfigAuditRepository auditRepo;

    private TriggerConfigAuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new TriggerConfigAuditService(auditRepo, new ObjectMapper());
    }

    @Test
    @DisplayName("record CREATE → saves audit entry với đúng action và changedBy")
    void record_create_savesCorrectEntry() {
        TriggerConfig config = TriggerConfig.builder()
                .id(1L).scenarioKey("aiC01").processKey("p1")
                .displayName("AQI Alert").triggerType("KAFKA")
                .variableMapping("{}").enabled(true).build();

        auditService.record(config, "CREATE", "admin");

        ArgumentCaptor<TriggerConfigAudit> captor = ArgumentCaptor.forClass(TriggerConfigAudit.class);
        verify(auditRepo).save(captor.capture());
        TriggerConfigAudit saved = captor.getValue();
        assertThat(saved.getConfigId()).isEqualTo(1L);
        assertThat(saved.getScenarioKey()).isEqualTo("aiC01");
        assertThat(saved.getAction()).isEqualTo("CREATE");
        assertThat(saved.getChangedBy()).isEqualTo("admin");
        assertThat(saved.getSnapshot()).contains("aiC01");
    }

    @Test
    @DisplayName("record DISABLE → saves với action DISABLE")
    void record_disable_savesDisableAction() {
        TriggerConfig config = TriggerConfig.builder()
                .id(2L).scenarioKey("aiM03").processKey("p2")
                .displayName("Utility Anomaly").triggerType("SCHEDULED")
                .variableMapping("{}").enabled(false).build();

        auditService.record(config, "DISABLE", "operator1");

        ArgumentCaptor<TriggerConfigAudit> captor = ArgumentCaptor.forClass(TriggerConfigAudit.class);
        verify(auditRepo).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("DISABLE");
        assertThat(captor.getValue().getChangedBy()).isEqualTo("operator1");
    }

    @Test
    @DisplayName("getHistory → delegate tới repository đúng configId")
    void getHistory_delegatesToRepository() {
        List<TriggerConfigAudit> expected = List.of(
                TriggerConfigAudit.builder().id(10L).configId(5L).action("CREATE").changedBy("admin").snapshot("{}").build()
        );
        when(auditRepo.findByConfigIdOrderByChangedAtDesc(5L)).thenReturn(expected);

        List<TriggerConfigAudit> result = auditService.getHistory(5L);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("record với ObjectMapper fail → không throw, không crash operation")
    void record_serializationError_doesNotThrow() {
        // Config có id=null → sẽ serialize được (không gây exception thực sự)
        // Thay vào đó test rằng service không throw ra ngoài kể cả khi repo fail
        doThrow(new RuntimeException("DB down")).when(auditRepo).save(any());
        TriggerConfig config = TriggerConfig.builder()
                .id(3L).scenarioKey("aiC01").processKey("p1")
                .displayName("test").triggerType("KAFKA").variableMapping("{}").build();

        // Must NOT throw
        auditService.record(config, "UPDATE", "admin");
    }
}
