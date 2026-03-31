package com.uip.backend.environment.service;

import com.uip.backend.environment.api.dto.AqiResponseDto;
import com.uip.backend.environment.api.dto.SensorDto;
import com.uip.backend.environment.api.dto.SensorReadingDto;
import com.uip.backend.environment.domain.Sensor;
import com.uip.backend.environment.domain.SensorReading;
import com.uip.backend.environment.repository.SensorReadingRepository;
import com.uip.backend.environment.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnvironmentService {

    private final SensorRepository        sensorRepository;
    private final SensorReadingRepository readingRepository;
    private final AqiCalculator           aqiCalculator;

    /** Sensor is considered online if last_seen within last 5 minutes. */
    private static final long ONLINE_THRESHOLD_MINUTES = 5;

    // ─── Sensors ─────────────────────────────────────────────────────────────

    public List<SensorDto> listSensors() {
        return sensorRepository.findByActiveTrueOrderBySensorNameAsc()
                .stream()
                .map(this::toSensorDto)
                .toList();
    }

    // ─── Readings ─────────────────────────────────────────────────────────────

    public List<SensorReadingDto> getReadings(String sensorId, Instant from, Instant to, int limit) {
        var pageable = PageRequest.of(0, Math.min(limit, 1000));
        return readingRepository.findByRange(sensorId, from, to, pageable)
                .stream()
                .map(this::toReadingDto)
                .toList();
    }

    // ─── AQI Current ─────────────────────────────────────────────────────────

    public List<AqiResponseDto> getCurrentAqi() {
        return readingRepository.findLatestPerSensor()
                .stream()
                .map(r -> {
                    Optional<Sensor> sensor = sensorRepository.findBySensorId(r.getSensorId());
                    return toAqiDto(r, sensor.map(Sensor::getDistrictCode).orElse(null));
                })
                .toList();
    }

    public List<AqiResponseDto> getAqiHistory(String districtCode, String period) {
        int hours = switch (period) {
            case "7d"  -> 24 * 7;
            case "30d" -> 24 * 30;
            default    -> 24;   // "24h"
        };
        Instant from = Instant.now().minus(hours, ChronoUnit.HOURS);
        var pageable = PageRequest.of(0, 500);
        return readingRepository.findAllInRange(from, Instant.now(), pageable)
                .stream()
                .filter(r -> {
                    if (districtCode == null || districtCode.isBlank()) return true;
                    Optional<Sensor> s = sensorRepository.findBySensorId(r.getSensorId());
                    return s.map(sensor -> districtCode.equals(sensor.getDistrictCode())).orElse(false);
                })
                .map(r -> toAqiDto(r, districtCode))
                .toList();
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────

    private SensorDto toSensorDto(Sensor s) {
        Instant threshold = Instant.now().minus(ONLINE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
        String status = (s.getLastSeenAt() != null && s.getLastSeenAt().isAfter(threshold))
                ? "ONLINE" : "OFFLINE";
        return SensorDto.builder()
                .id(s.getId())
                .sensorId(s.getSensorId())
                .sensorName(s.getSensorName())
                .sensorType(s.getSensorType())
                .districtCode(s.getDistrictCode())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
                .status(status)
                .lastSeenAt(s.getLastSeenAt())
                .installedAt(s.getInstalledAt())
                .build();
    }

    private SensorReadingDto toReadingDto(SensorReading r) {
        return SensorReadingDto.builder()
                .sensorId(r.getSensorId())
                .timestamp(r.getId().getTimestamp())
                .aqi(r.getAqi())
                .pm25(r.getPm25())
                .pm10(r.getPm10())
                .o3(r.getO3())
                .no2(r.getNo2())
                .so2(r.getSo2())
                .co(r.getCo())
                .temperature(r.getTemperature())
                .humidity(r.getHumidity())
                .build();
    }

    private AqiResponseDto toAqiDto(SensorReading r, String districtCode) {
        Integer aqi = aqiCalculator.calculateAqi(r.getPm25(), r.getPm10(), r.getO3(),
                r.getNo2(), r.getSo2(), r.getCo());
        if (aqi == null && r.getAqi() != null) {
            aqi = r.getAqi().intValue();
        }
        return AqiResponseDto.builder()
                .sensorId(r.getSensorId())
                .timestamp(r.getId().getTimestamp())
                .aqiValue(aqi)
                .category(aqi != null ? aqiCalculator.categoryLabel(aqi) : null)
                .color(aqi != null ? aqiCalculator.categoryColor(aqi) : null)
                .pm25(r.getPm25())
                .pm10(r.getPm10())
                .o3(r.getO3())
                .no2(r.getNo2())
                .so2(r.getSo2())
                .co(r.getCo())
                .districtCode(districtCode)
                .build();
    }
}
