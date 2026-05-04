package com.uip.backend.esg.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CacheKeyBuilder")
class CacheKeyBuilderTest {

    private final CacheKeyBuilder builder = new CacheKeyBuilder();

    @Nested
    @DisplayName("dashboardKey")
    class DashboardKey {

        @Test
        @DisplayName("includes tenantId and period info")
        void includesTenantIdAndPeriod() {
            String key = builder.dashboardKey("hcm", "QUARTERLY", 2026, 1);
            assertThat(key).isEqualTo("esg-dashboard:hcm:QUARTERLY:2026:Q1");
        }

        @Test
        @DisplayName("rejects null tenantId")
        void rejectsNullTenantId() {
            assertThatThrownBy(() -> builder.dashboardKey(null, "QUARTERLY", 2026, 1))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("tenantId");
        }
    }

    @Nested
    @DisplayName("energyKey")
    class EnergyKey {

        @Test
        @DisplayName("includes buildingId when present")
        void includesBuildingId() {
            Instant from = Instant.parse("2026-01-01T00:00:00Z");
            Instant to = Instant.parse("2026-02-01T00:00:00Z");

            String key = builder.energyKey("hcm", from, to, "BLDG-01");
            assertThat(key).startsWith("esg-dashboard:hcm:ENERGY:BLDG-01:");
        }

        @Test
        @DisplayName("uses empty string when buildingId is null")
        void emptyBuildingWhenNull() {
            Instant from = Instant.parse("2026-01-01T00:00:00Z");
            Instant to = Instant.parse("2026-02-01T00:00:00Z");

            String key = builder.energyKey("hcm", from, to, null);
            assertThat(key).contains("esg-dashboard:hcm:ENERGY::");
        }
    }

    @Nested
    @DisplayName("carbonKey")
    class CarbonKey {

        @Test
        @DisplayName("formats correctly")
        void formatsCorrectly() {
            Instant from = Instant.parse("2026-01-01T00:00:00Z");
            Instant to = Instant.parse("2026-04-01T00:00:00Z");

            String key = builder.carbonKey("hcm", from, to);
            assertThat(key).startsWith("esg-dashboard:hcm:CARBON:");
        }
    }

    @Nested
    @DisplayName("reportKey")
    class ReportKey {

        @Test
        @DisplayName("includes tenantId period year quarter")
        void includesAllFields() {
            String key = builder.reportKey("hcm", "QUARTERLY", 2026, 2);
            assertThat(key).isEqualTo("esg-report:hcm:QUARTERLY:2026:Q2");
        }
    }

    @Nested
    @DisplayName("reportStatusKey")
    class ReportStatusKey {

        @Test
        @DisplayName("includes tenantId and reportId")
        void includesTenantAndReportId() {
            UUID reportId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
            String key = builder.reportStatusKey("hcm", reportId);
            assertThat(key).isEqualTo("esg-report:hcm:status:aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        }
    }

    @Nested
    @DisplayName("trendKey")
    class TrendKey {

        @Test
        @DisplayName("includes tenantId and time range")
        void includesTenantAndTimeRange() {
            Instant from = Instant.parse("2026-01-01T00:00:00Z");
            Instant to = Instant.parse("2026-02-01T00:00:00Z");

            String key = builder.trendKey("default", from, to);
            assertThat(key).startsWith("esg-trend:default:");
        }
    }

    @Nested
    @DisplayName("tenant isolation")
    class TenantIsolation {

        @Test
        @DisplayName("different tenants produce different keys for same query params")
        void differentTenantsDifferentKeys() {
            String keyA = builder.dashboardKey("tenant-a", "QUARTERLY", 2026, 1);
            String keyB = builder.dashboardKey("tenant-b", "QUARTERLY", 2026, 1);
            assertThat(keyA).isNotEqualTo(keyB);
        }
    }
}
