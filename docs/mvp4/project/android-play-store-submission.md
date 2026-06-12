# Android Google Play Submission Guide — UIP Operator Mobile

**Package:** `com.uip.smartcity.operator`
**Bundle ID (iOS equivalent):** `com.uip.operatormobile`
**EAS Project ID:** `uip-smartcity-operator`
**Last updated:** 2026-06-12

---

## Prerequisites

1. **Google Play Developer Account** ($25 one-time fee)
   - Sign up at: https://play.google.com/console/signup
   - Requires a Google account

2. **Google Play Console access**
   - https://play.google.com/console

3. **EAS CLI** installed: `npm install -g eas-cli`

---

## Step 1: Create Application in Google Play Console

1. Go to https://play.google.com/console
2. Click **Create app**
3. Configure:
   - **App name:** UIP Operator Mobile
   - **Default language:** Vietnamese
   - **App or game:** App
   - **Free or paid:** Free
4. Click **Create app**
5. Complete **Dashboard** setup items:
   - Privacy policy URL: `https://uip-smartcity.vn/privacy`
   - App access: All features accessible
   - Ads: No ads
   - Content rating: Complete IARC questionnaire
   - Target audience: 18+ (city operators)
   - Store settings: News/Weather > Utilities category

## Step 2: Generate Signing Keystore

```bash
# Generate upload keystore (KEEP SAFE — cannot update app without it)
keytool -genkeypair \
  -v \
  -keystore uip-operator-upload.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 9125 \
  -alias uip-upload \
  -storepass "CHANGE_ME_store_password" \
  -keypass "CHANGE_ME_key_password"

# Fill in certificate info:
#   CN=UIP Smart City, OU=Engineering, O=UIP, L=Ho Chi Minh City, ST=HCMC, C=VN

# BACKUP this file securely! Loss = cannot update the app.
```

### Configure Gradle signing (local development):

Create or update `applications/operator-mobile/android/gradle.properties`:
```properties
MYAPP_UPLOAD_STORE_FILE=uip-operator-upload.jks
MYAPP_UPLOAD_STORE_PASSWORD=CHANGE_ME_store_password
MYAPP_UPLOAD_KEY_ALIAS=uip-upload
MYAPP_UPLOAD_KEY_PASSWORD=CHANGE_ME_key_password
```

## Step 3: Build AAB (Android App Bundle)

### Via EAS Build (Recommended):
```bash
cd applications/operator-mobile

# Build production AAB
eas build --platform android --profile production

# The AAB is built on EAS cloud servers.
# Download when complete.
```

The `eas.json` production profile already configures AAB:
```json
{
  "production": {
    "autoIncrement": { "versionCode": true },
    "android": {
      "buildType": "aab",
      "gradleCommand": ":app:bundleRelease"
    }
  }
}
```

### Alternative: Local build
```bash
cd applications/operator-mobile

# Generate native Android project
npx expo prebuild --platform android

# Build release AAB
cd android && ./gradlew bundleRelease

# Output: android/app/build/outputs/bundle/release/app-release.aab
```

## Step 4: Create Service Account for API Access

1. Go to Google Play Console > Setup > API access
2. Link or create a Google Cloud project
3. Create a service account:
   - Go to Google Cloud Console > IAM & Admin > Service Accounts
   - Create service account: `eas-play-publisher@<project>.iam.gserviceaccount.com`
   - Grant role: "Release Manager" in Play Console
4. Create JSON key and download:
   - Save as `google-service-account.json` in `applications/operator-mobile/`
   - This file is referenced in `eas.json` submit configuration

## Step 5: Upload and Submit

### Via EAS Submit:
```bash
cd applications/operator-mobile

# Submit latest build to Google Play
eas submit --platform android --profile production

# Or specify a specific build:
eas submit --platform android --build-id <BUILD_ID>
```

### Via Google Play Console (Manual):
1. Go to **Production** > **Create new release**
2. Upload the `.aab` file
3. Enter release notes
4. Click **Review release** > **Start rollout to production**

## Step 6: Store Listing Configuration

Complete the following in Google Play Console > Store presence > Main store listing:

### App Details
- **App name:** UIP Operator Mobile
- **Short description (80 chars):** Ung dung van hanh thanh pho thong minh
- **Full description (4000 chars):**
  ```
  UIP Operator Mobile la ung dung quan ly van hanh thanh pho thong minh
  danh cho can bo dieu hanh HCMC.

  Tinh nang chinh:
  - Giam sat chat luong khong khi (AQI) thoi gian thuc
  - Canh bao an toan cong trinh tu dong
  - Theo doi cam bien IoT toan thanh pho
  - Bao cao ESG va phat trien ben vung
  - Giao dich thong bao day tu he thong
  ```

### Screenshots (required)
- Minimum 4 screenshots per form factor
- **Phone:** 16:9 or 9:16, min 320px, max 3840px
- **Tablet (7-inch):** Recommended but optional
- **Tablet (10-inch):** Recommended but optional

Recommended screenshot content:
1. Dashboard with AQI map
2. Alert detail screen
3. Sensor monitoring list
4. ESG report overview

### Icon and Graphics
- **App icon:** 512x512 PNG (use `assets/icon.png`)
- **Feature graphic:** 1024x500 PNG (optional but recommended)
- **Apply icon:** Use `assets/adaptive-icon.png`

### Categorization
- **Category:** Tools
- **Tags:** smart city, IoT, monitoring, operations

## Step 7: Content Rating

Complete IARC questionnaire:
1. Violence: None
2. Sexuality: None
3. Language: None
4. Controlled substances: None
5. User interaction: None (no chat/UGC)
6. Data sharing: Collects device data for push notifications
7. Digital goods: None

Expected rating: **PEGI 3** / **Everyone**

## Review Timeline

- **Google Play:** 1-3 business days (faster than iOS)
- First submission may take longer (7+ days for new accounts)
- Subsequent updates: typically <24 hours

## Risk Mitigation

If Google Play rejects:
1. Review rejection email for specific violation
2. Fix and resubmit (usually processed within 1-2 days)
3. Common issues: missing privacy policy, placeholder content, permission justification

---

## Post-Approval Checklist

- [ ] App live on Google Play Store
- [ ] Push notifications working via FCM
- [ ] Correct API URL pointing to production backend
- [ ] Version code auto-incremented correctly
- [ ] Crash reporting active
- [ ] Service account key rotated if needed

## Automation: CI/CD Integration (Future)

```bash
# Build + submit in one command
eas build --platform android --profile production --auto-submit
```

Configure GitHub Actions or similar CI to trigger on tag push:
```yaml
# .github/workflows/release.yml (example)
on:
  push:
    tags: ['v*']
jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: npm install -g eas-cli
      - run: eas build --platform android --profile production --non-interactive
      - run: eas submit --platform android --profile production --non-interactive
```
