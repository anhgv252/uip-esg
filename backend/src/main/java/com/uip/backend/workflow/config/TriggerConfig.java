package com.uip.backend.workflow.config;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trigger_config", schema = "workflow")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TriggerConfig {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_key", nullable = false, unique = true)
    private String scenarioKey;

    @Column(name = "process_key", nullable = false)
    private String processKey;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String description;

    @Column(name = "trigger_type", nullable = false, length = 20)
    private String triggerType;

    @Column(name = "kafka_topic")
    private String kafkaTopic;

    @Column(name = "kafka_consumer_group")
    private String kafkaConsumerGroup;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_conditions", columnDefinition = "jsonb")
    private String filterConditions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variable_mapping", nullable = false, columnDefinition = "jsonb")
    private String variableMapping;

    @Column(name = "schedule_cron")
    private String scheduleCron;

    @Column(name = "schedule_query_bean")
    private String scheduleQueryBean;

    @Column(name = "prompt_template_path")
    private String promptTemplatePath;

    @Column(name = "ai_confidence_threshold", precision = 3, scale = 2)
    private BigDecimal aiConfidenceThreshold;

    @Column(name = "deduplication_key")
    private String deduplicationKey;

    private Boolean enabled;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private String updatedBy;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
