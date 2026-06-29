# MVP5 — Billing Unit Specification: Hybrid Model (Base + AI Overage)

**Sprint:** MVP5 M5-2  
**Task:** M5-2-T08 (SP:2)  
**Owner:** Business Analyst  
**Status:** Draft for PO Approval  
**Date:** 2026-06-26  

---

## §1. Billing Model Overview (D4 Decision Confirmed)

### 1.1 Billing Architecture

UIP Smart City platform adopts a **hybrid billing model** combining predictable base fees with usage-based AI overages:

```
Total Monthly Bill = Base Fee + AI Overage + Sensor Overage - Pilot Credits
```

**Rationale:** City authorities prefer predictable base costs for budget planning, while power users (high AI usage) pay proportionally for advanced analytics.

### 1.2 Billing Components

| Component | Unit | Base Allocation | Overage Rate |
|-----------|------|-----------------|--------------|
| **Base Tier** | Per Building | Flat 2,000,000 VND/month | N/A |
| **Sensor Coverage** | Per Sensor | 100 sensors included | 50,000 VND per 10 sensors |
| **AI Token Usage** | Per 1,000 Tokens | 100,000 tokens included | 50 VND per 1,000 tokens |
| **Alert Generation** | Unlimited | No cap | No charge (included) |
| **BPMN Workflow Execution** | Unlimited | No cap | No charge (included) |

### 1.3 Pilot Pricing (City Authority Agreement)

**Free tier for pilot phase:**
- 3 buildings × 6 months = no charge
- Full feature access (no limitations on sensors, AI tokens, alerts)
- Post-pilot: standard pricing applies from Month 7

**Post-pilot standard pricing:**
- Base: 2,000,000 VND/building/month
- Sensor overage: 50,000 VND per 10 sensors above 100 base
- AI overage: 50 VND per 1,000 tokens consumed above 100,000 base allocation

---

## §2. Metering Events — Backend Data Capture Requirements

Backend Data Engineering MUST capture the following events in `AiCostMetrics` ledger table:

### 2.1 Event Schema

```sql
CREATE TABLE ai_cost_metrics (
  id UUID PRIMARY KEY,
  tenant_id VARCHAR(50) NOT NULL,
  building_id VARCHAR(50) NOT NULL,
  event_type VARCHAR(50) NOT NULL,  -- SENSOR_READING | AI_INFERENCE | ALERT_GENERATED | BPMN_WORKFLOW_EXECUTED
  token_count INT DEFAULT 0,        -- For AI_INFERENCE only
  sensor_id VARCHAR(100),           -- For SENSOR_READING only
  alert_id UUID,                    -- For ALERT_GENERATED only
  workflow_id UUID,                 -- For BPMN_WORKFLOW_EXECUTED only
  timestamp TIMESTAMPTZ NOT NULL,
  metadata JSONB                    -- Additional context
);

CREATE INDEX idx_cost_metrics_tenant_month ON ai_cost_metrics(tenant_id, DATE_TRUNC('month', timestamp));
CREATE INDEX idx_cost_metrics_building_month ON ai_cost_metrics(building_id, DATE_TRUNC('month', timestamp));
```

### 2.2 Event Types & Capture Rules

#### Event: `SENSOR_READING`
- **When to capture:** Every sensor reading ingested by IoT Ingestion Service
- **Fields required:** `tenant_id`, `building_id`, `sensor_id`, `timestamp`
- **Token count:** 0
- **Example:** Water quality sensor reading every 60 seconds

#### Event: `AI_INFERENCE`
- **When to capture:** Every NL→BPMN transformation API call (Claude API)
- **Fields required:** `tenant_id`, `building_id`, `token_count`, `timestamp`
- **Token count:** Actual tokens consumed (input + output tokens from Claude API response)
- **Example:** User describes "Send alert when AQI > 200 in District 1" → generates BPMN workflow → logs 1,850 tokens

#### Event: `ALERT_GENERATED`
- **When to capture:** Every alert triggered by Alert Engine
- **Fields required:** `tenant_id`, `building_id`, `alert_id`, `timestamp`
- **Token count:** 0 (alerts are free, but count for usage analytics)
- **Example:** Flood alert P0 triggered at 2:30 AM → logs 1 event

#### Event: `BPMN_WORKFLOW_EXECUTED`
- **When to capture:** Every BPMN workflow execution (manual or automated trigger)
- **Fields required:** `tenant_id`, `building_id`, `workflow_id`, `timestamp`
- **Token count:** 0 (workflow execution is free, but count for analytics)
- **Example:** "Notify ward leader when PM2.5 > 100" workflow runs → logs 1 event

### 2.3 Aggregation Queries (for Invoice Generation)

Backend must provide these aggregation endpoints:

```sql
-- Monthly sensor count per building
SELECT building_id, COUNT(DISTINCT sensor_id) AS sensor_count
FROM ai_cost_metrics
WHERE tenant_id = ? 
  AND event_type = 'SENSOR_READING'
  AND timestamp >= DATE_TRUNC('month', NOW())
  AND timestamp < DATE_TRUNC('month', NOW()) + INTERVAL '1 month'
GROUP BY building_id;

-- Monthly AI token consumption per building
SELECT building_id, SUM(token_count) AS total_tokens
FROM ai_cost_metrics
WHERE tenant_id = ?
  AND event_type = 'AI_INFERENCE'
  AND timestamp >= DATE_TRUNC('month', NOW())
  AND timestamp < DATE_TRUNC('month', NOW()) + INTERVAL '1 month'
GROUP BY building_id;

-- Monthly alert count (for analytics, no billing impact)
SELECT building_id, COUNT(*) AS alert_count
FROM ai_cost_metrics
WHERE tenant_id = ?
  AND event_type = 'ALERT_GENERATED'
  AND timestamp >= DATE_TRUNC('month', NOW())
  AND timestamp < DATE_TRUNC('month', NOW()) + INTERVAL '1 month'
GROUP BY building_id;
```

---

## §3. Billing Calculation Logic

### 3.1 Formula Breakdown

```
Base Fee Charge = Number of Buildings × 2,000,000 VND

Sensor Overage Charge = 
  IF (Sensor Count > 100)
  THEN CEIL((Sensor Count - 100) / 10) × 50,000 VND
  ELSE 0

AI Overage Charge =
  IF (Total Tokens > 100,000)
  THEN CEIL((Total Tokens - 100,000) / 1,000) × 50 VND
  ELSE 0

Pilot Credit = 
  IF (Pilot Agreement Active AND Building Count ≤ 3)
  THEN -(Base Fee Charge + Sensor Overage Charge + AI Overage Charge)
  ELSE 0

Total Monthly Bill = Base Fee Charge + Sensor Overage Charge + AI Overage Charge + Pilot Credit
```

### 3.2 Example Calculations

#### Scenario A: Pilot tenant, 2 buildings, 85 sensors, 45,000 AI tokens
```
Base Fee:         2 × 2,000,000 = 4,000,000 VND
Sensor Overage:   0 (85 < 100)
AI Overage:       0 (45,000 < 100,000)
Pilot Credit:     -4,000,000 VND (pilot active, ≤3 buildings)
-------------------------------------------
Total Bill:       0 VND
```

#### Scenario B: Post-pilot tenant, 1 building, 135 sensors, 250,000 AI tokens
```
Base Fee:         1 × 2,000,000 = 2,000,000 VND
Sensor Overage:   CEIL((135 - 100) / 10) × 50,000 = 4 × 50,000 = 200,000 VND
AI Overage:       CEIL((250,000 - 100,000) / 1,000) × 50 = 150 × 50 = 7,500 VND
Pilot Credit:     0 (pilot expired)
-------------------------------------------
Total Bill:       2,207,500 VND
```

#### Scenario C: Post-pilot tenant, 5 buildings, 450 sensors, 1,200,000 AI tokens
```
Base Fee:         5 × 2,000,000 = 10,000,000 VND
Sensor Overage:   CEIL((450 - 100) / 10) × 50,000 = 35 × 50,000 = 1,750,000 VND
AI Overage:       CEIL((1,200,000 - 100,000) / 1,000) × 50 = 1,100 × 50 = 55,000 VND
Pilot Credit:     0
-------------------------------------------
Total Bill:       11,805,000 VND (~$470 USD at 25,000 VND/USD)
```

---

## §4. Acceptance Criteria (6 Testable Criteria for QA/Tester)

### AC-1: Tenant Usage Visibility
```gherkin
Given a tenant with multiple buildings
When the tenant views their billing dashboard
Then the dashboard displays:
  - Current month usage breakdown per building (sensor count, AI tokens, alert count)
  - Historical usage chart for last 6 months
  - Projected end-of-month charge based on current usage trajectory
And the tenant CANNOT see other tenants' billing data (403 Forbidden)
```

### AC-2: AI Overage Calculation Accuracy
```gherkin
Given a building with 250,000 AI tokens consumed in the current month
And the base AI allocation is 100,000 tokens
When the billing system calculates overage
Then the AI overage charge = CEIL((250,000 - 100,000) / 1,000) × 50 = 7,500 VND
And the result is rounded UP to nearest 1,000 tokens
And the overage is displayed on the invoice PDF
```

### AC-3: Zero Overage for Under-Allocation Buildings
```gherkin
Given a building with 45,000 AI tokens consumed
And 70 sensors active
When the billing system generates the invoice
Then the AI overage charge = 0 VND
And the sensor overage charge = 0 VND
And the invoice displays "No overage charges this month"
```

### AC-4: Invoice PDF Generation Performance
```gherkin
Given the billing period ends at 23:59:59 on the last day of the month
When the billing system triggers invoice generation
Then the invoice PDF is generated and stored within 5 minutes
And the tenant receives email notification with download link within 10 minutes
And the PDF includes: tenant name, building list, usage breakdown, itemized charges, total bill
```

### AC-5: Billing Reconciliation Accuracy
```gherkin
Given a tenant with metered events stored in ai_cost_metrics table
When the QA team cross-validates invoice charges against raw event logs
Then the variance between calculated charges and actual metered events ≤ 0.5%
And discrepancies are logged in billing_audit_log table
And Finance team can export audit trail for accounting verification
```

### AC-6: Multi-Tenant Isolation Enforcement
```gherkin
Given Tenant A and Tenant B both have active accounts
When Tenant A attempts to access Tenant B's billing API endpoint
Then the API returns 403 Forbidden
And the request is logged in security_audit_log
And Tenant A only sees their own buildings in the billing dashboard
```

---

## §5. Open Questions for Product Owner

### Q1: Base Unit Selection
**Question:** Is **per-building** or **per-sensor** the correct base billing unit?  
**Context:** City authority budgets are allocated per building (easier for procurement), but per-sensor pricing may better reflect actual cost structure.  
**Impact:** Pricing model overhaul if changed after M5-4 invoice generation.  
**Decision needed by:** M5-3 Sprint Planning (2026-07-01)

### Q2: Emergency Alert Exemption
**Question:** Should P0 flood/fire alerts count toward AI token overage?  
**Context:** Emergency response AI inference (e.g., flood risk analysis) uses Claude API tokens. Should life-safety alerts be billed or exempted?  
**Recommendation:** Exempt P0 alerts to align with public safety mission.  
**Decision needed by:** Before M5-4 invoice generation logic implementation

### Q3: Multi-Building Discount
**Question:** Should tenants with >10 buildings receive volume discount (e.g., 15% off)?  
**Context:** Enterprise city districts (e.g., HCMC District 1 with 50+ buildings) may request bulk pricing.  
**Impact:** Revenue forecasting and contract negotiation complexity.  
**Decision needed by:** M5-5 (before city-wide rollout)

### Q4: Invoice Currency
**Question:** Invoice currency — VND only or also USD option?  
**Context:** International organizations (UN Habitat, ADB-funded projects) prefer USD invoicing for easier budget reconciliation.  
**Recommendation:** Dual currency support (VND + USD at exchange rate locked at billing period start).  
**Decision needed by:** M5-4 Sprint Planning

### Q5: Pilot Exit Strategy
**Question:** What happens if pilot tenant wants to downgrade from 3 → 1 building after trial?  
**Context:** Pilot agreement covers 3 buildings free. If tenant only adopts 1 building post-pilot, do we charge for all 3 or only active 1?  
**Recommendation:** Charge only for active buildings with ≥1 sensor reading in the billing period.  
**Decision needed by:** Before pilot contract signature (M5-3)

---

## §6. Backend Implementation Handoff

### Required Backend Tasks (M5-2 T07 — Data Eng)
1. Create `ai_cost_metrics` table with indexes (§2.1 schema)
2. Instrument event capture in:
   - IoT Ingestion Service → `SENSOR_READING` events
   - AI Workflow Service → `AI_INFERENCE` events (capture token count from Claude API response)
   - Alert Engine → `ALERT_GENERATED` events
   - BPMN Engine → `BPMN_WORKFLOW_EXECUTED` events
3. Implement aggregation queries (§2.3)
4. Create `/api/v1/billing/usage/{tenantId}/{month}` endpoint returning:
   ```json
   {
     "tenantId": "t-hcmc-001",
     "billingMonth": "2026-06",
     "buildings": [
       {
         "buildingId": "b-district1-tower-a",
         "sensorCount": 125,
         "aiTokensConsumed": 180000,
         "alertsGenerated": 47,
         "workflowExecutions": 12
       }
     ],
     "charges": {
       "baseFee": 2000000,
       "sensorOverage": 150000,
       "aiOverage": 4000,
       "pilotCredit": 0,
       "totalBill": 2154000
     }
   }
   ```

### Required Frontend Tasks (M5-3 T06-T08)
1. Billing Dashboard UI component (display usage breakdown per building)
2. Invoice PDF download button
3. Usage projection chart (current month trend → estimated end-of-month charge)
4. Overage alert UI (notify tenant when approaching 80% of base allocation)

---

## §7. Success Metrics (Business KPIs)

| Metric | Target | Measurement |
|--------|--------|-------------|
| Invoice Accuracy | ≥99.5% (≤0.5% variance) | Monthly audit vs raw event logs |
| Invoice Delivery SLA | <10 minutes after billing period close | Email send timestamp - period end timestamp |
| Billing Dispute Rate | <2% of invoices | Support tickets tagged "billing dispute" |
| Tenant Billing Dashboard Adoption | ≥85% of tenants view dashboard monthly | Google Analytics event tracking |
| AI Overage Predictability | Tenant projected charge within ±10% of actual | Compare projection at day 20 vs final invoice |

---

## Appendix A: Glossary

| Term | Definition |
|------|------------|
| **Base Allocation** | Included usage (100,000 tokens, 100 sensors) in base fee before overage charges apply |
| **Billing Period** | Calendar month (1st 00:00:00 to last day 23:59:59 UTC+7 Vietnam time) |
| **Building** | Physical structure with unique building_id; base billing unit |
| **Token** | Claude API token (input + output); 1 token ≈ 4 characters for English, ≈1.5 characters for Vietnamese |
| **Pilot Agreement** | 3 buildings × 6 months free trial for city authority evaluation |
| **Overage** | Usage exceeding base allocation, charged at per-unit rate |

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-06-26 | BA (UIP Team) | Initial draft for M5-2 T08 |

---

**Next Actions:**
1. PO review & answer Q1-Q5 (§5) by 2026-07-01
2. Backend Data Eng implements metering events (M5-2 T07)
3. Finance team reviews pricing model for city authority budget alignment
4. Legal reviews pilot exit clause (Q5)
