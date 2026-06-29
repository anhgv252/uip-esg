package com.uip.backend.billing.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.billing.domain.MeteringEvent;
import com.uip.backend.billing.domain.MeteringEventType;
import com.uip.backend.billing.repository.MeteringEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * M5-2 T07: Consumes billing metering events from AI modules.
 * Topic: UIP.billing.metering.event.v1
 * 
 * Responsibilities:
 *   1. Deduplicate using Redis (5-minute window)
 *   2. Persist to billing.metering_events
 *   3. DLQ on persistent failures (max 3 retries)
 * 
 * Idempotency: event_id unique constraint + Redis dedup
 * Multi-tenancy: tenant_id set by TenantEntityListener
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MeteringEventKafkaConsumer {

    public static final String TOPIC = "UIP.billing.metering.event.v1";
    public static final String DLQ_TOPIC = "UIP.billing.metering.event.v1.dlq";
    private static final int MAX_RETRIES = 3;

    /**
     * Dedup TTL: 5 minutes — handles Kafka at-least-once redelivery.
     * Key format: billing:dedup:tenant:{tenantId}:event:{eventId}
     * Prevents cross-tenant dedup leaks.
     */
    private static final Duration DEDUP_TTL = Duration.ofMinutes(5);

    private final MeteringEventRepository meteringEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(
        topics = MeteringEventKafkaConsumer.TOPIC,
        groupId = "uip-backend-billing",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(Map<String, Object> payload, Acknowledgment ack,
                        @Header(value = "kafka_receivedTopic", required = false) String topic,
                        @Header(value = "x-retry-count", required = false, defaultValue = "0") int retryCount) {
        try {
            MeteringEvent event = mapToMeteringEvent(payload);

            // Tenant-prefixed dedup key — prevents cross-tenant leaks
            String tenantId = event.getTenantId() != null ? event.getTenantId() : "default";
            String dedupKey = "billing:dedup:tenant:%s:event:%s".formatted(tenantId, event.getEventId());
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_TTL);

            // Fail-open: null = Redis unavailable → process event (don't lose billing data)
            if (Boolean.FALSE.equals(isNew)) {
                log.debug("Duplicate metering event suppressed: eventId={} tenant={}",
                        event.getEventId(), tenantId);
                ack.acknowledge();
                return;
            }

            // DB-level idempotency check (event_id unique constraint)
            if (meteringEventRepository.existsByEventId(event.getEventId())) {
                log.debug("Metering event already exists in DB: eventId={}", event.getEventId());
                ack.acknowledge();
                return;
            }

            MeteringEvent saved = meteringEventRepository.save(event);
            log.info("Metering event persisted: eventId={} type={} cost={} cents tenant={}",
                    saved.getEventId(), saved.getEventType(), saved.getCostUsdCents(), saved.getTenantId());
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process metering event (attempt {}/{}): {}",
                    retryCount + 1, MAX_RETRIES, e.getMessage(), e);

            if (retryCount + 1 >= MAX_RETRIES) {
                try {
                    String json = objectMapper.writeValueAsString(payload);
                    kafkaTemplate.send(DLQ_TOPIC, json);
                    log.warn("Metering event sent to DLQ after {} failed attempts", MAX_RETRIES);
                } catch (Exception dlqEx) {
                    log.error("Failed to send metering event to DLQ: {}", dlqEx.getMessage());
                }
                ack.acknowledge(); // Ack to prevent infinite retry loop
            } else {
                throw new RuntimeException("Retry metering event processing", e);
            }
        }
    }

    private MeteringEvent mapToMeteringEvent(Map<String, Object> payload) throws JsonProcessingException {
        MeteringEvent event = new MeteringEvent();
        event.setTenantId(getStringValue(payload, "tenantId", "default"));
        event.setEventId(getStringValue(payload, "eventId", null));
        event.setEventType(MeteringEventType.valueOf(getStringValue(payload, "eventType", "API_CALL")));
        
        String workflowRunIdStr = getStringValue(payload, "workflowRunId", null);
        if (workflowRunIdStr != null) {
            event.setWorkflowRunId(UUID.fromString(workflowRunIdStr));
        }

        // Store metadata as JSON string
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) payload.get("metadata");
        if (metadata != null) {
            event.setMetadata(objectMapper.writeValueAsString(metadata));
        }

        event.setCostUsdCents(getIntValue(payload, "costUsdCents", 0));
        
        String recordedAtStr = getStringValue(payload, "recordedAt", null);
        event.setRecordedAt(recordedAtStr != null ? Instant.parse(recordedAtStr) : Instant.now());

        if (event.getEventId() == null || event.getEventId().isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }

        return event;
    }

    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private Integer getIntValue(Map<String, Object> map, String key, Integer defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return defaultValue;
    }
}
