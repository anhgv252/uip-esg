# ADR-012: gRPC cho Internal Service-to-Service Communication (Backend → Analytics Service)

**Date**: 2026-05-11  
**Status**: Accepted
**Deciders**: Solution Architect, Backend Lead  
**Context**: Extracted Analytics Service (ADR-011 Strangler Fig — Tier 2)

---

## 1. Context

Sau khi tách `analytics-service` thành container độc lập (ADR-011), giao tiếp giữa `backend` và `analytics-service` hiện đang dùng **REST** (`ClickHouseRestAnalyticsAdapter` → RestTemplate → HTTP/1.1 JSON).

Canonical SA architecture đã quy định rõ:

> **Backend module ↔ module (internal): Kafka hoặc gRPC — KHÔNG dùng REST internal.**  
> REST chỉ dành cho: **Frontend ↔ Backend** (public API).

Tức là việc dùng REST cho internal call hiện tại là **tạm thời chấp nhận được ở MVP** nhưng cần được migrate sang gRPC khi traffic tăng và hệ thống chuyển sang production.

### Vấn đề cụ thể với REST hiện tại

| Vấn đề | Tác động |
|--------|---------|
| JSON serialization/deserialization overhead mỗi request | CPU tăng khi ESG aggregate calls cao |
| HTTP/1.1 — không có multiplexing | Tắc nghẽn khi concurrent ESG report generation |
| Không có schema contract | Breaking changes không được bắt tại compile time |
| RestTemplate không có built-in retry policy | Phải implement thủ công |
| Không dùng được Istio L7 gRPC metrics (request rate, error rate per method) | Observability hạn chế |

### Load Profile Dự Kiến

```
ESG Report Generation (quarterly, fan-out):
  → 1 trigger → 50 districts × 200 buildings = 10,000 building-level analytics queries
  → Thời gian cho phép: <10 phút (SA target)
  → Với REST sequential: ~10,000 × (7ms avg) = ~70 giây cho network overhead thuần
  → Với gRPC + streaming: batch trong 1-2 calls, overhead giảm 5-10x
```

---

## 2. Decision

**Migrate backend → analytics-service communication từ REST sang gRPC** cho tất cả internal sync queries.

**Client (browser/mobile) → backend**: vẫn giữ REST — không thay đổi.

---

## 3. So Sánh Chi Tiết

| Criteria | REST (hiện tại) | gRPC (đề xuất) | Ghi chú |
|----------|-----------------|----------------|---------|
| **Protocol** | HTTP/1.1 + JSON | HTTP/2 + Protobuf binary | HTTP/2 = multiplexing, header compression |
| **Payload size** | ~500–1KB JSON | ~50–100B binary | Protobuf ~5-10x nhỏ hơn JSON |
| **Serialization overhead** | Jackson reflection | Protobuf codegen | gRPC: zero reflection, codegen |
| **Schema contract** | OpenAPI (runtime) | `.proto` file (compile-time) | Breaking change bắt tại build |
| **Streaming** | Không có | Server-streaming, bidirectional | Cho future real-time analytics |
| **Timeout control** | RestTemplate manually | gRPC deadline built-in | `withDeadlineAfter(5, SECONDS)` |
| **Load balancing** | HTTP round-robin | gRPC-aware (Istio native) | Istio DestinationRule `trafficPolicy: H2UpgradePolicy` |
| **Observability** | Micrometer HTTP metrics | Micrometer gRPC metrics | Method-level latency histogram |
| **Debugging** | `curl` / Postman | `grpcurl` / Postman gRPC | Hơi phức tạp hơn khi debug |
| **Implementation effort** | ✅ Đã có | ~2 Sprint points | `.proto` + codegen + wire up |
| **mTLS** | Manual cert | Istio auto mTLS | gRPC trong Istio = mTLS tự động |

### Performance Benchmark (Estimated)

```
Scenario: 1,000 concurrent energy-aggregate queries

REST (HTTP/1.1 JSON):
  → Throughput: ~2,000 req/s (limited by JSON parse + TCP connections)
  → p95 latency: ~25–40ms (network + JSON)
  → CPU analytics-service: ~35%

gRPC (HTTP/2 Protobuf):
  → Throughput: ~8,000–10,000 req/s (multiplexed, binary)
  → p95 latency: ~8–12ms (network + binary decode)
  → CPU analytics-service: ~15%
  
→ Expected: 4-5x throughput improvement, 60-70% latency reduction
```

---

## 4. Implementation Plan

### Phase A — Giữ nguyên REST (MVP3 — hiện tại)
`ClickHouseRestAnalyticsAdapter` đang hoạt động, `AnalyticsPort` interface đã tách đúng. **Không cần thay đổi gì ngay.**  
Tầng Port Interface đảm bảo code `EsgService` không cần đổi khi migrate.

### Phase B — Migrate sang gRPC (Sprint 7)

#### Step 1: Định nghĩa `.proto` contract

```protobuf
// shared/proto/analytics/v1/energy.proto
syntax = "proto3";
package uip.analytics.v1;

option java_package        = "com.uip.analytics.grpc.v1";
option java_outer_classname = "EnergyProto";
option java_multiple_files  = true;

service EnergyAnalyticsService {
  // Unary: ESG module query aggregate
  rpc GetEnergyAggregate(EnergyAggregateRequest) returns (EnergyAggregateResponse);

  // Server-streaming: real-time analytics (Phase 3 — future)
  rpc StreamBuildingEnergy(StreamEnergyRequest) returns (stream BuildingEnergyEvent);
}

message EnergyAggregateRequest {
  string        tenant_id    = 1;
  repeated string building_ids = 2;
  int64         from_epoch   = 3;
  int64         to_epoch     = 4;
}

message EnergyAggregateResponse {
  double          total_kwh          = 1;
  double          peak_demand_kw     = 2;
  double          avg_power_factor   = 3;
  map<string, double> per_building_kwh = 4;  // buildingId → kwh
}
```

#### Step 2: analytics-service thêm gRPC server

```gradle
// analytics-service/build.gradle
plugins {
    id 'com.google.protobuf' version '0.9.4'
}

dependencies {
    implementation 'net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE'
    implementation 'io.grpc:grpc-protobuf:1.63.0'
    implementation 'io.grpc:grpc-stub:1.63.0'
    compileOnly     'jakarta.annotation:jakarta.annotation-api'
}

protobuf {
    protoc { artifact = 'com.google.protobuf:protoc:3.25.3' }
    plugins { grpc { artifact = 'io.grpc:protoc-gen-grpc-java:1.63.0' } }
    generateProtoTasks { all()*.plugins { grpc {} } }
}
```

```java
// analytics-service: implement gRPC server
@GrpcService
public class EnergyAnalyticsGrpcService
        extends EnergyAnalyticsServiceGrpc.EnergyAnalyticsServiceImplBase {

    private final EnergyAggregateService aggregateService;  // reuse existing logic

    @Override
    public void getEnergyAggregate(
            EnergyAggregateRequest request,
            StreamObserver<EnergyAggregateResponse> responseObserver) {

        var result = aggregateService.aggregate(
            request.getTenantId(),
            request.getBuildingIdsList(),
            request.getFromEpoch(),
            request.getToEpoch()
        );

        responseObserver.onNext(EnergyAggregateResponse.newBuilder()
            .setTotalKwh(result.totalKwh())
            .setPeakDemandKw(result.peakDemandKw())
            .setAvgPowerFactor(result.avgPowerFactor())
            .putAllPerBuildingKwh(result.perBuildingKwh())
            .build());
        responseObserver.onCompleted();
    }
}
```

```yaml
# analytics-service/src/main/resources/application.yml (thêm)
grpc:
  server:
    port: 9090
```

#### Step 3: backend thêm gRPC client adapter (thay REST adapter)

```java
// backend: ClickHouseGrpcAnalyticsAdapter implements AnalyticsPort
@Component
@ConditionalOnProperty(
    name = "uip.capabilities.analytics-external",
    havingValue = "true"
)
@Slf4j
public class ClickHouseGrpcAnalyticsAdapter implements AnalyticsPort {

    private final EnergyAnalyticsServiceGrpc.EnergyAnalyticsServiceBlockingStub stub;

    public ClickHouseGrpcAnalyticsAdapter(
            @GrpcClient("analytics-service")
            Channel channel) {
        this.stub = EnergyAnalyticsServiceGrpc.newBlockingStub(channel)
            .withDeadlineAfter(5, TimeUnit.SECONDS);  // built-in deadline
    }

    @Override
    public EsgAggregateResult queryEnergyAggregate(
            String tenantId, List<String> buildingIds, long fromEpoch, long toEpoch) {

        try {
            var response = stub.getEnergyAggregate(
                EnergyAggregateRequest.newBuilder()
                    .setTenantId(tenantId)
                    .addAllBuildingIds(buildingIds)
                    .setFromEpoch(fromEpoch)
                    .setToEpoch(toEpoch)
                    .build());

            return new EsgAggregateResult(
                response.getTotalKwh(),
                response.getPeakDemandKw(),
                response.getPerBuildingKwhMap(),
                buildingIds
            );

        } catch (StatusRuntimeException e) {
            log.error("[Analytics-gRPC] Call failed: {} for tenant={}", e.getStatus(), tenantId);
            return new EsgAggregateResult(0.0, 0.0, Map.of(), buildingIds);  // graceful fallback
        }
    }
}
```

```gradle
// backend/build.gradle (thêm)
dependencies {
    implementation 'net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE'
    implementation 'io.grpc:grpc-protobuf:1.63.0'
    implementation 'io.grpc:grpc-stub:1.63.0'
}
```

```yaml
# backend/src/main/resources/application-t2.yml (thêm)
grpc:
  client:
    analytics-service:
      address: 'static://uip-analytics-service:9090'
      negotiation-type: plaintext  # Istio sẽ handle mTLS ở transport layer
```

#### Step 4: Helm — Expose port gRPC

```yaml
# infra/helm/uip-analytics-service/templates/service.yaml
ports:
  - port: 8081
    targetPort: 8081
    protocol: TCP
    name: http        # Giữ nguyên REST cho external/debug
  - port: 9090
    targetPort: 9090
    protocol: TCP
    name: grpc        # Mới — internal service mesh
```

```yaml
# infra/helm/uip-analytics-service/templates/destinationrule.yaml (Istio)
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: analytics-service-dr
spec:
  host: uip-analytics-service
  trafficPolicy:
    connectionPool:
      http:
        h2UpgradePolicy: UPGRADE  # Force HTTP/2 cho gRPC
```

---

## 5. Security

| Concern | Giải pháp |
|---------|-----------|
| Auth giữa services | **Istio mTLS** — auto inject cert, không cần code | 
| Không expose gRPC ra external | Istio `VirtualService` chỉ cho phép traffic từ backend namespace |
| Client token (hiện tại REST dùng JWT) | gRPC: dùng `Metadata` header `authorization: Bearer <service-account-token>` hoặc bỏ auth nội bộ (Istio mTLS đủ) |

**Khuyến nghị**: Trong Kubernetes + Istio, mTLS giữa services là đủ. Không cần JWT truyền sang analytics-service cho internal gRPC. JWT chỉ validate ở Kong API Gateway (entry point).

---

## 6. Consequences

### ✅ Positive
- **Performance**: ~4-5x throughput, 60-70% latency reduction cho aggregate queries
- **Type safety**: `.proto` là single source of truth cho contract backend ↔ analytics
- **Istio native**: gRPC metrics, circuit breaker, retry policy cấu hình qua Istio không cần code
- **Server streaming ready**: `.proto` đã định nghĩa `StreamBuildingEnergy` cho Phase 3 real-time analytics
- **Align với SA canonical**: Đúng quy tắc "module ↔ module = gRPC, không REST"

### ⚠️ Trade-offs
- **Debugging**: Không dùng được `curl` thuần — cần `grpcurl` hoặc Postman gRPC collection
- **Migration effort**: ~2 Sprint points (proto definition + codegen + 2 adapters + Helm)
- **Protobuf versioning**: Cần quản lý `.proto` evolution (backward compatible rules)
- **Testcontainers**: IT test cho gRPC server cần thêm gRPC client setup trong test

---

## 7. Migration Strategy (Zero Downtime)

Vì `AnalyticsPort` interface đã tồn tại, migration **không cần downtime**:

```
Sprint 6 (hiện tại):
  → REST adapter đang chạy, production OK
  → KHÔNG thay đổi gì ở Sprint 6

Sprint 7:
  → analytics-service: thêm gRPC server song song với REST server (cùng deploy)
  → backend: thêm ClickHouseGrpcAnalyticsAdapter với @Primary, REST adapter @ConditionalOnMissingBean
  → A/B flag: uip.capabilities.analytics-transport=grpc|rest (default: rest)
  → Test môi trường staging với grpc=true

Sprint 8:
  → Flip default sang grpc=true
  → Giữ REST endpoint cho backward compat / debug 30 ngày
  → Monitor gRPC error rate / latency qua Istio dashboard

Sprint 9+:
  → Remove ClickHouseRestAnalyticsAdapter (sau khi stable)
  → REST endpoint analytics-service chỉ giữ cho /actuator/health + /v3/api-docs (debug)
```

---

## 8. Tóm Tắt

| | |
|---|---|
| **Quyết định** | Migrate backend→analytics-service từ REST sang gRPC (Sprint 7) |
| **Client→backend** | Giữ nguyên REST — không thay đổi |
| **Migration risk** | Thấp — `AnalyticsPort` interface isolates ESG business logic khỏi transport |
| **Expected gain** | 4-5x throughput, 60% latency reduction, Istio-native observability |
| **Timeline** | Sprint 7 implement, Sprint 8 production flip |

---

*ADR-012 | Proposed 2026-05-11 | Relates to: ADR-011 (Strangler Fig Analytics Extraction)*
