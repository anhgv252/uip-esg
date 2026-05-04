# docs/mvp2/architecture/ — ADRs & Architecture Docs MVP2

## ADRs đã viết

| File | Status | Scope | Nội dung tóm tắt |
|------|--------|-------|-----------------|
| [ADR-010](ADR-010-multi-tenant-strategy.md) | ✅ Accepted | T1→T4 | tenant_id + LTREE (metadata only) + RLS + HikariCP SET LOCAL |
| [ADR-011](ADR-011-monorepo-module-extraction.md) | ✅ Accepted | T1→T4 | Monorepo structure + capability flags (2 loại) + thứ tự tách module + strangler fig procedure |
| [ADR-012](ADR-012-clickhouse-adoption-trigger.md) | ✅ Accepted | T3 | ClickHouse chỉ adopt khi ESG query >5 phút hoặc >10K sensors |
| [ADR-013](ADR-013-edge-computing-strategy.md) | ✅ Accepted | T2→T4 | EMQX Edge buffer (T2), Flink Edge aggregation (T3), Edge AI (T4) |
| [ADR-014](ADR-014-telemetry-enrichment-pattern.md) | ✅ Accepted | MVP3 | Inject tenant_id: RC pipeline tĩnh (Kịch bản A) + Flink Broadcast State CDC (Kịch bản B) |
| [ADR-015](ADR-015-caching-read-heavy-performance.md) | ✅ Accepted | MVP2-2 | Redis TTL cache + TimescaleDB Continuous Aggregates cho ESG API |
| [ADR-016](ADR-016-data-lakehouse-strategy.md) | ✅ Accepted | T4 | Iceberg on MinIO (không Snowflake) + Trino query engine |
| [ADR-017](ADR-017-multi-region-strategy.md) | ✅ Accepted | T4 | Warm DR trước → Active-Active khi ≥2 cities với independent SLA |
| [ADR-018](ADR-018-single-codebase-tier-delivery.md) | ~~Superseded~~ | — | Hợp nhất vào ADR-011 |
| [ADR-019](ADR-019-partner-customization-architecture.md) | ✅ Accepted | T1→T4 | 3-layer partner customization: Tenant Config (DB) → Partner Profile (YAML) → Extension Module (code) |
| [ADR-020](ADR-020-non-http-tenant-propagation.md) | 🟡 Proposed | MVP2 | Extract tenant_id từ Kafka message body cho consumer/Flink/@Async; TenantContextTaskDecorator cho @Async |
| [ADR-021](ADR-021-t1-force-rls-compat.md) | ✅ Accepted | MVP2 | T1 tenant isolation compatibility với force RLS |
| [ADR-022](ADR-022-cache-warming-strategy.md) | ✅ Accepted | MVP2-3 | Cache warming strategy: explicit tenantId param, không dùng TenantContext trong @Cacheable |
| [ADR-023](ADR-023-rls-migration-strategy.md) | ✅ Accepted | MVP2 | RLS migration zero-downtime: maintenance window 2-3 AM, metadata-only DDL <5 giây |
| [ADR-024](ADR-024-partner-id-naming-convention.md) | ✅ Accepted | MVP2-4 | Partner ID: kebab-case, 2-3 segments, descriptive; registry tại partner-registry.md |
| [ADR-025](ADR-025-tenant-admin-authorization.md) | ✅ Accepted | MVP2-4 | TenantAdmin authorization: service-layer tenant resolution, TENANT_ADMIN always serves own tenant |

## Tóm tắt nhanh các quyết định quan trọng

| Quyết định | Chọn | Không chọn |
|-----------|------|-----------|
| Quản lý code nhiều tier | 1 codebase + capability flags + Helm values | Branch per tier |
| Multi-tenancy T2 | RLS + tenant_id + SET LOCAL | Schema-per-tenant ngay |
| Module tách đầu tiên | iot-ingestion (khi >50K events/sec) | Big-bang tất cả |
| Analytics DB | TimescaleDB đến T3, ClickHouse khi triggered | ClickHouse ngay |
| Edge computing | EMQX Edge buffer (T2), Flink Edge (T3) | K8s/Greengrass tại edge |
| LTREE scope | Chỉ metadata tables (sensors, buildings) | Time-series tables |
| Data Lakehouse | Iceberg + MinIO + Trino | Snowflake |
| Multi-region | Warm DR trước (T4 init) | Active-Active ngay |
| Partner customization | 3-layer extension (DB config → YAML → Spring module) | Branch per partner |

## Spike documents

| File | Sprint | Nội dung |
|------|--------|---------|
| [multi-tenancy-implementation-spike.md](multi-tenancy-implementation-spike.md) | Sprint 2 | RLS, HikariCP, TenantContext implementation decisions |
| [sprint3-readiness-spike.md](sprint3-readiness-spike.md) | Sprint 3 | BT-22b P0 blocker, cache key isolation, Kafka SASL |
| [sprint4-readiness-spike.md](sprint4-readiness-spike.md) | Sprint 4 | Migration conflict V17/V18→V20/V21, EsgReportExportPort design, invite token, TenantAdmin auth |

## Thứ tự đọc đề xuất

1. **Bắt đầu ở đây** → ADR-010 (multi-tenancy: nền tảng mọi thứ)
2. **Source code & module strategy** → ADR-011 (monorepo + capability flags + extraction order)
3. **Partner customization** → ADR-019 (3-layer extension model, no per-partner branches) → ADR-024 (partner ID naming)
4. **Tầng data** → ADR-012 (ClickHouse) → ADR-015 (cache) → ADR-014 (telemetry)
5. **Infrastructure scale** → ADR-013 (edge) → ADR-016 (lakehouse) → ADR-017 (multi-region)
6. **Sprint 4 context** → sprint4-readiness-spike.md → ADR-025 (TenantAdmin auth)

## Quy ước đặt tên ADR

```
ADR-{number}-{short-slug}.md
Ví dụ: ADR-010-multi-tenant-strategy.md
```

**Status values**: `Proposed` → `Accepted` → `Deprecated` (khi thay thế bằng ADR mới)
