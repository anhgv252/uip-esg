# ADR-044: Operator Self-Service Architecture — Template Library + Wizard

| Field | Value |
|---|---|
| **ADR Number** | ADR-044 |
| **Title** | Operator Self-Service Architecture — Template Library + No-Code Wizard |
| **Status** | Accepted |
| **Date** | 2026-06-12 |
| **Author** | Solution Architect |
| **Sprint** | MVP4-S3/S4/S5 (Task #14/#18/#22 — M4-SS-01/02/03) |
| **Supersedes** | — |
| **Related ADRs** | ADR-039 (OpenAPI), ADR-042 (Correlation), ADR-046 (Feedback Loop) |

---

## Context

MVP3 operator workflows required a developer to hand-author BPMN XML, register it in Camunda, and wire trigger config. At MVP4's target of 50+ buildings, this is the dominant bottleneck: every new use case ("alert me when District 7 noise exceeds 80 dB after 22:00") queues behind backend engineering.

MVP4 Trụ 3 goal: **80% of workflows created by operators without a developer.** Three sub-features deliver this:

| ID | Feature | Sprint |
|---|---|---|
| M4-SS-01 | Workflow Template Library — 10–15 pre-built templates | S3-S4 |
| M4-SS-02 | No-code Trigger Config Wizard UI | S4-S5 |
| M4-SS-03 | BPMN NodePalette drag-and-drop (MVP3 carry-over) | S2 |

The architecture question: **how does a template become a running workflow without code?**

---

## Decision

**A three-stage pipeline: Template (frontend data) → Wizard (param customisation) → WorkflowDefinition (Camunda deploy). Templates are declarative TypeScript objects; the wizard binds params; the backend materialises a WorkflowDefinition per tenant.**

### Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│  STAGE 1 — TEMPLATE LIBRARY  (M4-SS-01)                            │
│  frontend/src/components/workflow/templates/*.ts                   │
│  Each template: WorkflowTemplate {                                 │
│    id, name, category, icon, params[], bpmnKey, tags               │
│  }                                                                  │
│  Categories: FLOOD, AIR_QUALITY, EQUIPMENT, ESG, COMPLAINT         │
│  TemplateParam: {key, label, type, required, defaultValue, options}│
└──────────────────────────────────┬─────────────────────────────────┘
                                   │ operator selects one
                                   ▼
┌────────────────────────────────────────────────────────────────────┐
│  STAGE 2 — WIZARD UI  (M4-SS-02)                                   │
│  TemplateGallery → param form → preview BPMN → "Deploy"            │
│  Form types: string | number | boolean | select                    │
│  Each param has defaultValue — wizard is non-empty on open         │
│  NodePalette (M4-SS-03) allows advanced users to edit BPMN nodes   │
└──────────────────────────────────┬─────────────────────────────────┘
                                   │ POST /api/v1/workflows (bound params)
                                   ▼
┌────────────────────────────────────────────────────────────────────┐
│  STAGE 3 — BACKEND MATERIALISE  (WorkflowDefinitionService)        │
│  1. Resolve bpmnKey → Camunda process definition                  │
│  2. Bind operator params into TriggerConfig                        │
│  3. Persist WorkflowDefinition (tenant-scoped)                    │
│  4. Register trigger with workflow engine                          │
│  5. Workflow runs on next matching sensor event                    │
└────────────────────────────────────────────────────────────────────┘
```

### Design choices

| Choice | Rationale |
|---|---|
| **Templates are TypeScript data, not BPMN** | A template is a *param schema* + a *bpmnKey pointer*. The BPMN lives in Camunda, versioned server-side. Operators configure params; they never edit BPMN directly (NodePalette is the escape hatch for power users). This keeps BPMN authoring a backend concern while making *configuration* a frontend concern. |
| **`bpmnKey` indirection** | The template references a Camunda process by key, not by XML. Swap the BPMN behind a key (bug fix, optimisation) and every template using it picks up the change — no template republish. |
| **Param `type` enum (string/number/boolean/select)** | Covers every configuration we've encountered. `select` with `options[]` handles enums (severity levels, channels). No free-form JSON — keeps the wizard form-renderable. |
| **`defaultValue` on every param** | The wizard opens with a valid, deployable workflow. Operators tweak rather than fill blanks — lowers the cognitive barrier to first deployment. |
| **Tenant-scoped WorkflowDefinition** | Each tenant's customised template is a separate row. Tenant A's "flood alert at 1.5m" doesn't leak to Tenant B. Enforced by the existing multi-tenancy filter (ADR-010). |
| **Category enum** | Drives the gallery's filter chips. Five categories cover the 10–15 templates; extensible without a migration (TS union type). |

### Template inventory (MVP4-S4)

| Category | Templates |
|---|---|
| FLOOD | flood-alert |
| AIR_QUALITY | air-quality-alert |
| EQUIPMENT | building-safety |
| ESG | esg-report |
| (more) | + complaint, noise-threshold, energy-recommendation, etc. |

Target: 10–15 templates operator-verifiable (G3 gate).

### NodePalette (M4-SS-03) — the escape hatch

The drag-and-drop BPMN node palette (MVP3 deferred, completed S2) lets a power user open the underlying BPMN and add/reorder nodes (AI decision, delegate, notification). This is the **20% path** — the 80% path is template + wizard only. The palette is gated behind an "Advanced mode" toggle so casual operators never see raw BPMN.

---

## Consequences

### Positive

- **80% no-code target is architectural.** Templates + typed params + defaults mean most workflows are a 3-click deploy. Verified in Sprint 4/5 UAT (`sprint4-template-uat.md`, `sprint5-wizard-uat.md`).
- **Backend stays in charge of BPMN.** Operators can't break a workflow by misconfiguring params — the BPMN is fixed, only the trigger threshold/channel changes. Worst case: an alert fires too often, tunable via ADR-046 feedback loop.
- **Template versioning is free.** A template is a TS file under git; a fix ships with the frontend. No DB migration, no Camunda redeploy for param-schema changes.
- **Mobile-friendly.** The wizard's form-based UI ports directly to the operator mobile app — no BPMN canvas on a phone.

### Negative

- **Template library needs curation.** A bad template (wrong default, missing param) propagates to every operator who uses it. Mitigated: templates are code-reviewed, UAT'd before merge.
- **`bpmnKey` is a silent dependency.** If a Camunda process key is renamed, templates pointing at it break at deploy time, not at template-edit time. Mitigated: bpmnKey is stable by convention; rename requires a template PR in the same change.
- **No free-form logic.** An operator who needs "if AQI > 100 AND wind < 5 km/h" can't express compound conditions in the wizard today. They fall through to NodePalette (advanced) or a developer. Multi-condition triggers are an MVP5 item.

### Risks & mitigations

| Risk | Mitigation |
|---|---|
| Template param drift from BPMN variables | bpmnKey indirection + UAT per template before merge |
| Operator self-deploys a noisy trigger → alert storm | ADR-041 token budget caps AI cost; ADR-046 feedback loop surfaces noisy triggers for tuning |
| Power-user NodePalette edits break BPMN | Advanced mode is opt-in; edits validated against Camunda schema before deploy |

---

## Compliance

- **Tenant isolation**: WorkflowDefinition tenant-scoped via ADR-010 filter (SA checklist item 5).
- **OpenAPI contract**: `POST /api/v1/workflows` documented per ADR-039.
- **Accessibility**: wizard form uses MUI labels + aria-label (SA frontend item 5).
- **UAT verified**: Sprint 4 template UAT, Sprint 5 wizard UAT — ≥3 templates through wizard end-to-end.

---

## Open questions (deferred to MVP5)

1. **NL→BPMN.** "Create a flood alert for District 7" in Vietnamese → auto-pick template + fill params. The competitive moat per MVP4 README §12.
2. **Compound triggers.** Multi-condition AND/OR in the wizard without falling to NodePalette.
3. **Template marketplace.** Cross-tenant template sharing (a city publishes a template, another city imports). Needs a governance model.

---

## References

- `frontend/src/types/workflowTemplate.ts` — WorkflowTemplate / TemplateParam schema
- `frontend/src/components/workflow/templates/*.ts` — template library
- `frontend/src/components/workflow/TemplateGallery.tsx` — gallery UI
- `backend/src/main/java/com/uip/backend/aiworkflow/service/WorkflowDefinitionService.java`
- `backend/src/main/java/com/uip/backend/aiworkflow/controller/WorkflowDefinitionController.java`
- `docs/mvp4/uat/sprint4-template-uat.md`, `docs/mvp4/uat/sprint5-wizard-uat.md`
- `docs/mvp4/README.md` §2 Trụ 3, §9 ADR-044

---

*Authored 2026-06-12 — MVP4 Sprint 5 retrospective, documented in Sprint 6 close-out.*
