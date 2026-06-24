# MVP5 Sprint M5-2 — Kickoff Plan + Risk Review

| Field | Value |
|---|---|
| **Sprint** | M5-2 — NL→BPMN POC + GAP-2 residency + billing skeleton + tenant fuzz |
| **Dates** | 2026-10-05 → 2026-10-18 (window chính thức) |
| **SP committed** | 43 |
| **Gate** | M5-G2 (tenant isolation fuzz 2-3 tenant 0 leak + cache-key namespace audit + CH RowPolicy synthetic) |
| **Author** | PM |
| **Date** | 2026-06-24 |
| **Source of truth** | `mvp5-sprint-plan.md` §Sprint M5-2 |
| **Status** | PLANNED — pending G1 PASS (T09/T12/T16 close M5-1) |

> Task-level detail 16 task xem `mvp5-sprint-plan.md` §Sprint M5-2 table. Doc này tập trung: (1) dependency readiness từ M5-1 output thật, (2) risk review R16/R2/R5 + mitigation sprint này, (3) sequencing khuyến nghị.

---

## §1. Dependency readiness (input từ M5-1 thực tế)

| M5-2 task | Dependency M5-1 | M5-1 status | Sẵn sàng? |
|---|---|---|---|
| T05 Tenant fuzz (G2) | T04 RowPolicy + T06 cache namespace | ✅ DONE | ✅ Sẵn — fuzz exercise thật RowPolicy + cache namespace |
| T06 CH partition synthetic | T12 synthetic scaffold | ⏳ T12 đang làm | 🟡 Phụ thuộc T12 xong đầu M5-2 |
| T02 NL parser | T01 ADR-049 GAP-2 | ⬜ T01 là task M5-2 (SA+Sec) | ✅ T01 mở sprint, T02 tuần 2 |
| T07 Billing skeleton | (độc lập) | — | ✅ Sẵn |
| T09 Vault rotation | T03 Vault backbone | ✅ DONE | ✅ Sẵn — T03 backbone có, rotation = M5-2 |
| T12 Flink tenant IT multi-tenant | T05 Flink tenant delegate | ✅ DONE | ✅ Sẵn |
| T14 K8s Helm skeleton | T01 ADR-050 | ✅ DONE | ✅ Sẵn |

**Kết luận:** Không có M5-2 task nào bị block bởi M5-1 pending (T09 mTLS, T12 synthetic scaffold). T06 synthetic phụ thuộc T12 scaffold — sequencing: T12 scaffold phải xong đầu tuần 1 M5-2.

**Quan trọng — carry từ M5-1 sang M5-2 (debt):**
- Vault per-consumer wiring (`env_file` mount từng service) — defer T03 → làm đầu M5-2 cùng T09 (rotation). Owner Backend+DevOps.
- G1 PASS còn chờ T09/T12/T16 — không block M5-2 planning nhưng cần close trước G2.

---

## §2. Risk review (R16, R2, R5) — sprint này

### R16 — build-for-50 chưa exercise scale (HIGH/MED) — xuyên suốt M5-1→M5-5
- **M5-1 status:** T12 synthetic scaffold đang làm. T04/T05/T06 tenant isolation đã exercise ở đơn-vị nhỏ (IT test, không phải 50 tenant).
- **M5-2 mitigation:** T06 (CH partition hotspot scan, synthetic) — tenant partition skew < 20%. Phụ thuộc T12 scaffold.
- **Watch:** nếu scaffold (T12) trễ → T06 M5-2 trễ → lan G2. **Action:** T12 phải DONE đầu tuần 1 M5-2.

### R2 — NL hallucination (HIGH/HIGH) — peak M5-2/M5-3
- **M5-2 status:** NL parser POC (T02) + template grounding (T03). Chưa có UAT (G3).
- **M5-2 mitigation:** T03 template grounding constrain generation 10 BPMN templates (chặn hallucination). T10 BPMN validation schema draft (fail-before-UI ở M5-3).
- **Watch:** parser intent hit rate < 80% → fallback local model. **Action:** T02 phải có 50-sentence corpus test ngay tuần 1 để đo hit rate sớm.

### R5 — GAP-2 NL residency P0 (HIGH/MED) — critical path compliance
- **M5-2 status:** T01 ADR-049 (D2 = Hybrid: gdpr_mode→on-prem cho PII) + DPIA skeleton. Block T02 NL parser + toàn bộ compliance chain → G6 → G8.
- **M5-2 mitigation:** T01 mở sprint (SA+Sec), chốt model hybrid routing đầu tuần 1. ModelRouter hook `gdpr_mode` wire vào T03.
- **Watch:** nếu ADR-049 trễ → T02/T03 trễ → NL→BPMN G3 bị ép. **Action:** T01 phải DONE tuần 1, không phải cuối sprint.

**Top 3 risk giữ nguyên từ sprint plan §6:** R16 (HIGH/MED), R2 (HIGH/HIGH), R5 (P0, HIGH/MED).

---

## §3. Sequencing khuyến nghị (tuần 1 → tuần 2)

**Tuần 1 (blocker-first):**
1. T01 ADR-049 GAP-2 (SA+Sec) — mở sprint, chốt hybrid model
2. T07 Billing skeleton (Data Eng) — độc lập, chạy song song
3. T12 Flink tenant IT multi-tenant (Backend-1) — T05 sẵn
4. T09 Vault rotation (DevOps) — T03 sẵn
5. T05 Tenant fuzz prep (QA+Backend-1) — T04/T06 sẵn
6. T13 LOTUS prep + ROI AC stub (BA) — độc lập

**Tuần 2 (dependent):**
7. T02 NL parser POC (Backend-2) — sau T01
8. T03 Template grounding (Backend-2) — sau T02
9. T06 CH partition synthetic (QA) — sau T12 M5-1 scaffold
10. T10 BPMN schema draft (SA) — sau T03
11. T04 Operator review UI (UX+Frontend-1) — sau T02
12. T11 Billing dashboard skeleton (Frontend-2) — sau T07
13. T14 K8s Helm skeleton (DevOps) — sau ADR-050
14. T15 NL UAT recruitment (PM+UX)
15. **T16 Gate M5-G2** (QA+SA) — tổng hợp fuzz + namespace + RowPolicy synthetic

---

## §4. DoD M5-2 + Gate G2 criteria

- 16 task DONE (executable artifact)
- Tenant fuzz 0 leak (2-3 tenant)
- NL parser POC ≥ 80% intent hit
- Billing ledger schema migrated
- **Gate G2 artifact:** fuzz test report 2-3 tenant (0 leak) + cache-key namespace audit + CH RowPolicy synthetic multi-tenant test

---

## §5. Go/No-Go cho M5-2

**No-Go blockers (phải close trước G2):**
- ~~G1 chưa PASS~~ ✅ **G1 PASS 2026-06-24** (16/16 task DONE, 4/4 criteria).
- ~~T12 M5-1 synthetic scaffold~~ ✅ DONE 2026-06-24 — T06 M5-2 sẵn sàng.

**Go conditions:** tất cả No-Go cleared → **M5-2 GO** ✅.

*Authored 2026-06-24. M5-2 kickoff source: `mvp5-sprint-plan.md` §Sprint M5-2 + M5-1 audit thực tế.*
