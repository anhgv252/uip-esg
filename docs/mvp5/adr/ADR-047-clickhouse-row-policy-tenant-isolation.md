# ADR-047: ClickHouse Row-Level Policy for Tenant Isolation

**Date**: 2026-06-22
**Status**: Accepted
**Priority**: P1 (post-pivot — build correctness for 50-building modular architecture)
**Sprint**: M5-1
**Author**: Solution Architect
**Supersedes assumption in**: `docs/mvp5/brainstorm/sa-mvp5-conflict-resolution.md` §1.2 (gRPC session context assumption — corrected by this ADR)

---

## 1. Context

### 1.1 Business driver

MVP5 pivot hướng tới **50+ paying building customers**. Một bug duy nhất ở bất kỳ adapter nào = **cross-tenant sensor data leak**. Ở quy mô commercial 50+ tenants:

- **Breach crititcal** — customer churn + pháp lý
- **Fail ISO 27001 / SOC 2 audit** — không pass compliance gate
- **Reputational damage** — city authority + investor trust

### 1.2 Current state (verified by code review 2026-06-22)

| Layer | Tenant enforcement | Status |
|---|---|---|
| PostgreSQL | RLS (Row-Level Security) | ✅ Migrations V16/V18/V30 deployed |
| ClickHouse | **NONE at storage layer** | ❌ Chỉ application-layer filter |
| Flink | **NONE at compute layer** | ❌ Chỉ application-layer filter |
| Redis cache | Key prefix convention | ⚠️ Convention, không enforced |

ClickHouse + Flink hiện chỉ filter tenant ở **application-layer** — một query quên `WHERE tenant_id` = silent leak.

### 1.3 Priority downgrade rationale

Post-pivot (MVP5), GAP-1 tenant isolation xuống **P0 → P1**:
- Giai đoạn pilot 2-3 tenant tin cậy (không breach-critical ngay)
- **NHƯNG** PO yêu cầu source code phải giống kiến trúc 50-building modular — design không thay đổi, chỉ schedule dịch

**→ Decision: build đúng architecture ngay M5-1, không defer.**

### 1.4 Flink tenant isolation — flink-jobs module (added M5-1 T05)

**Problem this closes.** Originally the Flink tenant-isolation contract lived only in the
backend module (`com.uip.backend.tenant.flink.*`) as a **forward guard**: that module declares
zero Flink dependencies and contains no Flink jobs, so its ArchUnit rule
(`TenantIsolationArchTest`) is vacuously true — it cannot catch a non-tenant-aware operator
because no operator exists there to catch. This is exactly the **false-DONE** failure mode
documented in `feedback_doc_vs_code_gap`: "file tên đúng" but no executable artifact enforcing
the contract.

**Fix.** Ported the tenant-isolation contract into the `flink-jobs` module (where the real
jobs live) and refactored the three sensor-stream jobs to use it:

| File (in `flink-jobs`) | Role |
|---|---|
| `src/main/java/com/uip/flink/common/tenant/TenantContext.java` | ThreadLocal tenant holder — fail-closed set, get/require/clear (ported from backend, package changed to `com.uip.flink.common.tenant`) |
| `src/main/java/com/uip/flink/common/tenant/TenantKeyedProcessFunction.java` | Contract holder — `TenantExtractor<IN>` functional interface |
| `src/main/java/com/uip/flink/common/tenant/TenantKeyedProcessFunctionDelegate.java` | Runtime half — `run(record, processor, emitter)` does extract→bind→process→clear, fail-closed drop + `droppedNoTenant()` counter |
| `src/main/java/com/uip/flink/common/tenant/TenantBindingProcessFunction.java` | **NEW.** Non-keyed `ProcessFunction` that binds tenant + fail-closed drops at pipeline entry (for window jobs that have no keyed operator of their own) |
| `src/test/java/com/uip/flink/arch/FlinkTenantArchTest.java` | **NEW.** ArchUnit rules that actually enforce the contract in-tree (NOT `allowEmptyShould`) |

**Operators refactored (ADR-047 §1.3):**

| Operator | Pattern | What changed |
|---|---|---|
| `TenantIdValidator` (esg) | `ProcessFunction` | Missing-tenant records still route to `ERROR_TAG` side output (observability preserved). Valid records now route through the delegate so `TenantContext` is bound during collect. |
| `DistrictAggregationJob` (ai) | window + `keyBy` | A `TenantBindingProcessFunction` is inserted **after** `.filter(hasDistrictAndValue)` and **before** `.keyBy(...)`. The window/aggregate logic is untouched — **G1 window-batching preserved** (still ~50 calls/min vs ~600K). The composite key still carries `tenantId`. |
| `WelfordKeyedProcessFunction` (structural) | `KeyedProcessFunction` | `processElement` delegates to a `TenantKeyedProcessFunctionDelegate` field; the Welford math, the 4σ rule, the absolute floor, and the cold-start (n≥1000) suppression are **unchanged**. **BR-010 preserved** — operator review, no auto-evacuate. |
| `StructuralPatternProcessFunction` (structural) | `PatternProcessFunction` (CEP) | The alert is built under a bound tenant (from the latest matched reading) via the delegate. CEP pattern (3 consecutive spikes / 10 s) **unchanged**. |
| `FloodPatternProcessFunction` (flood) | `PatternProcessFunction` (CEP) | Same pattern — same one-line-scope fix, applied to keep the ArchTest rule global (no carve-out). |
| `CorrelationPatternProcessFunction` (correlation) | `PatternProcessFunction` (CEP) | Same pattern — binds tenant from the first alert in the matched window. The correlation scoring (`evaluateWindow`) is **unchanged**. |

> **Scope note.** T05 named the 3 primary sensor-stream jobs (`TenantIdValidator`, `DistrictAggregationJob`,
> `VibrationAnomalyJob`). The ArchTest rule is global by design (no carve-out), so two additional
> `PatternProcessFunction` operators (`FloodPatternProcessFunction`, `CorrelationPatternProcessFunction`)
> were brought under the same delegate to satisfy it — leaving them out would have meant writing an
> exception into the rule, which is the exact hole ADR-047 exists to close.

**Enforcement (the teeth).** `FlinkTenantArchTest` enforces three rules that fail the build:

1. **Every** `ProcessFunction` / `KeyedProcessFunction` / `PatternProcessFunction` subclass in
   `com.uip.flink..` MUST depend on `TenantKeyedProcessFunctionDelegate` or `TenantContext`.
2. `TenantContext` / delegate / binding fn MUST live in `com.uip.flink.common.tenant`.
3. No class outside the tenant package may call `TenantContext.set` / `clear` (only the delegate mutates it).

These are **NOT** `allowEmptyShould` — there are in-tree operators today, so the rules have real
enforcement power. The backend copy (`com.uip.backend.tenant.flink.*` + `TenantIsolationArchTest`)
remains as a forward guard for any future in-backend operator; the two copies are kept in sync
by convention.

**Verification.** `mvn test` in `flink-jobs/` runs the ArchTest + delegate/binding/context unit
tests + the existing job tests (Welford `shouldEmit`, district aggregation, tenant validator).
All green.

---

## 2. Decision

### 2.1 Primary mechanism — CH RowPolicy

```sql
CREATE ROW POLICY tenant_iso ON analytics.esg_readings
  FOR SELECT
  USING tenant_id = currentSetting('tenant_id')
  AS RESTRICTIVE;
```

- Policy `RESTRICTIVE` — kết hợp AND với bất kỳ filter nào, **không thể bị bypass** bởi query thiếu `WHERE tenant_id`
- `currentSetting('tenant_id')` — ClickHouse custom session setting, set per-connection trước mỗi query
- Filter pushdown → **zero performance overhead** (CH optimizer apply ở storage scan)

### 2.2 Enforcement point — analytics-service JDBC layer (CRITICAL CORRECTION)

> ⚠️ **Sai assumption đã được sửa.** Draft gốc trong `sa-mvp5-conflict-resolution.md` §1.2 nói RowPolicy cần backend `ClickHouseGrpcAnalyticsAdapter` set `session.tenant_id` qua **gRPC metadata**. **Đây SAI.**

**Kiến trúc thực tế (verified):**

```
backend (ESG module)
  └─ ClickHouseGrpcAnalyticsAdapter       ← RPC passthrough ONLY — NO ClickHouse connection
       └─ gRPC stub → analytics-service
            └─ EnergyAnalyticsGrpcService  ← receives EnergyAggRequest(tenantId, ...)
                 └─ EnergyAggregateService
                      └─ ClickHouseEnergyRepository  ← THIS queries ClickHouse via JDBC
                           WHERE tenant_id = ?       ← L1 filter (existing)
                           + RowPolicy (L2)          ← this ADR adds
```

**Backend gRPC adapter không bao giờ chạm ClickHouse.** ClickHouse session context chỉ có ý nghĩa **bên trong analytics-service** (JDBC connection pool).

### 2.3 Defense-in-depth — RowPolicy là Layer 2

| Layer | Mechanism | Location | Purpose |
|---|---|---|---|
| **L1** | `WHERE tenant_id = ?` SQL param | `ClickHouseEnergyRepository:32,61,91` | Primary filter (đã có) |
| **L2** | CH RowPolicy `USING tenant_id = currentSetting(...)` | analytics-service DB | Defense-in-depth — chặn nếu L1 quên filter |
| **L3** | ArchUnit rule ban raw `KeyedProcessFunction` | `flink-jobs` module (`FlinkTenantArchTest`) + backend forward guard (`TenantIsolationArchTest`) | Force tenant-aware Flink operators (see §1.4 — in-tree enforcement, not vacuous) |

**L1 + L2 cùng tồn tại.** RowPolicy không thay thế SQL filter — nó là safety net. Một query nào đó vô tình quên `WHERE tenant_id` → RowPolicy vẫn chặn, query trả 0 rows thay vì leak.

### 2.4 Fallback — View-per-Tenant

`CREATE VIEW v_tenant_42 AS SELECT * FROM esg_readings WHERE tenant_id=42` + revoke base table.

**Chỉ dùng nếu** Spike S1 (redefined, §3) chứng minh HikariCP + clickhouse-jdbc **không** propagate session setting đúng khi borrow/return connection. Cost +2 SP.

---

## 3. Spike S1 — REDEFINED (critical correction)

### 3.1 Old (wrong) assumption

> Verify `ClickHouseGrpcAnalyticsAdapter` set được `session.tenant_id` qua gRPC metadata.

**Wrong vì:** gRPC adapter không query ClickHouse. Architecture fact đã verified (§2.2).

### 3.2 Corrected Spike S1 scope

> Verify **HikariCP connection pool + clickhouse-jdbc driver** propagate `SET tenant_id = ?` session setting đúng khi:
> 1. Connection được **borrow** từ pool → setting apply cho connection đó
> 2. Connection **return** về pool → setting được **reset/clear** (try-finally) để tránh tenant bleed khi connection tái sử dụng cho request khác

**Đây là JDBC test, không phải gRPC test.** Ước tính 1 ngày.

### 3.3 Spike S1 acceptance criteria

```java
// Pseudocode — verify pool isolation
try (Connection conn = dataSource.getConnection()) {
    try (Statement stmt = conn.createStatement()) {
        stmt.execute("SET tenant_id = 'tenant_A'");
    }
    // Query as tenant_A → expect tenant_A rows only
    List<Row> rowsA = repo.query(conn);
    assert rowsA.allMatch(r -> r.tenantId.equals("tenant_A"));
}
// Connection returns to pool — setting MUST be cleared

try (Connection conn = dataSource.getConnection()) {  // may be SAME physical connection
    // Query WITHOUT setting tenant_id → RowPolicy must DENY all rows
    // OR: setting from previous request must NOT bleed
    List<Row> rows = repo.queryWithoutSetting(conn);
    assert rows.isEmpty();  // fail-closed
}
```

### 3.4 If Spike S1 fails

Switch sang **View-per-Tenant fallback** (+2 SP, cùng sprint M5-1):
- `CREATE VIEW v_tenant_{id}` per tenant
- Revoke SELECT on base table
- `RowPolicyEngine` resolves view name từ tenant_id

---

## 4. Option comparison — RowPolicy (primary) vs View-per-Tenant (fallback)

| Tiêu chí | **CH RowPolicy** (PRIMARY ✅) | **View-per-Tenant** (FALLBACK) |
|---|---|---|
| Cơ chế | `CREATE ROW POLICY ... USING tenant_id = currentSetting(...)` + per-connection SET | `CREATE VIEW v_tenant_42 AS SELECT * WHERE tenant_id=42` + revoke base |
| Độ phức tạp migration | **Thấp** — 1 DDL + 1 RowPolicyEngine wrapper | Cao — N views + grant management + adapter biết view name pattern |
| Blast radius nếu bug | 1 policy sai = leak cho **mọi tenant** (fuzz test bắt — Task #5) | 1 view sai = leak **1 tenant** (cô lập hơn) |
| Tương thích JDBC pool | **Cần Spike S1 verify** — HikariCP session setting isolation (redefined §3) | OK — chỉ đổi table name |
| ~~Tương thích gRPC adapter~~ | ~~N/A~~ — gRPC adapter không query CH (corrected) | ~~N/A~~ |
| Thêm tenant mới | **Zero-touch** (policy generic) | Phải CREATE VIEW + grant (runbook ops) |
| Performance | **Không overhead** (filter pushdown) | View materialization hit memory ở 50+ views |
| **Verdict** | **SHIP M5-1** | FALLBACK — chỉ nếu Spike S1 fail |

---

## 5. Consequences

### 5.1 Positive

- ✅ **Defense-in-depth** — L1 SQL filter + L2 RowPolicy, single bug không leak
- ✅ **Zero-touch tenant onboarding** — policy generic, thêm tenant không cần DDL
- ✅ **Zero performance overhead** — CH filter pushdown at scan time
- ✅ **Compliance-ready** — storage-layer enforcement pass ISO 27001 / SOC 2 audit
- ✅ **Correct architecture for 50-building scale** — PO requirement satisfied

### 5.2 Trade-offs / risks

- ⚠️ **Blast radius** — 1 sai policy = leak mọi tenant. **Mitigation**: fuzz test Task #5 (2-3 tenant correctness + synthetic 50-tenant)
- ⚠️ **Connection pool session bleed** — nếu RowPolicyEngine quên reset setting khi return connection → tenant A bleed sang tenant B. **Mitigation**: try-finally wrapper + Spike S1 verify + fuzz test
- ⚠️ **Fail-closed strictness** — tenant_id null/blank → throw TenantContextException, không query. Có thể break existing flow nếu caller quên set context. **Mitigation**: ArchUnit rule + integration test
- ⚠️ **ClickHouse single-node M5-1** — HA deferred (ADR-036). RowPolicy trên single-node OK cho PoC; HA migration cần verify policy replicate

### 5.3 Migration impact

| Component | Change | Task |
|---|---|---|
| analytics-service DB | Migration V32 — CREATE ROW POLICY + user grant | #3 |
| analytics-service code | `RowPolicyEngine` wrapper (SET/reset tenant_id per query) | #3 |
| backend monolith | **NO CHANGE** — gRPC adapter unchanged (RPC passthrough) | — |
| shared-libraries (Flink) | `TenantKeyedProcessFunction` base class + ArchUnit rule | #4 |
| Compose HA test env | ClickHouse policy user + grant config | #6 |
| Fuzz test | 2-3 tenant + synthetic 50-tenant | #5 |

---

## 6. Implementation tasks

| Task | Assignee | SP | Blocks | Blocked by |
|---|---|---|---|---|
| #1 Author this ADR | SA | 3 | #3, #4, #6 | — |
| #2 Update `sa-mvp5-conflict-resolution.md` §1.2 (fix gRPC assumption) | SA | 1 | — | — |
| #3 Migration V32 + `RowPolicyEngine` (analytics-service) | Backend-1 | 3 | #4, #5 | #1 |
| #4 Flink `TenantKeyedProcessFunction` + ArchUnit rule | Backend-2 | 3 | #5 | #1, #3 |
| #5 Tenant isolation fuzz test (Gate M5-G2) | QA + Backend-1 | 3 | M5-G2 gate | #3, #4, #6 |
| #6 Compose HA test env + ClickHouse policy enablement | DevOps | 3 | #5 | #1 |

**Critical path (tenant correctness — single path):**
```
#1 ADR ──┬─→ #3 RowPolicyEngine ──┬─→ #5 Fuzz test ──→ M5-G2 gate
          ├─→ #6 Compose HA ───────┘
          └─→ #4 Flink tenant fn ──┘
```

---

## 7. Cross-references

- **ADR-012** — gRPC analytics port (backend → analytics-service contract) — explains why gRPC adapter is RPC-only
- **ADR-033** — Tenant hierarchy (multi-tenant data model, tenant_id propagation)
- **ADR-036** — ClickHouse HA ReplicatedMergeTree (deferred — RowPolicy single-node M5-1)
- `docs/mvp5/brainstorm/sa-mvp5-conflict-resolution.md` §1.2 — original draft (assumption corrected here)
- `docs/mvp5/plans/mvp5-sprint-plan.md` — M5-1-T01 (this ADR), T04 (migration), T05 (Flink fn)

---

## 8. Open questions (none blocking)

1. ClickHouse version target M5-1 — confirm 23.8+ supports `currentSetting()` in RowPolicy USING clause (✅ verified in CH docs, 22.3+)
2. Whether to extend RowPolicy to `sensor_readings` hypertable (TimescaleDB has RLS already) — defer, only `esg_readings` for M5-1
3. Observability — should RowPolicy denials emit metric? (recommend: counter `ch.row_policy.denied{tenant,table}`) — defer to observability task if budget allows

---

**End ADR-047**
