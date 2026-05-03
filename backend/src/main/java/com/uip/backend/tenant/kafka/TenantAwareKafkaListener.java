package com.uip.backend.tenant.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.tenant.context.TenantContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for Kafka listeners needing tenant context.
 * Extracts tenant_id from message body, sets TenantContext, wraps in try/finally.
 * ADR-020: Non-HTTP Tenant ID Propagation
 */
@Slf4j
public abstract class TenantAwareKafkaListener {

    private final ObjectMapper objectMapper = new ObjectMapper();

    protected String extractTenantId(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode tenantNode = node.get("tenant_id");
            if (tenantNode != null && !tenantNode.asText().isBlank()) {
                return tenantNode.asText();
            }
        } catch (Exception e) {
            log.warn("Failed to extract tenant_id from Kafka message: {}", e.getMessage());
        }
        return "default";
    }

    protected void withTenantContext(String tenantId, Runnable action) {
        try {
            TenantContext.setCurrentTenant(tenantId);
            action.run();
        } finally {
            TenantContext.clear();
        }
    }
}
