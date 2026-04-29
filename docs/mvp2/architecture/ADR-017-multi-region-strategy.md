# ADR-017: Multi-Region Strategy — Active-Active vs Warm DR

**Status**: Accepted
**Date**: 2026-04-28
**Deciders**: Tech Lead, Solution Architect, Product Owner
**Scope**: T4 — khi SLA 99.99% hoặc multi-city deployment yêu cầu

---

## Context

T1→T3 dùng single-region deployment (1 datacenter hoặc 1 cloud region). Đây là đúng vì:
- Single datacenter đơn giản hơn, dễ debug, ít failure mode
- SLA 99.9% (T1) và 99.95% (T2/T3) đạt được với HA trong cùng datacenter (Patroni cho Postgres, Kafka RF=3)
- Chi phí multi-region cao không tương xứng với lợi ích ở scale nhỏ

Ở T4 (Smart Metropolis), yêu cầu mới xuất hiện:

1. **SLA 99.99%**: ~52 phút downtime/năm cho phép. Không thể đạt với single-region (datacenter power outage, network partition).
2. **Multi-city deployment**: TP.HCM và Hà Nội có thể là 2 region riêng. Operations Center của Hà Nội không nên phụ thuộc vào datacenter ở TP.HCM.
3. **Disaster Recovery (DR)**: Government yêu cầu DR plan với RTO <1 giờ, RPO <15 phút cho critical urban infrastructure.
4. **Data locality**: Sensor data của Hà Nội lý tưởng nên được process gần Hà Nội (latency <50ms thay vì WAN roundtrip Hà Nội → HCM).

### Hai lựa chọn chính

1. **Active-Active**: 2+ regions đều active, serve traffic, share state. Zero downtime khi 1 region fail.
2. **Warm DR (Active-Passive)**: 1 region primary, 1 region DR standby (warm, replicated). Failover 5–15 phút khi primary fail.

---

## Decision

### Chiến lược 2 giai đoạn

#### Giai đoạn 1: Warm DR (T4 initial)

Khi T4 contract đầu tiên (1 thành phố lớn), deploy Warm DR:

```
Region Primary (HCM Datacenter A):
  ├── Full stack: Spring Boot, Kafka, Flink, TimescaleDB, ClickHouse, Redis
  ├── Serve 100% traffic
  └── Primary data — nguồn sự thật

Region DR (HCM Datacenter B hoặc Cloud Region):
  ├── Kafka MirrorMaker2 — replicate topics từ Primary
  ├── TimescaleDB streaming replication (WAL shipping)
  ├── ClickHouse remote replication
  └── Standby: không serve traffic, sẵn sàng failover
```

**RTO/RPO với Warm DR:**
- RPO (Recovery Point Objective): <15 phút (Kafka replication lag)
- RTO (Recovery Time Objective): <30 phút (manual failover → auto trong future)
- SLA đạt được: 99.95% → 99.99% với Warm DR

**Trigger failover:**
```
Primary datacenter không reachable >5 phút
HOẶC: TimescaleDB primary unreachable, không có automatic Patroni failover
HOẶC: Manual decision bởi on-call engineer
```

**Cơ chế failover:**
```
1. Kafka MirrorMaker2 promote DR topics thành primary (lag <15 phút)
2. DNS failover: uip-api.city.gov.vn → DR region IP (TTL 60s)
3. TimescaleDB DR promote: `pg_ctl promote`
4. Spring Boot restart với DR database URL
5. Validate: run smoke test → announce recovery
```

---

#### Giai đoạn 2: Active-Active (khi ≥2 cities)

Khi UIP deploy cho cả TP.HCM và Hà Nội (hai deployment riêng biệt với SLA độc lập):

```
Region HCM (Datacenter HCM):           Region HN (Datacenter Hanoi):
  ├── Serve HCM tenant traffic     ←→   ├── Serve HN tenant traffic
  ├── HCM sensor data                   ├── HN sensor data
  └── Kafka MirrorMaker2 ─────────────→ └── (read-only replica of HCM events)
                          ←─────────────   (read-only replica of HN events)
```

**Active-Active không có shared state** — mỗi city là một tenant đầy đủ trong region của mình. Cross-city analytics là asynchronous (không realtime).

```
Cross-city ESG comparison report (async):
  HCM ClickHouse ─→ Kafka MirrorMaker2 ─→ HN ClickHouse (replicated)
  Data Lakehouse (Iceberg/MinIO) ở cả 2 regions
  Trino federation query: SELECT ... FROM hcm_catalog.esg JOIN hn_catalog.esg ...
```

**Khi nào escalate từ Warm DR lên Active-Active:**
```
Deploy cho ≥2 thành phố với independent SLA requirements
HOẶC: Government yêu cầu mỗi tỉnh/thành có datacenter riêng (data residency)
HOẶC: Cross-city AI analytics (flood pattern correlation) cần real-time data từ nhiều city
```

---

### Kafka MirrorMaker2 Configuration

```yaml
# mirror-maker2.yml — replication từ HCM sang HN
clusters:
  - alias: hcm
    bootstrap.servers: kafka-hcm:9092
  - alias: hn
    bootstrap.servers: kafka-hn:9092

mirrors:
  - source.cluster.alias: hcm
    target.cluster.alias: hn
    topics: "UIP.*.sensor.*.v1, UIP.*.alert.*.v1, UIP.esg.*.v1"
    # Không replicate: traffic internal, citizen PII
    replication.factor: 2
    refresh.topics.interval.seconds: 600

  - source.cluster.alias: hn
    target.cluster.alias: hcm
    topics: "UIP.*.sensor.*.v1, UIP.*.alert.*.v1, UIP.esg.*.v1"
    replication.factor: 2
```

**Naming convention cho replicated topics:**
```
Source: UIP.environment.aqi.threshold-exceeded.v1 (tại HCM)
Mirror: hcm.UIP.environment.aqi.threshold-exceeded.v1 (tại HN)
```

Consumer tại HN đọc `hcm.UIP.*` để có data của HCM — không nhầm với local HN topics.

### TimescaleDB Replication

```
Warm DR: PostgreSQL streaming replication (WAL shipping)
  Primary (HCM): max_wal_senders = 5, wal_level = replica
  Standby (DR): hot_standby = on, recovery.conf pointing to primary

Active-Active: KHÔNG dùng bi-directional replication cho operational DB
  → Quá phức tạp và conflict resolution khó
  → Mỗi region có DB độc lập; cross-region data qua Lakehouse (async)
```

---

### Không áp dụng Active-Active cho shared state

Active-Active với shared mutable state (TimescaleDB, Redis) cực kỳ phức tạp:
- **Conflict resolution**: cùng một alert được acknowledge ở 2 region cùng lúc → ai win?
- **Split-brain**: network partition giữa 2 region → 2 region đều nghĩ mình là primary
- **Latency**: write phải wait cho cả 2 region confirm → tăng latency cho mọi write

**Quyết định**: Active-Active chỉ áp dụng khi mỗi region là **independent tenant** (HCM vs HN, không share data). Không áp dụng Active-Active cho shared-state multi-tenant deployment.

---

## Consequences

### Tích cực

- **Warm DR đơn giản, đủ cho T4 initial**: Đạt SLA 99.99% với complexity thấp hơn Active-Active
- **Active-Active khi thực sự cần**: Không over-engineer cho single city
- **Kafka MirrorMaker2 đã proven**: Battle-tested cho cross-datacenter Kafka replication
- **TimescaleDB WAL replication đơn giản**: Standard PostgreSQL feature, không cần tool mới

### Tiêu cực / Risks

| Rủi ro | Mức độ | Mitigation |
|--------|--------|-----------|
| Failover thủ công chậm | Medium | Runbook tự động hóa tối đa; target <30 phút RTO |
| Kafka MM2 lag khi write burst | Medium | Monitor MM2 lag; alert khi >5 phút |
| DNS TTL gây client cache khi failover | Low | TTL 60s cho production; test failover định kỳ |
| Bi-directional conflict (Active-Active) | High | Chỉ dùng Active-Active khi mỗi region độc lập hoàn toàn |
| TimescaleDB promote lag | Low | WAL shipping sub-second lag trong normal ops |

### Không chọn

| Phương án | Lý do loại |
|-----------|------------|
| Active-Active với shared state ngay từ đầu | Conflict resolution và split-brain quá phức tạp cho team hiện tại |
| Cold DR (backup restore) | RTO >4 giờ không đạt SLA 99.99% |
| Multi-region Kubernetes với Istio federation | Overkill cho T4 initial; Istio cross-cluster phức tạp hơn lợi ích |
| Cloud provider managed DR (AWS RDS Multi-AZ) | Vendor lock-in; data sovereignty concern cho government |

---

## SLA Targets per Strategy

| Deployment | SLA | Downtime/năm | Strategy |
|-----------|-----|-------------|---------|
| T1 (Docker Compose) | 99.9% | 8.7 giờ | Single-node, manual restart |
| T2/T3 (K8s 3-5 nodes) | 99.95% | 4.4 giờ | HA trong cluster (Patroni, RF=3) |
| T4 Warm DR | 99.99% | 52 phút | Cross-datacenter replication |
| T4 Active-Active | 99.999% | 5 phút | Multi-city, independent regions |

---

## Implementation Checklist

### Warm DR setup
- [ ] Provision DR datacenter/region với same stack version
- [ ] Configure PostgreSQL streaming replication (WAL)
- [ ] Deploy Kafka MirrorMaker2 với config trên
- [ ] DNS failover test: verify TTL propagation <60s
- [ ] Failover drill: monthly (simulate primary failure, execute failover runbook, verify RTO)
- [ ] Monitor: MM2 consumer lag dashboard trong Grafana; alert khi lag >5 phút

### Active-Active (khi ≥2 cities)
- [ ] Verify mỗi city là independent tenant (không cross-region shared state)
- [ ] Setup bidirectional MM2 với naming convention `{source}.UIP.*`
- [ ] Trino federation: query cả 2 catalogs từ single SQL endpoint
- [ ] Test cross-city ESG report: Trino join HCM + HN data

---

## Related

- ADR-010: Multi-Tenant Strategy (mỗi city = separate tenant trong Active-Active)
- ADR-012: ClickHouse Adoption (ClickHouse cần replication strategy riêng cho multi-region)
- ADR-016: Data Lakehouse (Iceberg/MinIO federation cho cross-city analytics)
- [Demo & Roadmap 2026-04-25 — Section 4.1, T4 infrastructure table](../project/demo-and-roadmap-2026-04-25.md)
