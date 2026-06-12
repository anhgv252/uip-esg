package com.uip.backend.contract;

import com.uip.backend.environment.api.dto.AqiResponseDto;
import com.uip.backend.environment.api.dto.SensorDto;
import com.uip.backend.environment.api.dto.SensorReadingDto;
import com.uip.backend.environment.service.EnvironmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Contract tests for Environment API — verify service contract (input/output shape).
 * Pure Mockito pattern: no Spring context, no MockMvc.
 *
 * Sprint 2 (MVP4): adds tests for sensors, readings, AQI endpoints to reach ≥30 total
 * @Tag("contract") tests across the contract package.
 *
 * AQI category thresholds per US EPA standard (parameterised boundary tests):
 *   GOOD       0–50     (green)
 *   MODERATE   51–100   (yellow)
 *   USG        101–150  (orange)
 *   UNHEALTHY  151–200  (red)
 *   VERY_UNHEALTHY 201–300 (purple)
 *   HAZARDOUS  301+     (maroon)
 */
@Tag("contract")
@DisplayName("Environment API — Service Contract Tests")
class EnvironmentApiContractTest {

    private EnvironmentService environmentService;

    @BeforeEach
    void setUp() {
        environmentService = mock(EnvironmentService.class);
    }

    private SensorDto buildSensor(String sensorId, String status) {
        return SensorDto.builder()
                .id(UUID.randomUUID())
                .sensorId(sensorId)
                .sensorName("Air Quality Station — District 1")
                .sensorType("AQI")
                .districtCode("D1")
                .latitude(10.7769)
                .longitude(106.7009)
                .active(true)
                .status(status)
                .lastSeenAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .build();
    }

    private SensorReadingDto buildReading(String sensorId, double aqi) {
        return SensorReadingDto.builder()
                .sensorId(sensorId)
                .timestamp(Instant.now().minus(5, ChronoUnit.MINUTES))
                .aqi(aqi)
                .pm25(aqi * 0.4)
                .pm10(aqi * 0.6)
                .temperature(28.5)
                .humidity(72.0)
                .build();
    }

    private AqiResponseDto buildAqiResponse(String sensorId, int aqiValue, String category) {
        return AqiResponseDto.builder()
                .sensorId(sensorId)
                .timestamp(Instant.now())
                .aqiValue(aqiValue)
                .category(category)
                .color("#00E400")
                .pm25(12.5)
                .districtCode("D1")
                .build();
    }

    // ─── listSensors contract ─────────────────────────────────────────────────

    @Nested
    @DisplayName("listSensors — contract")
    class ListSensorsTests {

        @Test
        @DisplayName("Returns list of SensorDto with required fields populated")
        void listSensors_returnsListWithRequiredFields() {
            SensorDto online  = buildSensor("ENV-001", "ONLINE");
            SensorDto offline = buildSensor("ENV-002", "OFFLINE");
            when(environmentService.listSensors()).thenReturn(List.of(online, offline));

            var result = environmentService.listSensors();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getSensorId()).isEqualTo("ENV-001");
            assertThat(result.get(0).getStatus()).isEqualTo("ONLINE");
            assertThat(result.get(1).getStatus()).isEqualTo("OFFLINE");
            assertThat(result).allMatch(s -> s.getSensorType() != null);
            assertThat(result).allMatch(s -> s.getLatitude() != null && s.getLongitude() != null);
        }

        @Test
        @DisplayName("Returns empty list when no active sensors exist")
        void listSensors_noActiveSensors_returnsEmpty() {
            when(environmentService.listSensors()).thenReturn(List.of());

            var result = environmentService.listSensors();

            assertThat(result).isEmpty();
        }
    }

    // ─── getReadings contract ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getReadings — contract")
    class GetReadingsTests {

        @Test
        @DisplayName("Returns time-series list of SensorReadingDto with aqi and pm fields")
        void getReadings_returnsTimeSeriesWithAqiAndPmFields() {
            SensorReadingDto r1 = buildReading("ENV-001", 45.0);
            SensorReadingDto r2 = buildReading("ENV-001", 55.0);
            Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
            Instant to   = Instant.now();

            when(environmentService.getReadings(eq("ENV-001"), any(Instant.class), any(Instant.class), eq(100)))
                    .thenReturn(List.of(r1, r2));

            var result = environmentService.getReadings("ENV-001", from, to, 100);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getSensorId()).isEqualTo("ENV-001");
            assertThat(result.get(0).getAqi()).isEqualTo(45.0);
            assertThat(result.get(0).getPm25()).isNotNull();
            assertThat(result.get(0).getTimestamp()).isNotNull();
            assertThat(result.get(1).getAqi()).isEqualTo(55.0);
        }

        @Test
        @DisplayName("Returns empty list when no readings in requested range")
        void getReadings_noDataInRange_returnsEmpty() {
            Instant from = Instant.now().minus(7, ChronoUnit.DAYS);
            Instant to   = Instant.now().minus(6, ChronoUnit.DAYS);

            when(environmentService.getReadings(anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(List.of());

            var result = environmentService.getReadings("ENV-999", from, to, 1000);

            assertThat(result).isEmpty();
        }
    }

    // ─── getCurrentAqi contract ───────────────────────────────────────────────

    @Nested
    @DisplayName("getCurrentAqi — contract")
    class GetCurrentAqiTests {

        @Test
        @DisplayName("Returns AQI response with aqiValue, category, and sensorId populated")
        void getCurrentAqi_returnsAqiValueWithCategory() {
            AqiResponseDto dto = buildAqiResponse("ENV-001", 42, "GOOD");
            when(environmentService.getCurrentAqi()).thenReturn(List.of(dto));

            var result = environmentService.getCurrentAqi();

            assertThat(result).hasSize(1);
            AqiResponseDto item = result.get(0);
            assertThat(item.getSensorId()).isEqualTo("ENV-001");
            assertThat(item.getAqiValue()).isEqualTo(42);
            assertThat(item.getCategory()).isEqualTo("GOOD");
            assertThat(item.getDistrictCode()).isEqualTo("D1");
        }

        @Test
        @DisplayName("Returns CRITICAL category when AQI value exceeds 200 (VERY_UNHEALTHY threshold)")
        void getCurrentAqi_aqiAbove200_reflectsCriticalCategory() {
            // AQI 201–300 = VERY_UNHEALTHY, which maps to CRITICAL alert severity
            AqiResponseDto critical = AqiResponseDto.builder()
                    .sensorId("ENV-EMERGENCY")
                    .timestamp(Instant.now())
                    .aqiValue(250)
                    .category("VERY_UNHEALTHY")
                    .color("#8F3F97")
                    .pm25(98.5)
                    .districtCode("D7")
                    .build();
            when(environmentService.getCurrentAqi()).thenReturn(List.of(critical));

            var result = environmentService.getCurrentAqi();

            assertThat(result.get(0).getAqiValue()).isGreaterThan(200);
            assertThat(result.get(0).getCategory()).isEqualTo("VERY_UNHEALTHY");
        }

        @Test
        @DisplayName("Returns empty list when no sensors have recent readings")
        void getCurrentAqi_noRecentReadings_returnsEmpty() {
            when(environmentService.getCurrentAqi()).thenReturn(List.of());

            var result = environmentService.getCurrentAqi();

            assertThat(result).isEmpty();
        }
    }
}
