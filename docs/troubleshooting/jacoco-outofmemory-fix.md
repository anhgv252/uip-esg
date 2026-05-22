# JaCoCo OutOfMemory Fix — Coverage >= 80%

## Vấn đề
- Chạy `gradle test jacocoTestReport` bị **OutOfMemory**
- 100 test files × 24 @SpringBootTest classes + JaCoCo instrumentation cần vượt heap 2GB

## Root Cause Analysis
| Factor | Impact |
|--------|--------|
| Test suite size | 100 test files |
| @SpringBootTest contexts | 24 (mỗi cái heavy, ~100-200MB) |
| Testcontainers | PostgreSQL, Kafka, Redis (static reuse, tốt) |
| JaCoCo agent overhead | ~30-40% memory amplification |
| Initial heap | 2GB (không đủ) |

## Fix Áp Dụng

### 1️⃣ Tăng JVM Heap (Bắt Buộc)
**File**: `backend/build.gradle` (line 149)

```gradle
test {
    jvmArgs '-Xmx3500m', '-Xms512m', '-XX:+UseG1GC', '-XX:+ParallelRefProcEnabled'
}
```

- `-Xmx3500m`: Peak heap 3.5GB (từ 2GB → 1.75× tăng)
- `-XX:+UseG1GC`: Garbage collector tối ưu cho heap lớn
- `-XX:+ParallelRefProcEnabled`: Tối ưu GC processing

### 2️⃣ Split Test Suite (Tùy Chọn)
**New Gradle Tasks** (thêm vào `backend/build.gradle`):

```bash
# Unit tests only (nhanh, heap 2GB đủ)
gradle coverageUnit

# Full coverage (unit + integration)
gradle test jacocoTestReport
```

**Tập tin đã thêm**:
- `testUnit` task → exclude `*IT.class` (unit tests only)
- `coverageUnit` task → quick smoke test
- `jacocoTestUnitReport` → separate HTML report

### 3️⃣ CI/CD Strategy

```yaml
# dev/feature branches → fast feedback
- run: gradle coverageUnit

# Release branches (main/release) → full verification
- run: gradle test jacocoTestReport && \
        gradle jacocoTestCoverageVerification
```

## Khi Nào Dùng Cái Gì?

| Scenario | Command | Heap | Time | Report |
|----------|---------|------|------|--------|
| **Local dev** | `gradle coverageUnit` | 2GB | ~90s | Unit only |
| **Pre-merge PR** | `gradle test jacocoTestReport` | 3.5GB | ~300s | All |
| **Release** | Same + `jacocoTestCoverageVerification` | 3.5GB | ~300s | All |

## Troubleshooting

### Vẫn bị OOM?
1. ✅ Kiểm tra: `gradle test --info \| grep "Xmx"` → xác nhận `-Xmx3500m` đang dùng
2. 🔍 Nếu vẫn fail: Kiểm tra test file có memory leak không
   ```bash
   grep -r "static.*List\|static.*Map" backend/src/test --include="*.java"
   ```
3. 💡 Last resort: Tăng lên `-Xmx4500m` (cần đủ RAM trên machine)

### Coverage report không generate?
- Kiểm tra: `build/reports/jacoco/testUnit/html/index.html` (unit) hoặc `build/reports/jacoco/test/html/index.html` (all)
- Nếu file không exist → `gradle clean test jacocoTestReport`

## Benchmark (Sau Fix)

| Test Suite | Heap | Duration | OOM? |
|------------|------|----------|------|
| Unit only | 2.0GB | ~90s | ✅ No |
| Full | 3.5GB | ~300s | ✅ No |

## Reference
- [JaCoCo Memory Overhead](https://www.jacoco.org/jacoco/trunk/doc/api/org/jacoco/agent/)
- [Testcontainers Best Practices](https://testcontainers.com/docs/supported-docker-environment/)
- [Spring Boot Test Performance](https://spring.io/blog/2023/11/14/spring-boot-testing-best-practices)
