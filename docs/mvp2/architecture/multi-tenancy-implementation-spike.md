# Multi-Tenancy Implementation Spike — Sprint 2

**Type:** Architecture Spike
**Author:** Solution Architect
**Date:** 2026-05-03
**Status:** Proposed
**Scope:** Sprint MVP2-2 (12–23 May 2026)
**ADRs tham chiếu:** ADR-010, ADR-020, ADR-021, ADR-023

---

## 1. Mục tiêu Spike

Tài liệu này là **implementation blueprint** cho team khi triển khai Multi-Tenancy trong Sprint 2. Mỗi section cung cấp:

- **What to build** — component/delta chính xác cần tạo
- **Where to put it** — file path và package
- **How it connects** — dependency chain với components khác
- **Verification** — test để confirm hoạt động đúng

---

## 2. Current State Assessment

### 2.1 Đã hoàn thành (Sprint 1)

| Item | Status | Chi tiết |
|------|--------|---------|
| V14 migration | Done | `tenant_id` + `location_path` (LTREE) đã add vào tất cả domain tables |
| ADR-010 | Accepted | Multi-tenant isolation strategy — RLS + SET LOCAL |
| ADR-020 | Proposed | Non-HTTP tenant propagation — Kafka, Flink, @Async |
| ADR-021 | Accepted | T1 luôn chạy TenantContextFilter với fallback `'default'` |
| ADR-023 | Accepted | RLS zero-downtime migration strategy |
| Audit log table | Done | V15 — `public.audit_log` có `tenant_id` column |

### 2.2 Chưa có (Cần implement Sprint 2)

| Item | Priority | Blocker cho |
|------|----------|-------------|
| `TenantContext.java` (ThreadLocal) | P0 | Mọi BE task Sprint 2 |
| `TenantContextFilter.java` (Servlet filter) | P0 | Mọi API request |
| `HikariTenantListener.java` (SET LOCAL) | P0 | RLS hoạt động đúng |
| JPA entities thêm `tenantId`/`locationPath` | P0 | Repository filter đúng |
| `Tenant.java` + `TenantRepository` | P0 | Tenant management |
| V16 migration: RLS policies | P0 | Data isolation |
| JWT claims thêm `tenant_id`/`scopes`/`allowed_buildings` | P0 | Frontend tasks |
| `TenantAwareKafkaListener` base class | P1 | Kafka consumers |
| `TenantContextTaskDecorator` | P1 | @Async methods |

### 2.3 Entity Gap Analysis

JPA entities hiện tại **chưa có** `tenantId` field, mặc dù DB đã có column:

| Entity | DB table | Has `tenant_id` in DB? | Has `tenantId` in JPA? | Action needed |
|--------|----------|------------------------|------------------------|---------------|
| `Sensor` | `environment.sensors` | YES | NO | Add field |
| `SensorReading` | `environment.sensor_readings` | YES | NO | Add field |
| `Building` | `citizens.buildings` | YES | NO | Add field |
| `Household` | `citizens.households` | YES | NO | Add field |
| `CitizenAccount` | `citizens.citizen_accounts` | YES | NO | Add field |
| `EsgMetric` | `esg.clean_metrics` | YES | NO | Add field |
| `EsgReport` | `esg.reports` | YES | NO | Add field |
| `AlertRule` | `alerts.alert_rules` | YES | NO | Add field |
| `AlertEvent` | `alerts.alert_events` | YES | NO | Add field |
| `TrafficCount` | `traffic.traffic_counts` | YES | NO | Add field |
| `TrafficIncident` | `traffic.traffic_incidents` | YES | NO | Add field |
| `ErrorRecord` | `error_mgmt.error_records` | YES | NO | Add field |
| `AppUser` | `public.app_users` | NO | NO | Add DB column + JPA field |
| `AuditLog` | `public.audit_log` | YES (VARCHAR) | NO | Add field |

---

## 3. Architecture Overview

### 3.1 Request Flow — T2 Multi-Tenant

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           HTTP Request                                       │
│  Authorization: Bearer <JWT>                                                 │
└──────────────┬──────────────────────────────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────┐
│  JwtAuthenticationFilter     │  Extract JWT → validate → set SecurityContext
│  (existing)                  │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│  TenantContextFilter (NEW)   │  JWT claim "tenant_id" → TenantContext.set()
│                              │  Fallback: tenant_id = 'default' (T1)
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│  Controller → Service        │  Business logic runs
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│  @Transactional method       │  Transaction begins
│  + HikariTenantListener      │  → SET LOCAL app.tenant_id = 'hcm'
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│  PostgreSQL RLS              │  USING (tenant_id = current_setting('app.tenant_id'))
│  Filter rows automatically   │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│  Transaction commits         │  SET LOCAL resets → connection clean
│  HikariCP returns connection │
└──────────────────────────────┘
```

### 3.2 Component Map

```
backend/src/main/java/com/uip/backend/
├── auth/
│   ├── config/
│   │   ├── JwtAuthenticationFilter.java    ← SỬA: extract tenant_id claim
│   │   └── SecurityConfig.java             ← SỬA: add TenantContextFilter
│   ├── domain/
│   │   └── AppUser.java                    ← SỬA: add tenantId field
│   └── service/
│       └── JwtTokenProvider.java           ← SỬA: add tenant_id to JWT claims
│
├── tenant/                                 ← PACKAGE MỚI
│   ├── domain/
│   │   └── Tenant.java                     ← Entity: tenant registry
│   ├── repository/
│   │   └── TenantRepository.java           ← JPA repository
│   ├── context/
│   │   └── TenantContext.java              ← ThreadLocal holder
│   ├── filter/
│   │   └── TenantContextFilter.java        ← Servlet filter: JWT → ThreadLocal
│   ├── hibernate/
│   │   └── HikariTenantListener.java       ← SET LOCAL on transaction begin
│   └── service/
│       └── TenantSetupService.java         ← Validate LTREE, onboard tenant
│
├── common/
│   ├── config/
│   │   └── AsyncConfig.java                ← SỬA: add TenantContextTaskDecorator
│   └── kafka/
│       └── TenantAwareKafkaListener.java   ← Base class: extract tenant_id
│
├── environment/domain/
│   └── Sensor.java                         ← SỬA: add tenantId, locationPath
├── environment/repository/
│   └── SensorRepository.java               ← SỬA: add tenantId filter
│
├── esg/domain/
│   ├── EsgMetric.java                      ← SỬA: add tenantId
│   └── EsgReport.java                      ← SỬA: add tenantId
│
├── traffic/domain/
│   ├── TrafficCount.java                    ← SỬA: add tenantId
│   └── TrafficIncident.java                 ← SỬA: add tenantId
│
├── alert/domain/
│   ├── AlertRule.java                       ← SỬA: add tenantId
│   └── AlertEvent.java                      ← SỬA: add tenantId
│
├── citizen/domain/
│   ├── Building.java                        ← SỬA: add tenantId, locationPath
│   ├── Household.java                       ← SỬA: add tenantId
│   └── CitizenAccount.java                  ← SỬA: add tenantId
│
└── admin/domain/
    └── ErrorRecord.java                     ← SỬA: add tenantId

backend/src/main/resources/db/migration/
└── V16__enable_rls_policies.sql             ← NEW: RLS + FORCE RLS + CREATE POLICY
```

---

## 4. Implementation Tasks — Theo thứ tự dependency

### Task BT-MT-01: TenantContext (ThreadLocal) — P0 BLOCKER

**Tạo mới:** `backend/src/main/java/com/uip/backend/tenant/context/TenantContext.java`

```java
package com.uip.backend.tenant.context;

/**
 * ThreadLocal holder cho tenant_id hiện tại.
 * Set bởi TenantContextFilter (HTTP) hoặc TenantAwareKafkaListener (Kafka).
 * Đọc bởi HikariTenantListener để chạy SET LOCAL trước query.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            CURRENT_TENANT.set("default");
        } else {
            CURRENT_TENANT.set(tenantId);
        }
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
```

**Verification:**
```java
// Unit test
@Test
void shouldSetAndGetTenantId() {
    TenantContext.setCurrentTenant("hcm");
    assertThat(TenantContext.getCurrentTenant()).isEqualTo("hcm");
    TenantContext.clear();
    assertThat(TenantContext.getCurrentTenant()).isNull();
}

@Test
void shouldFallbackToDefaultWhenNull() {
    TenantContext.setCurrentTenant(null);
    assertThat(TenantContext.getCurrentTenant()).isEqualTo("default");
}
```

---

### Task BT-MT-02: TenantContextFilter — P0 BLOCKER

**Tạo mới:** `backend/src/main/java/com/uip/backend/tenant/filter/TenantContextFilter.java`

```java
package com.uip.backend.tenant.filter;

import com.uip.backend.tenant.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Extracts tenant_id from JWT claims (or defaults to 'default' for T1).
 * Must run AFTER JwtAuthenticationFilter (set via SecurityConfig filter order).
 *
 * ADR-021: T1 also runs this filter — always fallback to 'default'.
 * No @ConditionalOnProperty needed.
 */
@Component
@Slf4j
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String tenantId = extractTenantId();
            TenantContext.setCurrentTenant(tenantId);
            log.debug("TenantContext set: {}", TenantContext.getCurrentTenant());
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String tenantId = jwt.getClaimAsString("tenant_id");
            if (tenantId != null && !tenantId.isBlank()) {
                return tenantId;
            }
        }
        return "default"; // T1 fallback (ADR-021)
    }
}
```

**Wiring in SecurityConfig:**

```java
// SecurityConfig.java — thêm filter sau JwtAuthFilter
.addFilterAfter(tenantContextFilter, JwtAuthenticationFilter.class)
```

**Verification:**
```java
// Integration test
@Test
void shouldSetDefaultTenantWhenNoJwtClaim() {
    // Login with existing user (no tenant_id claim in JWT)
    // Call GET /api/v1/sensors
    // Verify response 200 with data
}

@Test
void shouldSetTenantFromJwtClaim() {
    // Login, get JWT, verify tenant_id claim extracted
    // Call API, verify TenantContext has correct value
}
```

---

### Task BT-MT-03: HikariTenantListener (SET LOCAL) — P0 CRITICAL SECURITY

**Tạo mới:** `backend/src/main/java/com/uip/backend/tenant/hibernate/HikariTenantListener.java`

Đây là component **quan trọng nhất** để ngăn cross-tenant data leak. Theo ADR-010, phải dùng `SET LOCAL`, KHÔNG dùng `SET SESSION`.

```java
package com.uip.backend.tenant.hibernate;

import com.uip.backend.tenant.context.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Executes SET LOCAL app.tenant_id = '<tenant>' at the start of each transaction.
 * SET LOCAL auto-resets on COMMIT/ROLLBACK — safe with HikariCP connection pooling.
 *
 * CRITICAL: Must use SET LOCAL, not SET SESSION.
 * SET SESSION leaks tenant context to next connection user (ADR-010 §Security Constraint).
 */
@Component("hikariTenantListener")
@Slf4j
public class HikariTenantListener {

    /**
     * Called from @Transactional methods or AOP aspect.
     * Sets the PostgreSQL session variable for RLS policy evaluation.
     */
    public void setTenantContext(EntityManager em) {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            tenantId = "default";
            log.warn("TenantContext null during SET LOCAL — falling back to 'default'. " +
                     "Check TenantContextFilter is configured.");
        }

        em.unwrap(Session.class).doWork(connection -> {
            connection.createStatement().execute(
                "SET LOCAL app.tenant_id = '" + tenantId.replace("'", "''") + "'"
            );
        });

        log.debug("SET LOCAL app.tenant_id = '{}'", tenantId);
    }
}
```

**Approach 2 (Recommended): Hibernate Interceptor**

Tạo `TenantHibernateInterceptor` tự động set tenant context mỗi khi Hibernate mở session:

```java
package com.uip.backend.tenant.hibernate;

import com.uip.backend.tenant.context.TenantContext;
import org.hibernate.EmptyInterceptor;
import org.hibernate.Transaction;
import org.springframework.stereotype.Component;

/**
 * Hibernate interceptor that sets tenant context on each transaction begin.
 * Registered via application.yml: spring.jpa.properties.hibernate.session.events.interceptor
 */
@Component
public class TenantHibernateInterceptor extends EmptyInterceptor {

    // NOTE: Không thể execute SET LOCAL trong Hibernate Interceptor
    // vì connection chưa acquire. Dùng AOP approach thay thế.
}
```

**Approach 3 (Best for Spring Boot): AOP + @Transactional**

```java
package com.uip.backend.tenant.hibernate;

import com.uip.backend.tenant.context.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that runs SET LOCAL before every @Transactional method.
 * This is the safest approach — guaranteed to run within the transaction boundary.
 */
@Aspect
@Component
@Slf4j
public class TenantContextAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("@annotation(org.springframework.transaction.annotation.Transactional)")
    public void setTenantContext() {
        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            tenantId = "default";
        }

        entityManager.unwrap(Session.class).doWork(connection -> {
            connection.createStatement().execute(
                "SET LOCAL app.tenant_id = '" + tenantId.replace("'", "''") + "'"
            );
        });
    }
}
```

**Recommendation:** Dùng **Approach 3 (AOP)** vì:
- Tự động apply cho mọi `@Transactional` method
- Không cần developer nhớ call `setTenantContext()` thủ công
- Chạy trong transaction boundary — `SET LOCAL` có hiệu lực

**Verification:**
```java
@Test
void shouldSetLocalNotSession() {
    // Verify SET LOCAL is used, not SET SESSION
    // SET LOCAL resets on COMMIT — verify connection returns clean
}

@Test
void shouldBlockCrossTenantAccess() {
    // Set tenant to 'tenant_a'
    // Query sensor owned by 'tenant_b'
    // Verify empty result (RLS blocks)
}
```

---

### Task BT-MT-04: JWT Claims Extension — P0 BLOCKER cho Frontend

**Sửa:** `JwtTokenProvider.java`

```java
// JwtTokenProvider.java — thêm claims mới vào access token
public String generateAccessToken(UserDetails userDetails, String tenantId) {
    List<String> roles = userDetails.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList();

    Map<String, Object> claims = new HashMap<>();
    claims.put("roles", roles);
    claims.put("tenant_id", tenantId != null ? tenantId : "default");
    // tenant_path, scopes, allowed_buildings — populate từ tenant config
    // (sẽ implement trong TenantSetupService)

    return buildToken(userDetails.getUsername(), claims, jwtProperties.getExpirationMs());
}
```

**Sửa:** `AuthController.java` — inject `tenantId` vào login response

```java
// Login endpoint — resolve tenant from user entity
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
    Authentication auth = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
    );
    UserDetails userDetails = (UserDetails) auth.getPrincipal();

    // Resolve tenant from user entity
    AppUser user = appUserRepository.findByUsername(userDetails.getUsername()).orElseThrow();
    String tenantId = user.getTenantId() != null ? user.getTenantId() : "default";

    String accessToken = tokenProvider.generateAccessToken(userDetails, tenantId);
    String refreshToken = tokenProvider.generateRefreshToken(userDetails);

    return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken));
}
```

**Sửa:** `AppUser.java` — thêm `tenantId` field

```java
@Entity
@Table(name = "app_users")
public class AppUser {
    // ... existing fields ...

    @Column(name = "tenant_id")
    private String tenantId;  // T1 = 'default', T2+ = actual tenant ID

    @Column(name = "tenant_path")
    private String tenantPath; // LTREE path as String

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_scopes", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "scope")
    private Set<String> scopes = new HashSet<>();
}
```

**Migration mới:** `V17__add_tenant_fields_app_users.sql`

```sql
-- V17: Add multi-tenant fields to app_users
ALTER TABLE public.app_users
    ADD COLUMN IF NOT EXISTS tenant_id TEXT NOT NULL DEFAULT 'default',
    ADD COLUMN IF NOT EXISTS tenant_path TEXT DEFAULT 'city.default';

CREATE TABLE IF NOT EXISTS public.app_user_scopes (
    user_id UUID NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
    scope   VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, scope)
);

-- Backfill existing users with default tenant
UPDATE public.app_users SET tenant_id = 'default' WHERE tenant_id IS NULL;

-- Assign scopes based on role
UPDATE public.app_users SET tenant_id = 'default' WHERE tenant_id IS NULL;
-- admin gets all scopes
INSERT INTO public.app_user_scopes (user_id, scope)
SELECT id, unnest(ARRAY[
    'environment:read','environment:write',
    'esg:read','esg:write',
    'alert:read','alert:ack',
    'traffic:read','traffic:write',
    'sensor:read','sensor:write',
    'citizen:read','citizen:admin',
    'workflow:read','workflow:write',
    'tenant:admin'
])
FROM public.app_users WHERE role = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

-- operator gets operational scopes
INSERT INTO public.app_user_scopes (user_id, scope)
SELECT id, unnest(ARRAY[
    'environment:read','environment:write',
    'esg:read',
    'alert:read','alert:ack',
    'traffic:read','traffic:write',
    'sensor:read','sensor:write',
    'workflow:read'
])
FROM public.app_users WHERE role = 'ROLE_OPERATOR'
ON CONFLICT DO NOTHING;

-- citizen gets read-only scopes
INSERT INTO public.app_user_scopes (user_id, scope)
SELECT id, unnest(ARRAY[
    'environment:read',
    'esg:read',
    'alert:read',
    'traffic:read'
])
FROM public.app_users WHERE role = 'ROLE_CITIZEN'
ON CONFLICT DO NOTHING;
```

---

### Task BT-MT-05: JPA Entity Updates — P0

Mỗi entity cần thêm `tenantId` field tương ứng với DB column. Ví dụ cho `Sensor`:

```java
// Sensor.java — thêm fields
@Entity
@Table(name = "sensors", schema = "environment")
public class Sensor {
    // ... existing fields ...

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "location_path", columnDefinition = "ltree")
    private String locationPath;  // Stored as String, PG handles LTREE
}
```

**Quy tắc cho mọi entity:**
- `tenantId` field: `@Column(name = "tenant_id", nullable = false)` với default `"default"`
- `locationPath` field: **CHỈ** thêm vào metadata/entity tables (Sensor, Building). **KHÔNG** thêm vào time-series tables (SensorReading, EsgMetric, TrafficCount).

**Full list entities cần update:**

| Entity | Add `tenantId` | Add `locationPath` | Notes |
|--------|---------------|--------------------|----|
| `Sensor` | YES | YES | Metadata table |
| `SensorReading` | YES | NO | Time-series |
| `Building` | YES | YES | Metadata table |
| `Household` | YES | NO | FK to Building |
| `CitizenAccount` | YES | NO | Identity |
| `EsgMetric` | YES | NO | Time-series |
| `EsgReport` | YES | NO | Output |
| `AlertRule` | YES | NO | Config |
| `AlertEvent` | YES | NO | Event log |
| `TrafficCount` | YES | NO | Time-series |
| `TrafficIncident` | YES | NO | Event log |
| `ErrorRecord` | YES | NO | Ops |
| `AuditLog` | YES | NO | Audit |

---

### Task BT-MT-06: Repository Tenant Filtering

**KHÔNG** cần sửa tất cả repository queries để thêm `WHERE tenantId = :tenantId` thủ công. RLS sẽ filter tự động ở DB layer. Tuy nhiên, JPA entity phải có field để:

1. **WRITE**: Khi tạo entity mới, set `tenantId` từ `TenantContext`
2. **READ**: RLS filter tự động, không cần thêm WHERE clause

**Tạo Entity Listener để auto-set tenantId:**

```java
package com.uip.backend.tenant.hibernate;

import com.uip.backend.tenant.context.TenantContext;
import jakarta.persistence.PrePersist;

/**
 * Auto-fills tenantId on new entity persist.
 * Entity must have setTenantId() method.
 */
public class TenantEntityListener {

    @PrePersist
    public void setTenantId(Object entity) {
        if (entity instanceof TenantAware tenantAware) {
            if (tenantAware.getTenantId() == null) {
                tenantAware.setTenantId(TenantContext.getCurrentTenant());
            }
        }
    }
}
```

**Tạo interface `TenantAware`:**

```java
package com.uip.backend.tenant.domain;

/**
 * Marker interface for entities that support multi-tenancy.
 * All domain entities should implement this.
 */
public interface TenantAware {
    String getTenantId();
    void setTenantId(String tenantId);
}
```

**Entity update example:**

```java
@Entity
@Table(name = "sensors", schema = "environment")
@EntityListeners(TenantEntityListener.class)
public class Sensor implements TenantAware {
    // ... existing fields ...

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Override
    public String getTenantId() { return tenantId; }
    @Override
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
```

---

### Task BT-MT-07: V16 Migration — RLS Policies

**Tạo mới:** `backend/src/main/resources/db/migration/V16__enable_rls_policies.sql`

Theo ADR-023, chạy trong maintenance window. Lock time <5 giây (metadata-only).

```sql
-- V16: Enable RLS policies for multi-tenant isolation
-- ADR-010, ADR-023: Zero-downtime RLS migration
-- Prerequisite: V14 (tenant_id columns) + V17 (backfill) must be complete

-- Register custom GUC for app.tenant_id
-- (This is optional — SET LOCAL creates it implicitly, but explicit is clearer)

-- =============================================================================
-- Verify backfill complete
-- =============================================================================
DO $$
DECLARE
    null_count INT;
BEGIN
    SELECT COUNT(*) INTO null_count FROM environment.sensors WHERE tenant_id IS NULL;
    IF null_count > 0 THEN
        RAISE EXCEPTION 'Backfill incomplete: environment.sensors has % NULL tenant_id rows', null_count;
    END IF;

    SELECT COUNT(*) INTO null_count FROM environment.sensor_readings WHERE tenant_id IS NULL;
    IF null_count > 0 THEN
        RAISE EXCEPTION 'Backfill incomplete: environment.sensor_readings has % NULL tenant_id rows', null_count;
    END IF;

    -- Add similar checks for other tables...
END $$;

-- =============================================================================
-- Enable RLS on all domain tables
-- =============================================================================

-- Environment module
ALTER TABLE environment.sensors ENABLE ROW LEVEL SECURITY;
ALTER TABLE environment.sensors FORCE ROW LEVEL SECURITY;
ALTER TABLE environment.sensor_readings ENABLE ROW LEVEL SECURITY;
ALTER TABLE environment.sensor_readings FORCE ROW LEVEL SECURITY;

-- ESG module
ALTER TABLE esg.clean_metrics ENABLE ROW LEVEL SECURITY;
ALTER TABLE esg.clean_metrics FORCE ROW LEVEL SECURITY;
ALTER TABLE esg.reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE esg.reports FORCE ROW LEVEL SECURITY;

-- Alerts module
ALTER TABLE alerts.alert_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts.alert_rules FORCE ROW LEVEL SECURITY;
ALTER TABLE alerts.alert_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts.alert_events FORCE ROW LEVEL SECURITY;

-- Traffic module
ALTER TABLE traffic.traffic_counts ENABLE ROW LEVEL SECURITY;
ALTER TABLE traffic.traffic_counts FORCE ROW LEVEL SECURITY;
ALTER TABLE traffic.traffic_incidents ENABLE ROW LEVEL SECURITY;
ALTER TABLE traffic.traffic_incidents FORCE ROW LEVEL SECURITY;

-- Citizens module
ALTER TABLE citizens.buildings ENABLE ROW LEVEL SECURITY;
ALTER TABLE citizens.buildings FORCE ROW LEVEL SECURITY;
ALTER TABLE citizens.households ENABLE ROW LEVEL SECURITY;
ALTER TABLE citizens.households FORCE ROW LEVEL SECURITY;
ALTER TABLE citizens.citizen_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE citizens.citizen_accounts FORCE ROW LEVEL SECURITY;

-- Error management
ALTER TABLE error_mgmt.error_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE error_mgmt.error_records FORCE ROW LEVEL SECURITY;

-- =============================================================================
-- Create RLS policies
-- =============================================================================

-- Environment
CREATE POLICY tenant_isolation ON environment.sensors
    USING (tenant_id = current_setting('app.tenant_id', true));
CREATE POLICY tenant_isolation ON environment.sensor_readings
    USING (tenant_id = current_setting('app.tenant_id', true));

-- ESG
CREATE POLICY tenant_isolation ON esg.clean_metrics
    USING (tenant_id = current_setting('app.tenant_id', true));
CREATE POLICY tenant_isolation ON esg.reports
    USING (tenant_id = current_setting('app.tenant_id', true));

-- Alerts
CREATE POLICY tenant_isolation ON alerts.alert_rules
    USING (tenant_id = current_setting('app.tenant_id', true));
CREATE POLICY tenant_isolation ON alerts.alert_events
    USING (tenant_id = current_setting('app.tenant_id', true));

-- Traffic
CREATE POLICY tenant_isolation ON traffic.traffic_counts
    USING (tenant_id = current_setting('app.tenant_id', true));
CREATE POLICY tenant_isolation ON traffic.traffic_incidents
    USING (tenant_id = current_setting('app.tenant_id', true));

-- Citizens
CREATE POLICY tenant_isolation ON citizens.buildings
    USING (tenant_id = current_setting('app.tenant_id', true));
CREATE POLICY tenant_isolation ON citizens.households
    USING (tenant_id = current_setting('app.tenant_id', true));
CREATE POLICY tenant_isolation ON citizens.citizen_accounts
    USING (tenant_id = current_setting('app.tenant_id', true));

-- Error management
CREATE POLICY tenant_isolation ON error_mgmt.error_records
    USING (tenant_id = current_setting('app.tenant_id', true));
```

**Note:** `current_setting('app.tenant_id', true)` — tham số `true` (missing_ok) tránh lỗi khi GUC chưa set (trả về NULL → policy evaluates to FALSE → 0 rows, đúng behavior).

---

### Task BT-MT-08: Tenant Entity + Repository

**Tạo mới:** `backend/src/main/java/com/uip/backend/tenant/domain/Tenant.java`

```java
package com.uip.backend.tenant.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true, length = 50)
    private String tenantId; // e.g., "hcm", "hn", "default"

    @Column(name = "tenant_name", nullable = false)
    private String tenantName; // e.g., "Ho Chi Minh City"

    @Column(name = "tier", nullable = false, length = 10)
    private String tier; // T1, T2, T3, T4

    @Column(name = "location_path")
    private String locationPath; // LTREE root: "city.hcm"

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "config_json", columnDefinition = "jsonb")
    private String configJson; // Feature flags, branding, limits

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

**Migration:** `V18__create_tenants_table.sql`

```sql
-- V18: Tenant registry table
CREATE TABLE IF NOT EXISTS public.tenants (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   VARCHAR(50) NOT NULL UNIQUE,
    tenant_name VARCHAR(255) NOT NULL,
    tier        VARCHAR(10) NOT NULL DEFAULT 'T1',
    location_path TEXT,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    config_json JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed default tenant
INSERT INTO public.tenants (tenant_id, tenant_name, tier, location_path)
VALUES ('default', 'Default Tenant (T1)', 'T1', 'city.default')
ON CONFLICT (tenant_id) DO NOTHING;

-- HCMC demo tenant
INSERT INTO public.tenants (tenant_id, tenant_name, tier, location_path)
VALUES ('hcm', 'Ho Chi Minh City', 'T2', 'city.hcm')
ON CONFLICT (tenant_id) DO NOTHING;
```

---

### Task BT-MT-09: TenantAwareKafkaListener Base Class

**Tạo mới:** `backend/src/main/java/com/uip/backend/common/kafka/TenantAwareKafkaListener.java`

```java
package com.uip.backend.common.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.tenant.context.TenantContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for Kafka listeners that need tenant context.
 * Extracts tenant_id from message body, sets TenantContext, wraps in try/finally.
 *
 * ADR-020: Non-HTTP Tenant ID Propagation
 */
@Slf4j
public abstract class TenantAwareKafkaListener {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Extract tenant_id from Kafka message JSON body.
     * Subclasses call this before business logic.
     */
    protected String extractTenantId(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode tenantNode = node.get("tenant_id");
            if (tenantNode != null && !tenantNode.asText().isBlank()) {
                return tenantNode.asText();
            }
        } catch (Exception e) {
            log.warn("Failed to extract tenant_id from message: {}", e.getMessage());
        }
        return "default";
    }

    /**
     * Execute business logic with tenant context set.
     * Auto-clears TenantContext after execution.
     */
    protected void withTenantContext(String tenantId, Runnable action) {
        try {
            TenantContext.setCurrentTenant(tenantId);
            action.run();
        } finally {
            TenantContext.clear();
        }
    }
}
```

**Usage example:**

```java
@Component
public class TelemetryConsumer extends TenantAwareKafkaListener {

    @KafkaListener(topics = "UIP.environment.sensor.reading.v1")
    public void onSensorReading(String payload) {
        String tenantId = extractTenantId(payload);
        withTenantContext(tenantId, () -> {
            // Business logic with TenantContext set
            SensorReading reading = parseReading(payload);
            sensorReadingRepository.save(reading);
        });
    }
}
```

---

### Task BT-MT-10: TenantContextTaskDecorator — @Async Propagation

**Tạo mới:** `backend/src/main/java/com/uip/backend/tenant/context/TenantContextTaskDecorator.java`

```java
package com.uip.backend.tenant.context;

import org.springframework.core.task.TaskDecorator;

/**
 * Propagates TenantContext from parent thread to @Async child thread.
 * ADR-020: Non-HTTP Tenant ID Propagation
 */
public class TenantContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        String tenantId = TenantContext.getCurrentTenant();
        return () -> {
            try {
                TenantContext.setCurrentTenant(tenantId);
                runnable.run();
            } finally {
                TenantContext.clear();
            }
        };
    }
}
```

**Wiring in AsyncConfig:**

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setTaskDecorator(new TenantContextTaskDecorator());
        executor.setThreadNamePrefix("uip-async-");
        executor.initialize();
        return executor;
    }
}
```

---

## 5. Implementation Order & Dependency Chain

```
Week 1 (12-16 May)
═══════════════════

Day 1-2: Foundation
  BT-MT-01: TenantContext (ThreadLocal)          ← 30 min
  BT-MT-04: JWT Claims Extension                  ← 2 hours
      └─ V17 migration: app_users tenant fields
  BT-MT-05: JPA Entity Updates                    ← 3 hours (13 entities)
      └─ TenantAware interface
      └─ TenantEntityListener
      └─ @EntityListeners on each entity

Day 2-3: Security Layer
  BT-MT-02: TenantContextFilter                   ← 2 hours
  BT-MT-03: HikariTenantListener (AOP)            ← 3 hours
      └─ TenantContextAspect
  BT-MT-07: V16 Migration (RLS)                   ← 1 hour
      └─ Enable RLS on all tables
      └─ Create policies

Day 3-4: Infrastructure
  BT-MT-08: Tenant Entity + Repository            ← 1 hour
      └─ V18 migration: tenants table
  BT-MT-09: TenantAwareKafkaListener              ← 1 hour
  BT-MT-10: TenantContextTaskDecorator             ← 30 min

Day 4-5: Testing
  Integration tests                                ← 4 hours
      └─ Cross-tenant isolation test
      └─ SET LOCAL reset verification
      └─ T1 fallback test
      └─ Kafka tenant propagation test

Week 2 (19-23 May)
═══════════════════

  Frontend tasks (FE-01 through FE-28)
  Security hardening: ROLE_TENANT_ADMIN + scopes
  Rate limiting per tenant
  CORS dynamic multi-tenant
```

---

## 6. Testing Strategy

### 6.1 Critical Test Scenarios

| # | Test Case | Priority | Type |
|---|-----------|----------|------|
| 1 | Tenant A cannot read Tenant B data via API | P0 | Integration |
| 2 | Tenant A cannot read Tenant B data via direct DB query | P0 | Integration |
| 3 | SET LOCAL resets after COMMIT (no connection leak) | P0 | Integration |
| 4 | T1 deployment works without JWT tenant_id claim | P0 | Integration |
| 5 | Kafka consumer sets TenantContext correctly | P1 | Integration |
| 6 | @Async method receives TenantContext | P1 | Unit |
| 7 | New entity auto-gets tenantId from TenantContext | P1 | Unit |
| 8 | RLS blocks query when tenant_id is NULL | P0 | Integration |

### 6.2 Integration Test Template — Cross-Tenant Isolation

```java
@SpringBootTest
@Testcontainers
class TenantIsolationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("timescale/timescaledb:latest-pg15");

    @Test
    void tenantACannotReadTenantBData() {
        // Setup: Insert sensor for tenant_a
        jdbcTemplate.execute("SET LOCAL app.tenant_id = 'tenant_a'");
        jdbcTemplate.execute("INSERT INTO environment.sensors (id, sensor_id, sensor_name, tenant_id) " +
                           "VALUES ('...', 'sensor-a-1', 'Sensor A', 'tenant_a')");
        jdbcTemplate.execute("COMMIT");

        // Test: Try to read as tenant_b
        jdbcTemplate.execute("SET LOCAL app.tenant_id = 'tenant_b'");
        List<Map<String, Object>> results = jdbcTemplate.queryForList("SELECT * FROM environment.sensors");

        // Verify: Empty result
        assertThat(results).isEmpty();
    }

    @Test
    void setLocalResetsAfterCommit() {
        // Set tenant context
        jdbcTemplate.execute("SET LOCAL app.tenant_id = 'tenant_a'");
        jdbcTemplate.execute("COMMIT");

        // Verify: app.tenant_id is NULL after commit
        String value = jdbcTemplate.queryForObject(
            "SELECT current_setting('app.tenant_id', true)", String.class);
        assertThat(value).isNull();
    }
}
```

### 6.3 Test Data Setup

```sql
-- Seed data for multi-tenant testing
-- Tenant A (hcm)
INSERT INTO environment.sensors (id, sensor_id, sensor_name, tenant_id, location_path)
VALUES ('a0000000-0000-0000-0000-000000000001', 'SENSOR-HCM-001', 'HCM Sensor 1', 'hcm', 'city.hcm.d7.riverpark.tower_a');

-- Tenant B (hn)
INSERT INTO environment.sensors (id, sensor_id, sensor_name, tenant_id, location_path)
VALUES ('b0000000-0000-0000-0000-000000000001', 'SENSOR-HN-001', 'HN Sensor 1', 'hn', 'city.hn.ba.dinh.cluster_x.building_b');

-- Default tenant (T1)
INSERT INTO environment.sensors (id, sensor_id, sensor_name, tenant_id, location_path)
VALUES ('d0000000-0000-0000-0000-000000000001', 'SENSOR-DEF-001', 'Default Sensor 1', 'default', 'city.default.building.main');
```

---

## 7. Security Checklist

### 7.1 Code Review Checklist — Multi-Tenancy

- [ ] **SET LOCAL, not SET SESSION** — grep codebase for `SET SESSION app.tenant_id` → must be zero results
- [ ] **Every @Transactional method has tenant context** — TenantContextAspect covers this
- [ ] **No cross-schema JOIN** — grep repositories for schema prefixes not matching own module
- [ ] **Entity listener on every domain entity** — `@EntityListeners(TenantEntityListener.class)`
- [ ] **Kafka consumer extends TenantAwareKafkaListener** — check all `@KafkaListener` methods
- [ ] **@Async methods have TaskDecorator** — verify AsyncConfig wires TenantContextTaskDecorator
- [ ] **JWT contains tenant_id claim** — verify login response has correct claim
- [ ] **Frontend AuthUser includes tenantId** — verify `userFromToken()` extracts it

### 7.2 Anti-Pattern Detection

```bash
# Run these checks before PR merge:

# 1. No SET SESSION for tenant context
grep -rn "SET SESSION app.tenant_id" backend/src/
# Expected: 0 results

# 2. No cross-module repository injection
grep -rn "import com.uip.backend.environment.repository" backend/src/main/java/com/uip/backend/esg/
# Expected: 0 results

# 3. All domain entities have TenantAware
grep -rn "implements TenantAware" backend/src/main/java/com/uip/backend/
# Expected: 13 results (one per domain entity)

# 4. All Kafka listeners extend TenantAwareKafkaListener
grep -rn "extends TenantAwareKafkaListener" backend/src/main/java/
# Expected: N results (one per consumer)
```

---

## 8. Risk Register

| Risk | Impact | Probability | Mitigation | Owner |
|------|--------|-------------|------------|-------|
| SET SESSION thay SET LOCAL → data leak | Critical | Low | AOP approach auto-uses SET LOCAL; code review checklist | SA |
| Developer quên TenantContext trong consumer mới | High | Medium | TenantAwareKafkaListener base class; abstract method | Backend Lead |
| RLS migration lock quá lâu trên production | High | Low | Verify no long-running tx; maintenance window 2-3 AM | DevOps |
| Hibernate generate query không respect RLS | Medium | Low | Verify with integration test; use `SET LOCAL` not Hibernate filter | SA |
| Flyway migration V16 fail (backfill chưa xong) | Medium | Low | V16 verify step check NULL counts before proceeding | Backend |
| JPA entity mapping sai với V14 column type | Medium | Medium | Testcontainers integration test cho mỗi entity | QA |

---

## 9. Migration Numbering Plan

| Version | Content | Depends on |
|---------|---------|------------|
| V14 | Add tenant_id + location_path columns | Done |
| V15 | Audit log table | Done |
| V16 | Enable RLS + FORCE RLS + CREATE POLICY on all domain tables | V14, V17 |
| V17 | Add tenant fields to app_users + scopes table + backfill | V14 |
| V18 | Create tenants registry table + seed data | V16 |

**Note:** V16 và V17 có thể chạy song song nếu V16 check backfill chỉ trên data tables (không phải app_users). Nếu chạy tuần tự: V17 trước V16.

---

## 10. Frontend Impact Summary

Sprint 2 Frontend tasks liên quan multi-tenancy (tham chiếu detail plan):

| Task | File | Change |
|------|------|--------|
| FE-25 | `src/types/tenant.ts` | TypeScript interfaces cho Tenant, TenantConfig, Scopes |
| FE-01 | `src/contexts/AuthContext.tsx` | Parse JWT claims: tenantId, tenantPath, scopes, allowedBuildings |
| FE-03 | `src/contexts/TenantConfigContext.tsx` | NEW: fetch tenant config, expose feature flags |
| FE-18 | Multiple | React Query keys include tenantId |
| FE-04 | `src/components/AppShell.tsx` | Filter NAV_ITEMS by featureFlag |
| FE-05 | `src/routes/ProtectedRoute.tsx` | Support requiredRoles[] + requiredScope |
| FE-14 | `src/api/client.ts` | X-Tenant-Override header for Super-Admin |

---

## Appendix A: current_setting() Behavior

```sql
-- Khi app.tenant_id CHƯA được SET:
SELECT current_setting('app.tenant_id', true);  -- NULL (missing_ok = true)
SELECT current_setting('app.tenant_id', false); -- ERROR: unrecognized configuration parameter

-- Khi app.tenant_id đã SET LOCAL:
BEGIN;
SET LOCAL app.tenant_id = 'hcm';
SELECT current_setting('app.tenant_id', true);  -- 'hcm'
COMMIT;

-- Sau COMMIT:
SELECT current_setting('app.tenant_id', true);  -- NULL (SET LOCAL reset)
```

RLS Policy: `USING (tenant_id = current_setting('app.tenant_id', true))`
- `app.tenant_id` = NULL → `tenant_id = NULL` → FALSE → 0 rows (đúng, block access)
- `app.tenant_id` = 'hcm' → `tenant_id = 'hcm'` → rows của tenant hcm (đúng)

---

## Appendix B: LTREE Query Examples

```sql
-- Tìm tất cả sensors trong một district
SELECT * FROM environment.sensors
WHERE location_path <@ 'city.hcm.d7';  -- <@ = "descendant of"

-- Tìm sensors trong một cụm tòa nhà
SELECT * FROM environment.sensors
WHERE location_path <@ 'city.hcm.d7.riverpark';

-- Tìm sensors ở tầng 3 của bất kỳ tòa nhà nào
SELECT * FROM environment.sensors
WHERE location_path ~ 'city.hcm.d7.*.f3';

-- Lấy depth trong hierarchy
SELECT nlevel(location_path) FROM environment.sensors;
-- city.hcm.d7.riverpark.tower_a = 5 levels
```

---

## Appendix C: Rollback Plan

Nếu RLS gây vấn đề trên production:

```sql
-- Emergency rollback (chạy trong maintenance window)
-- V19__rollback_rls.sql

-- 1. Remove FORCE
ALTER TABLE environment.sensors NO FORCE ROW LEVEL SECURITY;
ALTER TABLE environment.sensor_readings NO FORCE ROW LEVEL SECURITY;
ALTER TABLE esg.clean_metrics NO FORCE ROW LEVEL SECURITY;
ALTER TABLE esg.reports NO FORCE ROW LEVEL SECURITY;
ALTER TABLE alerts.alert_rules NO FORCE ROW LEVEL SECURITY;
ALTER TABLE alerts.alert_events NO FORCE ROW LEVEL SECURITY;
ALTER TABLE traffic.traffic_counts NO FORCE ROW LEVEL SECURITY;
ALTER TABLE traffic.traffic_incidents NO FORCE ROW LEVEL SECURITY;
ALTER TABLE citizens.buildings NO FORCE ROW LEVEL SECURITY;
ALTER TABLE citizens.households NO FORCE ROW LEVEL SECURITY;
ALTER TABLE citizens.citizen_accounts NO FORCE ROW LEVEL SECURITY;
ALTER TABLE error_mgmt.error_records NO FORCE ROW LEVEL SECURITY;

-- 2. Drop policies
DROP POLICY IF EXISTS tenant_isolation ON environment.sensors;
DROP POLICY IF EXISTS tenant_isolation ON environment.sensor_readings;
DROP POLICY IF EXISTS tenant_isolation ON esg.clean_metrics;
DROP POLICY IF EXISTS tenant_isolation ON esg.reports;
DROP POLICY IF EXISTS tenant_isolation ON alerts.alert_rules;
DROP POLICY IF EXISTS tenant_isolation ON alerts.alert_events;
DROP POLICY IF EXISTS tenant_isolation ON traffic.traffic_counts;
DROP POLICY IF EXISTS tenant_isolation ON traffic.traffic_incidents;
DROP POLICY IF EXISTS tenant_isolation ON citizens.buildings;
DROP POLICY IF EXISTS tenant_isolation ON citizens.households;
DROP POLICY IF EXISTS tenant_isolation ON citizens.citizen_accounts;
DROP POLICY IF EXISTS tenant_isolation ON error_mgmt.error_records;

-- 3. Disable RLS
ALTER TABLE environment.sensors DISABLE ROW LEVEL SECURITY;
ALTER TABLE environment.sensor_readings DISABLE ROW LEVEL SECURITY;
ALTER TABLE esg.clean_metrics DISABLE ROW LEVEL SECURITY;
ALTER TABLE esg.reports DISABLE ROW LEVEL SECURITY;
ALTER TABLE alerts.alert_rules DISABLE ROW LEVEL SECURITY;
ALTER TABLE alerts.alert_events DISABLE ROW LEVEL SECURITY;
ALTER TABLE traffic.traffic_counts DISABLE ROW LEVEL SECURITY;
ALTER TABLE traffic.traffic_incidents DISABLE ROW LEVEL SECURITY;
ALTER TABLE citizens.buildings DISABLE ROW LEVEL SECURITY;
ALTER TABLE citizens.households DISABLE ROW LEVEL SECURITY;
ALTER TABLE citizens.citizen_accounts DISABLE ROW LEVEL SECURITY;
ALTER TABLE error_mgmt.error_records DISABLE ROW LEVEL SECURITY;
```

Rollback cũng cần ACCESS EXCLUSIVE lock, nhưng metadata-only nên <5 giây.
