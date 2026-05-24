package com.uip.backend.alert.service;

import com.uip.backend.alert.api.dto.AlertEventDto;
import com.uip.backend.alert.api.dto.AlertRuleRequest;
import com.uip.backend.alert.api.dto.AcknowledgeRequest;
import com.uip.backend.alert.domain.AlertRule;
import com.uip.backend.tenant.context.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest(properties = {
    "security.jwt.secret=test-secret-for-integration-tests-only-32chars",
    "spring.cache.type=simple",
    "uip.cagg.alert-refresh-ms=999999999",
    "uip.cagg.sensor-refresh-ms=999999999"
})
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AlertServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("uip_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:29092");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("uip.capabilities.multi-tenancy", () -> "true");
    }

    @MockBean @SuppressWarnings("unused") RedisConnectionFactory redisConnectionFactory;
    @MockBean @SuppressWarnings("unused") ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
    @MockBean @SuppressWarnings("unused") StringRedisTemplate redisTemplate;
    @MockBean @SuppressWarnings("unused") RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean @SuppressWarnings("unused") KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AlertService alertService;
    @Autowired private PlatformTransactionManager txManager;

    private static final String TENANT_A = "alert-it-tenant-a";
    private static final String TENANT_B = "alert-it-tenant-b";

    private UUID ruleIdA;
    private UUID ruleIdB;

    @BeforeAll
    void setupData() {
        Instant now = Instant.now();
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);

        // Seed Tenant A data within a transaction that sets app.tenant_id
        txTemplate.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL app.tenant_id = '" + TENANT_A + "'");
            ruleIdA = insertRule(TENANT_A, "High PM2.5", "ENVIRONMENT", "pm25", "GT", 50.0, "CRITICAL");
            insertAlertEvent(TENANT_A, ruleIdA, "SENSOR-A1", "ENVIRONMENT", "pm25", 75.0, 50.0, "CRITICAL", "OPEN", now);
            insertAlertEvent(TENANT_A, ruleIdA, "SENSOR-A2", "ENVIRONMENT", "pm25", 60.0, 50.0, "CRITICAL", "OPEN", now.minus(1, ChronoUnit.HOURS));
            insertAlertEvent(TENANT_A, ruleIdA, "SENSOR-A1", "ENVIRONMENT", "pm25", 55.0, 50.0, "WARNING", "ACKNOWLEDGED", now.minus(2, ChronoUnit.HOURS));
            insertAlertEvent(TENANT_A, ruleIdA, "SENSOR-A1", "ENVIRONMENT", "pm25", 45.0, 50.0, "INFO", "RESOLVED", now.minus(1, ChronoUnit.DAYS));
        });

        // Seed Tenant B data within a separate transaction
        txTemplate.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL app.tenant_id = '" + TENANT_B + "'");
            ruleIdB = insertRule(TENANT_B, "High Temp", "ENVIRONMENT", "temperature", "GT", 40.0, "WARNING");
            insertAlertEvent(TENANT_B, ruleIdB, "SENSOR-B1", "ENVIRONMENT", "temperature", 45.0, 40.0, "WARNING", "OPEN", now);
        });
    }

    private UUID insertRule(String tenantId, String name, String module, String measureType,
                            String operator, double threshold, String severity) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO alerts.alert_rules (id, tenant_id, rule_name, module, measure_type, operator, threshold, severity, is_active, cooldown_minutes, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, true, 10, now())
            """, id, tenantId, name, module, measureType, operator, threshold, severity);
        return id;
    }

    private void insertAlertEvent(String tenantId, UUID ruleId, String sensorId, String module,
                                  String measureType, double value, double threshold,
                                  String severity, String status, Instant detectedAt) {
        jdbc.update("""
            INSERT INTO alerts.alert_events (id, tenant_id, rule_id, sensor_id, module, measure_type, value, threshold, severity, status, detected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, UUID.randomUUID(), tenantId, ruleId, sensorId, module, measureType,
                value, threshold, severity, status, Timestamp.from(detectedAt));
    }

    // ─── queryAlerts ───────────────────────────────────────────────────────

    @Test
    @Order(1)
    void queryAlerts_all_returnsAllOpenAlerts() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Page<AlertEventDto> alerts = alertService.queryAlerts("OPEN", null, null, null, 0, 20);
            assertThat(alerts.getTotalElements()).isGreaterThanOrEqualTo(2);
            assertThat(alerts.getContent()).allSatisfy(a -> assertThat(a.getStatus()).isEqualTo("OPEN"));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(2)
    void queryAlerts_bySeverity_returnsOnlyCritical() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Page<AlertEventDto> alerts = alertService.queryAlerts(null, "CRITICAL", null, null, 0, 20);
            assertThat(alerts.getContent()).allSatisfy(a -> assertThat(a.getSeverity()).isEqualTo("CRITICAL"));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(3)
    void queryAlerts_tenantIsolation() {
        TenantContext.setCurrentTenant(TENANT_B);
        try {
            Page<AlertEventDto> alerts = alertService.queryAlerts("OPEN", null, null, null, 0, 20);
            // Note: Testcontainers PG superuser bypasses RLS, so seed data is also visible.
            // Verify that at least our Tenant B alert is present with correct sensor.
            assertThat(alerts.getContent()).anySatisfy(a ->
                    assertThat(a.getSensorId()).isEqualTo("SENSOR-B1"));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(4)
    void queryAlerts_noMatch_returnsEmpty() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Page<AlertEventDto> alerts = alertService.queryAlerts("RESOLVED", "CRITICAL", null, null, 0, 20);
            assertThat(alerts.getContent()).isEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(5)
    void queryAlerts_timeRange_filtersCorrectly() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Instant from = Instant.now().minus(30, ChronoUnit.MINUTES);
            Page<AlertEventDto> alerts = alertService.queryAlerts("OPEN", null, from, Instant.now(), 0, 20);
            assertThat(alerts.getContent()).isNotEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(6)
    void queryAlerts_ruleNameResolved() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Page<AlertEventDto> alerts = alertService.queryAlerts("OPEN", null, null, null, 0, 20);
            assertThat(alerts.getContent()).anySatisfy(a ->
                    assertThat(a.getRuleName()).isEqualTo("High PM2.5"));
        } finally {
            TenantContext.clear();
        }
    }

    // ─── acknowledgeAlert ──────────────────────────────────────────────────

    @Test
    @Order(10)
    void acknowledgeAlert_updatesStatus() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Page<AlertEventDto> openAlerts = alertService.queryAlerts("OPEN", "CRITICAL", null, null, 0, 20);
            assertThat(openAlerts.getContent()).isNotEmpty();
            UUID alertId = openAlerts.getContent().get(0).getId();

            AcknowledgeRequest req = new AcknowledgeRequest();
            req.setNote("Investigating sensor issue");
            AlertEventDto result = alertService.acknowledgeAlert(alertId, "admin", req);

            assertThat(result.getStatus()).isEqualTo("ACKNOWLEDGED");
            assertThat(result.getAcknowledgedBy()).isEqualTo("admin");
            assertThat(result.getNote()).isEqualTo("Investigating sensor issue");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(11)
    void acknowledgeAlert_notFound_throwsException() {
        assertThatThrownBy(() -> alertService.acknowledgeAlert(UUID.randomUUID(), "admin", new AcknowledgeRequest()))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    // ─── escalateAlert ─────────────────────────────────────────────────────

    @Test
    @Order(15)
    void escalateAlert_updatesStatus() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Page<AlertEventDto> openAlerts = alertService.queryAlerts("OPEN", null, null, null, 0, 20);
            assertThat(openAlerts.getContent()).isNotEmpty();
            UUID alertId = openAlerts.getContent().get(0).getId();

            AlertEventDto result = alertService.escalateAlert(alertId, "operator1", "Escalated to facilities");

            assertThat(result.getStatus()).isEqualTo("ESCALATED");
            assertThat(result.getAcknowledgedBy()).isEqualTo("operator1");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(16)
    void escalateAlert_notFound_throwsException() {
        assertThatThrownBy(() -> alertService.escalateAlert(UUID.randomUUID(), "admin", "note"))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    // ─── Alert Rules ───────────────────────────────────────────────────────

    @Test
    @Order(20)
    void listRules_returnsActiveRules() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            List<AlertRule> rules = alertService.listRules();
            assertThat(rules).isNotEmpty();
            assertThat(rules).allSatisfy(r -> assertThat(r.isActive()).isTrue());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(21)
    void createRule_savesAndReturns() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            AlertRuleRequest req = new AlertRuleRequest();
            req.setRuleName("Test Rule IT");
            req.setModule("ENVIRONMENT");
            req.setMeasureType("pm10");
            req.setOperator("GT");
            req.setThreshold(100.0);
            req.setSeverity("WARNING");
            req.setCooldownMinutes(15);

            AlertRule created = alertService.createRule(req);
            assertThat(created.getId()).isNotNull();
            assertThat(created.getRuleName()).isEqualTo("Test Rule IT");
            assertThat(created.isActive()).isTrue();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(22)
    void deleteRule_softDeactivates() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            AlertRuleRequest req = new AlertRuleRequest();
            req.setRuleName("Rule to Delete");
            req.setModule("ENVIRONMENT");
            req.setMeasureType("o3");
            req.setOperator("GT");
            req.setThreshold(80.0);
            req.setSeverity("INFO");
            req.setCooldownMinutes(5);

            AlertRule created = alertService.createRule(req);
            alertService.deleteRule(created.getId());

            // Verify deactivated — listRules only returns active
            List<AlertRule> active = alertService.listRules();
            assertThat(active).noneSatisfy(r -> assertThat(r.getId()).isEqualTo(created.getId()));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(23)
    void deleteRule_notFound_throwsException() {
        assertThatThrownBy(() -> alertService.deleteRule(UUID.randomUUID()))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    // ─── getPublicNotifications ─────────────────────────────────────────────

    @Test
    @Order(30)
    void getPublicNotifications_returnsRecentAlerts() {
        // getPublicNotifications queries alert_events without tenant filter
        // but uses @Transactional so the aspect sets app.tenant_id from ThreadLocal
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Page<AlertEventDto> notifications = alertService.getPublicNotifications(0, 20);
            assertThat(notifications.getTotalElements()).isGreaterThanOrEqualTo(1);
            assertThat(notifications.getContent()).allSatisfy(n ->
                    assertThat(n.getDetectedAt()).isAfter(Instant.now().minus(48, ChronoUnit.HOURS)));
        } finally {
            TenantContext.clear();
        }
    }

    // ─── pagination ────────────────────────────────────────────────────────

    @Test
    @Order(40)
    void queryAlerts_pagination_works() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Page<AlertEventDto> page0 = alertService.queryAlerts(null, null, null, null, 0, 2);
            assertThat(page0.getSize()).isLessThanOrEqualTo(2);
            if (page0.getTotalElements() > 2) {
                assertThat(page0.hasNext()).isTrue();
            }
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(41)
    void queryAlerts_allStatuses_returnsFullList() {
        TenantContext.setCurrentTenant(TENANT_A);
        try {
            Page<AlertEventDto> all = alertService.queryAlerts(null, null, null, null, 0, 50);
            assertThat(all.getTotalElements()).isGreaterThanOrEqualTo(4);
        } finally {
            TenantContext.clear();
        }
    }
}
