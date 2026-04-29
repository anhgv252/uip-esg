# ADR-024: Partner ID Naming Convention

**Status**: Accepted
**Date**: 2026-04-28
**Deciders**: Solution Architect, Engineering Lead
**Scope**: Toàn bộ codebase — backend, frontend, infra, documentation
**Related**: ADR-019 (Partner Customization Architecture), ADR-010 (Multi-Tenancy), ADR-011 (Monorepo)

---

## Context

ADR-019 định nghĩa 3-layer partner customization model, trong đó partner ID xuất hiện ở nhiều nơi:

| Vị trí | Ví dụ hiện tại | Ghi chú |
|--------|---------------|---------|
| ADR-019 examples | `energy-a`, `citizen-b` | Viết tắt, khó hiểu |
| MVP2 detail plan | `energy-optimizer`, `citizen-first` | Descriptive |
| Frontend theme files | `energy-optimizer.theme.ts`, `citizen-first.theme.ts` | Đã dùng descriptive |
| Backend Spring Profile | `application-partner-energy-optimizer.yml` | Đã dùng descriptive |
| Helm values | `values-partner-energy-optimizer.yaml` | Đã dùng descriptive |
| DB tenant_config | `energy-partner-a` (trong ADR-019 SQL) | Đang dùng format khác |

### Vấn đề

1. **Inconsistency**: ADR-019 dùng `energy-a` / `citizen-b` trong ví dụ, nhưng codebase thực tế dùng `energy-optimizer` / `citizen-first`. Dev mới đọc ADR-019 sẽ bị nhầm.
2. **No validation spec**: Không có quy tắc chính thức để validate partner ID khi tạo tenant mới.
3. **No registry**: Không có danh sách authoritative các partner ID đã dùng, dẫn đến risk trùng lặp hoặc xung đột naming.

---

## Decision

### Quy ước Partner ID

**Format**: `{domain}-{qualifier}` — lowercase, kebab-case, 2-3 segments.

```text
^([a-z][a-z0-9]*)(-[a-z0-9]+){1,2}$
```

**Ví dụ hợp lệ**:
- `energy-optimizer` — domain: energy, qualifier: optimizer
- `citizen-first` — domain: citizen, qualifier: first
- `traffic-analytics` — domain: traffic, qualifier: analytics
- `water-monitor` — domain: water, qualifier: monitor

**Ví dụ KHÔNG hợp lệ**:
- `energy-a` — quá ngắn, không descriptive
- `EnergyOptimizer` — uppercase, không kebab-case
- `energy_optimizer` — underscore, không kebab-case
- `eo` — quá ngắn, không descriptive
- `energy-optimizer-pro-v2-final` — quá 3 segments

### Quy tắc chi tiết

| Quy tắc | Mô tả |
|---------|-------|
| Ký tự | Chỉ lowercase `[a-z0-9-]` |
| Bắt đầu | Bắt đầu bằng chữ cái `[a-z]` |
| Kết thúc | Kết thúc bằng chữ cái hoặc số `[a-z0-9]` |
| Độ dài | 3-30 ký tự |
| Segments | 2-3 segments, phân tách bằng `-` |
| Segment đầu | Domain/chuyên ngành (energy, citizen, traffic, water, waste...) |
| Segment sau | Qualifier/mô tả (optimizer, first, analytics, monitor, smart...) |

### Mapping theo artifact type

| Artifact | Pattern | Ví dụ |
|----------|---------|-------|
| Partner ID (canonical) | `{partnerId}` | `energy-optimizer` |
| DB tenant_config | `tenants.tenant_config.partner_id = '{partnerId}'` | `'energy-optimizer'` |
| Spring Profile name | `partner-{partnerId}` | `partner-energy-optimizer` |
| Spring Profile file | `application-partner-{partnerId}.yml` | `application-partner-energy-optimizer.yml` |
| Helm values file | `values-partner-{partnerId}.yaml` | `values-partner-energy-optimizer.yaml` |
| Frontend theme file | `{partnerId}.theme.ts` | `energy-optimizer.theme.ts` |
| Extension module dir | `partner-extensions/partner-{partnerId}/` | `partner-extensions/partner-energy-optimizer/` |
| Maven artifactId | `partner-{partnerId}` | `partner-energy-optimizer` |
| Config property | `uip.partner.extensions.{partnerId}` | `uip.partner.extensions.energy-optimizer` |

### Validation

Backend enforce validation tại `TenantService.validatePartnerId()`:

```java
public class TenantService {

    private static final Pattern PARTNER_ID_PATTERN =
        Pattern.compile("^[a-z][a-z0-9-]{1,30}[a-z0-9]$");

    public void validatePartnerId(String partnerId) {
        if (partnerId == null || !PARTNER_ID_PATTERN.matcher(partnerId).matches()) {
            throw new InvalidPartnerIdException(
                "Partner ID must be 3-30 chars, lowercase, kebab-case, "
                + "start with letter, end with alphanumeric. Got: " + partnerId);
        }

        long segments = Arrays.stream(partnerId.split("-"))
            .filter(s -> !s.isEmpty())
            .count();
        if (segments < 2 || segments > 3) {
            throw new InvalidPartnerIdException(
                "Partner ID must have 2-3 segments. Got " + segments + ": " + partnerId);
        }
    }
}
```

Frontend cũng validate trước khi submit form tạo tenant:

```typescript
const PARTNER_ID_REGEX = /^[a-z][a-z0-9-]{1,30}[a-z0-9]$/;
const SEGMENT_COUNT = (id: string) => id.split('-').filter(Boolean).length;

function validatePartnerId(id: string): string | null {
  if (!PARTNER_ID_REGEX.test(id)) {
    return 'Partner ID: 3-30 ký tự, lowercase, kebab-case, bắt đầu bằng chữ cái';
  }
  if (SEGMENT_COUNT(id) < 2 || SEGMENT_COUNT(id) > 3) {
    return 'Partner ID: 2-3 segments phân tách bằng dấu gạch ngang';
  }
  return null;
}
```

### Partner ID Registry

Maintain danh sách partner ID đã dùng tại `docs/mvp2/architecture/partner-registry.md`:

```markdown
## Partner ID Registry

| Partner ID | Tên hiển thị | Domain | Tier | Ngày tạo | Ghi chú |
|-----------|-------------|--------|------|---------|---------|
| energy-optimizer | EcoCity Energy Platform | Energy | T2 | 2026-04-28 | ADR-019 Partner A |
| citizen-first | CityLink Citizen Portal | Citizen | T2 | 2026-04-28 | ADR-019 Partner B |
| traffic-analytics | SmartTraffic Authority | Traffic | T3 | 2026-04-28 | ADR-019 Partner C |
```

Khi thêm partner mới:
1. Check registry để đảm bảo không trùng ID
2. Validate format theo regex
3. Thêm vào registry
4. Tạo các artifact theo mapping table ở trên

---

## Reasoning

### Tại sao chọn descriptive names thay vì abbreviations

- `energy-optimizer` truyền tải ý nghĩa ngay lập tức, `energy-a` thì không. Khi đọc log, config file, hay Helm values, dev/ops không cần tra mapping table.
- Giảm cognitive load khi onboarding dev mới hoặc khi ops troubleshoot production issue.
- Abbreviations như `eo` hay `ea` dễ trùng lặp khi số partner tăng.

### Tại sao chọn kebab-case

- **Spring Profile**: Spring Boot dùng `-` trong profile names (`production`, `partner-energy-optimizer`). Kebab-case là convention tự nhiên.
- **Helm values**: Kubernetes/YAML convention là kebab-case.
- **URL-friendly**: Partner ID có thể xuất hiện trong URL path hoặc query param.
- **File system**: Kebab-case hoạt động tốt trên mọi OS, không cần escape.

### Tại sao 2-3 segments

- 1 segment (`energy`) — quá chung chung, không phân biệt được nhiều partner cùng domain.
- 2 segments (`energy-optimizer`) — sweet spot: đủ descriptive, đủ ngắn.
- 3 segments (`energy-optimizer-pro`) — chấp nhận được cho sub-variant.
- 4+ segments — quá dài, khó đọc, thường là dấu hiệu cần refactor naming.

---

## Consequences

### Positive
- **Single source of truth**: Mọi artifact (file name, DB value, config key, Maven artifactId) dùng chung một partner ID canonical.
- **Searchability**: `grep -r "energy-optimizer"` tìm được mọi reference xuyên suốt backend, frontend, infra.
- **Validation**: Backend + frontend enforce format, prevent invalid ID vào hệ thống.
- **Onboarding**: Dev mới đọc tên partner ID là hiểu context, không cần tra bảng mapping.

### Trade-offs
- Partner ID dài hơn abbreviations — tăng nhẹ config file size, nhưng negligible.
- Cần maintain registry file — overhead nhỏ, nhưng đảm bảo không trùng lặp.

### Action Items

- [ ] Cập nhật ADR-019: đổi `energy-a` thành `energy-optimizer`, `citizen-b` thành `citizen-first`, `traffic-c` thành `traffic-analytics` trong mọi ví dụ
- [ ] Cập nhật ADR-019 SQL examples: `energy-partner-a` thành `energy-optimizer`
- [ ] Tạo `docs/mvp2/architecture/partner-registry.md` với 3 partner hiện tại
- [ ] Implement `TenantService.validatePartnerId()` trong backend
- [ ] Implement frontend validation trong tenant management form
- [ ] CI check: thêm lint rule reject partner ID không match regex trong config files

---

## Related ADRs

| ADR | Liên quan |
|-----|----------|
| ADR-019 | Partner Customization Architecture — partner ID được dùng xuyên suốt 3 layers |
| ADR-010 | Multi-Tenancy — tenant_config.partner_id lưu partner ID theo convention này |
| ADR-011 | Monorepo — extension module directory naming theo `partner-{partnerId}` |
