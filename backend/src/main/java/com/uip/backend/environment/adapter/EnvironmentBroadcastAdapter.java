package com.uip.backend.environment.adapter;

import com.uip.backend.common.spi.EnvironmentBroadcastPort;
import com.uip.backend.environment.api.dto.AqiResponseDto;
import com.uip.backend.environment.service.EnvironmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code environment}-side implementation of {@link EnvironmentBroadcastPort} (ADR-052, migration D3).
 *
 * <p>Projects {@link AqiResponseDto} into the neutral {@link AqiSnapshot} record so the
 * {@code scheduler} module never imports {@code environment} DTOs.</p>
 */
@Component
@RequiredArgsConstructor
public class EnvironmentBroadcastAdapter implements EnvironmentBroadcastPort {

    private final EnvironmentService environmentService;

    @Override
    public List<AqiSnapshot> getCurrentAqiSnapshots() {
        return environmentService.getCurrentAqi().stream()
                .map(dto -> new AqiSnapshot(
                        dto.getSensorId(),
                        dto.getAqiValue(),
                        dto.getCategory(),
                        dto.getDistrictCode(),
                        dto.getTimestamp()))
                .toList();
    }
}
