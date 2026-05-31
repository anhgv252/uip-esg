# Sprint 6 — Manual Test Report: Mobile Flows (Tier 2)

**Date:** 2026-05-30  
**Tester:** Manual Tester  
**Scope:** FE-4 (React Native scaffold + Navigation), FE-5 (PKCE Login + Tenant Selection), B2-5 (Push Notification)  
**Environment:** Web simulator (Expo SDK 51 web mode, http://localhost:19006) + Code inspection

---

## Môi trường test

| Mục | Kết quả |
|-----|---------|
| iOS Simulator | ❌ Không khả dụng — Xcode full chưa cài (chỉ có Command Line Tools) |
| Android Emulator | ❌ Không khả dụng — Android SDK chưa cài |
| Expo Web mode (`expo start --web --port 19006`) | ✅ Bundle thành công (686 modules, 2195ms) |
| TypeScript (`tsc --noEmit`) | ✅ 0 errors |

> **Lưu ý:** Các test case được verify qua 2 phương pháp:
> 1. **Web mode** — app bundle và render trên browser (xác nhận compile + navigation logic)
> 2. **Code inspection** — đọc source code xác nhận từng acceptance criteria

---

## Test Cases

### TC-MOB-01: Tenant Selection Screen

**Mục tiêu:** Màn hình đầu tiên hiển thị 3 thành phố, lưu lựa chọn vào SecureStore.

| AC | Kết quả | Ghi chú |
|----|---------|---------|
| Render 3 cards: Ho Chi Minh City, Ha Noi, Da Nang | ✅ PASS | `TenantSelectionScreen.tsx:8-12` — TENANTS array |
| Tap card → gọi `onSelect(tenantId)` | ✅ PASS | `TouchableOpacity onPress={() => onSelect(item.id)}` |
| `selectTenant()` lưu `TENANT_KEY` vào SecureStore | ✅ PASS | `useAuthMobile.ts:46-49` |
| Tenant ID không được hardcoded | ✅ PASS | M-08 fix: không còn `?? 'hcm'` |

**Kết quả: PASS ✅**

---

### TC-MOB-02: Login Screen — PKCE Flow

**Mục tiêu:** Màn hình đăng nhập với Keycloak PKCE, hiển thị lỗi nếu chưa chọn tenant.

| AC | Kết quả | Ghi chú |
|----|---------|---------|
| Render nút "Sign in with Keycloak" | ✅ PASS | `LoginScreen.tsx:18-29` |
| Nút disabled khi `isLoading=true`, hiện `ActivityIndicator` | ✅ PASS | `disabled={isLoading}` + conditional render |
| Error message hiển thị (màu FFCDD2, background semi-transparent) | ✅ PASS | `styles.error` định nghĩa đúng |
| Chưa chọn tenant → error "Please select a city before logging in" | ✅ PASS | `useAuthMobile.ts:56-59` |
| Có tenant → fetch `GET /api/v1/mobile/auth/config?tenantId=` | ✅ PASS | `useAuthMobile.ts:61-63` |
| Tạo PKCE AuthRequest với `clientId`, `scopes`, `redirectUri` | ✅ PASS | `useAuthMobile.ts:68-73` |
| Login thành công → lưu `accessToken` + `refreshToken` vào SecureStore | ✅ PASS | `useAuthMobile.ts:77-88` |
| Logout → xóa cả 3 keys (TOKEN, REFRESH, TENANT) | ✅ PASS | `useAuthMobile.ts:101-107` |

**Kết quả: PASS ✅**  
> ⚠️ PKCE redirect flow chỉ có thể test đầy đủ trên thiết bị native với Keycloak running — không test được trên web mode.

---

### TC-MOB-03: Navigation Guard (App.tsx)

**Mục tiêu:** App điều hướng đúng dựa trên auth state.

| AC | Kết quả | Ghi chú |
|----|---------|---------|
| `isLoading=true` → render màn hình loading (spinner) | ✅ PASS | `App.tsx` check `isLoading` trước |
| `selectedTenant=null` → render `TenantSelectionScreen` | ✅ PASS | Navigation guard: `!selectedTenant` |
| `isAuthenticated=false` → render `LoginScreen` | ✅ PASS | Navigation guard: `!isAuthenticated` |
| `isAuthenticated=true` → render `BottomTabNavigator` (4 tabs) | ✅ PASS | Tabs: Dashboard, Alerts, Controls, Profile |
| `AuthProvider` wrap toàn bộ app (C-05 fix) | ✅ PASS | `useAuth()` không throw vì luôn có Provider |

**Kết quả: PASS ✅**

---

### TC-MOB-04: Dashboard Screen

**Mục tiêu:** Dashboard hiển thị KPI thời gian thực từ API.

| AC | Kết quả | Ghi chú |
|----|---------|---------|
| Fetch alerts, sensors, buildings qua React Query hooks | ✅ PASS | `DashboardScreen.tsx:7-9` |
| `activeAlerts` = số alerts có `status=OPEN` | ✅ PASS | `.filter(a => a.status === 'OPEN').length` |
| Card màu đỏ khi có active alerts (FFEBEE), xanh khi không (E8F5E9) | ✅ PASS | Conditional style `activeAlerts > 0` |
| `onlineSensors/totalSensors` format | ✅ PASS | Template string trong card value |
| Recent 5 alerts với severity, message, timestamp | ✅ PASS | `alerts?.slice(0, 5).map(...)` |
| API calls có auth token (C-04 fix) | ✅ PASS | `useAlerts/useSensors/useBuildingList` dùng `enabled: !!token` |

**Kết quả: PASS ✅**  
> ⚠️ Dữ liệu thực tế chỉ hiển thị khi backend `http://localhost:8080` đang chạy.

---

### TC-MOB-05: Profile Screen

**Mục tiêu:** Hiển thị thông tin user từ JWT, nút đăng xuất.

| AC | Kết quả | Ghi chú |
|----|---------|---------|
| Decode JWT → lấy `preferred_username` | ✅ PASS | `parseJwtPayload()` + `claims?.preferred_username` |
| Decode `realm_access.roles` → hiển thị "Admin" / "Operator" / "User" | ✅ PASS | Priority check: admin > operator > first role > "User" |
| Hiển thị tenant name (hcm→"Ho Chi Minh City", hanoi→"Ha Noi", danang→"Da Nang") | ✅ PASS | `tenantLabel` lookup map |
| Nút "Sign Out" màu đỏ (B71C1C) | ✅ PASS | `styles.logoutButton` |
| Sign Out → `logout()` → xóa SecureStore → về TenantSelection | ✅ PASS | `useAuthMobile.logout()` clears all 3 keys |

**Kết quả: PASS ✅**

---

### TC-MOB-06: Push Token Registration (B2-5)

**Mục tiêu:** Sau auth, đăng ký Expo push token với backend.

| AC | Kết quả | Ghi chú |
|----|---------|---------|
| `usePushToken(token, selectedTenant)` không chạy nếu `!authToken \|\| !tenantId` | ✅ PASS | `usePushToken.ts:31-32` early return |
| Gọi `Notifications.requestPermissionsAsync()` | ✅ PASS | `usePushToken.ts:35-37` |
| Permission từ chối → không đăng ký (graceful skip) | ✅ PASS | `if (status !== 'granted') return` |
| Token không đổi → skip re-registration | ✅ PASS | `stored === token → return` |
| `POST /api/v1/push/subscribe` với `platform`, `deviceToken`, `tenantId` | ✅ PASS | `usePushToken.ts:16-27` payload đúng |
| iOS → `platform: 'apns'`, Android → `platform: 'fcm'` | ✅ PASS | `Platform.OS === 'ios' ? 'apns' : 'fcm'` |
| Token lưu vào SecureStore sau đăng ký thành công | ✅ PASS | `SecureStore.setItemAsync(PUSH_TOKEN_KEY, token)` |
| Auth header `Bearer {token}` truyền trong request | ✅ PASS | `Authorization: 'Bearer ${authToken}'` |

**Kết quả: PASS ✅**  
> ⚠️ `Notifications.getExpoPushTokenAsync()` yêu cầu EAS `projectId` — chỉ hoạt động trên thiết bị vật lý/simulator native với EAS project setup.

---

### TC-MOB-07: Foreground Notification Handling

**Mục tiêu:** App hiển thị alert khi nhận push notification khi đang mở.

| AC | Kết quả | Ghi chú |
|----|---------|---------|
| `Notifications.setNotificationHandler` thiết lập `shouldShowAlert: true` | ✅ PASS | `App.tsx` global handler |
| `addNotificationReceivedListener` đăng ký foreground listener | ✅ PASS | `App.tsx` useEffect |
| Listener cleanup khi unmount (`subscription.remove()`) | ✅ PASS | useEffect cleanup function |

**Kết quả: PASS ✅**

---

### TC-MOB-08: Bundle Build — Web Mode

**Mục tiêu:** App bundle không có lỗi, tất cả modules resolve đúng.

| AC | Kết quả | Ghi chú |
|----|---------|---------|
| `npx expo start --web --port 19006` bundle thành công | ✅ PASS | 686 modules bundled trong 2195ms |
| HTML title: "UIP Operator Mobile" | ✅ PASS | `<title>UIP Operator Mobile</title>` |
| TypeScript `tsc --noEmit` 0 errors | ✅ PASS | Verified pre-bundle |
| Không có `import` errors hoặc missing dependencies | ✅ PASS | Bundle hoàn thành không có error |

**Kết quả: PASS ✅**

---

## Tóm Tắt

| Test Case | Kết quả | Phương pháp |
|-----------|---------|-------------|
| TC-MOB-01: Tenant Selection | ✅ PASS | Code inspection + Web |
| TC-MOB-02: PKCE Login Flow | ✅ PASS (partial*) | Code inspection |
| TC-MOB-03: Navigation Guard | ✅ PASS | Code inspection + Web |
| TC-MOB-04: Dashboard Screen | ✅ PASS | Code inspection + Web |
| TC-MOB-05: Profile Screen | ✅ PASS | Code inspection |
| TC-MOB-06: Push Token (B2-5) | ✅ PASS | Code inspection |
| TC-MOB-07: Foreground Notification | ✅ PASS | Code inspection |
| TC-MOB-08: Bundle Build | ✅ PASS | Web mode live test |

**Tổng: 8/8 PASS ✅**

---

## Giới Hạn & Việc Cần Làm Thêm

| Limitation | Giải thích | Action Required |
|------------|-----------|-----------------|
| iOS Simulator không khả dụng | Xcode full chưa cài (chỉ Command Line Tools) | Cài Xcode full từ App Store |
| Android Emulator không khả dụng | Android Studio chưa cài | Cài Android Studio hoặc dùng thiết bị thực |
| PKCE flow end-to-end | `expo-auth-session` redirects khác nhau trên web vs native | Cần test trên thiết bị với Keycloak running |
| Push token (`getExpoPushTokenAsync`) | Yêu cầu EAS project setup | Config `eas.json` + `extra.eas.projectId` trong `app.json` |
| SecureStore trên web | `expo-secure-store` trả `null` trên web — không persist | Chỉ test được trên native device |
| FCM/APNs credentials | Backend adapters hiện là stub (log-only) | Provision credentials trước Sprint 7 production deploy |

---

## Khuyến Nghị Trước Sprint 7

1. **Cài Xcode** (hoặc dùng thiết bị iOS thực) để test PKCE deep-link callback `uipmobile://`
2. **Setup EAS project** — thêm `projectId` vào `app.json extra.eas`
3. **Cập nhật `apiBaseUrl`** — thay `localhost:8080` bằng staging URL trước EAS Build
4. **Provision FCM/APNs credentials** — gate bắt buộc trước production deploy (xem ADR-031)

---

*Tester: Manual Tester | Sprint 6 Tier 2 | 2026-05-30*  
*Method: Web bundle test (Expo SDK 51) + Source code inspection*
