# ADR-050: Kubernetes Readiness-Only (Helm skeleton, defer cutover to MVP6)

**Date**: 2026-06-24
**Status**: Accepted
**Priority**: P2 (MVP5 — readiness, không cutover)
**Sprint**: M5-1
**Author**: Solution Architect
**Related**: ADR-048 (Compose HA test topology), ADR-038 (Flink CI/CD automated submission)
**Artifact**: `infra/helm/` (Helm charts), `infrastructure/k8s/` (HPA manifests)

---

## 1. Context

### 1.1 Business driver

PO decision (MVP5 synthesis): **K8s DEFER MVP6**. Pilot MVP5 chạy trên **Docker Compose HA** (ADR-048). Lý do defer:

1. **Pilot 2-3 building** — traffic thấp, Compose HA đủ, K8s overhead vận hành không tương xứng
2. **K8s cutover rủi ro cao** — StatefulSet CH/Kafka + PVC migration + Ingress + cert-manager là scope riêng, không trộn vào pilot
3. **Đầu tư K8s chỉ trả lời khi scale 10+ building** (MVP6 commercial)

**NHƯNG** PO cũng yêu cầu: source code + manifests phải **ready** để MVP6 cutover nhanh — không để MVP6 bắt đầu từ zero.

### 1.2 Current state (verified by artifact review 2026-06-24)

Đã có **Helm skeleton** (`infra/helm/`):

| Chart | Templates | Status |
|---|---|---|
| `uip-backend` | deployment, service, hpa, configmap, _helpers | ✅ Skeleton tồn tại |
| `uip-analytics-service` | deployment, service, hpa, _helpers | ✅ Skeleton tồn tại |
| `values/` | values-dev, values-staging, values-tier1, values-tier2 | ✅ 4 environment tier |

Plus `infrastructure/k8s/hpa-analytics-service.yaml`.

**Nhưng**: chưa có chart cho CH, Kafka, Kong, Keycloak, PostgreSQL, Flink (stateful services). Chưa có CI `helm lint`/`helm template` gate. Chưa deploy thật lên cluster nào.

## 2. Decision

### 2.1 Readiness-only — 3 tiêu chí "ready cho MVP6 cutover"

MVP5 kết thúc khi Helm charts thỏa mãn:

1. **`helm lint` + `helm template` PASS** trong CI — chart syntactically valid, render ra manifest đúng
2. **Every stateful service có chart** — CH (StatefulSet + PV), Kafka (StatefulSet), Keycloak, PG, Flink job submitter
3. **values-tier1/2 map đúng topology ADR-048** — replica count, resource, env vars khớp Compose HA

**KHÔNG yêu cầu trong MVP5:**
- ❌ Deploy thật lên K8s cluster (cutover)
- ❌ Production Ingress + cert-manager + external LB
- ❌ PVC migration từ Compose volume
- ❌ K8s observability stack (Prometheus operator, Grafana)

### 2.2 MVP5 deliverable (task M5-2-T14 spike)

- `helm lint` CI gate (`.github/workflows/helm-lint.yml`)
- Bổ sung chart cho stateful services còn thiếu (hoặc document dùng Helm community chart: bitnami/kafka, bitnami/postgresql, clickhouse/clickhouse-server)
- `values-tier2.yaml` = production-like (replica, resource) — **template only**, không apply

### 2.3 Cutover plan (MVP6 — out of MVP5 scope)

MVP6 sẽ:
1. Provision K8s cluster (EKS/GKE/on-prem)
2. `helm install` stateful services + migrate data từ Compose volume (pg_dump, CH backup, Kafka replay)
3. Blue-green traffic shift Compose → K8s
4. Decompose Compose HA

Đây là ADR/plan riêng MVP6 — MVP5 chỉ đảm bảo chart sẵn sàng.

## 3. Consequences

### 3.1 Positive

- MVP6 cutover không bắt đầu từ zero — chart + values ready
- Helm chart = single source of truth cho deployment manifest (thay vì YAML ad-hoc)
- `helm template` cho phép validate manifest trong CI mà không cần cluster

### 3.2 Negative / accepted risk

| Gap | Risk | Mitigation |
|---|---|---|
| **Chưa test trên cluster thật** | Chart có thể render đúng nhưng runtime sai (PVC, probe, service discovery) | MVP6 cutover có spike đầu sprint + staging cluster |
| **Stateful chart thiếu** (CH/Kafka/PG) | M5-2-T14 phải bổ sung hoặc pick community chart | Document decision trong spike |
| **No K8s observability** | Pilot không có K8s-native monitoring | Compose HA dùng Prometheus/Grafana standalone (đã có) |

### 3.3 Operational

- Pilot MVP5: **Docker Compose HA only** (ADR-048). K8s artifacts tồn tại nhưng không chạy.
- CI: thêm `helm lint` gate (M5-2-T14) — fail chart lỗi trước merge

### 3.4 M5-2-T14 Completion (2026-06-25)

Task M5-2-T14 (K8s readiness spike) **COMPLETED**. Deliverables:

| Artifact | Status | Location |
|---|---|---|
| **Helm lint CI** | ✅ Deployed | `.github/workflows/helm-lint.yml` — runs `helm lint --strict` + `helm template` on PR/push |
| **values-prod.yaml** | ✅ Created | `infra/helm/uip-backend/values-prod.yaml`, `infra/helm/uip-analytics-service/values-prod.yaml`, `infra/helm/values/values-prod.yaml` — production tier config |
| **Stateful chart decision** | ⚠️ Deferred | CH/Kafka/PG charts deferred to MVP6 cutover — bitnami/clickhouse, bitnami/kafka, bitnami/postgresql recommended |

**readiness criteria met:**
1. ✅ `helm lint` + `helm template` gate active in CI
2. ⚠️ Stateful charts deferred (documented — use bitnami community charts for MVP6)
3. ✅ `values-prod.yaml` created with production-like resource limits, replica counts, HA settings

MVP6 cutover can proceed without zero-day Helm work.

## 4. Alternatives considered

1. **K8s cutover trong MVP5** — rejected: PO defer MVP6, scope cutover quá lớn cho pilot 2-3 building.
2. **Bỏ K8s hoàn toàn (Compose forever)** — rejected: không scale được 10-50 building commercial, không có rolling update/self-heal.
3. **Kustomize thay Helm** — rejected: Helm có packaging + values tier sẵn, team đã quen. Kustomize chỉ layering.

## 5. Open questions / Follow-up

- **M5-2-T14 (K8s readiness spike)**: phải deliver `helm lint` CI + stateful chart decision. ADR-050 định nghĩa "ready"; T14 deliver artifact.
- **MVP6 cutover ADR**: cần ADR mới khi triển khai thật — cutover strategy, data migration, rollback.
- **Stateful chart build vs buy**: CH/Kafka/PG — build custom chart hay dùng bitnami/clickhouse official? Decision trong M5-2-T14.
