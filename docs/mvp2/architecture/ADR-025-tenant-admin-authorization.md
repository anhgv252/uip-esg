# ADR-025: Tenant Admin Authorization Pattern

**Date**: 2026-05-04  
**Status**: Accepted  
**Deciders**: Solution Architect, Engineering Lead  
**Related**: ADR-010 (Multi-Tenant Strategy), ADR-019 (Partner Customization)

---

## Context

Sprint 4 thêm `TenantAdminController` với 6 endpoints:

```
GET  /api/v1/admin/tenants/{tenantId}/users
POST /api/v1/admin/tenants/{tenantId}/users/invite
PUT  /api/v1/admin/tenants/{tenantId}/users/{userId}/role
GET  /api/v1/admin/tenants/{tenantId}/usage
GET  /api/v1/admin/tenants/{tenantId}/settings
PUT  /api/v1/admin/tenants/{tenantId}/settings
```

Hai loại actor cần access:

1. **ROLE_ADMIN (Super Admin)**: Quản trị hệ thống, có quyền xem và chỉnh bất kỳ tenant nào
2. **ROLE_TENANT_ADMIN**: Quản trị một tenant cụ thể, chỉ được thao tác trên tenant của mình

`AdminController` hiện tại (`/api/v1/admin/users`) chỉ cho `ROLE_ADMIN`. Cần mở rộng mà không phá vỡ existing pattern.

### Constraints

- JWT token chứa `tenant_id` claim (set bởi `JwtUtils`)
- `TenantContext` propagate `tenant_id` xuyên suốt request thread
- RLS PostgreSQL enforce isolation ở DB layer
- Phase 1: monolith, cùng JVM

---

## Decision

### Authorization rule

```
GET  /users    → hasRole('ADMIN') or hasRole('TENANT_ADMIN')
POST /invite   → hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and hasAuthority('tenant:admin'))
PUT  /role     → hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and hasAuthority('tenant:admin'))
GET  /usage    → hasRole('ADMIN') or hasRole('TENANT_ADMIN')
GET  /settings → hasRole('ADMIN') or hasRole('TENANT_ADMIN')
PUT  /settings → hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and hasAuthority('tenant:admin'))
```

`ROLE_TENANT_ADMIN` luôn cần scope `tenant:admin` cho write operations. Read operations (`GET`) chỉ cần role.

### Effective tenant resolution — service layer

`{tenantId}` trong path param được resolve khác nhau theo role:

```java
private String resolveEffectiveTenantId(String pathTenantId, Authentication auth) {
    if (hasRole(auth, "ROLE_ADMIN")) {
        return pathTenantId;              // Super Admin: dùng path param (any tenant)
    }
    // TENANT_ADMIN: luôn dùng tenant của họ từ JWT — ignore path param
    String jwtTenantId = TenantContext.getCurrentTenant();
    if (!jwtTenantId.equals(pathTenantId)) {
        // Log warning nhưng không throw 403 (không leak tenant existence)
        log.warn("TENANT_ADMIN {} attempted path={}, serving own tenant",
                 jwtTenantId, pathTenantId);
    }
    return jwtTenantId;
}
```

**Tại sao không throw 403?** IDOR (Insecure Direct Object Reference) protection: không tiết lộ tenant có tồn tại hay không. TENANT_ADMIN luôn nhận data của chính mình, không bao giờ nhận data tenant khác.

### Controller pattern

```java
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenant Admin API")
public class TenantAdminController {

    private final TenantAdminService tenantAdminService;

    @GetMapping("/{tenantId}/users")
    @PreAuthorize("hasRole('ADMIN') or hasRole('TENANT_ADMIN')")
    public ResponseEntity<List<TenantUserDto>> listUsers(
            @PathVariable String tenantId,
            Authentication auth) {
        String effectiveTenantId = tenantAdminService.resolveEffectiveTenantId(tenantId, auth);
        return ResponseEntity.ok(tenantAdminService.listUsers(effectiveTenantId));
    }

    @PostMapping("/{tenantId}/users/invite")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('TENANT_ADMIN') and hasAuthority('tenant:admin'))")
    public ResponseEntity<Void> inviteUser(
            @PathVariable String tenantId,
            @RequestBody @Valid InviteUserRequest request,
            Authentication auth) {
        String effectiveTenantId = tenantAdminService.resolveEffectiveTenantId(tenantId, auth);
        tenantAdminService.inviteUser(effectiveTenantId, request);
        return ResponseEntity.accepted().build();
    }

    // ... remaining endpoints follow same pattern
}
```

---

## Role Matrix

| Endpoint | ROLE_ADMIN | ROLE_TENANT_ADMIN (own) | ROLE_TENANT_ADMIN (other) |
|----------|-----------|------------------------|--------------------------|
| GET /users | ✅ Any tenant | ✅ Own tenant data | ✅ Own tenant data (path ignored) |
| POST /invite | ✅ Any tenant | ✅ (+ tenant:admin scope) | ✅ Own tenant (path ignored) |
| PUT /role | ✅ Any tenant | ✅ (+ tenant:admin scope) | ✅ Own tenant (path ignored) |
| GET /usage | ✅ Any tenant | ✅ Own tenant | ✅ Own tenant (path ignored) |
| GET /settings | ✅ Any tenant | ✅ Own tenant | ✅ Own tenant (path ignored) |
| PUT /settings | ✅ Any tenant | ✅ (+ tenant:admin scope) | ✅ Own tenant (path ignored) |

---

## Consequences

### Positive
- Đơn giản hơn SpEL expression phức tạp trong `@PreAuthorize`
- Enforcement ở service layer dễ test (unit test không cần mock Spring Security)
- Consistent với `TenantContext` pattern đã có trong codebase
- RLS ở PostgreSQL là defense-in-depth: dù service layer có bug, DB layer vẫn block

### Trade-offs
- TENANT_ADMIN nhận own data khi gọi sai tenantId — có thể gây confusion nếu FE không handle đúng
  - Mitigation: FE luôn dùng `user.tenantId` từ JWT, không để user input tenantId
- Path param `{tenantId}` là "suggestion" cho TENANT_ADMIN, không phải enforcement
  - Acceptable: API design là convenience cho ADMIN; TENANT_ADMIN FE không cần biết path param

### Không chọn Option A (SpEL trong @PreAuthorize)

```java
// ❌ Không dùng — SpEL phức tạp, khó debug, khó test
@PreAuthorize("hasRole('ADMIN') or " +
    "(hasRole('TENANT_ADMIN') and #tenantId == authentication.details.tenantId)")
```

Spring Security SpEL với custom authentication details yêu cầu casting phức tạp, dễ sai và khó unit test.

---

## Related ADRs

| ADR | Liên quan |
|-----|----------|
| ADR-010 | Multi-tenancy: RLS enforce DB isolation — defense-in-depth |
| ADR-020 | Non-HTTP tenant propagation: TenantContext pattern đã establish |
| ADR-019 | Partner Customization: TENANT_ADMIN là role cho partner admin |
