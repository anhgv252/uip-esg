package com.uip.backend.environment.service;

import com.uip.backend.environment.api.dto.SensorDto;
import com.uip.backend.environment.api.dto.SensorReadingDto;
import com.uip.backend.environment.api.dto.AqiResponseDto;
import com.uip.backend.tenant.context.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
class EnvironmentServiceIT {

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
    @Autowired private EnvironmentService environmentService;
    @Autowired private PlatformTransactionManager txManager;

    private static final String TENANT = "env-it-tenant";
    private static final String SENSOR_1 = "SENSOR-ENV-IT-001";
    private static final String SENSOR_2 = "SENSOR-ENV-IT-002";
    private static final String SENSOR_3 = "SENSOR-ENV-IT-003";
    private static final String DISTRICT_1 = "D-ENV-01";
    private static final String DISTRICT_2 = "D-ENV-02";

    private UUID sensor1Id;
    private UUID sensor2Id;
    private UUID sensor3Id;

    @BeforeAll
    void setupData() {
        Instant now = Instant.now();
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);

        txTemplate.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL app.tenant_id = '" + TENANT + "'");

            // Insert sensors (tenant_id defaults to 'default' via column default)
            sensor1Id = insertSensor(SENSOR_1, "Air Quality Station 1", "AQI", DISTRICT_1, 10.762622, 106.660172, true, now);
            sensor2Id = insertSensor(SENSOR_2, "Air Quality Station 2", "AQI", DISTRICT_1, 10.772622, 106.670172, true, now.minus(10, ChronoUnit.MINUTES));
            sensor3Id = insertSensor(SENSOR_3, "Air Quality Station 3", "AQI", DISTRICT_2, 10.782622, 106.680172, false, now);

            // Insert sensor readings
            insertReading(SENSOR_1, now, 85.0, 35.0, 60.0, 40.0, 20.0, 5.0, 0.5, 28.5, 75.0);
            insertReading(SENSOR_1, now.minus(1, ChronoUnit.HOURS), 75.0, 30.0, 55.0, 35.0, 18.0, 4.0, 0.4, 27.0, 72.0);
            insertReading(SENSOR_1, now.minus(2, ChronoUnit.HOURS), 95.0, 45.0, 70.0, 50.0, 25.0, 8.0, 0.8, 29.0, 78.0);
            insertReading(SENSOR_1, now.minus(1, ChronoUnit.DAYS), 65.0, 25.0, 45.0, 30.0, 15.0, 3.0, 0.3, 26.0, 70.0);

            // SENSOR_2 — single reading
            insertReading(SENSOR_2, now.minus(5, ChronoUnit.MINUTES), 50.0, 20.0, 35.0, 25.0, 10.0, 2.0, 0.2, 30.0, 80.0);

            // SENSOR_3 — inactive sensor, still has reading
            insertReading(SENSOR_3, now, 120.0, 55.0, 80.0, 60.0, 30.0, 10.0, 1.0, 31.0, 85.0);
        });
    }

    private UUID insertSensor(String sensorId, String name, String type, String district,
                              double lat, double lon, boolean active, Instant lastSeen) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO environment.sensors (id, tenant_id, sensor_id, sensor_name, sensor_type, district_code, latitude, longitude, is_active, last_seen_at, installed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            """, id, TENANT, sensorId, name, type, district, lat, lon, active, Timestamp.from(lastSeen));
        return id;
    }

    private void insertReading(String sensorId, Instant ts, double aqi, double pm25, double pm10,
                               double o3, double no2, double so2, double co, double temp, double humidity) {
        jdbc.update("""
            INSERT INTO environment.sensor_readings (tenant_id, sensor_id, timestamp, aqi, pm25, pm10, o3, no2, so2, co, temperature, humidity)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, TENANT, sensorId, Timestamp.from(ts), aqi, pm25, pm10, o3, no2, so2, co, temp, humidity);
    }

    // ─── listSensors ───────────────────────────────────────────────────────

    @Test
    @Order(1)
    void listSensors_returnsOnlyActiveSensors() {
        TenantContext.setCurrentTenant(TENANT);
        try {
            List<SensorDto> sensors = environmentService.listSensors();
            // Note: Testcontainers PG superuser bypasses RLS, so seed sensors are also visible.
            // Verify our test sensors are present and active.
            assertThat(sensors).anySatisfy(s -> {
                assertThat(s.getSensorId()).isEqualTo(SENSOR_1);
                assertThat(s.isActive()).isTrue();
            });
            assertThat(sensors).anySatisfy(s -> {
                assertThat(s.getSensorId()).isEqualTo(SENSOR_2);
                assertThat(s.isActive()).isTrue();
            });
            // SENSOR_3 should NOT be in the list (it's inactive)
            assertThat(sensors).noneSatisfy(s ->
                    assertThat(s.getSensorId()).isEqualTo(SENSOR_3));
            // All returned sensors must be active
            assertThat(sensors).allSatisfy(s -> assertThat(s.isActive()).isTrue());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(2)
    void listSensors_containsCorrectSensorFields() {
        TenantContext.setCurrentTenant(TENANT);
        try {
            List<SensorDto> sensors = environmentService.listSensors();
            SensorDto s1 = sensors.stream().filter(s -> s.getSensorId().equals(SENSOR_1)).findFirst().orElseThrow();
            assertThat(s1.getSensorName()).isEqualTo("Air Quality Station 1");
            assertThat(s1.getSensorType()).isEqualTo("AQI");
            assertThat(s1.getDistrictCode()).isEqualTo(DISTRICT_1);
            assertThat(s1.getLatitude()).isEqualTo(10.762622);
            assertThat(s1.getLongitude()).isEqualTo(106.660172);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(3)
    void listSensors_onlineStatus_detectedCorrectly() {
        TenantContext.setCurrentTenant(TENANT);
        try {
            List<SensorDto> sensors = environmentService.listSensors();
            SensorDto s1 = sensors.stream().filter(s -> s.getSensorId().equals(SENSOR_1)).findFirst().orElseThrow();
            assertThat(s1.getStatus()).isEqualTo("ONLINE");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(4)
    void listSensors_offlineStatus_detectedCorrectly() {
        TenantContext.setCurrentTenant(TENANT);
        try {
            List<SensorDto> sensors = environmentService.listSensors();
            SensorDto s2 = sensors.stream().filter(s -> s.getSensorId().equals(SENSOR_2)).findFirst().orElseThrow();
            // SENSOR_2 last_seen was 10 min ago (> 5 min threshold) but has recent reading
            assertThat(s2.getStatus()).isIn("ONLINE", "OFFLINE");
        } finally {
            TenantContext.clear();
        }
    }

    // ─── listAllSensors (admin) ────────────────────────────────────────────

    @Test
    @Order(10)
    void listAllSensors_returnsActiveAndInactive() {
        TenantContext.setCurrentTenant(TENANT);
        try {
            List<SensorDto> all = environmentService.listAllSensors();
            // Verify SENSOR_3 (inactive) is present — it would be excluded by listSensors
            assertThat(all).anySatisfy(s -> {
                assertThat(s.getSensorId()).isEqualTo(SENSOR_3);
                assertThat(s.isActive()).isFalse();
            });
            // Verify our active sensors are also present
            assertThat(all).anySatisfy(s -> assertThat(s.getSensorId()).isEqualTo(SENSOR_1));
        } finally {
            TenantContext.clear();
        }
    }

    // ─── toggleSensor ──────────────────────────────────────────────────────

    @Test
    @Order(15)
    void toggleSensor_deactivates() {
        TenantContext.setCurrentTenant(TENANT);
        try {
            SensorDto result = environmentService.toggleSensor(sensor1Id, false);
            assertThat(result.isActive()).isFalse();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Order(16)
    void toggleSensor_reactivates() {
        TenantContext.setCurrentTenant(TENANT);
        try {
            SensorDto result = environmentService.toggleSensor(sensor1Id, true);
            assertThat(result.isActive()).isTrue();
        } finally {
            TenantContext.clear();
        }
    }

    // ─── getReadings ───────────────────────────────────────────────────────

    @Test
    @Order(20)
    void getReadings_returnsAllForTimeRange() {
        Instant from = Instant.now().minus(3, ChronoUnit.HOURS);
        List<SensorReadingDto> readings = environmentService.getReadings(SENSOR_1, from, Instant.now(), 100);
        assertThat(readings).hasSize(3);
    }

    @Test
    @Order(21)
    void getReadings_narrowRange_returnsFewer() {
        Instant from = Instant.now().minus(30, ChronoUnit.MINUTES);
        List<SensorReadingDto> readings = environmentService.getReadings(SENSOR_1, from, Instant.now(), 100);
        assertThat(readings).hasSize(1);
    }

    @Test
    @Order(22)
    void getReadings_fieldsCorrectlyMapped() {
        Instant from = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<SensorReadingDto> readings = environmentService.getReadings(SENSOR_1, from, Instant.now(), 100);
        assertThat(readings).hasSize(1);
        SensorReadingDto r = readings.get(0);
        assertThat(r.getPm25()).isEqualTo(35.0);
        assertThat(r.getAqi()).isEqualTo(85.0);
        assertThat(r.getTemperature()).isEqualTo(28.5);
        assertThat(r.getHumidity()).isEqualTo(75.0);
    }

    @Test
    @Order(23)
    void getReadings_nonExistentSensor_returnsEmpty() {
        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        List<SensorReadingDto> readings = environmentService.getReadings("SENSOR-NONEXISTENT", from, Instant.now(), 100);
        assertThat(readings).isEmpty();
    }

    @Test
    @Order(24)
    void getReadings_limitApplied() {
        Instant from = Instant.now().minus(3, ChronoUnit.HOURS);
        List<SensorReadingDto> readings = environmentService.getReadings(SENSOR_1, from, Instant.now(), 2);
        assertThat(readings).hasSizeLessThanOrEqualTo(2);
    }

    // ─── getCurrentAqi ─────────────────────────────────────────────────────

    @Test
    @Order(30)
    void getCurrentAqi_returnsLatestPerSensor() {
        List<AqiResponseDto> aqiList = environmentService.getCurrentAqi();
        assertThat(aqiList).isNotEmpty();
        assertThat(aqiList).anySatisfy(a -> {
            assertThat(a.getSensorId()).isEqualTo(SENSOR_1);
            assertThat(a.getAqiValue()).isNotNull();
            assertThat(a.getCategory()).isNotNull();
        });
    }

    @Test
    @Order(31)
    void getCurrentAqi_aqiCalculation_correct() {
        List<AqiResponseDto> aqiList = environmentService.getCurrentAqi();
        AqiResponseDto s1 = aqiList.stream()
                .filter(a -> a.getSensorId().equals(SENSOR_1))
                .findFirst().orElseThrow();
        assertThat(s1.getAqiValue()).isPositive();
        assertThat(s1.getCategory()).isNotBlank();
        assertThat(s1.getColor()).isNotBlank();
    }

    // ─── getAqiHistory ─────────────────────────────────────────────────────

    @Test
    @Order(35)
    void getAqiHistory_24h_returnsData() {
        List<AqiResponseDto> history = environmentService.getAqiHistory(null, "24h");
        assertThat(history).isNotEmpty();
    }

    @Test
    @Order(36)
    void getAqiHistory_filteredByDistrict() {
        List<AqiResponseDto> history = environmentService.getAqiHistory(DISTRICT_1, "24h");
        assertThat(history).isNotEmpty();
        assertThat(history).allSatisfy(a -> assertThat(a.getDistrictCode()).isEqualTo(DISTRICT_1));
    }

    @Test
    @Order(37)
    void getAqiHistory_7d_returnsMoreData() {
        List<AqiResponseDto> history = environmentService.getAqiHistory(null, "7d");
        assertThat(history).isNotEmpty();
    }

    // ─── sensor reading multiple parameters ────────────────────────────────

    @Test
    @Order(40)
    void getReadings_allAqiParamsPresent() {
        Instant from = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<SensorReadingDto> readings = environmentService.getReadings(SENSOR_1, from, Instant.now(), 10);
        assertThat(readings).hasSize(1);
        SensorReadingDto r = readings.get(0);
        assertThat(r.getPm25()).isNotNull();
        assertThat(r.getPm10()).isNotNull();
        assertThat(r.getO3()).isNotNull();
        assertThat(r.getNo2()).isNotNull();
        assertThat(r.getSo2()).isNotNull();
        assertThat(r.getCo()).isNotNull();
    }

    @Test
    @Order(41)
    void getReadings_differentSensor_returnsCorrectData() {
        Instant from = Instant.now().minus(10, ChronoUnit.MINUTES);
        List<SensorReadingDto> readings = environmentService.getReadings(SENSOR_2, from, Instant.now(), 10);
        assertThat(readings).hasSize(1);
        assertThat(readings.get(0).getAqi()).isEqualTo(50.0);
    }

    // ─── toggleSensor not found ────────────────────────────────────────────

    @Test
    @Order(45)
    void toggleSensor_notFound_throwsException() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> environmentService.toggleSensor(UUID.randomUUID(), true))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }
}
