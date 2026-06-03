# Sprint 7 — Mobile Regression Test Cases (20 Cases)

**Created:** 2026-06-02
**Priority:** P1 | **Tier:** 2
**Prerequisites:** Staging environment running, mobile device hoặc emulator
**Related:** QA-5 in sprint7-task-assignments.md
**Responsive breakpoints:** 375px (iPhone SE), 768px (iPad Mini), 1024px (iPad)

---

## Test Environment

- **App URL:** `http://staging:3000` (PWA) hoặc native build
- **Test Accounts:**
  - Citizen: `loginAsCitizen()` helper
  - Operator: `pilot-operator` / `PilotOp#2026!`
  - Admin: `pilot-admin` / `PilotAdmin#2026!`
- **Devices:** iOS (Safari), Android (Chrome), hoặc Playwright mobile emulation

---

## Test Cases

### Dashboard (5 cases)

#### TC-MOB-01: Mobile Dashboard loads with KPI cards

| Field | Value |
|-------|-------|
| **Priority** | P0 |
| **Viewport** | 390x844 (iPhone 14) |
| **Preconditions** | Logged in as operator |

**Steps:**
1. Open app on mobile viewport
2. Wait for dashboard to load

**Expected Result:**
- KPI cards visible: active sensors, open alerts, safety score
- Cards stack vertically (1 column on mobile)
- No horizontal scroll
- Loading skeleton → data → rendered cards

**Status:** ⬜ PASS / ⬜ FAIL

---

#### TC-MOB-02: Mini 7-day energy trend sparkline renders

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Viewport** | 390x844 |

**Steps:**
1. Scroll down on dashboard
2. Locate "Energy Trend" card

**Expected Result:**
- Sparkline chart renders with 7 data points
- Chart is responsive (fills card width)
- No overflow or clipping

**Status:** ⬜ PASS / ⬜ FAIL

---

#### TC-MOB-03: Dashboard responsive at 768px (tablet)

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Viewport** | 768x1024 (iPad Mini) |

**Steps:**
1. Resize viewport to 768px width
2. Observe layout changes

**Expected Result:**
- KPI cards in 2-column grid
- Charts expand to fill width
- Navigation switches from bottom nav to sidebar (if applicable)

**Status:** ⬜ PASS / ⬜ FAIL

---

#### TC-MOB-04: Dashboard responsive at 1024px (tablet landscape)

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Viewport** | 1024x768 |

**Steps:**
1. Resize viewport to 1024px width

**Expected Result:**
- Full desktop-like layout
- Charts render at full width
- All KPI cards visible without scroll

**Status:** ⬜ PASS / ⬜ FAIL

---

#### TC-MOB-05: Dashboard loading state (skeleton)

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Viewport** | 390x844 |

**Steps:**
1. Clear cache / hard reload
2. Observe loading state before data arrives

**Expected Result:**
- Skeleton placeholders visible for KPI cards
- No layout shift when data loads
- Error state shown if API fails

**Status:** ⬜ PASS / ⬜ FAIL

---

### Alerts (5 cases)

#### TC-MOB-06: Alert list loads with pull-to-refresh

| Field | Value |
|-------|-------|
| **Priority** | P0 |
| **Viewport** | 390x844 |

**Steps:**
1. Navigate to Alerts screen
2. Pull down on list
3. Release to trigger refresh

**Expected Result:**
- Pull-to-refresh indicator shows
- List refreshes with latest data
- Loading spinner during refresh
- Data updates after refresh completes

**Status:** ⬜ PASS / ⬜ FAIL

---

#### TC-MOB-07: Alert detail screen on tap

| Field | Value |
|-------|-------|
| **Priority** | P0 |
| **Viewport** | 390x844 |

**Steps:**
1. Tap on an alert in the list

**Expected Result:**
- Alert detail screen slides in from right
- Severity badge, description, timestamp visible
- Sensor info section present
- Back button works

**Status:** ⬜ PASS / ⬜ FAIL

---

#### TC-MOB-08: Filter by severity chips

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Viewport** | 390x844 |

**Steps:**
1. Tap severity filter chip (e.g., "HIGH")
2. Observe filtered list

**Expected Result:**
- Only HIGH severity alerts shown
- Active filter chip highlighted
- Tap again to deselect → all alerts shown
- Multiple filters can be active simultaneously

**Status:** ⬜ PASS / ⬜ FAIL

---

#### TC-MOB-09: Alert list empty state

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Viewport** | 390x844 |

**Steps:**
1. Apply filter that matches no alerts (e.g., select severity with no matches)

**Expected Result:**
- "No alerts found" message displayed
- Clear filter option available
- No broken layout

**Status:** ⬜ PASS / ⬜ FAIL

---

#### TC-MOB-10: Alert badge count on bottom nav

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Viewport** | 390x844 |

**Steps:**
1. Navigate away from Alerts screen
2. Check bottom nav badge

**Expected Result:**
- Alert count badge visible on Alerts tab
- Count matches number of unread/open alerts
- Badge updates after viewing alerts

**Status:** ⬜ PASS / ⬜ FAIL

---

### Profile & Login (5 cases)

#### TC-MOB-11: Profile screen shows user info

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Viewport** | 390x844 |

**Steps:**
1. Tap Profile tab in bottom nav

**Expected Result:**
- User name displayed
- Role displayed (Operator/Viewer)
- Tenant info shown
- Settings link available

**Status:** ⬜ PASS / ⬜ FAIL

---

#### TC-MOB-12: Logout from mobile

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Viewport** | 390x844 |

**Steps:**
1. Go to Profile screen
2. Tap "Sign Out" button
3. Confirm logout

**Expected Result:**
- Token cleared from storage
- Redirected to login screen
- Cannot access protected routes after logout

**Status:** ⬜ PASS / ⬜ FAIL

---

#### TC-MOB-13: Login flow on mobile

| Field | Value |
|-------|-------|
| **Priority** | P0 |
| **Viewport** | 390x844 |

**Steps:**
1. Open app (logged out)
2. Enter credentials
3. Tap Sign In

**Expected Result:**
- Login form renders correctly on mobile
- Keyboard doesn't obscure password field
- Loading state during auth
- Successful login → dashboard

**Status:** ⬜ PASS / ⬜ FAIL

---

#### TC-MOB-14: Session expiry handling

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Viewport** | 390x844 |

**Steps:**
1. Leave app idle for token expiry period (or simulate)
2. Try to perform an action

**Expected Result:**
- App detects expired token
- Redirects to login screen
- No error crash
- Data preserved if possible

**Status:** ⬜ PASS / ⬜ FAIL

---

#### TC-MOB-15: Network error handling

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Viewport** | 390x844 |

**Steps:**
1. Turn off network on device
2. Try to load dashboard or alerts

**Expected Result:**
- "No internet connection" message shown
- Cached data displayed if available
- Retry button available
- No crash

**Status:** ⬜ PASS / ⬜ FAIL

---

### Responsive & Layout (5 cases)

#### TC-MOB-16: No horizontal scroll at 375px (iPhone SE)

| Field | Value |
|-------|-------|
| **Priority** | P0 |
| **Viewport** | 375x667 |

**Steps:**
1. Set viewport to 375x667
2. Navigate all screens (Dashboard, Alerts, Profile)
3. Check for horizontal scroll

**Expected Result:**
- No horizontal scroll on any screen
- All content fits within 375px width
- Tables scroll horizontally within container (not page-level)

**Status:** ⬜ PASS / ⬜ FAIL

---

#### TC-MOB-17: Bottom navigation accessible and tappable

| Field | Value |
|-------|-------|
| **Priority** | P0 |
| **Viewport** | 390x844 |

**Steps:**
1. Check bottom navigation bar
2. Tap each tab

**Expected Result:**
- Bottom nav visible at all times (sticky)
- All tabs tappable with adequate touch target (≥44px)
- Active tab highlighted
- Navigation between screens works

**Status:** ⬜ PASS / ⬜ FAIL

---

#### TC-MOB-18: Touch targets meet 44px minimum

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Viewport** | 390x844 |

**Steps:**
1. Inspect all interactive elements (buttons, links, tabs)
2. Measure touch target size

**Expected Result:**
- All buttons ≥44x44px
- All list items tappable
- Adequate spacing between interactive elements
- No overlapping touch targets

**Status:** ⬜ PASS / ⬜ FAIL

---

#### TC-MOB-19: Font sizes readable on mobile

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Viewport** | 390x844 |

**Steps:**
1. Check text readability across screens

**Expected Result:**
- Body text ≥14px
- Headings ≥18px
- Labels ≥12px
- Sufficient contrast ratio (WCAG AA)

**Status:** ⬜ PASS / ⬜ FAIL

---

#### TC-MOB-20: Orientation change (portrait ↔ landscape)

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Viewport** | 390x844 → 844x390 |

**Steps:**
1. Rotate device to landscape
2. Check layout adaptation
3. Rotate back to portrait

**Expected Result:**
- Layout adapts to landscape orientation
- No content cut off
- Bottom nav still accessible
- Charts resize appropriately

**Status:** ⬜ PASS / ⬜ FAIL

---

## Results Summary

| TC-ID | Module | Title | Status |
|-------|--------|-------|--------|
| TC-MOB-01 | Dashboard | KPI cards load | ⬜ |
| TC-MOB-02 | Dashboard | Energy sparkline | ⬜ |
| TC-MOB-03 | Dashboard | 768px responsive | ⬜ |
| TC-MOB-04 | Dashboard | 1024px responsive | ⬜ |
| TC-MOB-05 | Dashboard | Skeleton loading | ⬜ |
| TC-MOB-06 | Alerts | Pull-to-refresh | ⬜ |
| TC-MOB-07 | Alerts | Detail on tap | ⬜ |
| TC-MOB-08 | Alerts | Severity filters | ⬜ |
| TC-MOB-09 | Alerts | Empty state | ⬜ |
| TC-MOB-10 | Alerts | Badge count | ⬜ |
| TC-MOB-11 | Profile | User info | ⬜ |
| TC-MOB-12 | Profile | Logout | ⬜ |
| TC-MOB-13 | Login | Login flow | ⬜ |
| TC-MOB-14 | Login | Session expiry | ⬜ |
| TC-MOB-15 | Login | Network error | ⬜ |
| TC-MOB-16 | Responsive | 375px no scroll | ⬜ |
| TC-MOB-17 | Responsive | Bottom nav | ⬜ |
| TC-MOB-18 | Responsive | Touch targets | ⬜ |
| TC-MOB-19 | Responsive | Font sizes | ⬜ |
| TC-MOB-20 | Responsive | Orientation | ⬜ |

**Pass Rate:** 0/20

---

## Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Tester | | | |
| QA Engineer | | | |

---

*Sprint 7 — Mobile Regression Test Cases | 20 test cases | Dashboard + Alerts + Profile + Responsive*
