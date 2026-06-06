package com.uip.backend.testutil;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class dùng chung cho tất cả Integration Tests dùng PostgreSQL.
 *
 * <h3>Vấn đề đang giải quyết</h3>
 * Mỗi IT test class trước đây tự khởi động một {@code postgres:15-alpine} container riêng.
 * Với 27 IT tests, khi chạy full suite → 27 containers song song, tiêu tốn RAM/CPU.
 * Class này chia sẻ MỘT container duy nhất cho toàn bộ JVM test run → giảm từ N xuống 1.
 *
 * <h3>Cleanup cơ chế</h3>
 * <ol>
 *   <li><b>Ryuk (primary)</b> — Testcontainers Ryuk reaper container tự động xóa tất cả
 *       test containers khi JVM thoát. Ryuk đã được bật lại trong
 *       {@code testcontainers.properties} ({@code ryuk.disabled=false}).</li>
 *   <li><b>Safety-net</b> — Gradle task {@code integrationTest} chạy
 *       {@code docker container prune --filter label=org.testcontainers=true} sau khi test xong.</li>
 *   <li><b>Manual</b> — Nếu test bị kill đột ngột (Ctrl+C, OOM):
 *       {@code ./gradlew cleanTestContainers}</li>
 * </ol>
 *
 * <h3>Cách migrate IT test hiện có</h3>
 * <pre>{@code
 * // Trước:
 * class MyServiceIT {
 *     @Container
 *     static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
 *             .withDatabaseName("uip_test").withUsername("test").withPassword("test");
 *
 *     @DynamicPropertySource
 *     static void overrideDataSource(DynamicPropertyRegistry registry) {
 *         postgres.start();
 *         registry.add("spring.datasource.url", postgres::getJdbcUrl);
 *         // ... các dòng lặp lại
 *     }
 * }
 *
 * // Sau (extends AbstractIT):
 * class MyServiceIT extends AbstractIT {
 *     // Bỏ @Container + @DynamicPropertySource cho postgres/flyway/kafka/redis
 *     // Giữ lại @MockBean và các properties test-specific như spring.cache.type
 *
 *     // Nếu cần override thêm property:
 *     @DynamicPropertySource
 *     static void extraProps(DynamicPropertyRegistry registry) {
 *         registry.add("spring.cache.type", () -> "none");
 *     }
 * }
 * }</pre>
 *
 * <h3>Lưu ý với TenantIsolationIT</h3>
 * {@code TenantIsolationIT} cần container riêng (tạo user non-owner để test RLS policies).
 * Class đó KHÔNG nên extend AbstractIT — giữ nguyên container riêng + thêm {@code @AfterAll}
 * để stop container explicit.
 */
public abstract class AbstractIT {

    /**
     * Một Postgres container dùng chung cho toàn bộ test JVM run.
     * Static final → khởi động một lần, tất cả subclass chia sẻ.
     * Ryuk sẽ xóa container này khi JVM thoát.
     */
    @SuppressWarnings("resource") // Ryuk reaper container dọn sạch khi JVM thoát
    protected static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("uip_test")
                .withUsername("uip_test_user")
                .withPassword("uip_test_pass");
        POSTGRES.start();
    }

    /**
     * Wire DataSource, Flyway, Kafka stub, Redis stub cho tất cả IT tests.
     * Subclass có thể khai báo thêm {@code @DynamicPropertySource} để override
     * các properties test-specific (e.g. {@code spring.cache.type}).
     */
    @DynamicPropertySource
    static void configureSharedContainer(DynamicPropertyRegistry registry) {
        // DataSource
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Flyway — cùng DB với DataSource
        registry.add("spring.flyway.url",          POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user",         POSTGRES::getUsername);
        registry.add("spring.flyway.password",     POSTGRES::getPassword);

        // Kafka stub — tests dùng @MockBean KafkaTemplate nên port này không thực sự connect
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:29092");

        // Redis stub — tests dùng @MockBean Redis beans
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "16399");

        // Cho phép @MockBean override Spring beans
        registry.add("spring.main.allow-bean-definition-overriding", () -> "true");

        // Multi-tenancy mặc định bật trong IT tests
        registry.add("uip.capabilities.multi-tenancy", () -> "true");
    }
}
