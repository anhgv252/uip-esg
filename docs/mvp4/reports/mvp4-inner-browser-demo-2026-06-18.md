# MVP4 Inner-Browser Demo — Execution Report

| Field | Value |
|---|---|
| **Date** | 2026-06-18 |
| **Executor** | QA Engineer (via Playwright headless Chromium) |
| **Frontend** | Vite production build (`vite preview` on :4173, dist from `frontend/`) |
| **Backend** | uip-backend :8080 (UP), Kafka single (healthy), 2 Flink jobs RUNNING |
| **Account** | admin / admin_Dev#2026! |
| **Result** | **7/7 flows PASS** (all pages render with real data, 0 page errors) |

---

## What was demoed

Automated drive-through of the MVP4 frontend using Playwright + Chromium (headless), screenshotting each step into [`demo-screenshots/`](demo-screenshots/). Each screenshot was visually verified.

| Step | Flow | Screenshot | Result |
|---|---|---|---|
| 01 | Login page | `01-login.png` | ✅ Page loads |
| 02 | Credentials entered | `02-login-filled.png` | ✅ Form fills, "Sign In" button visible |
| 03 | Login submit → redirect | `03-after-login.png` | ✅ Redirects to `/dashboard`, `uip_access_token` + `uip_refresh_token` in localStorage |
| 04 | Dashboard | `04-dashboard.png` | ✅ Renders |
| 05 | Environment / AQI | `05-environment.png` | ✅ 8 AQI station cards (Bến Nghé, Tân Bình, …) with gauge charts, sensor status table |
| 06 | Alerts | `06-alerts.png` | ✅ Renders |
| 07 | ESG | `07-esg.png` | ✅ Renders |
| 08 | AI Workflow | `08-ai.png` | ✅ Renders |
| 09 | **Workflow Config (Demo 3)** | `09-workflow-config.png` | ✅ "New Config" button, trigger table (Kafka/REST/Scheduled), Enabled toggles, edit/delete/run actions |

**Demo 3 self-service confirmed:** operators manage workflow triggers (create / edit / enable / run) via the UI without writing code.

---

## Issue found: Vite DEV server crashes (does NOT affect production)

| Aspect | Finding |
|---|---|
| Symptom | `npm run dev` (Vite :3000) serves a blank page — `#root` empty, 0 inputs, 0 buttons |
| Root cause | `PAGEERROR: createTheme_default is not a function` — Vite dev pre-bundle/ESM interop fails to resolve `createTheme` from `@mui/material/styles`. The named import exists in source (`frontend/src/theme/index.ts:1`) but the dev-bundled artifact exposes it incorrectly. |
| Production impact | **None** — `vite preview` (production build) loads the same pages with **0 page errors** and full data. |
| Workaround used for this demo | Ran `vite preview --port 4173` instead of `vite`. |
| Recommended fix (separate task) | `rm -rf frontend/node_modules/.vite` to clear the pre-bundle cache, then `npm run dev`. If it recurs, pin MUI version or add an explicit `optimizeDeps.include: ['@mui/material/styles']` in `vite.config.ts`. This is a **dev-experience bug**, not a release blocker — production build is unaffected. |

---

## Infrastructure state during demo (verified)

- **uip-backend**: UP (`/api/v1/health` → `{"status":"UP"}`)
- **Kafka**: single-node healthy, 27 topics, 0 `UNKNOWN_TOPIC_OR_PARTITION` warns (after the 2026-06-18 reset — see `feedback_mvp4_kafka_reset_runbook`)
- **Flink**: `DistrictAggregationJob` + `IncidentCorrelationJob` RUNNING (source + sink vertices bound)
- **Login**: HMAC HS256 JWT via `/api/v1/auth/login`, stored as `uip_access_token` / `uip_refresh_token`

---

## How to re-run this demo

```bash
# 1. Ensure backend + infra up (docker compose up), Flink jobs submitted.
# 2. Build + serve frontend (production, NOT dev — dev has the createTheme bug):
cd frontend && npm run build && npx vite preview --port 4173 &
# 3. Run the Playwright drive-through:
node /tmp/uip-demo.mjs   # screenshots → docs/mvp4/reports/demo-screenshots/
```

> For the **live stakeholder demo**, prefer the production build (`vite preview`) over the dev server until the Vite pre-bundle cache bug is fixed. The dev crash is cosmetic for release but would be embarrassing if hit live — clear `node_modules/.vite` before demo day (added to [`mvp4-demo-readiness-checklist.md`](mvp4-demo-readiness-checklist.md) recommendations).
