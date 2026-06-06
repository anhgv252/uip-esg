# UIP MVP3 — Demo Day Package Index
## Investor & PO Presentation — 2026-06-06

---

## 📦 Demo Package (4 Documents)

| Document | Purpose | Audience | Agent |
|----------|---------|----------|-------|
| [demo-full-features-2026-06-05.md](demo-full-features-2026-06-05.md) | Full demo script (Acts 0–4, 30 min) | Presenter | — |
| [investor-brief-2026-06-06.md](investor-brief-2026-06-06.md) | Executive brief, ROI, revenue model, investment ask | Investor, PO | PM |
| [demo-talking-points-2026-06-06.md](demo-talking-points-2026-06-06.md) | Pitch narrative, user stories, objection handling | Presenter | BA |
| [demo-preflight-checklist-2026-06-06.md](demo-preflight-checklist-2026-06-06.md) | T-30 min setup, 8-feature smoke tests, contingency | Presenter, Tester | Tester |

---

## 🗓️ Demo Day Timeline

```
T-30 min  →  Infrastructure start: cd infrastructure && make up-ha
T-20 min  →  Seed demo data: ./scripts/demo-setup.sh
T-10 min  →  Run 8-feature smoke tests (see preflight checklist)
T-5 min   →  Open browser tabs: Frontend | Kafka UI | Swagger
T-0       →  🎬 DEMO STARTS (30–45 min)
```

---

## 🎯 Demo Structure (Acts)

| Act | Theme | Duration | Key Proof Point |
|-----|-------|----------|----------------|
| 0 | Platform Overview | 2 min | IoT → AI → ESG pipeline |
| 1 | MVP1: IoT Foundation | 5 min | Sensor → Alert < 30s |
| 2 | MVP2: Smart City | 8 min | Multi-tenant, AI workflow, Cache 11× |
| **3** | **🆕 MVP3 Sprint 3+** | **12 min** | **8 new features** |
| 4 | ROI & Roadmap | 3 min | $2.5M ask, Pilot 2026-08-04 |

---

## 📊 Headline Numbers (for opening slide)

| Metric | Value |
|--------|-------|
| API Endpoints documented | **107** |
| Tests passing | **1,191 / 1,191 (0 failures)** |
| HA uptime achieved | **99.9%** |
| Security CVEs (blocking) | **0** |
| Dashboard p95 latency | **45ms** (SLA: <3s) |
| ESG report generation | **<1 second** (was: 3 days) |
| Energy forecast accuracy | **MAPE 3.54%** |
| Cache performance | **11× improvement** |
| Pilot launch date | **2026-08-04** |

---

## 🆕 MVP3 New Features (Act 3 Highlights)

1. **BMS** — Modbus/BACnet device control (−60% on-site costs)
2. **Building Safety** — Structural vibration monitoring (TCVN 9386 compliant)
3. **AI Workflow Designer** — No-code BPMN (10 min vs 2 weeks)
4. **Predictive Analytics** — ARIMA forecasting, MAPE 3.54%
5. **Mobile App** — React Native iOS+Android, push notifications < 10s
6. **HA Infrastructure** — Kafka 3-node KRaft, ClickHouse 2-node quorum
7. **107 API Endpoints** — OpenAPI 3.0 complete
8. **ESG PDF Export** — GRI 302 + GRI 305, ready for government submission

---

## 🔑 Top 3 Investor Talking Points

1. **Regulatory tailwind**: Vietnam ESG deadline Dec 31, 2026 — we're the only compliant platform live today
2. **Unit economics**: $24K–$96K/building/year, 65%+ gross margin, HCMC pilot signed
3. **Technology moat**: AI-native + multi-tenant + self-healing HA — 3-year lead over Schneider/Siemens

---

## 🚨 Critical Demo Contingencies (Summary)

| If... | Then... |
|-------|---------|
| Kafka UI not loading | Show pre-captured 3-broker screenshot |
| Demo data missing | Re-run `./scripts/demo-setup.sh` |
| PDF export hangs | Use pre-generated `~/Downloads/UIP-ESG-Q1-2026.pdf` |
| Mobile app can't connect | Switch to pre-recorded video |
| Backend 500 errors | Switch to Swagger live API demo |

→ Full contingency playbook: [demo-preflight-checklist-2026-06-06.md](demo-preflight-checklist-2026-06-06.md)

---

## 💰 Investment Ask

> **$2.5M Seed** — Infrastructure ($600K) · Sales ($1M) · Engineering ($700K) · Contingency ($200K)
>
> Target: Series A by Q3 2027 at $50–150M valuation

---

*Demo Package v1.0 — Prepared 2026-06-06 | UIP Smart City Platform MVP3 Final*
