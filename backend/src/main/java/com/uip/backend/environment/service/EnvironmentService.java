package com.uip.backend.environment.service;

import com.uip.backend.environment.api.dto.AqiResponseDto;
import com.uip.backend.environment.api.dto.SensorDto;
import com.uip.backend.environment.api.dto.SensorReadingDto;
import com.uip.backend.environment.domain.Sensor;
import com.uip.backend.environment.domain.SensorReading;
import com.uip.backend.environment.domain.SensorReadingId;
import com.uip.backend.environment.repository.SensorReadingRepository;
import com.uip.backend.environment.repository.SensorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
        List<Sensor> sensors = sensorRepository.findByActiveTrueOrderBySensorNameAsc();
        // Fallback: use latest reading timestamp if sensors.last_seen_at is stale
        // (covers dev environments where the DB trigger fires via Flink, not directly)
        Map<String, Instant> latestReadingTs = readingRepository.findLatestPerSensor()
                .stream()
                .collect(Collectors.toMap(
                        SensorReading::getSensorId,
                        r -> r.getId().getTimestamp(),
                        (a, b) -> a.isAfter(b) ? a : b
                ));
        return sensors.stream()
                .map(s -> toSensorDto(s, latestReadingTs.get(s.getSensorId())))
                .toList();
    }

    /** Admin: list ALL sensors (active + inactive), sorted by name. */
    public List<SensorDto> listAllSensors() {
        return sensorRepository.findAll(Sort.by("sensorName"))
                .stream()
                .map(this::toSensorDto)
                .toList();
    }

    /** Admin: toggle active status of a sensor. Returns the updated SensorDto. */
    @Transactional
    public SensorDto toggleSensor(UUID id, boolean active) {
        Sensor sensor = sensorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Sensor not found: " + id));
        sensor.setActive(active);
        return toSensorDto(sensorRepository.save(sensor));
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
        return readingRepository.findLatestPerSensorWithDistrict()
                .stream()
                .map(row -> {
                    SensorReading r = mapRowToSensorReading(row);
                    String districtCode = (String) row[row.length - 1];
                    return toAqiDto(r, districtCode);
                })
                .toList();
    }

    public List<AqiResponseDto> getAqiHistory(String districtCode, String period) {
        int hours = switch (period) {
            case "7d"  -> 24 * 7;
            case "30d" -> 24 * 30;
            default    -> 24;
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

    private SensorReading mapRowToSensorReading(Object[] row) {
        SensorReading r = new SensorReading();
        SensorReadingId id = new SensorReadingId();
        id.setId(((Number) row[0]).longValue());
        id.setTimestamp((Instant) row[2]);
        r.setId(id);
        r.setSensorId((String) row[1]);
        r.setAqi(toDouble(row[3]));
        r.setPm25(toDouble(row[4]));
        r.setPm10(toDouble(row[5]));
        r.setO3(toDouble(row[6]));
        r.setNo2(toDouble(row[7]));
        r.setSo2(toDouble(row[8]));
        r.setCo(toDouble(row[9]));
        r.setTemperature(toDouble(row[10]));
        r.setHumidity(toDouble(row[11]));
        return r;
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        return null;
    }

    private SensorDto toSensorDto(Sensor s) {
        return toSensorDto(s, null);
    }

    private SensorDto toSensorDto(Sensor s, Instant latestReadingTs) {
        // Use the most recent of sensor.last_seen_at and latest reading timestamp
        Instant effectiveLastSeen = s.getLastSeenAt();
        if (latestReadingTs != null &&
                (effectiveLastSeen == null || latestReadingTs.isAfter(effectiveLastSeen))) {
            effectiveLastSeen = latestReadingTs;
        }
        Instant threshold = Instant.now().minus(ONLINE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
        String status = (effectiveLastSeen != null && effectiveLastSeen.isAfter(threshold))
                ? "ONLINE" : "OFFLINE";
        return SensorDto.builder()
                .id(s.getId())
                .sensorId(s.getSensorId())
                .sensorName(s.getSensorName())
                .sensorType(s.getSensorType())
                .districtCode(s.getDistrictCode())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
                .active(s.isActive())
                .status(status)
                .lastSeenAt(effectiveLastSeen)
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
