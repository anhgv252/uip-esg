package com.uip.backend.workflow.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ProcessInstanceDto {
    private String id;
    private String processDefinitionId;
    private String processDefinitionKey;
    private String businessKey;
    private String state;
    private LocalDateTime startTime;
    private Map<String, Object> variables;
}
