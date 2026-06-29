# ADR-049: NL Model Residency — Hybrid gdpr_mode Routing for Vietnamese NL→BPMN

**Date**: 2026-06-26
**Status**: Accepted
**Priority**: P1 (MVP5 compliance gate — Decree 13/2023/ND-CP data residency)
**Sprint**: M5-2
**Author**: Solution Architect + Security Contractor
**Related**: ADR-048 (Compose HA topology — NL services run same infra), GAP-2 compliance gap
**Supersedes**: none (new decision for NL feature)
**Next action**: M5-3 — Backend-2 wires ModelRouter into BPMN synthesis service

---

## 1. Problem Statement (GAP-2 Compliance Risk)

### 1.1 Feature context

NL→BPMN feature lets Vietnamese city operators type natural-language commands — e.g.,
`"tắt đèn khu vực lũ, kích hoạt bơm thoát nước"` — and auto-generate BPMN workflow
drafts for review. This is a **P1 MVP5 feature** (operator productivity for incident
response) and a demo anchor for the 50-building commercial pitch.

### 1.2 Compliance gap

The NL text entered by operators **may contain personal identifiable information (PII)**:

- Operator personal names (e.g., `"giao cho anh Nguyễn Văn A điều phối"`)
- Citizen identifiers in incident descriptions (e.g., `"hộ gia đình số 12, CCCD 012...")`)
- Location data combined with named individuals

Under **Decree 13/2023/ND-CP** (Vietnam Personal Data Protection Decree, effective
01-07-2023), PII of Vietnamese data subjects **must not be transferred outside Vietnam**
without explicit consent and cross-border transfer approval from the Ministry of Public
Security (Article 25). Using Claude API (Anthropic, US infrastructure) for PII-containing
text is **non-compliant** without a transfer mechanism that does not currently exist.

### 1.3 What is NOT a gap

Non-PII operator commands — district names, sensor types, standard workflow intents —
do not carry personal data. Routing these through Claude API (higher quality BPMN
generation) is **permitted** and preferred.

**→ The system needs a routing mechanism, not a cloud ban.**

---

## 2. Options Considered

### Option A: Cloud-only (Claude API for all requests)

Route all NL→BPMN requests through Claude API.

| | |
|---|---|
| **Pro** | Best BPMN quality; no GPU infra cost; fastest time-to-demo |
| **Con** | **Violates Decree 13** when PII present; no PII control mechanism; legal risk blocks commercial signing |
| **Verdict** | ❌ REJECTED — compliance blocker |

### Option B: On-prem only (ViT5 local model for all requests)

Route all NL→BPMN through on-prem ViT5 fine-tuned model on UIP infra.

| | |
|---|---|
| **Pro** | Full Decree 13 compliance; no cloud dependency; zero data egress |
| **Con** | ViT5 BPMN quality significantly lower than Claude; p95 latency ~8s vs ~4s; requires GPU node maintenance; MVP demo risk |
| **Verdict** | ❌ REJECTED — quality gap unacceptable for commercial pitch at 50-building scale |

### Option C: Hybrid — gdpr_mode flag + content-based PII scan (CHOSEN)

Operator request carries a `X-GDPR-Mode: true/false` header. A `ModelRouter` component
performs: (1) trust the explicit flag; (2) for `gdpr_mode=false`, run a lightweight PII
scan and escalate to on-prem if PII detected. Non-PII → Claude API; PII or gdpr_mode →
local ViT5.

| | |
|---|---|
| **Pro** | Decree 13 compliant; preserves Claude quality for non-PII; operator retains control; auditable routing decision |
| **Con** | PII scan adds ~50ms latency; false-positive rate on Vietnamese names may over-route to on-prem; requires maintaining two model endpoints |
| **Verdict** | ✅ CHOSEN — balances compliance, quality, and operational simplicity |

---

## 3. Decision: Hybrid Routing (D2)

**The system MUST implement hybrid model routing governed by a `gdpr_mode` flag plus
content-based PII fallback.**

Key invariants:

1. `gdpr_mode=true` → **always route to on-prem ViT5**, never send to cloud API
2. `gdpr_mode=false` → run PII scan; if PII detected, escalate to on-prem (same as `gdpr_mode=true`)
3. If on-prem inference fails with `gdpr_mode=true` or PII detected → **reject with 503**, do NOT silently fall back to cloud
4. PII routing decisions MUST be logged (no PII content in log, only the routing decision: `routed=local, reason=pii_detected`)
5. The `gdpr_mode` default is `false` for system-generated calls; operator-facing UI sets `true` by default for all operator text inputs

---

## 4. ModelRouter Architecture

### 4.1 Component diagram

```
Operator UI / API caller
        │
        │  POST /api/v1/nl/parse
        │  Header: X-GDPR-Mode: true|false
        │  Body: { "text": "...", "workflow_context": "..." }
        ▼
┌───────────────────────────────────────────────────────┐
│                   NL Parse Controller                  │
│  (Spring @RestController, validates request, extracts │
│   gdpr_mode header, builds NLParseRequest DTO)        │
└──────────────────┬────────────────────────────────────┘
                   │ NLParseRequest
                   ▼
┌───────────────────────────────────────────────────────┐
│                    ModelRouter                         │
│                                                        │
│  1. if gdpr_mode == true → route = LOCAL               │
│  2. else: run PiiScanner.scan(text)                    │
│     → if PII detected → route = LOCAL                 │
│     → if clean        → route = CLOUD                 │
│                                                        │
│  Returns: RoutingDecision { route, reason, scanMs }   │
└──────────┬──────────────────────────┬─────────────────┘
           │ route=LOCAL              │ route=CLOUD
           ▼                          ▼
┌──────────────────────┐   ┌─────────────────────────────┐
│  LocalInferenceClient│   │   ClaudeApiClient            │
│  (underthesea +      │   │   (Anthropic SDK)            │
│   ViT5 HTTP endpoint │   │   BPMN generation prompt     │
│   on UIP GPU node)   │   │   structured output          │
│  p95 ≤ 8s            │   │   p95 ≤ 4s                   │
│  timeout = 10s       │   │   timeout = 6s               │
└──────────┬───────────┘   └─────────────┬───────────────┘
           │                             │
           └─────────────┬───────────────┘
                         │ NLParseResponse
                         ▼
                 NL Parse Controller
                 (returns to caller)
```

### 4.2 ModelRouter interface contract (for Backend-2 T02)

```java
/**
 * Routes NL→BPMN inference requests based on gdpr_mode flag
 * and content-based PII detection.
 *
 * Implementors MUST be thread-safe.
 */
public interface ModelRouter {

    /**
     * @param request  the incoming NL parse request (immutable DTO)
     * @return routing decision — never null
     * @throws ModelRouterException if routing itself fails (config error, etc.)
     */
    RoutingDecision route(NLParseRequest request);
}

// ---- DTOs ----

public record NLParseRequest(
    String text,              // raw operator input — may contain PII
    String workflowContext,   // e.g. "flood_response", "energy_mgmt"
    boolean gdprMode,         // from X-GDPR-Mode header; default true for operator UI
    String tenantId,          // from JWT claim, injected by controller
    String requestId          // for audit log correlation
) {}

public enum Route { LOCAL, CLOUD }

public record RoutingDecision(
    Route route,
    RoutingReason reason,     // GDPR_MODE_SET | PII_DETECTED | NO_PII
    long piiScanMs            // 0 if gdpr_mode=true (scan skipped)
) {}

public enum RoutingReason {
    GDPR_MODE_SET,   // explicit flag; scan was skipped
    PII_DETECTED,    // scan found PII pattern in text
    NO_PII           // scan ran and found nothing; routed to cloud
}
```

### 4.3 Failure behaviour

| Scenario | Behaviour |
|---|---|
| On-prem ViT5 timeout (10s) + route=LOCAL | Return HTTP 503 `{"error":"nl_inference_unavailable","route":"local"}` |
| On-prem ViT5 timeout (10s) + route=CLOUD (no PII) | Retry once (circuit breaker), then 503 |
| Claude API timeout (6s) + route=CLOUD | Retry once, then **do NOT fall back to on-prem** — return 503 |
| PII detected but gdpr_mode=false | Override route to LOCAL, log `reason=pii_detected` |
| gdpr_mode=true AND on-prem down | **Reject** with 503 — never re-route to cloud |

**Fail-closed for PII.** The system must never transmit PII to cloud infrastructure even
under degraded conditions.

---

## 5. PII Classification Rules

### 5.1 What IS PII in the NL→BPMN context

| Category | Example | Detection |
|---|---|---|
| Vietnamese personal name (full name) | `Nguyễn Văn A`, `Trần Thị B` | Regex: Vietnamese name pattern (see §5.3) |
| Citizen ID / CCCD | `012 345 678 901` | Regex: 12-digit CCCD pattern |
| Phone number | `0912 345 678` | Regex: VN phone patterns |
| Passport number | `B1234567` | Regex: VN passport pattern |
| Specific address with named person | `"nhà ông Minh tại 12 Lê Lợi"` | Name + address co-occurrence |

### 5.2 What is NOT PII

| Category | Example | Rationale |
|---|---|---|
| District / ward names | `"quận 7"`, `"phường Bến Nghé"` | Administrative geography, not personal data |
| Sensor IDs / device codes | `"sensor AQI-0042"`, `"pump P-03"` | Technical identifiers |
| Workflow intent keywords | `"tắt đèn"`, `"kích hoạt bơm"` | Operational commands |
| Building / asset names | `"Tòa nhà A"`, `"Trạm bơm quận 4"` | Infrastructure references |
| Job roles without names | `"kỹ thuật viên"`, `"điều phối viên"` | Role description, not identity |

### 5.3 PII scanner implementation (Phase 1: regex)

```java
// Phase 1: regex patterns (ship in M5-3)
// Phase 2: underthesea NER integration (M5-4 if false-positive rate > 5%)

public final class VietnamesePiiPatterns {

    // Full Vietnamese name: 2–4 words, first word is a common Vietnamese surname
    // Conservative: require at least surname + middle + given name for "full name" detection
    public static final Pattern FULL_NAME = Pattern.compile(
        "\\b(Nguyễn|Trần|Lê|Phạm|Huỳnh|Hoàng|Phan|Vũ|Võ|Đặng|Bùi|Đỗ|Hồ|Ngô|Dương|Lý)" +
        "\\s+[A-ZĐÀÁÂÃÈÉÊÌÍÒÓÔÕÙÚÝĂĐ][a-zđàáâãèéêìíòóôõùúýăđ]+" +
        "(\\s+[A-ZĐÀÁÂÃÈÉÊÌÍÒÓÔÕÙÚÝĂĐ][a-zđàáâãèéêìíòóôõùúýăđ]+)+\\b"
    );

    // CCCD: 12 digits, optionally spaced in groups of 3
    public static final Pattern CCCD = Pattern.compile(
        "\\b\\d{3}[\\s-]?\\d{3}[\\s-]?\\d{3}[\\s-]?\\d{3}\\b"
    );

    // Vietnamese phone: 10 digits starting 03x/05x/07x/08x/09x or +84
    public static final Pattern PHONE = Pattern.compile(
        "(\\+84|0)(3[2-9]|5[6-9]|7[0-9]|8[0-9]|9[0-9])[\\s.-]?\\d{3}[\\s.-]?\\d{4}\\b"
    );

    // Passport: B + 7 digits (common VN format)
    public static final Pattern PASSPORT = Pattern.compile("\\bB\\d{7}\\b");

    public static boolean hasPii(String text) {
        return FULL_NAME.matcher(text).find()
            || CCCD.matcher(text).find()
            || PHONE.matcher(text).find()
            || PASSPORT.matcher(text).find();
    }
}
```

**Phase 2 trigger:** If production monitoring shows false-positive rate > 5% (legitimate
non-PII text being routed to slower on-prem), integrate `underthesea` NER for Vietnamese
person entity detection. This is a M5-4 decision gate, not M5-3.

---

## 6. SLA Table

| Metric | Claude API (CLOUD) | ViT5 On-prem (LOCAL) | Acceptable? |
|---|---|---|---|
| p50 latency | ~1.5s | ~4.5s | ✅ both acceptable |
| p95 latency | **≤ 4s** | **≤ 8s** | ✅ both within 10s UX budget |
| Timeout threshold | 6s | 10s | Circuit breaker at threshold |
| Intent hit rate (10 MVP4 intents) | ~92% (Claude BPMN quality) | **≥ 80% minimum** | 80% gate for M5-3 acceptance |
| BPMN validity (parseable by bpmn-js) | ≥ 95% | ≥ 85% | Both gate thresholds |
| Availability SLA (on-prem) | N/A (external) | ≥ 99% (GPU node + watchdog) | GPU watchdog in M5-3 scope |
| PII scan overhead | ~50ms (regex) | ~50ms (regex) | Negligible vs total latency |

**Intent coverage test set** (must pass before M5-3 acceptance):
`flood_response`, `energy_mgmt`, `air_quality_alert`, `traffic_signal`,
`pump_activation`, `light_control`, `sensor_disable`, `escalate_incident`,
`maintenance_schedule`, `report_generation`

---

## 7. DPIA Skeleton

_This skeleton is authored by the Solution Architect. Formal sign-off by Security
Contractor is required in M5-4 before production deployment._

### 7.1 Data subject

City operators (nhân viên vận hành thành phố) employed by municipal department customers.
Not general citizens.

### 7.2 Processing purpose

Automated conversion of natural-language operator commands to BPMN workflow drafts,
enabling faster incident response orchestration within the city operations platform.

### 7.3 Data categories processed

| Category | Presence | Notes |
|---|---|---|
| Operator text input | Always | May include personal identifiers (names, IDs) incidentally |
| Personal names | Conditional | Only if operator includes in command text |
| Location + identity co-occurrence | Conditional | E.g. assigning a named person to a location |
| Citizen data (indirect) | Rare | Operator may reference a citizen's ID in an incident; treated as PII |

No biometric, health, or financial data expected in NL→BPMN commands.

### 7.4 Lawful basis

**Article 17, Decree 13/2023/ND-CP** — Processing for legitimate interests of the data
controller (city authority) where operator commands are necessary for public safety and
city service delivery. Risk is low because:
- Data subjects are employees, not vulnerable citizens
- Processing is for operational efficiency, not profiling
- Minimal data retention (no persistent storage of NL command text — see §7.6)

### 7.5 Cross-border transfer assessment

| Route | Transfer to third country? | Mechanism |
|---|---|---|
| `gdpr_mode=true` or PII detected → LOCAL | ❌ No transfer | On-prem ViT5, Vietnam infra |
| No PII detected → CLOUD (Claude API) | ✅ Transfers to US | **Non-personal data only** (workflow intents, district/sensor names); no Decree 13 obligation |

**Key assertion:** The `ModelRouter` ensures PII never reaches the Claude API endpoint.
This assertion must be validated by the Security Contractor review in M5-4.

### 7.6 Risks and controls

| Risk | Likelihood | Impact | Control |
|---|---|---|---|
| PII scanner false-negative (PII reaches Claude API) | Low (regex coverage ≥ 95%) | High (Decree 13 violation) | `gdpr_mode=true` default for operator UI; PII scan as second layer |
| NL command text logged at API gateway (Kong) | Medium (Kong access logs) | Medium (PII in logs) | Kong log masking rule for `/api/v1/nl/*` request body (M5-3 DevOps task) |
| On-prem GPU node compromise | Low | High | GPU node on isolated VLAN, no internet egress, mTLS to backend |
| Operator enters citizen PII intentionally in command | Low | Medium | UX warning in operator UI ("không nhập thông tin cá nhân"); not a technical control |

### 7.7 Retention

- NL command text: **NOT stored** beyond inference call. No persistent log of raw input.
- Routing decision log (no text content): retained 90 days for audit, then purged.
- Generated BPMN draft: stored as workflow artifact under normal retention policy (not PII).

### 7.8 DPIA conclusion (preliminary)

**Risk level: LOW** — provided controls in §7.6 are implemented. The mandatory controls
for M5-3 are:

1. Kong body masking for `/api/v1/nl/*` (DevOps)
2. `gdpr_mode=true` as default in operator UI (Frontend)
3. No NL text in application logs (Backend — use `requestId` only)
4. On-prem ViT5 endpoint isolated from internet (DevOps/Infra)

Formal DPIA sign-off by Security Contractor: **target M5-4 before production go-live.**

---

## 8. Consequences

### 8.1 Positive

- Full Decree 13/2023/ND-CP compliance for PII paths
- Preserves Claude BPMN quality for ~80–90% of real operator commands (non-PII)
- Auditable routing with no PII in audit log
- Clear extension path: Phase 2 NER upgrade is isolated in `PiiScanner` without changing `ModelRouter` contract

### 8.2 Negative / Trade-offs

- On-prem GPU node required from M5-3 — DevOps must provision before T02 acceptance test
- ViT5 BPMN quality is lower; 80% intent hit rate may require fine-tuning on real
  operator commands (feedback loop needed post-pilot)
- PII scan ~50ms per request; acceptable but adds latency for all NL calls
- Two model endpoints to maintain and monitor

### 8.3 Open decisions (for Sec contractor — M5-4 DPIA sign-off)

1. **False-negative tolerance**: Is the current regex-based scanner sufficient, or must
   underthesea NER be mandatory for production launch? Security team to define acceptable
   false-negative rate.
2. **Kong body masking**: Confirm whether Kong's `request-transformer` plugin or a custom
   Lua plugin is required for body masking on `/api/v1/nl/*`. DevOps to prototype.
3. **Operator consent mechanism**: Do city authority operators require explicit consent
   notice for NL command processing? Legal team to advise per Decree 13 Article 11.
4. **Audit log retention**: Is 90-day routing decision log sufficient for compliance
   audit, or does the authority require longer (Article 37 Decree 13 — 5 years for
   certain processing records)?
5. **ViT5 model provenance**: Sec to confirm ViT5 fine-tuning data does not include
   personal data from prior incidents (model training data DPIA).

### 8.4 M5-3 follow-up tasks

| Task | Owner | Depends on |
|---|---|---|
| Implement `ModelRouter` + `PiiScanner` (regex Phase 1) | Backend-2 | This ADR (interface contract §4.2) |
| Wire `ModelRouter` into BPMN synthesis service | Backend-2 | T02 NL parser POC |
| Provision on-prem ViT5 endpoint + GPU watchdog | DevOps | ADR-048 infra |
| Kong body masking for `/api/v1/nl/*` | DevOps | Kong config |
| Operator UI: set `X-GDPR-Mode: true` default | Frontend | ModelRouter API |
| Intent hit-rate acceptance test (10 intents) | QA | ViT5 endpoint live |

---

## Appendix A: Reference

- Decree 13/2023/ND-CP — Vietnamese Personal Data Protection Decree (effective 01-07-2023)
- ADR-047 — ClickHouse Row-Level Policy for Tenant Isolation
- ADR-048 — Compose HA Test Topology (NL services run in same infra)
- `docs/mvp5/brainstorm/sa-mvp5-conflict-resolution.md` §D2 — Hybrid model decision
- `docs/mvp5/plans/mvp5-sprint-plan.md` §M5-2 — Sprint planning context
- underthesea (Vietnamese NLP library): https://github.com/undertheseanlp/underthesea
- ViT5 (Vietnamese T5-based model): pretrained base, fine-tuned on UIP intent classification corpus
