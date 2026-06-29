# MVP5 — LOTUS VN Checklist + ROI Dashboard Prep

**Sprint:** MVP5 M5-2  
**Task:** M5-2-T13 (SP:2)  
**Owner:** Business Analyst  
**Status:** Prep for M5-4 Implementation  
**Date:** 2026-06-26  

---

## Part A: LOTUS VN Certification Checklist

### Context

**LOTUS VN** = Vietnam Green Building Rating System (Hệ thống đánh giá công trình xanh Việt Nam)  
**Purpose:** Smart city buildings must demonstrate environmental performance to qualify for government green building incentives and tax breaks.  
**Implementation Sprint:** M5-4 T06 (LOTUS VN Certification Engine)  
**Why now:** Backend needs checklist to design data aggregation logic.

---

## A1. LOTUS VN — 5 Categories Overview

LOTUS VN evaluates buildings across 5 categories. Each indicator earns 1-4 points. Total score out of 100 determines certification level:

| Certification Level | Minimum Score | City Authority Benefit |
|---------------------|---------------|------------------------|
| **Certified** | 40 points | Basic green building recognition |
| **Silver** | 50 points | 5% property tax reduction (3 years) |
| **Gold** | 60 points | 10% property tax reduction (5 years) + priority permit processing |
| **Platinum** | 75 points | 15% property tax reduction (7 years) + ESG showcase status |

**UIP Value Proposition:** Automated LOTUS scoring based on real-time sensor data → eliminates manual audit costs (typically 50-100 million VND per building per year).

---

## A2. LOTUS Category 1: Năng lượng (Energy) — EN

### Key Indicators (UIP can automate)

#### EN-1: Energy Consumption Intensity
- **Metric:** kWh/m²/year
- **LOTUS Threshold:**
  - 4 points: <80 kWh/m²/year (office), <100 kWh/m²/year (residential)
  - 3 points: 80-120 kWh/m²/year (office), 100-150 kWh/m²/year (residential)
  - 2 points: 120-150 kWh/m²/year (office), 150-200 kWh/m²/year (residential)
  - 1 point: 150-200 kWh/m²/year (office), 200-250 kWh/m²/year (residential)
- **UIP Data Source:** ESG Module → Building energy meters → aggregated monthly
- **Backend Query:**
  ```sql
  SELECT building_id, 
         SUM(kwh_consumed) / building_area_sqm AS intensity
  FROM esg_energy_consumption
  WHERE year = 2026
  GROUP BY building_id;
  ```

#### EN-2: Renewable Energy Percentage
- **Metric:** Renewable kWh / Total kWh × 100%
- **LOTUS Threshold:**
  - 4 points: ≥30% renewable
  - 3 points: 20-29% renewable
  - 2 points: 10-19% renewable
  - 1 point: 5-9% renewable
- **UIP Data Source:** Solar panel meters (if installed) → tagged as `energy_source: SOLAR`
- **Challenge:** Most pilot buildings lack solar panels → manual input required for now
- **M5-5 Enhancement:** EV Charging integration may contribute to renewable score (grid-connected solar)

#### EN-3: Building Automation System (BMS) Coverage
- **Metric:** % of HVAC/lighting controlled by BMS
- **LOTUS Threshold:**
  - 4 points: ≥80% BMS coverage
  - 3 points: 60-79%
  - 2 points: 40-59%
  - 1 point: 20-39%
- **UIP Data Source:** Manual input (BMS sensor count / total building sensors)
- **Automation Opportunity:** IoT sensor metadata field `is_bms_controlled: true/false`

#### EN-4: Energy Sub-Metering
- **Metric:** Presence of sub-meters for HVAC, lighting, IT loads
- **LOTUS Threshold:**
  - 4 points: All 3 sub-systems metered
  - 3 points: 2 sub-systems metered
  - 2 points: 1 sub-system metered
  - 1 point: No sub-metering
- **UIP Data Source:** Sensor metadata `meter_category: HVAC | LIGHTING | IT`
- **Backend Query:**
  ```sql
  SELECT building_id,
         COUNT(DISTINCT meter_category) AS subsystem_count
  FROM iot_sensors
  WHERE sensor_type = 'ENERGY_METER'
    AND meter_category IN ('HVAC', 'LIGHTING', 'IT')
  GROUP BY building_id;
  ```

---

## A3. LOTUS Category 2: Nước (Water) — WA

### Key Indicators (UIP can automate)

#### WA-1: Water Consumption per Occupant
- **Metric:** Liters/person/day
- **LOTUS Threshold:**
  - 4 points: <120 L/person/day
  - 3 points: 120-150 L/person/day
  - 2 points: 150-200 L/person/day
  - 1 point: 200-250 L/person/day
- **UIP Data Source:** Water flow sensors → occupancy count (manual input or badge scan system)
- **Backend Query:**
  ```sql
  SELECT building_id,
         SUM(liters_consumed) / AVG(occupant_count) / 365 AS liters_per_person_day
  FROM water_consumption_daily
  WHERE year = 2026
  GROUP BY building_id;
  ```

#### WA-2: Greywater Recycling System
- **Metric:** Presence of greywater recycling (Yes/No)
- **LOTUS Threshold:**
  - 3 points: System installed and operational
  - 0 points: No system
- **UIP Data Source:** Manual input (infrastructure audit)
- **Automation Opportunity:** Greywater system sensors → tag `water_source: GREYWATER_RECYCLED`

#### WA-3: Water Point-of-Use Metering
- **Metric:** % of water fixtures with individual meters
- **LOTUS Threshold:**
  - 4 points: ≥80% fixtures metered
  - 3 points: 60-79%
  - 2 points: 40-59%
  - 1 point: 20-39%
- **UIP Data Source:** Sensor inventory metadata `fixture_type: RESTROOM | KITCHEN | IRRIGATION`

---

## A4. LOTUS Category 3: Chất lượng môi trường trong nhà (Indoor Environment Quality) — IEQ

### Key Indicators (UIP FULLY automates)

#### IEQ-1: CO₂ Concentration in Occupied Spaces
- **Metric:** ppm (parts per million)
- **LOTUS Threshold:**
  - 4 points: <800 ppm (95% of working hours)
  - 3 points: 800-1000 ppm
  - 2 points: 1000-1200 ppm
  - 1 point: >1200 ppm
- **UIP Data Source:** ✅ **AVAILABLE** — CO₂ sensors (if installed in pilot buildings)
- **Backend Query:**
  ```sql
  SELECT building_id,
         PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY co2_ppm) AS p95_co2
  FROM environment_sensor_readings
  WHERE sensor_type = 'CO2'
    AND hour >= 8 AND hour <= 18  -- Working hours only
    AND year = 2026
  GROUP BY building_id;
  ```

#### IEQ-2: PM2.5 Indoor Air Quality
- **Metric:** μg/m³ (micrograms per cubic meter)
- **LOTUS Threshold:**
  - 4 points: <15 μg/m³ (WHO guideline)
  - 3 points: 15-25 μg/m³ (Good AQI)
  - 2 points: 25-35 μg/m³ (Moderate AQI)
  - 1 point: 35-50 μg/m³ (Unhealthy for Sensitive Groups)
- **UIP Data Source:** ✅ **AVAILABLE** — AQI sensors (PM2.5 component)
- **Backend Query:**
  ```sql
  SELECT building_id,
         AVG(pm25_ugm3) AS avg_pm25_indoor
  FROM environment_sensor_readings
  WHERE sensor_type = 'AQI'
    AND location_type = 'INDOOR'
    AND year = 2026
  GROUP BY building_id;
  ```
- **Competitive Advantage:** Real-time AQI monitoring is UIP's strongest LOTUS differentiator.

#### IEQ-3: Thermal Comfort (ASHRAE 55)
- **Metric:** % of occupied hours within 20-26°C + 30-60% relative humidity
- **LOTUS Threshold:**
  - 4 points: ≥90% of hours in comfort zone
  - 3 points: 80-89%
  - 2 points: 70-79%
  - 1 point: 60-69%
- **UIP Data Source:** ⚠️ **PARTIAL** — Temperature sensors (yes), humidity sensors (if installed)
- **Backend Query:**
  ```sql
  SELECT building_id,
         COUNT(*) FILTER (
           WHERE temp_celsius BETWEEN 20 AND 26
             AND humidity_percent BETWEEN 30 AND 60
         ) * 100.0 / COUNT(*) AS comfort_percent
  FROM environment_sensor_readings
  WHERE sensor_type IN ('TEMPERATURE', 'HUMIDITY')
    AND hour >= 8 AND hour <= 18
    AND year = 2026
  GROUP BY building_id;
  ```

---

## A5. LOTUS Category 4: Vật liệu (Materials) — MA

### Key Indicators (Manual Input Only)

#### MA-1: Certified Sustainable Materials
- **Metric:** % of structural materials with green certification (FSC, LEED, etc.)
- **LOTUS Threshold:**
  - 4 points: ≥20% certified materials
  - 3 points: 10-19%
  - 2 points: 5-9%
  - 1 point: 1-4%
- **UIP Data Source:** ❌ **NOT AUTOMATED** — Requires construction audit (one-time)
- **M5-4 Implementation:** Manual form input in LOTUS VN module

#### MA-2: Local Materials Usage
- **Metric:** % of materials sourced within 800 km of project site
- **LOTUS Threshold:**
  - 4 points: ≥50% local materials
  - 3 points: 30-49%
  - 2 points: 20-29%
  - 1 point: 10-19%
- **UIP Data Source:** ❌ **NOT AUTOMATED** — Supply chain documentation (one-time)

---

## A6. LOTUS Category 5: Địa điểm và giao thông (Site & Transport) — ST

### Key Indicators (Partial Automation)

#### ST-1: Public Transport Access
- **Metric:** Distance to nearest bus stop / metro station
- **LOTUS Threshold:**
  - 4 points: <200m walking distance
  - 3 points: 200-500m
  - 2 points: 500-800m
  - 1 point: 800-1000m
- **UIP Data Source:** 🔜 **M5-5 GIS INTEGRATION** — Calculate via Google Maps API
- **Backend Query:** POST to Google Maps Distance Matrix API

#### ST-2: EV Charging Infrastructure
- **Metric:** EV charging stations as % of total parking capacity
- **LOTUS Threshold:**
  - 4 points: ≥10% parking with EV charging
  - 3 points: 5-9%
  - 2 points: 2-4%
  - 1 point: 1%
- **UIP Data Source:** 🔜 **M5-5 EV CHARGING MODULE** — Sensor data from charging stations
- **Backend Query:**
  ```sql
  SELECT building_id,
         COUNT(*) FILTER (WHERE charger_type = 'EV_CHARGER') * 100.0 / 
         COUNT(*) AS ev_parking_percent
  FROM parking_inventory
  WHERE year = 2026
  GROUP BY building_id;
  ```

#### ST-3: Bicycle Facilities
- **Metric:** Presence of secure bicycle parking + showers
- **LOTUS Threshold:**
  - 3 points: Both facilities present
  - 2 points: Bicycle parking only
  - 0 points: Neither
- **UIP Data Source:** ❌ **NOT AUTOMATED** — Manual input (infrastructure audit)

---

## A7. UIP Data Availability Summary

| LOTUS Indicator | UIP Data Available? | Data Source | Automation Level |
|-----------------|---------------------|-------------|------------------|
| EN-1 Energy Intensity | ✅ YES | ESG Module energy meters | **Fully Automated** |
| EN-2 Renewable % | ⚠️ PARTIAL | Solar meter tags (if present) | Manual fallback |
| EN-3 BMS Coverage | ⚠️ PARTIAL | Sensor metadata | Manual input required |
| EN-4 Sub-Metering | ✅ YES | Sensor `meter_category` field | **Fully Automated** |
| WA-1 Water per Occupant | ✅ YES | Water sensors + occupancy count | **Fully Automated** |
| WA-2 Greywater Recycling | ❌ NO | Infrastructure audit | Manual only |
| WA-3 Water Point Metering | ✅ YES | Water sensor inventory | **Fully Automated** |
| IEQ-1 CO₂ | ⚠️ PARTIAL | CO₂ sensors (if installed) | Automated when available |
| IEQ-2 PM2.5 | ✅ YES | AQI sensors (core UIP feature) | **Fully Automated** |
| IEQ-3 Thermal Comfort | ⚠️ PARTIAL | Temp sensors + humidity (if installed) | Automated when available |
| MA-1 Certified Materials | ❌ NO | Construction audit | Manual only |
| MA-2 Local Materials | ❌ NO | Supply chain docs | Manual only |
| ST-1 Public Transport | 🔜 M5-5 | Google Maps API | M5-5 GIS integration |
| ST-2 EV Charging | 🔜 M5-5 | EV charger sensors | M5-5 EV module |
| ST-3 Bicycle Facilities | ❌ NO | Infrastructure audit | Manual only |

**Automation Coverage:** 6/15 fully automated, 4/15 partial, 5/15 manual only.

---

## A8. Backend Implementation Requirements (M5-4 T06)

### Task 1: LOTUS Scoring Engine
Create `/api/v1/lotus/score/{buildingId}` endpoint returning:

```json
{
  "buildingId": "b-district1-tower-a",
  "scoringDate": "2026-06-26",
  "categories": {
    "energy": {
      "indicators": [
        {
          "id": "EN-1",
          "name": "Energy Intensity",
          "value": 95.3,
          "unit": "kWh/m²/year",
          "points": 3,
          "maxPoints": 4,
          "dataSource": "automated",
          "lastUpdated": "2026-06-25T23:59:59Z"
        },
        {
          "id": "EN-2",
          "name": "Renewable Energy %",
          "value": 0,
          "unit": "%",
          "points": 0,
          "maxPoints": 4,
          "dataSource": "manual",
          "lastUpdated": "2026-05-01T00:00:00Z"
        }
      ],
      "totalPoints": 12,
      "maxPoints": 20
    },
    "water": { /* ... similar structure ... */ },
    "ieq": { /* ... */ },
    "materials": { /* ... */ },
    "site": { /* ... */ }
  },
  "overallScore": 58,
  "certificationLevel": "Gold",
  "nextLevelThreshold": 60,
  "improvementRecommendations": [
    "Install CO₂ sensors in meeting rooms (+4 points potential)",
    "Add solar panels (20% renewable = +2 points)",
    "Document local materials sourcing (+2 points)"
  ]
}
```

### Task 2: Manual Override Interface
For indicators without automated data, provide form inputs:
- Materials certification upload (PDF/photo of FSC certificates)
- Greywater system operational status (Yes/No toggle)
- Bicycle facility checklist (parking + showers)

### Task 3: Historical Scoring
Store monthly LOTUS score snapshots for trend analysis:
```sql
CREATE TABLE lotus_score_history (
  id UUID PRIMARY KEY,
  building_id VARCHAR(50) NOT NULL,
  scoring_date DATE NOT NULL,
  overall_score INT NOT NULL,
  certification_level VARCHAR(20),
  category_scores JSONB NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);
```

---

## Part B: ROI Dashboard — Acceptance Criteria Stub

### Context

**ROI Dashboard** = Return on Investment display for city authority budget justification.  
**Implementation Sprint:** M5-3 T06-T08 (Frontend + Backend)  
**Purpose:** Show quantified value of UIP vs manual operations to justify continued funding.

---

## B1. ROI Calculation Formula

### Annual Savings Formula
```
Annual Savings = Manual Operations Cost - UIP Total Cost

Where:
  Manual Operations Cost = 
    (Manual alert monitoring hours × hourly wage) +
    (Manual ESG report hours × hourly wage) +
    (Manual sensor data collection hours × hourly wage) +
    (Paper-based reporting costs)

  UIP Total Cost =
    (Base fee × 12 months) +
    (AI overage charges annual total) +
    (Sensor installation one-time cost / equipment lifetime) +
    (Training costs / 3 years)
```

### Payback Period
```
Payback Period (months) = Total Implementation Cost / (Annual Savings / 12)

Where:
  Total Implementation Cost =
    Sensor hardware +
    Installation labor +
    Platform setup fee +
    Staff training +
    Data migration (if applicable)
```

### ESG Value (Carbon Offset)
```
ESG Value (VND/year) = CO₂ Reduction (tons/year) × Carbon Credit Price (VND/ton)

Where:
  CO₂ Reduction = Baseline Emissions - Post-UIP Emissions
  Carbon Credit Price ≈ 500,000 VND/ton CO₂ (Vietnam carbon market estimate)
```

---

## B2. ROI Dashboard UI Components (Wireframe Spec)

### Component 1: Cost Breakdown Card (Per Building)
```
┌─────────────────────────────────────────┐
│ Building: District 1 Tower A           │
│ Billing Period: Jun 2026               │
├─────────────────────────────────────────┤
│ COSTS                                   │
│ ├─ Base Fee:        2,000,000 VND      │
│ ├─ Sensor Overage:    150,000 VND      │
│ ├─ AI Tokens:           7,500 VND      │
│ └─ Total:           2,157,500 VND      │
│                                         │
│ SAVINGS                                 │
│ ├─ Manual monitoring: 5,000,000 VND    │
│ ├─ ESG report labor:  3,000,000 VND    │
│ └─ Total Saved:       8,000,000 VND    │
│                                         │
│ NET SAVINGS:          5,842,500 VND    │
│ ROI:                  271% this month   │
└─────────────────────────────────────────┘
```

### Component 2: Payback Period Gauge
```
Implementation Cost: 50,000,000 VND (one-time)
Monthly Savings:      5,842,500 VND
Payback Period:       8.6 months ← Display prominently

[████████░░░░] 65% paid back (6 months elapsed)
```

### Component 3: CO₂ Savings Chart (Year-over-Year)
```
Energy Consumption Comparison
─────────────────────────────
Before UIP (2025):  150,000 kWh/year → 75 tons CO₂
After UIP (2026):   120,000 kWh/year → 60 tons CO₂
───────────────────────────────────────────────────
Reduction:          30,000 kWh (-20%) → 15 tons CO₂
Carbon Credit Value: 15 tons × 500,000 VND = 7,500,000 VND/year
```

### Component 4: Pre/Post Comparison Table
| Metric | Pre-UIP (Manual) | Post-UIP (Automated) | Improvement |
|--------|------------------|----------------------|-------------|
| Alert Response Time | 45 min | 3 min | **93% faster** |
| ESG Report Generation | 8 hours/quarter | 10 min/quarter | **99% time saved** |
| Sensor Data Collection | 20 hours/week | 0 hours (automated) | **100% automation** |
| Data Accuracy | 85% (manual errors) | 99.5% (validated) | **+14.5% accuracy** |

---

## B3. Acceptance Criteria (5 Testable Criteria for M5-3 Sign-Off)

### AC-1: Per-Building Cost Breakdown Visibility
```gherkin
Given a tenant with 3 buildings
When the tenant views the ROI dashboard
Then the dashboard displays cost breakdown for each building:
  - Base fee
  - Sensor overage charge
  - AI token overage charge
  - Manual labor cost saved (calculated from baseline)
And the dashboard displays net savings per building
And the tenant can filter by date range (month/quarter/year)
```

### AC-2: Payback Period Calculation & Display
```gherkin
Given a building with total implementation cost = 50,000,000 VND
And monthly savings = 5,842,500 VND
When the dashboard calculates payback period
Then the displayed payback period = 50,000,000 / 5,842,500 = 8.6 months
And the progress gauge shows 65% paid back (if 6 months have elapsed)
And the remaining months to break-even = 2.6 months
```

### AC-3: CO₂ Savings Quantification
```gherkin
Given a building with baseline energy consumption = 150,000 kWh/year (pre-UIP)
And current energy consumption = 120,000 kWh/year (post-UIP)
When the ROI dashboard calculates CO₂ savings
Then the dashboard displays:
  - Energy reduction = 30,000 kWh (-20%)
  - CO₂ reduction = 15 tons/year (using 0.5 kg CO₂/kWh conversion)
  - Carbon credit value = 15 tons × 500,000 VND = 7,500,000 VND/year
And the savings are displayed both in metric tons and VND equivalent
```

### AC-4: Pre-UIP vs Post-UIP Comparison Chart
```gherkin
Given a building with ≥12 months of post-UIP data
When the tenant views the comparison chart
Then the chart displays:
  - Energy consumption trend line (pre-UIP vs post-UIP)
  - Month-over-month comparison for ≥2 buildings
  - Tooltip on hover showing exact kWh values
  - Percentage improvement badge
And the chart is exportable as PNG for presentations
```

### AC-5: PDF Export for Budget Justification
```gherkin
Given a city authority user viewing the ROI dashboard
When the user clicks "Export ROI Report"
Then a PDF is generated within 30 seconds containing:
  - Executive summary (total savings, payback period, CO₂ reduction)
  - Per-building cost breakdown table
  - Pre/Post comparison charts
  - LOTUS VN certification status (if applicable)
  - City authority logo and official report header
And the PDF is downloadable with filename format: ROI-Report-{TenantName}-{YYYY-MM}.pdf
```

---

## B4. Backend Data Requirements (M5-3 T06-T07)

### Endpoint 1: `/api/v1/roi/summary/{tenantId}`
```json
{
  "tenantId": "t-hcmc-001",
  "reportDate": "2026-06-26",
  "totalImplementationCost": 50000000,
  "monthsElapsed": 6,
  "totalSavings": 35055000,
  "paybackPeriodMonths": 8.6,
  "paybackProgress": 0.65,
  "buildings": [
    {
      "buildingId": "b-district1-tower-a",
      "monthlyCosts": {
        "baseFee": 2000000,
        "sensorOverage": 150000,
        "aiOverage": 7500,
        "total": 2157500
      },
      "monthlySavings": {
        "manualMonitoring": 5000000,
        "esgReportLabor": 3000000,
        "total": 8000000
      },
      "netSavings": 5842500,
      "roi": 2.71
    }
  ],
  "carbonReduction": {
    "baselineKwh": 150000,
    "currentKwh": 120000,
    "reductionKwh": 30000,
    "reductionPercent": 20.0,
    "co2TonsAvoided": 15.0,
    "carbonCreditValueVnd": 7500000
  }
}
```

### Endpoint 2: `/api/v1/roi/comparison/{buildingId}`
Pre-UIP vs Post-UIP time-series data for charts.

### Endpoint 3: `/api/v1/roi/export/{tenantId}` → PDF generation

---

## B5. Open Questions for Product Owner

### Q1: Manual Operations Baseline
**Question:** How do we establish the "manual operations cost" baseline if the building never had manual monitoring?  
**Recommendation:** Use industry benchmarks (e.g., 1 full-time facility manager = 15,000,000 VND/month salary) + time-motion study for alert response.

### Q2: Carbon Credit Price Fluctuation
**Question:** Should CO₂ savings use live carbon credit market price or fixed rate?  
**Recommendation:** Use fixed 500,000 VND/ton for simplicity; update quarterly based on Vietnam carbon market (if active).

### Q3: Multi-Year ROI Projection
**Question:** Should the dashboard show 3-year or 5-year projected savings?  
**Recommendation:** 3-year projection aligns with pilot contract duration; 5-year for enterprise contracts.

---

## Appendix: LOTUS VN Scoring Example

### Example Building: HCMC District 1 Office Tower A

| Category | Indicators Scored | Points Earned | Max Points | Notes |
|----------|-------------------|---------------|------------|-------|
| **Energy (EN)** | EN-1, EN-2, EN-3, EN-4 | 12 | 20 | Missing solar panels (EN-2 = 0) |
| **Water (WA)** | WA-1, WA-3 | 7 | 12 | No greywater system (WA-2 = 0) |
| **IEQ** | IEQ-1, IEQ-2, IEQ-3 | 11 | 15 | Excellent AQI sensors (IEQ-2 = 4) |
| **Materials (MA)** | MA-1, MA-2 | 4 | 10 | Manual input (limited documentation) |
| **Site (ST)** | ST-1 | 3 | 10 | Good public transport access (ST-1 = 3) |
| **TOTAL** | — | **37** | **67** | **Below Certified threshold** |

**Verdict:** Building scores 37/67 = 55% → Does NOT qualify for LOTUS Certified (needs ≥40 points absolute, not percentage).

**Improvement Plan:**
1. Install solar panels (EN-2 +2 points) → 39 points (still not Certified)
2. Add CO₂ sensors to meeting rooms (IEQ-1 +4 points) → 41 points → **Certified achieved** ✓

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-06-26 | BA (UIP Team) | Initial draft for M5-2 T13 |

---

**Next Actions:**
1. Backend Data Eng reviews LOTUS VN data queries (A2-A6) for M5-4 implementation
2. Frontend reviews ROI UI wireframes (B2) for M5-3 T06-T08
3. PO answers open questions (B5) for baseline calculation method
4. Finance team reviews carbon credit pricing assumption (Q2)
