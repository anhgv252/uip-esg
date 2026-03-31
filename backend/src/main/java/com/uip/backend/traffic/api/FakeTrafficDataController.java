package com.uip.backend.traffic.api;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Internal endpoint consumed by Redpanda Connect traffic HTTP adapter.
 * Returns dummy traffic data for Sprint 1 pipeline testing.
 */
@RestController
@RequestMapping("/api/v1/internal")
@Hidden
public class FakeTrafficDataController {

    private static final String[] INTERSECTIONS = {
        "HCM-IT-001", "HCM-IT-002", "HCM-IT-003", "HCM-IT-004", "HCM-IT-005"
    };
    private static final String[] CONGESTION_LEVELS = {"LOW", "MEDIUM", "HIGH", "CRITICAL"};
    private static final Random RANDOM = new Random();

    @GetMapping("/fake-traffic")
    public List<Map<String, Object>> getFakeTraffic() {
        return IntStream.range(0, INTERSECTIONS.length)
                .mapToObj(i -> Map.<String, Object>of(
                        "intersection_id", INTERSECTIONS[i],
                        "vehicle_count",   50 + RANDOM.nextInt(200),
                        "avg_speed_kmh",   20.0 + RANDOM.nextDouble() * 60,
                        "congestion_level", CONGESTION_LEVELS[RANDOM.nextInt(CONGESTION_LEVELS.length)]
                ))
                .toList();
    }
}
