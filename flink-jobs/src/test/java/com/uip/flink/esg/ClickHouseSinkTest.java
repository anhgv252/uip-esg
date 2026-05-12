package com.uip.flink.esg;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClickHouseSinkTest {

    @Test
    void insertSql_targetsCorrectTable() {
        assertThat(ClickHouseSink.INSERT_SQL)
                .contains("analytics.esg_readings")
                .contains("tenant_id")
                .contains("building_id")
                .contains("recorded_at");
    }

    @Test
    void batchSize_is5000() {
        assertThat(ClickHouseSink.BATCH_SIZE).isEqualTo(5_000);
    }

    @Test
    void batchInterval_is2000ms() {
        assertThat(ClickHouseSink.BATCH_INTERVAL).isEqualTo(2_000L);
    }

    @Test
    void driver_isClickHouseJdbc() {
        assertThat(ClickHouseSink.DRIVER).isEqualTo("com.clickhouse.jdbc.ClickHouseDriver");
    }

    @Test
    void create_returnsNonNullSink() {
        // Verify factory doesn't throw with dummy credentials (no actual connection)
        var sink = ClickHouseSink.create(
                "jdbc:clickhouse://localhost:8123/analytics",
                "test_user",
                "test_pass"
        );
        assertThat(sink).isNotNull();
    }
}
