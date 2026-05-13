# Integration Test & Testcontainers Guide — UIP ESG POC

## 1. Tổng quan

Dự án dùng **Testcontainers** để chạy integration test với các container thực (PostgreSQL/TimescaleDB, ClickHouse, Redis). Không dùng embedded database hay mock cho DB layer.

### Phân loại test

| Loại | Suffix | Ví dụ | Chạy khi nào |
|------|--------|-------|-------------|
| Unit test | `*Test.java` | `AlertServiceTest.java` | Mỗi PR |
| Integration test | `*IT.java` | `TenantIsolationIT.java` | Mỗi PR (cần Docker) |
| Integration test | `*IntegrationTest.java` | `AuthControllerIntegrationTest.java` | Mỗi PR (cần Docker) |
| E2E test | `*E2EIT.java` | `EsgDualSinkFlinkE2EIT.java` | Mỗi PR (cần Docker) |

### Dependencies

**Backend (Gradle)** — `backend/build.gradle`:
```groovy
testImplementation 'org.testcontainers:junit-jupiter'
testImplementation 'org.testcontainers:postgresql'
testImplementation 'org.testcontainers:kafka'
testImplementation 'com.redis:testcontainers-redis:2.2.0'

dependencyManagement {
    imports {
        mavenBom "org.testcontainers:testcontainers-bom:1.21.4"
    }
}
```

**Flink (Maven)** — `flink-jobs/pom.xml`:
```xml
<testcontainers.version>1.19.6</testcontainers.version>

<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
```

---

## 2. 3 Pattern chính

Dự án hiện dùng 3 pattern khởi tạo container:

### Pattern A — Testcontainers Library (khuyên dùng)

Dùng `@Testcontainers` + `@Container` + `PostgreSQLContainer`. Testcontainers quản lý vòng đời container tự động.

```java
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MyFeatureIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("uip_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:29092");
        registry.add("spring.data.redis.host", () -> "localhost");
    }

    // ... tests
}
```

**Dùng cho**: `TenantIsolationIT`, `CrossBuildingAggregationServiceIT`, `CrossBuildingConcurrentRLSIT`, `NotificationFlowIT`, `PushSubscriptionIT`, `EsgDualSinkFlinkE2EIT`

### Pattern B — Docker CLI trực tiếp

Dùng `ProcessBuilder` gọi `docker run`/`docker stop`. Bypass docker-java library (bị lỗi với Docker Engine 29.x).

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    private static String postgresId;
    private static String redisId;
    static int postgresPort;
    static int redisPort;

    @BeforeAll
    static void startContainers() throws Exception {
        assumeTrue(new File("/var/run/docker.sock").exists(),
            "Docker socket not available — skipping integration tests");
        assumeTrue(runAndWait(5, "/usr/local/bin/docker", "info") == 0,
            "Docker daemon not responding — skipping integration tests");

        postgresId = startContainer(
            "-e", "POSTGRES_DB=uip_test",
            "-e", "POSTGRES_USER=uip",
            "-e", "POSTGRES_PASSWORD=test_password",
            "-p", "0:5432",
            "timescale/timescaledb:2.13.1-pg15"
        );
        postgresPort = getMappedPort(postgresId, 5432);

        redisId = startContainer(
            "-p", "0:6379",
            "redis:7.2-alpine",
            "redis-server", "--requirepass", "testredis"
        );
        redisPort = getMappedPort(redisId, 6379);

        waitForPostgres(postgresPort, 60);
    }

    @AfterAll
    static void stopContainers() {
        stopContainer(postgresId);
        stopContainer(redisId);
    }

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
            () -> "jdbc:postgresql://localhost:" + postgresPort + "/uip_test");
        registry.add("spring.datasource.username", () -> "uip");
        // ...
    }
}
```

**Dùng cho**: `AuthControllerIntegrationTest`, `TriggerConfigCacheServiceIT` (cần cả PostgreSQL + Redis thật)

### Pattern C — ApplicationContextRunner (không cần container)

Test `@ConditionalOnProperty` / capability flags. Không khởi động full Spring context, chạy <1s.

```java
class CapabilityFlagIT {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(IotIngestionAutoConfiguration.class);

    @Test
    void tier1_noFlag_orchestratorPresent() {
        contextRunner
            .withBean("sensorRepository", SensorRepository.class,
                () -> Mockito.mock(SensorRepository.class))
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(SensorIngestionOrchestrator.class);
            });
    }

    @Test
    void tier2_flagTrue_orchestratorAbsent() {
        contextRunner
            .withPropertyValues("uip.capabilities.iot-ingestion-external=true")
            .run(ctx -> {
                assertThat(ctx).doesNotHaveBean(SensorIngestionOrchestrator.class);
            });
    }
}
```

**Dùng cho**: `CapabilityFlagIT`

---

## 3. MockBean checklist — Spring Boot context

Khi dùng `@SpringBootTest`, phải mock các bean hạ tầng không có trong test environment. Dưới đây là danh sách **5 bean bắt buộc** phải `@MockBean` nếu không chạy Kafka/Redis container:

```java
// 1. Redis — mock khi chỉ test PostgreSQL logic
@MockBean @SuppressWarnings("unused") RedisConnectionFactory redisConnectionFactory;
@MockBean @SuppressWarnings("unused") ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
@MockBean @SuppressWarnings("unused") StringRedisTemplate redisTemplate;
@MockBean @SuppressWarnings("unused") RedisMessageListenerContainer redisMessageListenerContainer;

// 2. Kafka — mock khi không test Kafka consumer/producer
@MockBean @SuppressWarnings("unused") KafkaTemplate<String, Object> kafkaTemplate;
```

> **Lưu ý**: `@SuppressWarnings("unused")` là bắt buộc vì các mock field không bao giờ được reference trực tiếp — chúng chỉ cần tồn tại trong Spring context.

### Khi nào KHÔNG mock?

| Bean | Mock | Lý do |
|------|------|-------|
| `RedisConnectionFactory` | Mock | Redis không cần cho hầu hết logic test |
| `KafkaTemplate` | Mock | Kafka broker không chạy trong hầu hết IT |
| `JdbcTemplate` | **Autowired** | Cần query thật vào DB |
| `WebMvc` / `MockMvc` | **Autowired** | Cần test HTTP request thật |
| `Camunda HistoryService` | **Autowired** | Cần query process history thật |
| `ClaudeApiService` | **Mock** | Gọi external API, không gọi thật trong test |
| `EsgService` | **Tùy context** | Mock trong workflow test, autowired trong ESG test |

---

## 4. Flyway migration tự chạy

Khi `@SpringBootTest` khởi động, Flyway chạy **tất cả migration** trong `src/main/resources/db/migration/` tự động. Đảm bảo:

1. `spring.flyway.url/user/password` override đúng trong `@DynamicPropertySource`
2. Không cần tạo bảng thủ công — Flyway lo hết
3. Test data chèn qua `JdbcTemplate` trong `@BeforeAll`

### Pattern seed data

```java
@BeforeAll
void seedTestData() {
    // Dùng ON CONFLICT DO NOTHING để test chạy lại được
    jdbc.execute("INSERT INTO public.tenants (tenant_id, tenant_name, is_aggregator) " +
            "VALUES ('test-tenant', 'Test', false) ON CONFLICT (tenant_id) DO NOTHING");

    // Hoặc xóa hết rồi insert lại
    // jdbc.execute("DELETE FROM push_subscriptions");  // trong @BeforeEach
}
```

---

## 5. Container images

| Container | Image | Dùng trong |
|-----------|-------|-----------|
| PostgreSQL (plain) | `postgres:15-alpine` | Backend IT tests, Flink E2E |
| TimescaleDB | `timescale/timescaledb:2.13.1-pg15` | `AuthControllerIntegrationTest`, `TriggerConfigCacheServiceIT` |
| ClickHouse | `clickhouse/clickhouse-server:23.8` | `EsgDualSinkFlinkE2EIT`, `ClickHouseEnergyRepositoryIT` |
| Redis | `redis:7.2-alpine` | `AuthControllerIntegrationTest`, `TriggerConfigCacheServiceIT` |

---

## 6. Chạy test

### Backend (Gradle)

```bash
# Chạy tất cả test (unit + IT)
cd backend && ./gradlew test

# Chạy chỉ một IT test
./gradlew test --tests "com.uip.backend.tenant.TenantIsolationIT"

# Chạy tất cả IT tests
./gradlew test --tests "*IT"

# Bỏ qua IT tests (không có Docker)
IT_TESTS_ENABLED=false ./gradlew test
```

**Cấu hình Gradle** (`backend/build.gradle`):
```groovy
test {
    useJUnitPlatform()
    environment 'RYUK_DISABLED', 'true'      // Skip Ryuk cleanup container
    maxParallelForks = 1                      // Sequential: @SpringBootTest chia sẻ DB pool
    jvmArgs '-Xmx2g', '-Xms256m'
    finalizedBy jacocoTestReport
}
```

### Flink (Maven)

```bash
# Chạy tất cả test (unit + E2E IT)
cd flink-jobs && mvn test

# Chạy chỉ E2E IT
mvn test -Dtest=EsgDualSinkFlinkE2EIT -DfailIfNoTests=false

# Skip toàn bộ test
mvn test -DskipTests
```

### Yêu cầu môi trường

- **Docker Desktop** chạy (hoặc Docker daemon trên Linux)
- **Min 4GB RAM** cho Docker (ClickHouse + PostgreSQL + Redis)
- **Docker socket** accessible — xem [Section 11](#11-cấu-hình-docker-desktop-macos) nếu gặp lỗi socket trên macOS

---

## 7. Template — Tạo IT test mới

### 7A. Service-level IT (chỉ PostgreSQL)

```java
package com.uip.backend.mymodule;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "security.jwt.secret=test-secret-for-integration-tests-only-32chars")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MyNewFeatureIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("uip_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:29092");
        registry.add("spring.data.redis.host", () -> "localhost");
    }

    // Bắt buộc: mock 5 bean hạ tầng
    @MockBean @SuppressWarnings("unused") RedisConnectionFactory redisConnectionFactory;
    @MockBean @SuppressWarnings("unused") ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
    @MockBean @SuppressWarnings("unused") StringRedisTemplate redisTemplate;
    @MockBean @SuppressWarnings("unused") RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean @SuppressWarnings("unused") KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MyService myService;

    @BeforeAll
    void seedData() {
        jdbc.execute("INSERT INTO ... ON CONFLICT ... DO NOTHING");
    }

    @Test
    @Order(1)
    @DisplayName("TC-01: Mô tả test case")
    void testCase1_expectedBehavior() {
        // act
        var result = myService.doSomething();

        // assert
        assertThat(result).isNotNull();
    }
}
```

### 7B. Controller IT (MockMvc + PostgreSQL)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyControllerIT {

    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("uip_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:29092");
        registry.add("security.jwt.secret", () ->
                Base64.getEncoder().encodeToString("uip-integration-test-secret-32b!".getBytes()));
    }

    // Mock 5 bean + thêm mock bean khác nếu cần
    @MockBean @SuppressWarnings("unused") RedisConnectionFactory redisConnectionFactory;
    @MockBean @SuppressWarnings("unused") ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
    @MockBean @SuppressWarnings("unused") StringRedisTemplate redisTemplate;
    @MockBean @SuppressWarnings("unused") RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean @SuppressWarnings("unused") KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    private String token;

    @BeforeAll
    void obtainToken() throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin_Dev#2026!\"}"))
            .andExpect(status().isOk())
            .andReturn();
        token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    void myEndpoint_returnsData() throws Exception {
        mockMvc.perform(get("/api/v1/my-resource")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }
}
```

### 7C. Flink E2E IT (PostgreSQL + ClickHouse)

```java
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MyFlinkJobIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("uip_test")
            .withUsername("test")
            .withPassword("test");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> clickhouse = new GenericContainer<>("clickhouse/clickhouse-server:23.8")
            .withExposedPorts(8123)
            .waitingFor(Wait.forHttp("/ping").forPort(8123)
                    .withStartupTimeout(Duration.ofSeconds(90)));

    @BeforeAll
    void createSchemas() throws Exception {
        // Tạo bảng bằng JDBC connection trực tiếp
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), "test", "test");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS ...");
        }
    }

    @Test
    void pipeline_producesCorrectOutput() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(1);
        env.setRestartStrategy(RestartStrategies.noRestart());

        env.fromCollection(testData())
            .flatMap(...)
            .addSink(jdbcSink);

        env.execute("MyFlinkJobIT");

        // Assert rows in database
        // ...
    }
}
```

---

## 8. Quy tắc vàng

1. **Luôn dùng `disabledWithoutDocker = true`** — CI không có Docker sẽ skip thay vì fail
2. **`@DynamicPropertySource` phải gọi `postgres.start()`** — static field khởi tạo lazy, cần trigger trước khi Spring đọc property
3. **Mock đúng 5 bean** — quên mock sẽ gây `NoSuchBeanDefinitionException` hoặc connection refused
4. **Dùng `ON CONFLICT DO NOTHING`** khi seed data — test chạy lại không fail
5. **Không dùng `@DirtiesContext`** trừ khi thật cần (tốn 10-20s rebuild context). Chỉ `GenericTriggerIntegrationTest` dùng vì cần exclude auto-config
6. **`@TestMethodOrder(OrderAnnotation.class)`** — test chạy tuần tự, seed data ở `@BeforeAll` không bị race
7. **`maxParallelForks = 1`** trong Gradle — nhiều `@SpringBootTest` song song sẽ exhaust HikariCP pool
8. **Container dùng chung static field** — `@Container static` khởi tạo 1 lần cho cả class, không khởi tạo mỗi test method
9. **ClickHouse cần `Thread.sleep(500)` sau seed** — MergeTree engine cần thời gian flush
10. **`@SuppressWarnings("resource")`** trên `GenericContainer` — không cần close thủ công, Testcontainers lo

---

## 9. Danh sách IT tests hiện có

### Backend module

| Test | Container | Loại | Mô tả |
|------|-----------|------|-------|
| `TenantIsolationIT` | PostgreSQL | Service | RLS multi-tenant isolation, SET LOCAL reset |
| `CrossBuildingAggregationServiceIT` | PostgreSQL | Service | Cross-building aggregation math, tenant isolation, perf |
| `CrossBuildingConcurrentRLSIT` | PostgreSQL | Service | 50-thread concurrent RLS stress test |
| `NotificationFlowIT` | PostgreSQL | Controller | Push notification SSE + REST flow |
| `PushSubscriptionIT` | PostgreSQL | Controller | Push subscription CRUD lifecycle |
| `TriggerConfigCacheServiceIT` | PG + Redis (Docker CLI) | Service | Spring Cache @Cacheable/@CacheEvict với Redis thật |
| `AuthControllerIntegrationTest` | TimescaleDB + Redis (Docker CLI) | Controller | Auth login/logout JWT flow |
| `NotificationControllerIntegrationTest` | ? | Controller | Notification API |
| `GenericTriggerIntegrationTest` | PostgreSQL | Service | 7 AI scenario Kafka/REST/Scheduled triggers |
| `CitizenAiScenariosIntegrationTest` | PostgreSQL | Service | Citizen AI workflow scenarios |
| `ManagementAiScenariosIntegrationTest` | PostgreSQL | Service | Management AI workflow scenarios |
| `Sprint2ApiRegressionIntegrationTest` | PostgreSQL | Regression | Sprint 2 API regression |
| `Sprint5ApiRegressionIntegrationTest` | PostgreSQL | Regression | Sprint 5 API regression |
| `CapabilityFlagIT` | Không cần | Context | @ConditionalOnProperty capability flags |
| `WorkflowStartupTest` | PostgreSQL | Smoke | Camunda engine starts correctly |

### Flink module

| Test | Container | Loại | Mô tả |
|------|-----------|------|-------|
| `EsgDualSinkFlinkE2EIT` | PG + ClickHouse | E2E | Dual-sink pipeline 100 msg, source_id, tenant isolation |

### Analytics module

| Test | Container | Loại | Mô tả |
|------|-----------|------|-------|
| `ClickHouseEnergyRepositoryIT` | ClickHouse | Repository | ClickHouse aggregation queries, tenant isolation |

---

## 10. Troubleshooting

| Lỗi | Nguyên nhân | Fix |
|-----|-------------|-----|
| `NoSuchBeanDefinitionException: RedisConnectionFactory` | Quên `@MockBean` | Thêm 4 mock bean Redis + `RedisMessageListenerContainer` |
| `Connection refused: localhost:29092` | Kafka không chạy | Thêm `registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9999")` — port không tồn tại, Kafka auto-config sẽ fail softly |
| `Flyway migration failed` | Container chưa ready | Đảm bảo `postgres.start()` trong `@DynamicPropertySource` |
| `Docker socket not available` | Docker Desktop không chạy | Khởi động Docker Desktop hoặc thêm `assumeTrue` check |
| `HikariCP connection exhausted` | Nhiều `@SpringBootTest` chạy song song | `maxParallelForks = 1` trong build config |
| `docker-java HTTP 400` | Docker Engine 29.x incompatible | Dùng Pattern B (Docker CLI trực tiếp) như `AuthControllerIntegrationTest` |
| Test chậm (>30s) | `@DirtiesContext` rebuild context | Tránh dùng, chỉ dùng khi thay đổi auto-config giữa test |
| ClickHouse `count() = 0` | MergeTree chưa flush | Thêm `Thread.sleep(500)` sau khi insert data |
| **`Tests run: N, Skipped: N` khi Docker đang chạy** | `docker.host` trong `~/.testcontainers.properties` trỏ sai socket (symlink `/var/run/docker.sock` không hoạt động với docker-java) | Xem [Section 11](#11-cấu-hình-docker-desktop-macos) — xóa `docker.host` khỏi properties file |
| **`Could not find a valid Docker environment`** | Forked JVM của Surefire không kế thừa Docker context | Thêm `-Ddocker.host=unix:///...` vào `surefire` `<argLine>` trong `pom.xml` — xem [Section 11](#11-cấu-hình-docker-desktop-macos) |

---

## 11. Cấu hình Docker Desktop (macOS)

> **Áp dụng khi:** Testcontainers báo `Tests run: N, Skipped: N` hoặc `Could not find a valid Docker environment` dù Docker Desktop đang chạy bình thường.

### Root cause

Trên macOS, Docker Desktop dùng socket tại **`~/.docker/run/docker.sock`** (không phải `/var/run/docker.sock`). Symlink `/var/run/docker.sock → ~/.docker/run/docker.sock` tồn tại nhưng `docker-java` (thư viện Testcontainers dùng) **không follow symlink** khi khởi tạo Unix socket client trong forked JVM.

Kết quả:
- `docker` CLI hoạt động bình thường (follow symlink)
- Testcontainers trong forked Surefire JVM không tìm được Docker → `disabledWithoutDocker=true` → `Tests Skipped`

### Fix 1 — `~/.testcontainers.properties`

Kiểm tra và đảm bảo file **KHÔNG có** `docker.host` (để Testcontainers tự dùng `DockerDesktopClientProviderStrategy`):

```bash
# Kiểm tra
cat ~/.testcontainers.properties

# Nội dung đúng — KHÔNG có dòng docker.host
testcontainers.reuse.enable=false
ryuk.disabled=true

# Nếu có docker.host trỏ symlink → xóa đi
sed -i '' '/^docker.host=/d' ~/.testcontainers.properties
```

### Fix 2 — `flink-jobs/pom.xml` (Surefire argLine)

Thêm `-Ddocker.host` vào argLine để forked JVM luôn dùng đúng socket:

```xml
<plugin>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <argLine>
      --add-opens java.base/java.util=ALL-UNNAMED
      --add-opens java.base/java.lang=ALL-UNNAMED
      -Ddocker.host=unix:///Users/anhgv/.docker/run/docker.sock
    </argLine>
    <environmentVariables>
      <DOCKER_HOST>unix:///Users/anhgv/.docker/run/docker.sock</DOCKER_HOST>
      <TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE>/Users/anhgv/.docker/run/docker.sock</TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE>
    </environmentVariables>
  </configuration>
</plugin>
```

> **Lưu ý CI/Linux:** Trên Linux CI (không có Docker Desktop), socket thường ở `/var/run/docker.sock`. Cân nhắc dùng Maven profile `<id>macos</id>` với `<activation><os><family>mac</family></os></activation>` để chỉ apply fix này trên macOS.

### Kiểm tra nhanh

```bash
# 1. Xác nhận socket path đang active
docker context ls | grep '*'

# 2. Xác nhận socket file tồn tại (không phải symlink)
ls -la ~/.docker/run/docker.sock

# 3. Chạy test — nếu thấy "Tests run: N, Failures: 0" là thành công
cd flink-jobs && mvn test -Dtest=EsgDualSinkFlinkE2EIT -DfailIfNoTests=false
```
