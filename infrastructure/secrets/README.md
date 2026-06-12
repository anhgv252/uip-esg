# Push Notification Secrets Directory

This directory holds credential files for push notification services.
These files are mounted read-only into the backend container at `/opt/uip/secrets/`.

## Required Files

### FCM (Firebase Cloud Messaging)
- **firebase-adminsdk.json** — Firebase Admin SDK service account key
  - Download from: Firebase Console > Project Settings > Service Accounts > Generate New Private Key
  - This is a JSON file containing `project_id`, `private_key`, `client_email`, etc.

### APNs (Apple Push Notification service)
- **apns-auth-key.p8** — APNs authentication key (PKCS8 format)
  - Obtain from: Apple Developer > Certificates, Identifiers & Profiles > Keys > APNs
  - 10-character Key ID must be set as `APNS_KEY_ID` in .env.staging
  - Team ID must be set as `APNS_TEAM_ID` in .env.staging

## SECURITY
- **NEVER commit these files to git**
- Add `*.json` and `*.p8` to .gitignore (already excluded via `secrets/` pattern)
- Rotate keys if accidentally committed
