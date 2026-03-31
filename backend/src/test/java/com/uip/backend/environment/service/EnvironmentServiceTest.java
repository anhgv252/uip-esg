package com.uip.backend.environment.service;

import com.uip.backend.environment.domain.Sensor;
import com.uip.backend.environment.domain.SensorReading;
import com.uip.backend.environment.domain.SensorReadingId;
import com.uip.backend.environment.repository.SensorReadingRepository;
import com.uip.backend.environment.repository.SensorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnvironmentService")
class EnvironmentServiceTest {

    @Mock private SensorRepository        sensorRepository;
    @Mock private SensorReadingRepository readingRepository;
    @Mock private AqiCalculator           aqiCalculator;

    @InjectMocks private EnvironmentService environmentService;

    private Sensor activeSensor;
    private SensorReading reading;

    @BeforeEach
    void setUp() {
        activeSensor = new Sensor();
        activeSensor.setId(UUID.randomUUID());
        activeSensor.setSensorId("ENV-001");
        activeSensor.setSensorName("Sensor Quận 1");
        activeSensor.setSensorType("AQI");
        activeSensor.setDistrictCode("D01");
        activeSensor.setLatitude(10.775);
        activeSensor.setLongitude(106.700);
        activeSensor.setActive(true);
        activeSensor.setLastSeenAt(Instant.now().minusSeconds(60)); // 1 min ago → ONLINE

        SensorReadingId rid = new SensorReadingId();
        rid.setTimestamp(Instant.now().minusSeconds(30));
        reading = new SensorReading();
        reading.setId(rid);
        reading.setSensorId("ENV-001");
        reading.setPm25(15.0);
        reading.setPm10(35.0);
        reading.setO3(60.0);
        reading.setNo2(30.0);
        reading.setSo2(5.0);
        reading.setCo(0.5);
        reading.setTemperature(28.5);
        reading.setHumidity(75.0);
        reading.setAqi(65.0);
    }

    // ─── listSensors ────────────────────────────────────────────────────────

    @Test
    @DisplayName("listSensors: returns ONLINE sensor when lastSeenAt is recent")
    void listSensors_recentLastSeen_returnsOnlineStatus() {
        when(sensorRepository.findByActiveTrueOrderBySensorNameAsc())
                .thenReturn(List.of(activeSensor));

        var result = environmentService.listSensors();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSensorId()).isEqualTo("ENV-001");
        assertThat(result.get(0).getStatus()).isEqualTo("ONLINE");
        assertThat(result.get(0).getDistrictCode()).isEqualTo("D01");
    }

    @Test
    @DisplayName("listSensors: returns OFFLINE when lastSeenAt is old")
    void listSensors_oldLastSeen_returnsOfflineStatus() {
        activeSensor.setLastSeenAt(Instant.now().minusSeconds(600)); // 10 min ago → OFFLINE
        when(sensorRepository.findByActiveTrueOrderBySensorNameAsc())
                .thenReturn(List.of(activeSensor));

        var result = environmentService.listSensors();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("OFFLINE");
    }

    @Test
    @DisplayName("listSensors: returns OFFLINE when lastSeenAt is null")
    void listSensors_nullLastSeen_returnsOfflineStatus() {
        activeSensor.setLastSeenAt(null);
        when(sensorRepository.findByActiveTrueOrderBySensorNameAsc())
                .thenReturn(List.of(activeSensor));

        var result = environmentService.listSensors();

        assertThat(result.get(0).getStatus()).isEqualTo("OFFLINE");
    }

    @Test
    @DisplayName("listSensors: empty repository returns empty list")
    void listSensors_noSensors_returnsEmpty() {
        when(sensorRepository.findByActiveTrueOrderBySensorNameAsc()).thenReturn(List.of());

        assertThat(environmentService.listSensors()).isEmpty();
    }

    // ─── getReadings ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getReadings: returns mapped DTOs for valid range")
    void getReadings_validRange_returnsMappedDtos() {
        when(readingRepository.findByRange(eq("ENV-001"), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(reading));

        Instant from = Instant.now().minusSeconds(3600);
        var result = environmentService.getReadings("ENV-001", from, Instant.now(), 100);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSensorId()).isEqualTo("ENV-001");
        assertThat(result.get(0).getPm25()).isEqualTo(15.0);
    }

    @Test
    @DisplayName("getReadings: limit is capped at 1000")
    void getReadings_limitOverMaxCapped() {
        when(readingRepository.findByRange(any(), any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        environmentService.getReadings("ENV-001", Instant.now().minusSeconds(3600), Instant.now(), 9999);

        // Verify that PageRequest was created with max 1000
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(readingRepository).findByRange(any(), any(), any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(1000);
    }

    // ─── getCurrentAqi ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getCurrentAqi: maps reading to AqiResponseDto with district info")
    void getCurrentAqi_withSensorFound_includesDistrictCode() {
        when(readingRepository.findLatestPerSensor()).thenReturn(List.of(reading));
        when(sensorRepository.findBySensorId("ENV-001")).thenReturn(Optional.of(activeSensor));

        var result = environmentService.getCurrentAqi();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSensorId()).isEqualTo("ENV-001");
        assertThat(result.get(0).getDistrictCode()).isEqualTo("D01");
    }

    @Test
    @DisplayName("getCurrentAqi: null district when sensor not found in registry")
    void getCurrentAqi_sensorNotFound_districtCodeIsNull() {
        when(readingRepository.findLatestPerSensor()).thenReturn(List.of(reading));
        when(sensorRepository.findBySensorId("ENV-001")).thenReturn(Optional.empty());

        var result = environmentService.getCurrentAqi();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDistrictCode()).isNull();
    }

    @Test
    @DisplayName("getCurrentAqi: empty readings returns empty list")
    void getCurrentAqi_noReadings_returnsEmpty() {
        when(readingRepository.findLatestPerSensor()).thenReturn(List.of());

        assertThat(environmentService.getCurrentAqi()).isEmpty();
    }

    // ─── getAqiHistory ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getAqiHistory: 24h period fetches 24h range")
    void getAqiHistory_defaultPeriod_24h() {
        when(readingRepository.findAllInRange(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(reading));

        var result = environmentService.getAqiHistory(null, "24h");

        verify(readingRepository).findAllInRange(any(), any(), any(Pageable.class));
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getAqiHistory: 7d period accepted")
    void getAqiHistory_7dPeriod() {
        when(readingRepository.findAllInRange(any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(environmentService.getAqiHistory(null, "7d")).isEmpty();
    }

    @Test
    @DisplayName("getAqiHistory: 30d period accepted")
    void getAqiHistory_30dPeriod() {
        when(readingRepository.findAllInRange(any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(environmentService.getAqiHistory(null, "30d")).isEmpty();
    }

    @Test
    @DisplayName("getAqiHistory: districtCode filter excludes non-matching sensors")
    void getAqiHistory_withDistrictFilter_excludesOtherDistricts() {
        // reading belongs to ENV-001 → D01; filter for D02 → should be excluded
        when(readingRepository.findAllInRange(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(reading));
        Sensor otherDistrict = new Sensor();
        otherDistrict.setSensorId("ENV-001");
        otherDistrict.setDistrictCode("D02"); // different district
        when(sensorRepository.findBySensorId("ENV-001")).thenReturn(Optional.of(otherDistrict));

        var result = environmentService.getAqiHistory("D01", "24h");

        assertThat(result).isEmpty(); // D02 sensor filtered out when searching for D01
    }

    @Test
    @DisplayName("getAqiHistory: blank districtCode returns all readings")
    void getAqiHistory_blankDistrict_returnsAll() {
        when(readingRepository.findAllInRange(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(reading));

        var result = environmentService.getAqiHistory("", "24h");

        assertThat(result).hasSize(1);
    }
}
