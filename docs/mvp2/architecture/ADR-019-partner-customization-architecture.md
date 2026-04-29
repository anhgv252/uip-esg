# ADR-019: Partner Customization Architecture — Extension Without Branching

**Date**: 2026-04-28  
**Status**: Accepted  
**Deciders**: Solution Architect, Engineering Lead  
**Related**: ADR-011 (Monorepo + Module Extraction), ADR-010 (Multi-Tenancy)

---

## Context

UIP platform phục vụ nhiều loại khách hàng khác nhau:

- **Đối tác A (Energy-Optimizer)**: Tập trung tiết kiệm năng lượng — dashboard năng lượng theo giờ, alert khi consumption vượt budget, báo cáo ROI năng lượng tái tạo.
- **Đối tác B (Citizen-First)**: Tập trung dịch vụ công dân — complaint tracking ưu tiên, citizen notification đa kênh (Zalo, SMS), citizen engagement score.
- **Đối tác C (Traffic Authority)**: Tập trung giao thông thông minh — signal optimization loop, incident auto-escalation, VIP vehicle priority routing.

Mỗi đối tác cần:
1. **Feature focus khác nhau**: module nào nổi bật, module nào ẩn
2. **Branding/white-label**: logo, tên sản phẩm, color scheme riêng
3. **Business logic nhỏ khác nhau**: threshold khác, workflow khác, integration bên thứ ba khác
4. **Data isolation**: tenant của mỗi đối tác phải tách biệt (đã giải quyết ở ADR-010)

### Vấn đề cần giải quyết

Không thể để mỗi đối tác có branch riêng vì:
- Feature update ở `main` không tự flow sang partner branches → divergence
- Bug fix phải cherry-pick thủ công → human error
- N partners = N branches = N CI pipelines = O(N) maintenance cost
- Code review phân tán, khó enforce quality gate

Nhưng cũng không thể để tất cả trong `main` mà không có isolation:
- `if (partner == "energy-optimizer") { ... }` trong business logic → spaghetti code
- Partner A nhìn thấy config/feature của Partner B → security issue
- Build 1 binary chứa tất cả partner code → binary bloat, không audit được

### Constraint

- Monorepo (theo ADR-011) — phải tương thích
- Multi-tenancy (theo ADR-010) — tenant isolation đã có ở DB layer
- Không tạo per-partner git branches

---

## Decision

**3-Layer Customization Model** — mỗi layer giải quyết một mức độ khác nhau:

```
Layer 3: Extension Module     ← code mới, Spring @ConditionalOnProperty, riêng partner
Layer 2: Partner Profile      ← application-partner-xyz.yml, override config
Layer 1: Tenant Config        ← DB table, zero code, runtime toggle per customer
```

**Nguyên tắc cốt lõi**: *Never branch per partner. Extend per partner.*

Core business logic KHÔNG chứa `if (partner == "xyz")`. Partner customization là additive (thêm), không phải invasive (sửa core).

---

## Architecture Detail

### Layer 1 — Tenant Config (DB-level, zero code change)

Dùng cho: toggle feature on/off, thay đổi threshold, metadata display, branding.

```sql
-- Bảng config per tenant (đã có từ ADR-010 multi-tenancy)
CREATE TABLE tenants.tenant_config (
    tenant_id   TEXT NOT NULL,
    config_key  TEXT NOT NULL,
    config_value TEXT NOT NULL,
    PRIMARY KEY (tenant_id, config_key)
);

-- Ví dụ data
INSERT INTO tenants.tenant_config VALUES
('energy-partner-a', 'features.energy-dashboard.enabled',   'true'),
('energy-partner-a', 'features.citizen-portal.enabled',     'false'),
('energy-partner-a', 'alert.energy.budget-threshold-kwh',   '5000'),
('citizen-partner-b','features.citizen-portal.enabled',     'true'),
('citizen-partner-b','features.energy-dashboard.enabled',   'false'),
('citizen-partner-b','notification.channels',               'zalo,sms,email');
```

**Dùng khi nào**: Mọi thay đổi có thể model bằng key-value — không cần deploy lại.

**Không dùng khi nào**: Cần business logic khác nhau (→ Layer 3), cần Spring bean khác nhau (→ Layer 3).

---

### Layer 2 — Partner Profile (Config override per deployment)

Dùng cho: infrastructure config, external service endpoints, Spring properties override không muốn đặt trong DB.

```
uip-platform/
└── infra/
    └── partner-profiles/
        ├── application-partner-energy-a.yml    ← energy optimizer profile
        ├── application-partner-citizen-b.yml   ← citizen-first profile
        └── application-partner-traffic-c.yml   ← traffic authority profile
```

```yaml
# application-partner-energy-a.yml
uip:
  partner:
    id: energy-partner-a
    name: "EcoCity Energy Platform"
    extensions:
      energy-optimizer: true
      citizen-portal: false

  # Override energy module config
  energy:
    real-time-interval-seconds: 10    # default là 60
    renewable-tracking: true
    export-format: iso-50001          # partner yêu cầu ISO 50001

  # Override alert thresholds
  alert:
    energy-budget-warning-percent: 80
    energy-budget-critical-percent: 95

spring:
  profiles:
    active: production, partner-energy-a
```

Activate profile khi deploy:
```yaml
# Helm values-partner-energy-a.yaml
env:
  SPRING_PROFILES_ACTIVE: "production,partner-energy-a"
  UIP_PARTNER_ID: "energy-partner-a"
```

---

### Layer 3 — Extension Module (Code, monorepo, Spring auto-config)

Dùng cho: business logic khác nhau không thể config-driven, integration với hệ thống bên thứ 3 riêng của partner, custom workflow, custom report format.

#### Directory structure trong monorepo

```
uip-platform/
├── modules/                          ← core modules (ADR-011)
│   ├── energy-module/
│   ├── citizen-module/
│   └── ...
├── partner-extensions/               ← partner-specific code, KHÔNG chứa core logic
│   ├── partner-energy-optimizer/
│   │   ├── pom.xml
│   │   ├── src/main/java/
│   │   │   └── uip/partners/energy/
│   │   │       ├── EnergyOptimizerAutoConfiguration.java
│   │   │       ├── Iso50001ReportExporter.java      ← ISO 50001 format riêng
│   │   │       ├── EnergyBudgetAlertExtension.java  ← alert logic bổ sung
│   │   │       └── RenewableRoiCalculator.java
│   │   └── src/main/resources/
│   │       └── META-INF/spring/
│   │           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   │
│   ├── partner-citizen-first/
│   │   ├── pom.xml
│   │   └── src/main/java/uip/partners/citizen/
│   │       ├── CitizenFirstAutoConfiguration.java
│   │       ├── ZaloNotificationAdapter.java        ← Zalo API riêng partner B
│   │       ├── CitizenEngagementScoreService.java
│   │       └── ComplaintPriorityExtension.java
│   │
│   └── partner-traffic-authority/
│       └── ...
│
└── applications/
    ├── monolith/
    │   └── pom.xml  ← include partner extensions theo profile/flag
    └── ...
```

#### Extension Point Interfaces (defined in core)

Core module expose interface mà partner có thể implement:

```java
// Trong energy-module — core defines the port, partner implements
package uip.energy.extension;

/**
 * Extension point cho custom energy report export.
 * Partner implement interface này để inject format riêng.
 */
public interface EnergyReportExportPort {
    String getFormatId();
    byte[] export(EnergyReportData data);
}

// Trong citizen-module — notification channel extension
public interface NotificationChannelPort {
    String getChannelId();           // "zalo", "sms", "push"
    void send(Notification notification, String recipientId);
    boolean isAvailable();
}
```

#### Partner implements extension point

```java
// partner-energy-optimizer module
@Component
@ConditionalOnProperty(
    name = "uip.partner.extensions.energy-optimizer",
    havingValue = "true"
)
public class Iso50001ReportExporter implements EnergyReportExportPort {

    @Override
    public String getFormatId() { return "iso-50001"; }

    @Override
    public byte[] export(EnergyReportData data) {
        // ISO 50001 specific format logic — chỉ load khi partner enabled
        return Iso50001Serializer.serialize(data);
    }
}
```

```java
// partner-energy-optimizer: AutoConfiguration
@AutoConfiguration
@ConditionalOnProperty(
    name = "uip.partner.extensions.energy-optimizer",
    havingValue = "true"
)
public class EnergyOptimizerAutoConfiguration {

    @Bean
    public Iso50001ReportExporter iso50001ReportExporter() {
        return new Iso50001ReportExporter();
    }

    @Bean
    public EnergyBudgetAlertExtension energyBudgetAlertExtension(
            AlertConfigProperties config) {
        return new EnergyBudgetAlertExtension(config);
    }
}
```

#### Core gọi extension thông qua interface — không biết partner

```java
// energy-module core service — KHÔNG import partner code
@Service
public class EnergyReportService {

    // Spring inject TẤT CẢ implementations — chỉ partner enabled mới load
    private final List<EnergyReportExportPort> exporters;

    public byte[] exportReport(String reportId, String formatId) {
        return exporters.stream()
            .filter(e -> e.getFormatId().equals(formatId))
            .findFirst()
            .orElseThrow(() -> new UnsupportedFormatException(formatId))
            .export(loadReportData(reportId));
    }
}
```

#### Partner inclusion trong build

```xml
<!-- applications/monolith/pom.xml -->
<dependencies>
    <!-- Core modules — luôn include -->
    <dependency><groupId>uip</groupId><artifactId>energy-module</artifactId></dependency>
    <dependency><groupId>uip</groupId><artifactId>citizen-module</artifactId></dependency>

    <!-- Partner extensions — include theo Maven profile hoặc tất cả -->
    <!-- Option A: Build artifact riêng cho từng partner (CI chọn profile) -->
    <dependency>
        <groupId>uip.partners</groupId>
        <artifactId>partner-energy-optimizer</artifactId>
        <optional>true</optional>  <!-- Spring auto-config SPI điều khiển activation -->
    </dependency>
    <dependency>
        <groupId>uip.partners</groupId>
        <artifactId>partner-citizen-first</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

> **Option B (nếu binary size quan trọng)**: Maven profile `partner-energy-a` chỉ include module đó. Mỗi partner có 1 artifact riêng build từ cùng commit.
>
> **Option A (đơn giản hơn, đề xuất cho T1-T2)**: Build 1 binary chứa tất cả partner code; Spring `@ConditionalOnProperty` điều khiển activation. Chỉ code của partner enabled mới instantiate beans.

---

## Quy tắc thiết kế (Design Rules)

### KHÔNG làm — Anti-patterns

```java
// ❌ WRONG: Partner check trong core business logic
public class AlertService {
    public void processAlert(Alert alert) {
        if (tenantId.startsWith("energy-")) {
            // energy-specific logic
        } else if (tenantId.equals("citizen-b")) {
            // citizen-specific logic
        }
    }
}

// ❌ WRONG: Import partner module từ core
import uip.partners.energy.Iso50001ReportExporter;  // hard coupling

// ❌ WRONG: Per-partner git branches
git checkout -b partner/energy-optimizer
git checkout -b partner/citizen-first

// ❌ WRONG: Partner config hardcode trong application.yml core
uip.energy.iso50001.enabled: true  // ai đọc cũng biết partner nào
```

### NÊN làm — Correct patterns

```java
// ✅ CORRECT: Core dùng extension point interface
@Service
public class AlertService {
    private final List<AlertExtensionPort> extensions;  // inject tất cả implementations

    public void processAlert(Alert alert) {
        AlertContext ctx = buildContext(alert);
        extensions.forEach(ext -> ext.onAlertProcessed(ctx));  // partner tự extend
    }
}

// ✅ CORRECT: Partner code dùng @ConditionalOnProperty
@Bean
@ConditionalOnProperty("uip.partner.extensions.energy-optimizer", havingValue = "true")
public EnergyOptimizerAlertExtension energyAlertExtension() { ... }
```

### Ranh giới Extension Module

| Loại code | Đặt ở đâu |
|-----------|-----------|
| Domain logic chung (alert, sensor ingestion) | `modules/` — core |
| Partner-specific report format | `partner-extensions/partner-xyz/` |
| Partner-specific external integration (Zalo, SAP) | `partner-extensions/partner-xyz/` |
| Partner-specific UI theme/branding | `applications/*/src/partners/xyz/` (frontend) |
| Feature toggle per customer | `tenants.tenant_config` table (Layer 1) |
| Infrastructure/endpoint override | `partner-profiles/application-partner-xyz.yml` (Layer 2) |
| `if (partner == "xyz")` trong core | **KHÔNG BAO GIỜ** |

---

## Deployment Model

```
Git tag: v2.3.0 (single source of truth)
          │
          ├── CI build: monolith-v2.3.0.jar   (all partner-extensions included)
          │                    │
          │         Spring auto-config SPI: chỉ load bean khi property = true
          │
          ├── Helm deploy Partner A:
          │       values-partner-energy-a.yaml
          │       SPRING_PROFILES_ACTIVE: production,partner-energy-a
          │       → uip.partner.extensions.energy-optimizer: true
          │       → uip.partner.extensions.citizen-first: false
          │
          └── Helm deploy Partner B:
                  values-partner-citizen-b.yaml
                  SPRING_PROFILES_ACTIVE: production,partner-citizen-b
                  → uip.partner.extensions.citizen-first: true
                  → uip.partner.extensions.energy-optimizer: false
```

### Frontend partner customization

#### Cấu trúc file (bổ sung vào codebase hiện tại)

```
frontend/src/
├── theme/
│   ├── index.ts                        ← sửa: export createPartnerTheme() factory
│   ├── baseTheme.ts                    ← tách base config
│   └── partnerThemes/
│       ├── default.theme.ts
│       ├── energy-optimizer.theme.ts   ← green primary (#2E7D32)
│       └── citizen-first.theme.ts      ← orange primary (#E65100)
├── contexts/
│   └── TenantConfigContext.tsx         ← TẠO MỚI: Provider + useFeatureFlags
├── config/
│   └── partner-features.ts            ← TẠO MỚI: feature flag → nav path mapping
└── hooks/
    ├── useScope.ts                     ← TẠO MỚI: scope-gated action check
    └── useTenantConfig.ts              ← TẠO MỚI: convenience hook
```

#### Luồng runtime

```
User đăng nhập
    → JWT parse: tenant_id, scopes, allowed_buildings (AuthContext)
    → GET /api/v1/tenant/config (TenantConfigContext)
    → createPartnerTheme(config.branding) (ThemeProvider)
    → AppShell filter NAV_ITEMS theo featureFlag (isFeatureEnabled)
    → Scope-gated buttons: useScope('esg:write')
    → Building selector filter: user.allowedBuildings
```

#### Contract: GET /api/v1/tenant/config

Backend endpoint (task FE-02 trong detail plan):
```json
{
  "tenantId": "energy-partner-a",
  "features": {
    "city-ops":           { "enabled": true },
    "environment-module": { "enabled": true },
    "esg-module":         { "enabled": true },
    "traffic-module":     { "enabled": false },
    "citizen-portal":     { "enabled": false },
    "ai-workflow":        { "enabled": true }
  },
  "branding": {
    "partnerName": "EcoCity Energy Platform",
    "primaryColor": "#2E7D32",
    "logoUrl": "https://cdn.partner-a.com/logo.png"
  }
}
```

Nguồn: đọc từ `tenants.tenant_config` table (Layer 1) theo `tenant_id` từ JWT.

#### Quy tắc fail-open cho T1 single-tenant

T1 deployment không có config trong DB. Frontend phải fail-open:
- `isFeatureEnabled('any-flag')` → `true` khi config chưa load hoặc flag không tồn tại
- `branding.primaryColor` absent → dùng default `#1976D2`
- Không có `scopes` trong JWT → `useScope()` trả `false` (secure default, không fail-open vì đây là action gate)

#### AppShell — thêm `featureFlag` vào NavItem interface

```typescript
// Hiện tại: src/components/AppShell.tsx
interface NavItem {
  label: string; path: string; icon: React.ReactNode
  roles?: string[]   // chỉ có role check
}

// Phải sửa thành:
interface NavItem {
  label: string; path: string; icon: React.ReactNode
  roles?: string[]
  featureFlag?: string   // key trong tenant_config.features
}
```

Feature flag → path mapping được centralize tại `src/config/partner-features.ts` để tránh inconsistency giữa nav items và feature flags.

---

## Khi nào dùng layer nào

| Yêu cầu đối tác | Layer | Ví dụ |
|----------------|-------|-------|
| Bật/tắt module | Layer 1 | citizen-portal: false |
| Thay đổi threshold/limit | Layer 1 | energy-budget-kwh: 5000 |
| Branding (màu sắc, logo) | Layer 1 + FE theme | primary-color: #2E7D32 |
| Kết nối hệ thống bên ngoài riêng | Layer 3 | ZaloNotificationAdapter |
| Report format đặc biệt (ISO, GRI) | Layer 3 | Iso50001ReportExporter |
| Notification channel mới | Layer 3 | SmsViettelAdapter |
| UI widget custom | Layer 2 + FE component | EnergyBudgetGauge |
| Alert logic bổ sung riêng | Layer 3 | EnergyBudgetAlertExtension |
| Infrastructure endpoint riêng | Layer 2 | smtp.partner-a.internal |

---

## Consequences

### Positive
- Không có per-partner branches → tất cả partner nhận bug fix và feature update từ `main` ngay lập tức
- Core logic sạch — không có `if (partner == "xyz")` ở bất kỳ đâu
- Partner extension code tách biệt → dễ review, dễ audit, dễ onboard partner dev
- Spring `@ConditionalOnProperty` + auto-config SPI: industry-standard pattern (Spring Boot Starters dùng y chang)
- Feature toggle (Layer 1) không cần deploy lại — ops team có thể enable/disable runtime

### Trade-offs
- Cần thiết kế extension point interfaces kỹ từ đầu — nếu core không expose đúng port, partner phải hack
- Layer 3 code trong monorepo → CI build time tăng nhẹ khi số partner nhiều (mitigate: Maven module caching)
- Cần convention rõ ràng để tránh `partner-extensions/` chứa code lẽ ra thuộc core (creep risk)

### Decision Criteria để chọn Layer

```
Yêu cầu có thể model bằng key-value?
  └─ Có → Layer 1 (DB config)

Yêu cầu là infrastructure/endpoint override?
  └─ Có → Layer 2 (YAML profile)

Yêu cầu cần code mới (Java class, external integration)?
  └─ Có → Layer 3 (Extension Module)

Code mới có liên quan đến domain chung?
  └─ Có → thuộc core module, không phải partner-extensions
  └─ Không → partner-extensions/partner-xyz/
```

---

## Implementation Checklist

### Cho mỗi partner mới

- [ ] Tạo `partner-extensions/partner-{id}/` module trong monorepo
- [ ] Tạo `infra/partner-profiles/application-partner-{id}.yml`
- [ ] Tạo `infra/helm/values-partner-{id}.yaml`
- [ ] Định nghĩa extension point interfaces cần thiết trong core module (nếu chưa có)
- [ ] Insert tenant config vào `tenants.tenant_config` table
- [ ] Tạo CI pipeline target: `build-partner-{id}` (nếu dùng separate artifact)
- [ ] Viết unit test cho từng `@ConditionalOnProperty` — test cả enabled và disabled case
- [ ] Review: không có `if (partner == "{id}")` trong bất kỳ core module nào

### Cho mỗi Extension Point Interface mới trong core

- [ ] Đặt trong `{module}/src/main/java/uip/{module}/extension/` package
- [ ] Document: mô tả contract, khi nào gọi, data trong context
- [ ] Provide no-op default implementation → core không fail khi không có partner extension
- [ ] Inject dưới dạng `List<ExtensionPort>` — Spring collect tất cả beans implement interface

---

## Related ADRs

| ADR | Liên quan |
|-----|----------|
| ADR-010 | Multi-tenancy (tenant isolation ở DB) — Layer 1 dùng `tenant_id` từ ADR-010 |
| ADR-011 | Monorepo + capability flags — partner-extensions cùng cấu trúc với modules/ |

