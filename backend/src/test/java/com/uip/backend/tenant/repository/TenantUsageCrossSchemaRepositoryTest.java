package com.uip.backend.tenant.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantUsageCrossSchemaRepositoryTest {

    @Mock JdbcTemplate jdbcTemplate;
    @InjectMocks TenantUsageCrossSchemaRepository repository;

    @Test
    void countSensorReadings_returnsValue() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-02T00:00:00Z");
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any()))
                .thenReturn(42L);

        long result = repository.countSensorReadings("tenant-a", start, end);

        assertThat(result).isEqualTo(42L);
    }

    @Test
    void countSensorReadings_nullResult_returnsZero() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(), any(), any()))
                .thenReturn(null);

        long result = repository.countSensorReadings("tenant-b", Instant.now(), Instant.now());

        assertThat(result).isEqualTo(0L);
    }
}
