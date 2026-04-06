package com.uip.backend.workflow.dto;

import lombok.Data;

import java.util.List;

@Data
public class ClaudeApiResponse {
    private String id;
    private String type;
    private String role;
    private List<Content> content;
    private String model;
    private String stopReason;
    
    @Data
    public static class Content {
        private String type;
        private String text;
    }
}
