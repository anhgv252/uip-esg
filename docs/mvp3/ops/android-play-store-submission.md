# Android Play Store — APK Submission Guide

**App:** UIP Operator Mobile  
**Package:** `vn.uip.operator`  
**Build tool:** EAS (Expo Application Services)

---

## 1. Pre-submission Checklist

| Item | Status |
|---|---|
| `app.json` — `version` and `android.versionCode` bumped | Required |
| `eas.json` — `production` profile configured | Required |
| `EXPO_TOKEN` secret set in EAS dashboard | Required |
| `google-services.json` in project root | Required for FCM push |
| APK signed with production keystore | Auto-handled by EAS |
| Privacy Policy URL set in Store listing | Required |
| App screenshots (phone + tablet) ready | Required |
| Content rating questionnaire completed | Required |

---

## 2. Build Configuration

### `eas.json`
```json
{
  "cli": { "version": ">= 7.0.0" },
  "build": {
    "preview": {
      "android": { "buildType": "apk" }
    },
    "production": {
      "android": {
        "buildType": "app-bundle",
        "gradleCommand": ":app:bundleRelease"
      }
    }
  },
  "submit": {
    "production": {
      "android": {
        "serviceAccountKeyPath": "./google-play-service-account.json",
        "track": "internal"
      }
    }
  }
}
```

### `app.json` — required fields
```json
{
  "expo": {
    "name": "UIP Operator",
    "slug": "uip-operator",
    "version": "1.0.0",
    "android": {
      "package": "vn.uip.operator",
      "versionCode": 1,
      "googleServicesFile": "./google-services.json",
      "permissions": [
        "NOTIFICATIONS",
        "INTERNET",
        "ACCESS_NETWORK_STATE"
      ]
    }
  }
}
```

---

## 3. Build Steps

```bash
# Step 1 — Install EAS CLI
npm install -g eas-cli

# Step 2 — Login to Expo
eas login

# Step 3 — Configure project (first time only)
eas build:configure

# Step 4 — Build AAB for production
cd applications/operator-mobile
eas build --platform android --profile production --non-interactive

# Step 5 — Check build status
eas build:list --limit 5 --platform android

# Step 6 — Download AAB from EAS dashboard or submit directly
eas submit --platform android --profile production
```

---

## 4. Play Console Setup

### New App Registration
1. Go to [Google Play Console](https://play.google.com/console)
2. Click **Create app** → App name: `UIP Operator`
3. Default language: `Vietnamese — vi`
4. App type: `App`
5. Free / paid: `Free`
6. Accept policies

### Internal Testing Track (First Upload)
1. Navigate to **Internal testing** → Create new release
2. Upload AAB file from EAS build
3. Add testers (Google account emails)
4. Release name: `v1.0.0 (versionCode 1)`
5. Release notes (vi): `Phiên bản thử nghiệm nội bộ — UIP Operator Mobile`

### Store Listing Requirements
- **Short description** (max 80 chars): `Quản lý thành phố thông minh — giám sát cảm biến và cảnh báo theo thời gian thực`
- **Full description** (max 4000 chars): Include sensor monitoring, BMS control, ESG reporting features
- **Screenshots**: Minimum 2 phone screenshots (1080x1920 or 1080x2340)
- **Feature graphic**: 1024x500 px PNG/JPG
- **App icon**: 512x512 px PNG (already in `assets/adaptive-icon.png`)

---

## 5. API Key & Service Account

### Create Service Account for automated submit
1. Play Console → Setup → API access → Link Google Cloud project
2. IAM → Create service account: `eas-submit@<project>.iam.gserviceaccount.com`
3. Role: **Release Manager**
4. Download JSON key → save as `google-play-service-account.json` (gitignored)
5. In Play Console → Invite user → paste service account email → grant **Internal testing** access

---

## 6. Release Checklist Before Production Track

- [ ] All crash-free sessions > 99.5% on internal track (minimum 50 installs)
- [ ] No ANR (Application Not Responding) complaints
- [ ] Privacy Policy URL live and accessible
- [ ] Content rating submitted and approved (likely PEGI 3 / Everyone)
- [ ] Data safety form completed (location: no, personal data: auth token only, encryption: yes)
- [ ] Target API level ≥ 34 (Android 14) — confirmed in `build.gradle`

---

## 7. Version Management

| Field | Location | Update rule |
|---|---|---|
| `version` | `app.json` | Semantic (1.0.0, 1.1.0) |
| `versionCode` | `app.json` > `android` | Increment by 1 each build |
| EAS build number | Auto | Set by EAS from `versionCode` |

**Script to bump versionCode before each build:**
```bash
# In applications/operator-mobile/
node -e "
  const fs = require('fs');
  const app = JSON.parse(fs.readFileSync('app.json','utf8'));
  app.expo.android.versionCode += 1;
  fs.writeFileSync('app.json', JSON.stringify(app, null, 2));
  console.log('versionCode bumped to', app.expo.android.versionCode);
"
```
