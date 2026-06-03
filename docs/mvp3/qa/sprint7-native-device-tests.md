# Sprint 7 — Native Device Test Cases (iOS + Android)

**Created:** 2026-06-02
**Priority:** P1 | **Tier:** 2
**Prerequisites:** Xcode (iOS), Android Studio (Android), staging environment running
**Related:** QA-4 in sprint7-task-assignments.md

---

## Pre-Test Setup

### iOS
- [ ] Xcode 15+ installed
- [ ] TestFlight access configured
- [ ] Physical iOS device registered (iPhone 12+)
- [ ] Expo Go hoặc development build installed

### Android
- [ ] Android Studio installed
- [ ] APK built from `frontend/` (Expo export hoặc EAS build)
- [ ] Physical Android device (API 30+)
- [ ] USB debugging enabled

### Common
- [ ] Staging environment accessible from device network
- [ ] Keycloak reachable: `http://staging:8085`
- [ ] Test accounts available: `pilot-admin`, `pilot-operator`, `pilot-viewer`

---

## Test Cases

### TC-ND-01: PKCE Login Flow — iOS

| Field | Value |
|-------|-------|
| **Priority** | P0 |
| **Platform** | iOS (Safari / WKWebView) |
| **Preconditions** | App installed, not logged in |

**Steps:**
1. Launch app on iOS device
2. Tap "Sign In" button
3. Safari opens Keycloak login page
4. Enter credentials: `pilot-operator` / `PilotOp#2026!`
5. Tap "Sign In" on Keycloak page
6. Safari redirects back to app via universal link

**Expected Result:**
- App receives authorization code via PKCE flow
- Token exchanged successfully
- User lands on Operator dashboard
- No blank screen or redirect loop
- Token stored securely in Keychain

**Status:** ⬜ PASS / ⬜ FAIL

---

### TC-ND-02: PKCE Login Flow — Android

| Field | Value |
|-------|-------|
| **Priority** | P0 |
| **Platform** | Android (Chrome Custom Tab) |
| **Preconditions** | App installed, not logged in |

**Steps:**
1. Launch app on Android device
2. Tap "Sign In" button
3. Chrome Custom Tab opens Keycloak login
4. Enter credentials: `pilot-operator` / `PilotOp#2026!`
5. Tap "Sign In"
6. Custom Tab redirects back to app via deep link

**Expected Result:**
- PKCE code exchange completes
- User lands on Operator dashboard
- Token stored securely in EncryptedSharedPreferences
- No crash or ANR

**Status:** ⬜ PASS / ⬜ FAIL

---

### TC-ND-03: Push Token Registration — iOS (APNs)

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Platform** | iOS |
| **Preconditions** | TC-ND-01 PASS, notification permission granted |

**Steps:**
1. App prompts for notification permission
2. Tap "Allow" on iOS permission dialog
3. App registers for APNs push token
4. Verify token sent to backend: `POST /api/v1/devices/register`

**Expected Result:**
- APNs device token received
- Token registered with backend
- Device appears in user's device list
- No error in app logs

**Status:** ⬜ PASS / ⬜ FAIL

---

### TC-ND-04: Push Token Registration — Android (FCM)

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Platform** | Android |
| **Preconditions** | TC-ND-02 PASS, Google Play Services available |

**Steps:**
1. App prompts for notification permission (Android 13+)
2. Tap "Allow"
3. App registers for FCM push token
4. Verify token sent to backend

**Expected Result:**
- FCM registration token received
- Token registered with backend
- Device appears in user's device list

**Status:** ⬜ PASS / ⬜ FAIL

---

### TC-ND-05: Push Notification Received — iOS

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Platform** | iOS |
| **Preconditions** | TC-ND-03 PASS, app in background |

**Steps:**
1. Put app in background (home button)
2. Trigger alert from staging: `POST /api/v1/test/inject-structural-alert`
3. Wait for push notification (max 30s)

**Expected Result:**
- iOS notification banner appears
- Alert title and body visible
- App icon badge updated
- Sound plays (if configured)

**Status:** ⬜ PASS / ⬜ FAIL

---

### TC-ND-06: Push Notification Received — Android

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Platform** | Android |
| **Preconditions** | TC-ND-04 PASS, app in background |

**Steps:**
1. Put app in background
2. Trigger alert from staging
3. Wait for push notification (max 30s)

**Expected Result:**
- Android notification appears in system tray
- Alert title and body visible
- Notification sound/vibration

**Status:** ⬜ PASS / ⬜ FAIL

---

### TC-ND-07: Deep-Link — Tap Notification → Alert Detail (iOS)

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Platform** | iOS |
| **Preconditions** | TC-ND-05 PASS |

**Steps:**
1. Tap the push notification banner
2. App opens to foreground
3. Navigation triggers deep-link

**Expected Result:**
- App opens and navigates to alert detail screen
- Alert ID matches the triggered alert
- Alert data loads (severity, description, timestamp, sensor info)
- Back navigation works correctly

**Status:** ⬜ PASS / ⬜ FAIL

---

### TC-ND-08: Deep-Link — Tap Notification → Alert Detail (Android)

| Field | Value |
|-------|-------|
| **Priority** | P1 |
| **Platform** | Android |
| **Preconditions** | TC-ND-06 PASS |

**Steps:**
1. Tap the push notification
2. App opens to foreground
3. Navigation triggers deep-link

**Expected Result:**
- App opens and navigates to alert detail screen
- Alert data loads correctly
- Back navigation works

**Status:** ⬜ PASS / ⬜ FAIL

---

### TC-ND-09: Foreground Notification Banner (iOS)

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Platform** | iOS |
| **Preconditions** | App in foreground |

**Steps:**
1. Keep app in foreground
2. Trigger alert from staging
3. Verify in-app notification banner appears

**Expected Result:**
- In-app banner/notification displayed at top of screen
- Banner shows alert severity and title
- Tap banner → navigate to alert detail
- Banner auto-dismisses after 5s if not tapped

**Status:** ⬜ PASS / ⬜ FAIL

---

### TC-ND-10: Foreground Notification Banner (Android)

| Field | Value |
|-------|-------|
| **Priority** | P2 |
| **Platform** | Android |
| **Preconditions** | App in foreground |

**Steps:**
1. Keep app in foreground
2. Trigger alert from staging
3. Verify in-app notification banner appears

**Expected Result:**
- In-app banner displayed
- Tap → navigate to alert detail
- Auto-dismiss after 5s

**Status:** ⬜ PASS / ⬜ FAIL

---

## Results Summary

| TC-ID | Platform | Title | Status |
|-------|----------|-------|--------|
| TC-ND-01 | iOS | PKCE Login | ⬜ |
| TC-ND-02 | Android | PKCE Login | ⬜ |
| TC-ND-03 | iOS | Push Token (APNs) | ⬜ |
| TC-ND-04 | Android | Push Token (FCM) | ⬜ |
| TC-ND-05 | iOS | Push Notification | ⬜ |
| TC-ND-06 | Android | Push Notification | ⬜ |
| TC-ND-07 | iOS | Deep-Link | ⬜ |
| TC-ND-08 | Android | Deep-Link | ⬜ |
| TC-ND-09 | iOS | Foreground Banner | ⬜ |
| TC-ND-10 | Android | Foreground Banner | ⬜ |

**Pass Rate:** 0/10

---

## Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Tester | | | |
| QA Engineer | | | |

---

*Sprint 7 — Native Device Test Cases | 10 test cases | iOS + Android | Requires physical devices*
