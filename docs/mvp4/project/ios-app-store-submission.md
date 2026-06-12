# iOS App Store Submission Guide — UIP Operator Mobile

**Bundle ID:** `com.uip.operatormobile`
**EAS Project ID:** `uip-smartcity-operator`
**Last updated:** 2026-06-12

---

## Prerequisites

1. **Apple Developer Account** (Organization enrollment, $99/year)
   - Enroll at: https://developer.apple.com/programs/enroll/
   - Requires D-U-N-S Number (free lookup at https://developer.apple.com/enroll/duns/)
   - Account holder must have legal authority to bind the organization

2. **App Store Connect access**
   - https://appstoreconnect.apple.com/
   - Roles needed: Account Holder, Admin, or App Manager

3. **EAS CLI** installed: `npm install -g eas-cli`

---

## Step 1: Create App ID in Apple Developer Portal

1. Go to https://developer.apple.com/account/resources/identifiers/add
2. Select **App IDs** > Continue
3. Configure:
   - **Description:** UIP Operator Mobile
   - **Bundle ID:** `com.uip.operatormobile` (Explicit)
   - **Capabilities:**
     - Push Notifications (required for APNs)
     - Sign in with Apple (optional, future)
4. Click Register

## Step 2: Create Provisioning Profile

1. Go to https://developer.apple.com/account/resources/profiles/add
2. Select **App Store** > Continue
3. Select App ID: `com.uip.operatormobile`
4. Select certificates (requires a valid Distribution Certificate)
5. Name the profile: `UIP Operator Mobile App Store`
6. Download the `.mobileprovision` file

### If no Distribution Certificate exists:
1. Go to Certificates > Add > Apple Distribution
2. Follow CSR generation steps in Keychain Access
3. Upload CSR, download certificate, install in Keychain

## Step 3: Configure EAS for iOS Build

Ensure `eas.json` has the production profile:

```json
{
  "build": {
    "production": {
      "autoIncrement": { "versionCode": true },
      "ios": {
        "simulator": false
      },
      "env": {
        "API_BASE_URL": "https://api.uip-smartcity.vn"
      }
    }
  }
}
```

## Step 4: Build IPA with EAS

```bash
cd applications/operator-mobile

# Login to EAS (first time)
eas login

# Build for iOS production
eas build --platform ios --profile production

# The build runs on EAS cloud servers.
# Download the IPA when complete.
```

### Alternative: Local build with Xcode

```bash
# Generate Xcode project from Expo
npx expo prebuild --platform ios

# Open in Xcode
open ios/operatormobile.xcworkspace

# Archive > Distribute App > App Store Connect
```

## Step 5: Submit to App Store Connect

### Via Xcode/Transporter:
1. Open **Transporter** app (macOS)
2. Drag the `.ipa` file
3. Click **Deliver**

### Via EAS Submit:
```bash
eas submit --platform ios --profile production
# Or submit the latest build:
eas submit --platform ios --latest
```

## Step 6: App Store Connect Configuration

1. Go to https://appstoreconnect.apple.com/
2. Create New App:
   - **Name:** UIP Operator Mobile
   - **Primary Language:** Vietnamese
   - **Bundle ID:** com.uip.operatormobile
   - **SKU:** uip-operator-mobile-vn

3. Fill in required metadata:
   - **Screenshots:** 6.7" iPhone, 5.5" iPhone, 12.9" iPad
     - City Operations dashboard, alert detail, AQI map, sensor list
   - **Description:** (Vietnamese)
     ```
     Ứng dụng quản lý vận hành thành phố thông minh UIP.
     - Giám sát chất lượng không khí (AQI) thời gian thực
     - Cảnh báo an toàn công trình tự động
     - Theo dõi cảm biến IoT toàn thành phố
     - Báo cáo ESG và phát triển bền vững
     ```
   - **Keywords:** smart city, IoT, AQI, ESG, operations
   - **Support URL:** https://uip-smartcity.vn/support
   - **Privacy Policy URL:** https://uip-smartcity.vn/privacy
   - **Category:** Utilities (primary), Business (secondary)
   - **Age Rating:** 4+ (no content restrictions)

4. **App Review Information:**
   - Provide test account credentials
   - Notes: "This is an internal pilot application for HCMC city operators"

## Step 7: Submit for Review

1. Select the build uploaded in Step 5
2. Click **Submit for Review**
3. Expected review time: **2-7 business days** (Risk R3)

### Common Rejection Reasons & Prevention:
- **Missing privacy policy:** Ensure URL is live and accessible
- **Placeholder content:** All UI text must be real, no "Lorem ipsum"
- **Broken functionality:** Test all screens with staging API
- **Missing APNs setup:** Ensure push notifications work in dev/sandbox

## Risk Mitigation (R3: iOS Reject)

If Apple rejects the submission:
1. Address rejection reasons immediately
2. Resubmit (typically 1-3 days for re-review)
3. **Fallback:** Deploy Android APK + PWA for pilot; resubmit iOS in Sprint 3

---

## Post-Approval Checklist

- [ ] App visible on App Store
- [ ] Push notifications working on production devices
- [ ] Deep linking configured (if applicable)
- [ ] Analytics/monitoring reporting correctly
- [ ] Version number matches expected release
