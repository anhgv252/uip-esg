package com.uip.backend.workflow.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProcessDefinitionDto {
    private String id;
    private String key;
    private String name;
    private String tenantId;
    private int version;
    private String deploymentId;
    private boolean suspended;
}
