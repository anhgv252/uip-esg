# S11-SEC-01 & S11-TD-04: iOS Certificate + Android APK Store Submission

**Date**: 2026-06-10
**Status**: PREPARATION COMPLETE (pending external account access)

---

## S11-SEC-01: iOS Certificate Submission (1 SP)

### Current Status
- iOS app structure exists in `applications/operator-mobile/ios/`
- Expo EAS build configured for iOS in `eas.json`
- **BLOCKED**: Apple Developer Program account access required

### Prerequisites
1. Apple Developer Program enrollment ($99/year)
2. Access to Apple Developer Portal: https://developer.apple.com
3. App Store Connect access: https://appstoreconnect.apple.com
4. Xcode installed on macOS build machine

### Steps to Submit (when unblocked)
1. **Generate certificates**:
   ```bash
   # Apple Distribution Certificate
   # Sign in to developer.apple.com → Certificates → Create
   # Export .p12 file for EAS
   ```

2. **Generate provisioning profile**:
   ```bash
   # Register app bundle ID: com.uip.smartcity.operator
   # Create App Store provisioning profile
   ```

3. **Configure EAS credentials**:
   ```bash
   cd applications/operator-mobile
   eas credentials
   # Upload .p12 certificate + provisioning profile
   ```

4. **Build and submit**:
   ```bash
   eas build --platform ios --profile production
   eas submit --platform ios --profile production
   ```

5. **App Store metadata** (prepare now):
   - App Name: UIP Smart City — Operator
   - Bundle ID: com.uip.smartcity.operator
   - Category: Business / Utilities
   - Age Rating: 4+ (no restricted content)
   - Description: HCMC Smart City Operations Center for operators — real-time monitoring, alerts, building safety, ESG reporting
   - Keywords: smart city, HCMC, ESG, monitoring, building safety, urban operations
   - Screenshots: Dashboard, Alerts, Buildings, Map views (6.7" and 5.5" sizes)
   - Privacy Policy URL: https://uip-smartcity.vn/privacy
   - Support URL: https://uip-smartcity.vn/support

### Mitigation
- Android APK pipeline is fully operational (see below)
- iOS submission tracked as v3.1 carry-over
- Pilot Impact: LOW — Android is primary mobile delivery channel

---

## S11-TD-04: Android APK Store Submission (2 SP)

### Current Status
- Android APK build pipeline operational via EAS
- Staging build tested and verified
- `eas.json` configured with production profile (AAB format for Play Store)

### Prerequisites
1. Google Play Developer Console access ($25 one-time)
2. Service account key for EAS automated submission
3. App content rating questionnaire completed

### Steps to Submit
1. **Create Google Play Developer account** (if not existing)
   - Go to https://play.google.com/console
   - Pay $25 registration fee
   - Complete developer profile

2. **Create service account for EAS**:
   ```bash
   # Google Cloud Console → IAM → Service Accounts
   # Create service account with "Android Management API" access
   # Download JSON key → save as google-service-account.json
   ```

3. **Set up app in Play Console**:
   - App name: UIP Smart City — Operator
   - Package name: com.uip.smartcity.operator
   - Default language: Vietnamese (vi)
   - Free app

4. **Complete content rating**:
   - IARC questionnaire
   - Expected rating: PEGI 3 / Everyone (no violence, no user-generated content)
   - Declarations: app accesses device location (for building proximity features)

5. **Build production AAB**:
   ```bash
   cd applications/operator-mobile
   eas build --platform android --profile production
   ```

6. **Submit to Play Store**:
   ```bash
   eas submit --platform android --profile production
   # Or manual upload of AAB to Play Console
   ```

7. **Store listing metadata** (prepare now):
   - Title: UIP Smart City — Trung tâm Điều hành
   - Short description: Điều hành đô thị thông minh TP.HCM — giám sát, cảnh báo, an toàn công trình
   - Full description: Ứng dụng dành cho cán bộ điều hành đô thị thông minh TP.HCM. Cung cấp khả năng giám sát cảm biến theo thời gian thực, cảnh báo môi trường và an toàn công trình, báo cáo ESG, và quản lý tòa nhà.
   - Screenshots: 6.7" + 5.5" variants (Dashboard, Alerts, Buildings, Map, ESG, Safety)
   - Feature graphic: 1024×500 banner
   - Category: Business
   - Tags: smart city, urban operations, ESG, building safety

### Production Checklist
- [ ] EAS build succeeds for android/production profile
- [ ] `google-service-account.json` placed in `applications/operator-mobile/`
- [ ] App signing key generated and backed up
- [ ] Store listing completed in Play Console
- [ ] Content rating questionnaire completed
- [ ] Privacy policy URL accessible
- [ ] Internal test track verified before production release

---

## EAS Build Verification

```bash
cd applications/operator-mobile

# Typecheck
npm run typecheck

# Build staging APK (for testing)
npx eas build --platform android --profile staging

# Build production AAB (for store submission)
npx eas build --platform android --profile production
```

---

*Document: store-submission-prep.md | Sprint 11 | 2026-06-10*
