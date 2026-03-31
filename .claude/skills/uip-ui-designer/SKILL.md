---
name: uip-ui-designer
description: >
  UIP UI Designer skill. Domain knowledge for: smart city dashboard UI/UX design,
  City Operations Center layout, ESG Analytics Dashboard, environmental monitoring displays,
  traffic management UI, citizen services portal, GIS map visualization specs,
  BPMN workflow visual editor (urban AI nodes), alert rule builder interface,
  alert severity color system, AQI gauge design, MUI theme for smart city,
  accessibility (WCAG 2.1 AA), responsive layout, Vietnamese smart city UX patterns.
---

# UIP UI Designer

You are the **UI/UX Designer** for the UIP Smart City system. You create precise visual specifications for urban intelligence dashboards that are clear for city operations staff and accessible to citizens.

## Design System

### Technology Base
- **Material-UI 5** (MUI) — component foundation
- **Custom UIP Smart City Theme** — extended Material Design
- **Leaflet / MapLibre GL JS** — map visualization
- **MUI X-Charts** + **recharts** — data visualization
- **bpmn-js** — BPMN workflow designer

### Color System

#### Primary Palette (UIP Smart City)
```
Primary:    #1565C0  (UIP Blue — trust, city authority)
Secondary:  #2E7D32  (City Green — sustainability, ESG)
Background: #F5F7FA  (Light gray — dashboard backgrounds)
Surface:    #FFFFFF  (Card backgrounds)
```

#### Alert Severity Colors (critical for smart city)
```
P0 EMERGENCY: background #FFEBEE, text #B71C1C, border #F44336
P1 WARNING:   background #FFF3E0, text #E65100, border #FF9800
P2 ADVISORY:  background #FFFDE7, text #F9A825, border #FFC107
P3 INFO:      background #E3F2FD, text #1565C0, border #42A5F5
```

#### AQI Level Color Scale
```
GOOD (0–50):              #4CAF50  (green)
MODERATE (51–100):        #FFEB3B  (yellow)
SENSITIVE (101–150):      #FF9800  (orange)
UNHEALTHY (151–200):      #F44336  (red)
VERY_UNHEALTHY (201–300): #9C27B0  (purple)
HAZARDOUS (>300):         #B71C1C  (dark red/maroon)
```

#### ESG Category Colors
```
Environmental: #2E7D32  (deep green)
Social:        #1565C0  (primary blue)
Governance:    #6A1B9A  (deep purple)
```

#### Sensor Status Colors
```
ONLINE:       #4CAF50  (green)
OFFLINE:      #F44336  (red)
DEGRADED:     #FF9800  (orange)
MAINTENANCE:  #1976D2  (blue)
```

### Typography
```
Font Family: "Inter", "Roboto", sans-serif

H1: 2.125rem / 700 — page titles
H2: 1.875rem / 600 — section headers
H3: 1.5rem / 600 — card titles
H4: 1.25rem / 600 — metric values
Body1: 1rem / 400 — content
Body2: 0.875rem / 400 — secondary text
Caption: 0.75rem / 400 — labels, sensor IDs, timestamps
Overline: 0.625rem / 500 UPPERCASE — category tags (ENVIRONMENTAL / SOCIAL)
```

**Spacing**: 8px base unit (spacing(1) = 8px)

**Elevation**: Cards: 1–3. Alert banners: 6. Map overlay panels: 8. Modals: 16.

## Application Layouts

### City Operations Center (Main Dashboard)
```
┌─────────────────────────────────────────────────────┐
│ [UIP Logo] City Operations Center  [🔔3] [⚙] [User]│
├─────────────────────────────────────────────────────┤
│ 🔴 [P0 ALERT BANNER — visible when emergency active] │
├────────────┬────────────────────────────────────────┤
│ Side Nav   │ Real-Time City Map (main viewport)     │
│ ─────────  │                                        │
│ Operations │  [Colored sensor dots on map]          │
│ Environment│  [District boundary overlays]          │
│ Traffic    │  [Incident markers]                    │
│ Energy     │                                        │
│ Citizens   │  [Legend] [Layer toggle] [Zoom ±]     │
│ ESG        │                                        │
│ AI Flows   │                                        │
├────────────┤────────────────────────────────────────┤
│ Alert Panel│ KPI Strip: AQI | Traffic | Energy | 💬 │
│ P0: [0]    │                                        │
│ P1: [3]    │                                        │
│ P2: [12]   │                                        │
└────────────┴────────────────────────────────────────┘
```

### ESG Dashboard
```
┌─────────────────────────────────────────────────────┐
│ ESG Dashboard            [Q1 2025 ▼] [Export PDF]   │
├──────────────┬───────────────┬─────────────────────┤
│ ENVIRONMENTAL│ SOCIAL        │ GOVERNANCE           │
│ Score: 72/100│ Score: 68/100 │ Score: 81/100        │
│ [Ring chart] │ [Ring chart]  │ [Ring chart]         │
├──────────────┴───────────────┴─────────────────────┤
│ Key Indicators                  [Filter by zone ▼]  │
│ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│ │ CO2     │ │ AQI avg │ │ CitiSat │ │Complian.│   │
│ │3.2t/yr  │ │65 (Mod.)│ │NPS: 52  │ │99.1%    │   │
│ │↓ Better │ │→ Stable │ │↑ Better │ │✓ On tgt │   │
│ └─────────┘ └─────────┘ └─────────┘ └─────────┘   │
├─────────────────────────────────────────────────────┤
│ 12-month Trend: [AQI / CO2 / Social Score line chart]│
└─────────────────────────────────────────────────────┘
```

### Environmental Monitoring Panel
```
┌─────────────────────────────────────────────────────┐
│ Air Quality Monitor     [District: All ▼] [● LIVE]  │
├──────────────┬──────────────────────────────────────┤
│ District List│ AQI Heatmap                          │
│ Q1: 125 ⚠  │  [Color gradient overlay on city map] │
│ Q2:  68 ✓  │  Each district = avg AQI color        │
│ Q3: 201 🔴 │                                       │
│ Q4:  45 ✓  │  [Legend always visible, bottom-left] │
├──────────────┴──────────────────────────────────────┤
│ 24h Trend                          [1h][6h][24h][7d]│
│ [LineChart — with Unhealthy/Hazardous threshold lines]│
└─────────────────────────────────────────────────────┘
```

## Component Specs

### AQI Gauge Component
| Property | Value | Token |
|----------|-------|-------|
| Size | 120×120px | — |
| Shape | Arc (270°, starts bottom-left) | — |
| Fill color | Dynamic by AQI level | aqi color scale |
| Center text | AQI value, bold | typography.h4 |
| Sub-text | Level name ("Moderate") | typography.caption |
| Background arc | #E0E0E0 | grey.300 |
| State: Loading | Skeleton circle | — |
| State: Offline | Gray arc, "—", OFFLINE badge | — |

### Alert Notification Card
```
┌──────────────────────────────────────────────────┐
│ 🔴 P0 EMERGENCY               2025-01-15 14:32   │
│ Flood Warning — District 7                        │
│ Water level: 2.3m (threshold: 1.8m)              │
│ 3 sensors confirmed                               │
│ [View on Map]  [Acknowledge]  [Escalate]         │
└──────────────────────────────────────────────────┘
```
| Element | Spec |
|---------|------|
| Left border | 4px solid, alert level color |
| Background | Alert color at 10% opacity |
| Title | typography.subtitle1, 600 weight |
| Timestamp | typography.caption, text.secondary |
| Actions | text buttons, small, no padding |
| Icon | Level icon (🔴⚠️💛ℹ️) left of title |

### ESG KPI Card
```
┌─────────────────────────┐
│ ENVIRONMENTAL            │  ← overline, category color
│ Average AQI              │  ← body1, 600 weight
│ 65.4                     │  ← h3, primary color
│ μg/m³                    │  ← body2, secondary
│ ████████░░  72%          │  ← LinearProgress
│ Target: 50  ↑ Improving  │  ← caption + trend chip
└─────────────────────────┘
```

### Sensor Status Badge
| Status | Color | Icon |
|--------|-------|------|
| ONLINE | success.main (#4CAF50) | WiFi icon |
| OFFLINE | error.main (#F44336) | WiFi-off icon |
| DEGRADED | warning.main (#FF9800) | Warning icon |
| MAINTENANCE | info.main (#1976D2) | Build icon |

### BPMN AI Decision Node (Urban Workflow)
```
┌──────────────────────────┐
│ 🤖 AI Decision           │  ← header, #6A1B9A background, white text
├──────────────────────────┤
│ Flood Risk Assessment    │  ← body1
│ Model: flood-predict-v2  │  ← caption, secondary
│ Confidence: ≥ 0.85 ✓    │  ← badge: green if met
│ Auto-exec: OFF           │  ← always visible for P0 actions
└──────────────────────────┘
```
Border: 2px solid #6A1B9A, border-radius: 8px, min size: 200×80px

### Alert Rule Builder Row
```
┌───────────────┐ ┌────────────┐ ┌──────────────────┐ [×]
│ AQI Value   ▼ │ │ > (greater)│ │ 150              │
└───────────────┘ └────────────┘ └──────────────────┘

Then trigger: [P1 WARNING ▼]  Notify: [☑ App] [☑ SMS] [☐ Siren]
```

## Map Visualization Standards

### Sensor Marker Design
- Circle markers: radius 8px (zoom 10), scales to 12px (zoom 14)
- Fill: AQI color (or category color for non-air sensors)
- Stroke: 2px white (contrast on dark map tiles)
- Hover: +4px radius + tooltip popup
- Offline sensor: gray fill with diagonal slash

### Map Clustering (>200 markers)
- Use Leaflet.markercluster
- Cluster badge: count, background = dominant AQI color of cluster
- Expand on click

### Map Legend (always present)
```
┌──────────────────┐
│ Air Quality Index │
│ ● Good     0–50  │
│ ● Moderate 51–100│
│ ● Sensitive 101+ │
│ ● Unhealthy 151+ │
│ ● Very Unhl 201+ │
│ ● Hazardous 301+ │
└──────────────────┘
Position: bottom-left, elevation 4
```

### Heatmap Overlay
- District polygon fill (PostGIS boundaries)
- Opacity: 0.5 by default (user-adjustable with slider)
- Always accompanied by legend
- Colorblind-safe mode: toggle in settings (uses pattern fill)

## Accessibility Requirements (WCAG 2.1 AA)

- **Color + Text + Icon**: Alert severity uses 3 cues (never color alone)
- **AQI levels**: text label required next to color indicator
- **Maps**: keyboard navigation, screen reader description of current map state
- **Charts**: `role="img"` + `aria-label` with data summary (e.g., "Bar chart showing AQI average by district")
- **Minimum contrast**: 4.5:1 body text, 3:1 UI components
- **Focus ring**: 2px outline, visible on all interactive elements
- **Form inputs**: all have associated `<label>` or `aria-label`
- **Modals**: Escape closes, focus trapped inside, returns on close
- **Alert banners**: `role="alert"` + `aria-live="assertive"` for P0

## Responsive Breakpoints (MUI)

| Breakpoint | Width | Layout Behavior |
|------------|-------|-----------------|
| xs | 0–600px | Mobile: bottom nav, stacked cards, simplified map |
| sm | 600–900px | Tablet: slide-in drawer, 2-col grid |
| md | 900–1200px | Desktop: fixed sidebar, 3-col grid |
| lg | 1200px+ | Full ops center: multi-pane with map |

## Navigation Structure

```
UIP Smart City Platform
├── Operations Center     ← Real-time map + alerts
├── Environment
│   ├── Air Quality       ← AQI heatmap + sensors
│   ├── Water Quality
│   └── Noise Monitor
├── Traffic
│   ├── Traffic Flow      ← Congestion map
│   ├── Incidents         ← Active incidents
│   └── Signal Control
├── Energy
│   ├── Grid Monitor
│   └── Consumption
├── Citizen Services
│   ├── Complaints
│   └── Service Requests
├── ESG Dashboard
│   ├── Environmental KPIs
│   ├── Social KPIs
│   └── Quarterly Reports
├── AI Workflows          ← BPMN designer + monitoring
├── Alert Rules           ← Rule builder
└── Settings
    ├── Sensor Management
    └── System Config
```

## UX Principles for Smart City

1. **Operational First**: Operators need real-time situational awareness — primary view is always map
2. **Alert Hierarchy**: P0 emergency MUST interrupt current view — full-screen overlay if needed
3. **Data Density with Clarity**: Many metrics visible, but AQI/alert status always dominant
4. **Progressive Disclosure**: Raw sensor data → district aggregates → city-wide KPIs (drill-up/down)
5. **Accessibility for Public**: Citizen portal = simple, Vietnamese language, large text options
6. **Trust Indicators**: Show data freshness timestamp on every live panel ("Last updated 12s ago")

Docs reference: `docs/design/`, `docs/frontend/`, `docs/api/`
